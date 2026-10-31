package app.thermida.voice

import android.Manifest
import android.os.Bundle
import android.os.ParcelFileDescriptor
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
 * The coach's mic code with this phone's own speech engines: the phone's voice says one English
 * and one Greek question into files, and those go straight into [VoiceInput] (no microphone).
 * Needs the phone's speech services ready; right after an emulator restart it can fail once.
 */
@RunWith(AndroidJUnit4::class)
class VoiceInputTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dir = File(context.cacheDir, "voice-test").apply { mkdirs() }

    private class Clip(val name: String, val tag: String, val pcm: File, val rate: Int)

    /** A spoken sentence ends by itself and is handed over whole. */
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
        Log.i("VoiceInputTest", line)
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
            tts.setLanguage(Locale.forLanguageTag(tag))
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
            tts.synthesizeToFile(text, Bundle(), wav, name)
            done.await(40, TimeUnit.SECONDS)
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
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt ") {
                channels = buf.getShort(body + 2).toInt()
                rate = buf.getInt(body + 4)
            }
            if (id == "data") {
                val end = if (size <= 0 || body + size > bytes.size) bytes.size else body + size
                val pcm = File(dir, "$name.pcm")
                pcm.writeBytes(bytes.copyOfRange(body, end) + ByteArray(rate * channels * 2))
                return Clip(name, tag, pcm, rate)
            }
            pos = body + size + (size and 1)
        }
        return null
    }
}
