package app.thermida.ui.coach

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pull down to call the coach, like pull-to-refresh: drag down while the page is already at its top
 * (or has nothing to scroll). A drop of liquid stretches down from the top edge on a thinning neck;
 * once it's past the line, letting go snaps it free and the chat swells out of it. A quick flick
 * opens it too. Letting go before the line springs it back up and nothing happens.
 */
@Stable
class CoachPull internal constructor(
    private val scope: CoroutineScope,
    private val threshold: Float,
    private val max: Float,
    private val flick: Float,
) {
    /** How far the drop has come down, in pixels. */
    var pull by mutableFloatStateOf(0f)
        private set

    /** Runs 0 to 1 once the drop lets go: its neck snaps back up and it wobbles, then everything resets. */
    val snap = Animatable(0f)

    var popped by mutableStateOf(false)
        private set

    internal var enabled = false
    internal var onStep: () -> Unit = {}
    internal var onArm: () -> Unit = {}
    internal var onPop: () -> Unit = {}

    /** 0 to 1 on the way to the line. */
    val progress get() = (pull / threshold).coerceIn(0f, 1f)

    /** Past the line: letting go now opens the coach. */
    val armed get() = pull >= threshold

    /** 0 to 1 past the line, towards the furthest the drop goes: its neck thins as if about to break. */
    val strain get() = ((pull - threshold) / (max - threshold)).coerceIn(0f, 1f)

    // How far the finger has gone; the drop follows it less and less (see stretch).
    private var finger = 0f
    private var steps = 0
    private var settleJob: Job? = null

    /** A finger movement; positive is downwards. */
    internal fun drag(dy: Float) {
        if (!enabled || popped) return
        settleJob?.cancel()
        val wasArmed = armed
        finger = (finger + dy).coerceAtLeast(0f)
        pull = stretch(finger)
        // Light ticks on the way to the line, a firm one when it's reached.
        val step = (progress * STEPS).toInt()
        if (!armed && step > steps) onStep()
        steps = step
        if (armed && !wasArmed) onArm()
    }

    /** Like pulling on something elastic: the further it goes, the harder it gets, never past [max]. */
    private fun stretch(distance: Float) = max * (1f - 1f / (1f + distance * RESISTANCE / max))

    /** The finger let go, moving at [velocity] pixels a second (positive is downwards). */
    internal fun release(velocity: Float = 0f) {
        if (popped) return
        if (pull == 0f) {
            finger = 0f
            return
        }
        if (armed || (velocity > flick && pull > threshold * 0.4f)) {
            scope.launch { pop() }
            return
        }
        finger = 0f
        steps = 0
        // Back up at the finger's speed if it was flicked up, otherwise a soft spring. No bounce: one
        // that overshoots would take the drop past the top and bring it back faintly, like a flash.
        settleJob = scope.launch {
            animate(
                pull,
                0f,
                initialVelocity = min(velocity, 0f),
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) { v, _ -> pull = v.coerceAtLeast(0f) }
        }
    }

    private suspend fun pop() {
        popped = true
        onPop()
        snap.snapTo(0f)
        snap.animateTo(1f, tween(SNAP_MS, easing = LinearEasing))
        pull = 0f
        finger = 0f
        steps = 0
        snap.snapTo(0f)
        popped = false
    }

    /** The page is at its top and the finger keeps pulling down: that leftover becomes the pull. */
    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Pushing back up while the drop is out takes it back in before the page scrolls.
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
            release(available.y)
            return if (pulling) available else Velocity.Zero
        }
    }

    internal companion object {
        const val RESISTANCE = 1.2f
        const val STEPS = 3
        const val SNAP_MS = 320
    }
}

@Composable
fun rememberCoachPull(): CoachPull {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(density) {
        with(density) { CoachPull(scope, threshold = 64.dp.toPx(), max = 130.dp.toPx(), flick = 1_400.dp.toPx()) }
    }
}

/**
 * Listens for the pull on everything inside. Scrolling pages hand over what they can't scroll
 * (nested scroll); pages with nothing to scroll, like the camera, are read straight from the finger.
 * A gesture any page uses itself (a sideways swipe, a scroll, a pinch) is left alone.
 */
fun Modifier.coachPull(
    state: CoachPull,
    enabled: Boolean,
    onStep: () -> Unit,
    onArm: () -> Unit,
    onPop: () -> Unit,
): Modifier {
    state.enabled = enabled
    state.onStep = onStep
    state.onArm = onArm
    state.onPop = onPop
    if (!enabled) return this
    return nestedScroll(state.connection).pointerInput(state) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val tracker = VelocityTracker()
            var total = Offset.Zero
            var active = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || event.changes.count { it.pressed } > 1) break
                    tracker.addPosition(change.uptimeMillis, change.position)
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
                if (active) state.release(tracker.calculateVelocity().y)
            }
        }
    }
}

/** The drop's width and height at full size, and where its centre is (for the chat that opens out of it). */
internal const val BUBBLE_DP = 52

internal fun bubbleCenterY(statusBarTop: Float, pull: Float, density: Density): Float =
    statusBarTop + pull + 0.7f * with(density) { BUBBLE_DP.dp.toPx() }

/**
 * How the drop looks at one moment, so the real pull and the tutorial draw the very same thing:
 * [progress] towards the line, [strain] past it, and [snap] once it has let go.
 */
internal class DropLook(val progress: Float, val strain: Float, val snap: Float, val popped: Boolean, val armed: Float)

/**
 * Draws the drop hanging from the top edge (y = 0) on its neck, centred at [centerY]: a wide base
 * clinging to the edge, a waist that thins as it stretches, and the round drop with a wet gleam and
 * the coach's sparkle. Once it lets go, the upper part of the neck springs back into the edge, the
 * tip it leaves on the drop sinks in, and the drop wobbles.
 */
internal fun DrawScope.drawCoachDrop(
    look: DropLook,
    centerY: Float,
    fill: Color,
    accent: Color,
    sparkle: VectorPainter,
) {
    val cx = size.width / 2f
    val full = BUBBLE_DP.dp.toPx() / 2f
    val radius = full * (if (look.popped) 1f else 0.55f + 0.45f * look.progress)
    val color = lerp(fill, accent, 0.22f * look.armed)
    val joinDeg = 62f - 28f * look.progress - 10f * look.strain
    val join = Math.toRadians(joinDeg.toDouble())
    val joinX = radius * sin(join).toFloat()
    val joinY = centerY - radius * cos(join).toFloat()
    val waistY = joinY * 0.5f
    val base = radius * (1.1f + 0.6f * look.progress)

    if (!look.popped) {
        val waist = radius * (0.42f - 0.24f * look.progress) * (1f - 0.5f * look.strain)
        drawPath(neck(cx, base, waistY, waist, joinX, joinY, centerY, radius, joinDeg), color)
    } else {
        // The neck broke at its waist: the top half springs back into the edge...
        val back = FastOutSlowInEasing.transform((look.snap * 1.6f).coerceAtMost(1f))
        val stub = waistY * (1f - back)
        if (stub > 1f) {
            // A thin strand, wide only where it still clings to the edge, curling back up into it.
            val half = base * (1f - back) * (1f - back)
            val thin = radius * 0.12f * (1f - back)
            drawPath(
                Path().apply {
                    moveTo(cx - half, 0f)
                    cubicTo(cx - thin - (half - thin) * 0.15f, 0f, cx - thin, stub * 0.4f, cx, stub)
                    cubicTo(cx + thin, stub * 0.4f, cx + thin + (half - thin) * 0.15f, 0f, cx + half, 0f)
                    close()
                },
                color,
            )
        }
        // ...and the tip left on the drop sinks into it.
        val tip = (joinY - waistY) * (1f - back)
        if (tip > 1f) {
            drawPath(
                Path().apply {
                    moveTo(cx - joinX, joinY + 2f)
                    quadraticTo(cx - joinX * 0.3f, joinY - tip * 0.3f, cx, joinY - tip)
                    quadraticTo(cx + joinX * 0.3f, joinY - tip * 0.3f, cx + joinX, joinY + 2f)
                    close()
                },
                color,
            )
        }
    }

    // The drop itself; after letting go it wobbles, wider then taller, settling as the chat grows.
    val wobble = if (look.popped) 0.14f * sin(look.snap * 2f * PI).toFloat() * (1f - look.snap) else 0f
    scale(1f + wobble, 1f - wobble, pivot = Offset(cx, centerY)) {
        drawCircle(color, radius, Offset(cx, centerY))
        val gleam = radius - 6.dp.toPx()
        if (gleam > 4.dp.toPx()) {
            drawArc(
                color = Color.White.copy(alpha = 0.38f),
                startAngle = 198f,
                sweepAngle = 52f,
                useCenter = false,
                topLeft = Offset(cx - gleam, centerY - gleam),
                size = Size(2f * gleam, 2f * gleam),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        val icon = 24.dp.toPx() * radius / full
        translate(cx - icon / 2f, centerY - icon / 2f) {
            with(sparkle) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(accent)) }
        }
    }
}

/**
 * The neck: it clings to the top edge in a soft curve, thins to a narrow waist, then flares out and
 * runs smoothly into the drop's outline, so the whole thing reads as one piece of liquid.
 */
private fun neck(
    cx: Float,
    base: Float,
    waistY: Float,
    waist: Float,
    joinX: Float,
    joinY: Float,
    centerY: Float,
    radius: Float,
    joinDeg: Float,
) = Path().apply {
    val rad = Math.toRadians(joinDeg.toDouble())
    val tx = cos(rad).toFloat()
    val ty = sin(rad).toFloat()
    // How far the curves reach along the drop's edge where they meet it, so there's no corner.
    val k = (joinY - waistY) * 0.55f
    moveTo(cx - base, 0f)
    cubicTo(cx - waist - (base - waist) * 0.12f, 0f, cx - waist, waistY * 0.3f, cx - waist, waistY)
    cubicTo(cx - waist, waistY + (joinY - waistY) * 0.5f, cx - joinX + k * tx, joinY - k * ty, cx - joinX, joinY)
    // Round the drop's underside, from where the neck meets it on the left to the right.
    arcTo(Rect(Offset(cx, centerY), radius), -90f - joinDeg, -(360f - 2f * joinDeg), false)
    cubicTo(cx + joinX - k * tx, joinY - k * ty, cx + waist, waistY + (joinY - waistY) * 0.5f, cx + waist, waistY)
    cubicTo(cx + waist, waistY * 0.3f, cx + waist + (base - waist) * 0.12f, 0f, cx + base, 0f)
    close()
}

/**
 * The drop that follows the finger down from the top, and the short "Pull for coach" / "Release to
 * ask" line under it while [showHint].
 */
@Composable
fun CoachPullBubble(state: CoachPull, showHint: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val armed by animateFloatAsState(if (state.armed || state.popped) 1f else 0f, tween(160), label = "armed")
    val sparkle = rememberVectorPainter(Icons.Rounded.AutoAwesome)
    val fill = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primary
    if (state.pull <= 0f && !state.popped) return
    val centerY = bubbleCenterY(top, state.pull, density)
    val height = with(density) { (centerY + BUBBLE_DP.dp.toPx() * 2f).toDp() + 40.dp }
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Shows up quickly as the pull starts (the square root) but fades all the way out as it
                    // goes back up, so letting go early never leaves a faint drop behind. After letting go
                    // it gives way to the chat swelling out of the same spot.
                    alpha = if (state.popped) (1f - state.snap.value * 1.5f).coerceIn(0f, 1f) else sqrt(state.progress)
                },
        ) {
            drawCoachDrop(DropLook(state.progress, state.strain, state.snap.value, state.popped, armed), centerY, fill, accent, sparkle)
        }
        if (showHint && !state.popped) {
            val radius = with(density) { BUBBLE_DP.dp.toPx() / 2f } * (0.55f + 0.45f * state.progress)
            Text(
                if (state.armed) "Release to ask" else "Pull for coach",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (centerY + radius + with(density) { 10.dp.toPx() }).roundToInt()) }
                    .graphicsLayer { alpha = state.progress },
            )
        }
    }
}
