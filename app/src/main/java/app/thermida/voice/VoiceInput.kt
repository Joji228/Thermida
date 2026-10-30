package app.thermida.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.thermida.data.DEFAULT_PAUSE_MS

/** A recording to recognize instead of the mic, for tests. */
class TestAudio(val pcm: ParcelFileDescriptor, val rate: Int)

/**
 * The coach's mic, using the phone's own speech recognizer (Speech Services by Google on most
 * phones), so Thermida ships no speech model. It listens only between start() and the end of the
 * sentence (or stop()); nothing is recorded or kept. Use it from the main thread.
 *
 * English is recognized on the phone itself when the phone can (the audio never leaves it).
 * Greek has no on-device model, so it always goes through Google's speech service, which may
 * send the audio to Google; so does English on phones without the offline English pack.
 */
class VoiceInput(private val context: Context) {
    /** True from start() until the recognizer has delivered what it heard. */
    var listening by mutableStateOf(false)
        private set

    /** How loud the voice is right now, 0 to 1, for the waveform. */
    var level by mutableFloatStateOf(0f)
        private set

    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())

    /** Turns false once the on-device recognizer turns out to lack the language; then Google's is used. */
    private var onDeviceWorks = true

    /**
     * Listens in [language] until the sentence ends: [pauseMs] with no new words and a quiet mic. [onText] gets the words as they come (each call has everything heard so far),
     * [onDone] the finished sentence, and [onError] a short message when nothing usable was heard.
     */
    fun start(
        language: VoiceLanguage,
        onText: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
        pauseMs: Long = DEFAULT_PAUSE_MS.toLong(),
        /** For tests: 16-bit mono PCM to hear instead of the mic. */
        audio: TestAudio? = null,
    ) {
        release()
        val onDevice = language == VoiceLanguage.ENGLISH && audio == null && onDeviceWorks &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("This phone has no speech recognizer. Install Speech Services by Google.")
            return
        }
        var heard = ""
        var spoke = false
        var lastActivity = SystemClock.uptimeMillis()
        val startedAt = lastActivity
        val session = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = session
        // Ends the sentence after a short pause instead of waiting for the recognizer's own (long,
        // and on Google's online path for Greek sometimes endless) timeout. Also stops if nobody
        // talks for a few seconds, and ends every session after half a minute.
        val watch = object : Runnable {
            override fun run() {
                if (recognizer !== session) return
                val now = SystemClock.uptimeMillis()
                val ended = heard.isNotBlank() && now - lastActivity > pauseMs
                val nobody = !spoke && heard.isBlank() && now - startedAt > NOBODY_MS
                if (ended || nobody || now - startedAt > MAX_MS) session.stopListening() else main.postDelayed(this, 100)
            }
        }
        main.postDelayed(watch, 100)
        fun finish() {
            main.removeCallbacks(watch)
            if (recognizer === session) {
                recognizer = null
                listening = false
                level = 0f
            }
            session.destroy()
        }
        session.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onBeginningOfSpeech() {
                spoke = true
                lastActivity = SystemClock.uptimeMillis()
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Speech sits roughly between -2 and 10 dB here.
                level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                if (rmsdB > LOUD_DB) lastActivity = SystemClock.uptimeMillis()
            }

            override fun onEndOfSpeech() {
                level = 0f
            }

            override fun onPartialResults(partialResults: Bundle?) {
                best(partialResults)?.takeIf { it != heard }?.let {
                    heard = it
                    lastActivity = SystemClock.uptimeMillis()
                    onText(it)
                }
            }

            override fun onResults(results: Bundle?) {
                // Google sometimes ends with an empty result after the live text was right.
                val text = best(results) ?: heard
                finish()
                if (text.isNotBlank()) onDone(text) else onError("Didn't catch that. Try again.")
            }

            override fun onError(error: Int) {
                finish()
                // The on-device recognizer can't do the language here: use Google's from now on, and
                // start listening again with it straight away.
                if (onDevice && heard.isBlank() && error in MISSING_LANGUAGE) {
                    onDeviceWorks = false
                    start(language, onText, onDone, onError, pauseMs, audio)
                    return
                }
                // Stopping after words came through isn't a failure: use what was heard.
                if (heard.isNotBlank() && error in QUIET_ERRORS) onDone(heard) else onError(message(error))
            }
        })
        listening = true
        session.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, pauseMs)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, pauseMs)
                .apply {
                    if (audio != null) {
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audio.pcm)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, audio.rate)
                    }
                },
        )
    }

    /** Stops listening; the recognizer still hands over what it heard so far. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Drops the recognizer at once, without delivering anything: cancel, or the coach closing. */
    fun release() {
        recognizer?.destroy()
        recognizer = null
        listening = false
        level = 0f
    }

    private fun best(results: Bundle?) =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val NOBODY_MS = 6_000L
        const val MAX_MS = 30_000L

        /** Louder than this counts as still talking. */
        const val LOUD_DB = 5f

        val QUIET_ERRORS = setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT)

        /** The on-device recognizer has no model for the language (not downloaded, or not offered). */
        val MISSING_LANGUAGE = setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)

        fun message(error: Int) = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try again."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Voice input needs an internet connection."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Thermida needs the microphone for voice input."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice input is busy. Try again in a moment."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "That language isn't available for voice input on this phone."
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many tries. Wait a moment."
            else -> "Voice input stopped. Try again."
        }
    }
}
