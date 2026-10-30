package app.thermida.ui.coach

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.thermida.ui.components.RoundIconButton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import app.thermida.voice.VoiceInput
import app.thermida.voice.VoiceLanguage
import app.thermida.voice.VoiceOutput

/**
 * The chat's mic and speaker: the phone's recognizer and voices, plus the saved choices (which
 * language the mic listens for, and whether answers are read aloud).
 */
class CoachVoice(
    val input: VoiceInput,
    val output: VoiceOutput,
    val language: VoiceLanguage,
    val speak: Boolean,
    val onLanguage: (VoiceLanguage) -> Unit,
    /** A spoken question was sent: its answer is read aloud even with the speaker off. */
    val onSpokenQuestion: () -> Unit,
    val onSpeak: (Boolean) -> Unit,
    /** True while a back-and-forth by voice is going: the mic reopens after each spoken answer. */
    val conversation: Boolean,
    /** Counts up each time the mic should open again by itself. */
    val listenAgain: Int,
    /** The pause that ends a spoken question, in milliseconds. */
    val pauseMs: Long,
    /** The conversation is over: something other than voice took the lead. */
    val onLoopEnd: () -> Unit,
    /** Why the last answer couldn't be read aloud, until it has been shown. */
    val problem: String? = null,
    val onProblemShown: () -> Unit = {},
)

/** What the text box shows for voice: the language chip and the mic, with its state. */
internal class VoiceBar(
    val listening: Boolean,
    val level: Float,
    val language: VoiceLanguage,
    val onMic: () -> Unit,
    val onLanguage: () -> Unit,
)

/** The language chip and the mic, side by side at the end of the text box. */
@Composable
internal fun VoiceControls(bar: VoiceBar) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
        // The mic listens in one language at a time; this says which, and a tap switches.
        Surface(
            onClick = bar.onLanguage,
            enabled = !bar.listening,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(if (bar.listening) 0.5f else 1f),
        ) {
            Text(
                bar.language.label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        MicButton(bar.listening, bar.level, bar.onMic)
    }
}

/** Grey while idle; filled with the accent while listening, with a ring that swells with the voice. */
@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (listening) 1f + level * 0.45f else 0.8f, tween(90), label = "mic ring")
    val accent = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
        if (listening) {
            Box(
                Modifier
                    .size(36.dp)
                    .scale(ring)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.25f)),
            )
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (listening) accent else Color.Transparent,
            contentColor = if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mic, if (listening) "Stop listening" else "Speak to the coach", Modifier.size(22.dp))
            }
        }
    }
}

/**
 * The text box while the mic listens: Cancel, a waveform that moves with the voice, and Send for
 * sending before the pause. The sentence is also sent by itself after a short pause.
 */
@Composable
internal fun VoiceModeBar(level: Float, language: VoiceLanguage, onCancel: () -> Unit, onSendNow: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.Rounded.Close, "Cancel", onCancel, size = 48.dp)
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 18.dp, end = 12.dp)) {
                Waveform(level, Modifier.weight(1f).height(36.dp))
                Spacer(Modifier.width(10.dp))
                Text(language.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        RoundIconButton(
            Icons.Rounded.ArrowUpward,
            "Send now",
            onSendNow,
            size = 48.dp,
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Rounded bars in the accent colour: a gentle ripple while quiet, tall and lively with the voice. */
@Composable
private fun Waveform(level: Float, modifier: Modifier) {
    val loud by animateFloatAsState(level, tween(110), label = "voice level")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "wave phase",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val bars = 28
        val gap = size.width / bars
        val barWidth = gap * 0.45f
        for (i in 0 until bars) {
            // The middle bars move the most, like a voice note.
            val centre = 1f - abs(i - (bars - 1) / 2f) / (bars / 2f)
            val wave = 0.5f + 0.5f * sin(phase + i * 0.55f)
            val height = size.height * (0.12f + (0.18f + 0.7f * loud) * centre * wave).coerceAtMost(1f)
            drawRoundRect(
                color = color.copy(alpha = 0.55f + 0.45f * centre),
                topLeft = Offset(i * gap + (gap - barWidth) / 2, (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}
