package app.thermida.ui.coach

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pull down and hold to call the coach, like pull-to-refresh: drag down while the page is already at
 * its top (or has nothing to scroll). A soft glow grows down from the top edge as you pull; past the
 * line, keep holding while a thin line fills along the top, and the coach opens. Letting go before
 * that springs it back and nothing happens, so a stray swipe never opens it.
 */
@Stable
class CoachPull internal constructor(
    private val scope: CoroutineScope,
    private val threshold: Float,
    private val max: Float,
) {
    /** How far the glow has come down, in pixels. */
    var pull by mutableFloatStateOf(0f)
        private set

    /** Fills from 0 to 1 while held past the line; at 1 the coach opens. */
    val hold = Animatable(0f)

    /** Runs 0 to 1 once it opens: the glow swells and gives way to the chat, then everything resets. */
    val snap = Animatable(0f)

    var popped by mutableStateOf(false)
        private set

    internal var enabled = false
    internal var onArm: () -> Unit = {}
    internal var onPop: () -> Unit = {}

    /** 0 to 1 on the way to the line. */
    val progress get() = (pull / threshold).coerceIn(0f, 1f)

    /** Past the line: holding now opens the coach. */
    val armed get() = pull >= threshold

    // How far the finger has gone; the glow follows it less and less (see stretch).
    private var finger = 0f
    private var holdJob: Job? = null
    private var settleJob: Job? = null

    /** A finger movement; positive is downwards. */
    internal fun drag(dy: Float) {
        if (!enabled || popped) return
        settleJob?.cancel()
        val wasArmed = armed
        finger = (finger + dy).coerceAtLeast(0f)
        pull = stretch(finger)
        if (armed && !wasArmed) {
            onArm()
            holdJob = scope.launch {
                hold.snapTo(0f)
                hold.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                pop()
            }
        } else if (!armed && wasArmed) {
            holdJob?.cancel()
            scope.launch { hold.snapTo(0f) }
        }
    }

    /** Like pulling on something elastic: the further it goes, the harder it gets, never past [max]. */
    private fun stretch(distance: Float) = max * (1f - 1f / (1f + distance * RESISTANCE / max))

    internal fun release() {
        if (popped) return
        holdJob?.cancel()
        scope.launch { hold.snapTo(0f) }
        finger = 0f
        if (pull == 0f) return
        // No bounce: one that overshoots would take the glow past the top and bring it back faintly.
        settleJob = scope.launch {
            animate(pull, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                pull = v.coerceAtLeast(0f)
            }
        }
    }

    private suspend fun pop() {
        popped = true
        onPop()
        snap.snapTo(0f)
        snap.animateTo(1f, tween(SNAP_MS, easing = FastOutSlowInEasing))
        pull = 0f
        finger = 0f
        hold.snapTo(0f)
        snap.snapTo(0f)
        popped = false
    }

    /** The page is at its top and the finger keeps pulling down: that leftover becomes the pull. */
    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Pushing back up while the glow is out takes it back in before the page scrolls.
            if (pull > 0f && available.y < 0f && source == NestedScrollSource.UserInput) {
                val before = finger
                drag(available.y)
                return Offset(0f, finger - before)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            drag(available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val pulling = pull > 0f
            release()
            return if (pulling) available else Velocity.Zero
        }
    }

    internal companion object {
        const val RESISTANCE = 1.2f
        const val HOLD_MS = 700
        const val SNAP_MS = 300
    }
}

@Composable
fun rememberCoachPull(): CoachPull {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(density) { with(density) { CoachPull(scope, threshold = 72.dp.toPx(), max = 140.dp.toPx()) } }
}

/**
 * Listens for the pull on everything inside. Scrolling pages hand over what they can't scroll
 * (nested scroll); pages with nothing to scroll, like the camera, are read straight from the finger.
 * A gesture any page uses itself (a sideways swipe, a scroll, a pinch) is left alone.
 */
fun Modifier.coachPull(state: CoachPull, enabled: Boolean, onArm: () -> Unit, onPop: () -> Unit): Modifier {
    state.enabled = enabled
    state.onArm = onArm
    state.onPop = onPop
    if (!enabled) return this
    return nestedScroll(state.connection).pointerInput(state) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            var total = Offset.Zero
            var active = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || event.changes.count { it.pressed } > 1) break
                    val delta = change.positionChangeIgnoreConsumed()
                    if (!active) {
                        // Something on the page took this gesture (a scroll, a swipe): not ours.
                        if (change.isConsumed) break
                        total += delta
                        when {
                            total.y > slop && total.y > abs(total.x) * 1.5f -> {
                                active = true
                                state.drag(total.y - slop)
                                change.consume()
                            }
                            abs(total.x) > slop || total.y < -slop -> break
                        }
                    } else {
                        state.drag(delta.y)
                        change.consume()
                    }
                }
            } finally {
                if (active) state.release()
            }
        }
    }
}

/** The size of the small drop the chat swells out of when it opens. */
internal const val BUBBLE_DP = 52

/** Where the chat opens from: the middle of the glow at this pull. */
internal fun pullCenterY(statusBarTop: Float, pull: Float): Float = statusBarTop + pull * 0.55f

/**
 * The glow: a soft wash of the accent colour spreading down from the top edge, deepest in the
 * middle, as if something were pressing in from above the screen. It grows with [progress] and
 * brightens while [hold] fills, which a thin line along the top edge also shows. [snap] runs once it
 * opens: the glow swells and fades into the chat.
 */
internal fun DrawScope.drawCoachGlow(depth: Float, progress: Float, hold: Float, snap: Float, accent: Color) {
    val cx = size.width / 2f
    val reach = depth * (1f + 0.6f * snap)
    val strength = (0.22f + 0.32f * progress + 0.3f * hold) * (1f - snap)
    if (reach > 1f && strength > 0f) {
        drawRect(
            Brush.radialGradient(
                colorStops = arrayOf(0f to accent.copy(alpha = strength), 0.55f to accent.copy(alpha = strength * 0.35f), 1f to Color.Transparent),
                center = Offset(cx, -reach * 0.15f),
                radius = reach * 1.5f + size.width * 0.18f,
            ),
            size = Size(size.width, reach * 1.6f),
        )
    }
    if (hold > 0f && snap == 0f) {
        val width = size.width * 0.5f * hold
        val thick = 3.dp.toPx()
        drawRoundRect(
            accent.copy(alpha = 0.85f),
            topLeft = Offset(cx - width / 2f, 0f),
            size = Size(width, thick),
            cornerRadius = CornerRadius(thick / 2f),
        )
    }
}

/**
 * The glow that follows the finger down from the top, and the short "Pull down for coach" /
 * "Keep holding" line under it while [showHint].
 */
@Composable
fun CoachPullBubble(state: CoachPull, showHint: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val accent = MaterialTheme.colorScheme.primary
    if (state.pull <= 0f && !state.popped) return
    val depth = top + state.pull
    val height = with(density) { (depth * 2.2f).toDp() + 40.dp }
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxSize()) { drawCoachGlow(depth, state.progress, state.hold.value, state.snap.value, accent) }
        if (showHint && !state.popped) {
            Text(
                if (state.armed) "Keep holding" else "Pull down for coach",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (depth + with(density) { 6.dp.toPx() }).roundToInt()) }
                    .graphicsLayer { alpha = state.progress },
            )
        }
    }
}
