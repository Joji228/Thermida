package app.thermida.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.thermida.data.DEFAULT_SPEECH_RATE

/**
 * Reads coach answers aloud with the phone's own voices, choosing Greek or English per sentence
 * and preferring voices that work offline. The engine starts on the first answer and stops with
 * [shutdown]. Use it from the main thread.
 */
class VoiceOutput(private val context: Context) {
    /** True while an answer is being read. */
    var speaking by mutableStateOf(false)
        private set

    /** How fast the voice talks: 1.0 is Android's normal speed. Applies from the next answer. */
    var rate: Float = DEFAULT_SPEECH_RATE

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var waiting: Pair<String, (() -> Unit)?>? = null
    private var lastId: String? = null
    private var finished: (() -> Unit)? = null
    private var failed: ((String) -> Unit)? = null

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post { speaking = true }
        }

        override fun onDone(utteranceId: String?) {
            main.post {
                if (utteranceId != lastId) return@post
                speaking = false
                val done = finished
                finished = null
                done?.invoke()
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            main.post { speaking = false }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            main.post { fail(CANT_READ) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post { fail(CANT_READ) }
        }
    }

    /**
     * Reads [answer] aloud. [onFinished] runs when the last sentence has been spoken to the end:
     * not when it was stopped or failed. An answer with nothing to read calls it at once.
     * [onFailed] gets a short reason when the phone can't read it (no voice engine, a missing
     * language, an engine error), so the screen can say so instead of going quiet.
     */
    fun speak(answer: String, onFinished: (() -> Unit)? = null, onFailed: ((String) -> Unit)? = null) {
        val text = SpeechText.clean(answer)
        if (text.isBlank()) {
            onFinished?.invoke()
            return
        }
        failed = onFailed
        if (ready) {
            say(text, onFinished)
            return
        }
        waiting = text to onFinished
        if (engine == null) {
            engine = TextToSpeech(context) { status ->
                main.post {
                    ready = status == TextToSpeech.SUCCESS
                    val next = waiting
                    waiting = null
                    when {
                        ready && next != null -> say(next.first, next.second)
                        // The engine didn't start: drop it, so the next answer tries again.
                        !ready -> {
                            engine?.shutdown()
                            engine = null
                            if (next != null) fail(NO_ENGINE)
                        }
                    }
                }
            }.apply { setOnUtteranceProgressListener(progress) }
        }
    }

    fun stop() {
        waiting = null
        finished = null
        failed = null
        engine?.stop()
        speaking = false
    }

    /** Reading stopped because of a problem: nothing more is said, and the screen is told why. */
    private fun fail(reason: String) {
        speaking = false
        finished = null
        val report = failed
        failed = null
        engine?.stop()
        report?.invoke(reason)
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun say(text: String, onFinished: (() -> Unit)?) {
        val tts = engine ?: return
        finished = onFinished
        tts.setSpeechRate(rate)
        val parts = SpeechText.segments(text)
        parts.forEachIndexed { i, (language, part) ->
            if (!use(tts, language)) {
                fail(if (language == VoiceLanguage.GREEK) NO_GREEK else NO_ENGLISH)
                return
            }
            val id = "coach-${System.nanoTime()}-$i"
            if (i == parts.lastIndex) lastId = id
            tts.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    /**
     * The language's default voice, swapped for an offline one when the default needs the network.
     * False when the phone has no voice for the language at all.
     */
    private fun use(tts: TextToSpeech, language: VoiceLanguage): Boolean {
        val result = tts.setLanguage(language.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false
        if (tts.voice?.isNetworkConnectionRequired != true) return true
        offline(tts, language)?.let { tts.voice = it }
        return true
    }

    private fun offline(tts: TextToSpeech, language: VoiceLanguage): Voice? {
        val voices = tts.voices.orEmpty().filter {
            it.locale.language == language.locale.language && !it.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features
        }
        return voices.firstOrNull { it.locale.country == language.locale.country } ?: voices.firstOrNull()
    }

    private companion object {
        const val CANT_READ = "Couldn't read that aloud."
        const val NO_ENGINE = "This phone has no voice for reading aloud. Install Speech Services by Google."
        const val NO_GREEK = "No Greek voice on this phone. Add one in Android's text-to-speech settings."
        const val NO_ENGLISH = "No English voice on this phone. Add one in Android's text-to-speech settings."
    }
}
