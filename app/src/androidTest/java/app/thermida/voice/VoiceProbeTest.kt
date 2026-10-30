package app.thermida.voice

import android.Manifest
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A probe, not a pass/fail test: what this phone's own speech engines can do for the coach.
 * The phone's voice says one English and one Greek question into files, and those files go
 * straight into the speech recognizer (no microphone), with and without automatic language
 * detection. Everything it learns goes to logcat under the tag "VoiceProbe".
 */
@RunWith(AndroidJUnit4::class)
class VoiceProbeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dir = File(context.cacheDir, "voice-probe").apply { mkdirs() }

    private class Clip(val name: String, val tag: String, val pcm: File, val rate: Int, val channels: Int)

    @Test
    fun probe() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        log("recognizer available=${SpeechRecognizer.isRecognitionAvailable(context)} onDevice=${SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}")
        listOf("en-US", "el-GR").forEach { tag ->
            var support = support(tag)
            log("support $tag before: $support")
            if (!support.startsWith("installed=true")) download(tag)
        }
        val clips = synthesize(
            listOf(
                Triple("en", "en-US", "How many calories did I eat today?"),
                Triple("el", "el-GR", "Πόσες θερμίδες έφαγα σήμερα;"),
            ),
        )
        for (clip in clips) {
            log("${clip.name} fixed ${clip.tag}, on device: ${recognize(clip, auto = false, onDevice = true)}")
            log("${clip.name} auto (starts as en-US), on device: ${recognize(clip, auto = true, onDevice = true)}")
            log("${clip.name} fixed ${clip.tag}, default recognizer: ${recognize(clip, auto = false, onDevice = false)}")
            log("${clip.name} auto (starts as en-US), default recognizer: ${recognize(clip, auto = true, onDevice = false)}")
        }
    }

    /** The coach's own mic code: a spoken sentence ends by itself and is handed over whole. */
    @Test
    fun aSpokenSentenceEndsByItselfInEitherLanguage() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val clips = synthesize(
            listOf(
                Triple("en", "en-US", "How many calories did I eat today?"),
                Triple("el", "el-GR", "Πόσες θερμίδες έφαγα σήμερα;"),
            ),
        )
        assertEquals(2, clips.size)
        for (clip in clips) {
            val done = CountDownLatch(1)
            var result = ""
            val audio = ParcelFileDescriptor.open(clip.pcm, ParcelFileDescriptor.MODE_READ_ONLY)
            lateinit var input: VoiceInput
            instrumentation.runOnMainSync {
                input = VoiceInput(context)
                input.start(
                    if (clip.tag == "el-GR") VoiceLanguage.GREEK else VoiceLanguage.ENGLISH,
                    onText = {},
                    onDone = { result = "done: $it"; done.countDown() },
                    onError = { result = "error: $it"; done.countDown() },
                    audio = TestAudio(audio, clip.rate),
                )
            }
            assertTrue("${clip.name} never finished", done.await(20, TimeUnit.SECONDS))
            audio.close()
            log("${clip.name} hand-over: $result")
            val word = if (clip.tag == "el-GR") "θερμίδες" else "calories"
            assertTrue("${clip.name}: $result", result.startsWith("done:") && result.contains(word, ignoreCase = true))
            instrumentation.runOnMainSync { assertTrue(!input.listening) }
        }
    }

    private fun log(line: String) {
        Log.i("VoiceProbe", line)
    }

    private fun intent(tag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private fun support(tag: String): String {
        val latch = CountDownLatch(1)
        var out = "no answer"
        var recognizer: SpeechRecognizer? = null
        instrumentation.runOnMainSync {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                checkRecognitionSupport(intent(tag), context.mainExecutor, object : RecognitionSupportCallback {
                    override fun onSupportResult(s: RecognitionSupport) {
                        out = "installed=${tag in s.installedOnDeviceLanguages} pending=${tag in s.pendingOnDeviceLanguages} " +
                            "supported=${tag in s.supportedOnDeviceLanguages} online=${tag in s.onlineLanguages} " +
                            "(installed: ${s.installedOnDeviceLanguages})"
                        latch.countDown()
                    }

                    override fun onError(error: Int) {
                        out = "error $error"
                        latch.countDown()
                    }
                })
            }
        }
        latch.await(15, TimeUnit.SECONDS)
        instrumentation.runOnMainSync { recognizer?.destroy() }
        return out
    }

    private fun download(tag: String) {
        instrumentation.runOnMainSync {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer.triggerModelDownload(intent(tag))
            log("asked to download $tag")
            recognizer.destroy()
        }
    }

    private fun synthesize(lines: List<Triple<String, String, String>>): List<Clip> {
        val ready = CountDownLatch(1)
        var status = -99
        lateinit var tts: TextToSpeech
        instrumentation.runOnMainSync { tts = TextToSpeech(context) { status = it; ready.countDown() } }
        ready.await(20, TimeUnit.SECONDS)
        log("tts init=$status engine=${tts.defaultEngine}")
        val clips = mutableListOf<Clip>()
        for ((name, tag, text) in lines) {
            val locale = Locale.forLanguageTag(tag)
            val language = tts.setLanguage(locale)
            val voices = tts.voices.orEmpty().filter { it.locale.language == locale.language }
            log("tts $tag setLanguage=$language voices=${voices.map { "${it.name}(net=${it.isNetworkConnectionRequired})" }}")
            val wav = File(dir, "$name.wav").apply { delete() }
            val done = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = done.countDown()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = done.countDown()

                override fun onError(utteranceId: String?, errorCode: Int) {
                    log("tts $tag error $errorCode")
                    done.countDown()
                }
            })
            val queued = tts.synthesizeToFile(text, Bundle(), wav, name)
            done.await(40, TimeUnit.SECONDS)
            log("tts $tag synthesizeToFile=$queued bytes=${wav.length()}")
            readWav(wav, name, tag)?.let(clips::add)
        }
        tts.shutdown()
        return clips
    }

    /** The PCM samples of a WAV file, with a second of silence after so the recognizer hears the end. */
    private fun readWav(wav: File, name: String, tag: String): Clip? {
        val bytes = wav.takeIf { it.exists() }?.readBytes() ?: return null
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF") return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var rate = 0
        var channels = 0
        var bits = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt ") {
                channels = buf.getShort(body + 2).toInt()
                rate = buf.getInt(body + 4)
                bits = buf.getShort(body + 14).toInt()
            }
            if (id == "data") {
                val end = if (size <= 0 || body + size > bytes.size) bytes.size else body + size
                val pcm = File(dir, "$name.pcm")
                pcm.writeBytes(bytes.copyOfRange(body, end) + ByteArray(rate * channels * 2))
                log("clip $name rate=$rate channels=$channels bits=$bits samples=${(end - body) / 2}")
                return Clip(name, tag, pcm, rate, channels)
            }
            pos = body + size + (size and 1)
        }
        return null
    }

    private fun recognize(clip: Clip, auto: Boolean, onDevice: Boolean): String {
        val latch = CountDownLatch(1)
        val out = StringBuilder()
        val audio = ParcelFileDescriptor.open(clip.pcm, ParcelFileDescriptor.MODE_READ_ONLY)
        val intent = intent(if (auto) "en-US" else clip.tag).apply {
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audio)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, clip.channels)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, clip.rate)
            if (auto) {
                val languages = arrayListOf("en-US", "el-GR")
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, languages)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, languages)
            }
        }
        var recognizer: SpeechRecognizer? = null
        instrumentation.runOnMainSync {
            recognizer = (if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {
                        out.append(" heard")
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { out.append(" partial=\"$it\"") }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}

                    override fun onError(error: Int) {
                        out.append(" error=$error")
                        latch.countDown()
                    }

                    override fun onResults(results: Bundle?) {
                        out.append(" text=\"${results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()}\"")
                        latch.countDown()
                    }

                    override fun onLanguageDetection(results: Bundle) {
                        out.append(
                            " detected=${results.getString(SpeechRecognizer.DETECTED_LANGUAGE)}" +
                                "(confidence ${results.getInt(SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL)})",
                        )
                    }
                })
                startListening(intent)
            }
        }
        if (!latch.await(40, TimeUnit.SECONDS)) out.append(" timed out")
        instrumentation.runOnMainSync { recognizer?.destroy() }
        audio.close()
        return out.toString().trim().ifEmpty { "nothing" }
    }
}
