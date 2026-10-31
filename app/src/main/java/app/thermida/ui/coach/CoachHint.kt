package app.thermida.ui.coach

import androidx.compose.ui.res.stringResource
import app.thermida.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Shown once after the coach is turned on, until the user pulls it down for the first time: a
 * finger drags the drop down on its stretching neck past the line, lets go, and the drop snaps free,
 * on a loop. Nothing here takes touches except Skip, so the real gesture works right through it.
 */
@Composable
fun CoachHint(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    // 0 to 1 on the way to the line, then on to 1.6 for the stretch past it.
    val travel = remember { Animatable(0f) }
    val snap = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(400)
        while (true) {
            travel.snapTo(0f)
            snap.snapTo(0f)
            travel.animateTo(1.6f, tween(900, easing = FastOutSlowInEasing))
            delay(150)
            snap.animateTo(1f, tween(CoachPull.SNAP_MS, easing = LinearEasing))
            delay(900)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxSize()
            // Darkens the top a little so the demo reads over any screen.
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.72f), 0.55f to Color.Transparent))
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                val popped = snap.value > 0f
                val reach = with(LocalDensity.current) { 64.dp.toPx() }
                val sparkle = rememberVectorPainter(Icons.Rounded.AutoAwesome)
                val fill = MaterialTheme.colorScheme.surfaceContainerHighest
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (popped) (1f - snap.value * 1.5f).coerceIn(0f, 1f) else travel.value.coerceIn(0.25f, 1f) },
                ) {
                    val progress = travel.value.coerceAtMost(1f)
                    val strain = ((travel.value - 1f) / 0.6f).coerceIn(0f, 1f)
                    val pulled = reach * (progress + 0.6f * strain)
                    drawCoachDrop(
                        DropLook(progress, strain, snap.value, popped, armed = if (travel.value >= 1f) 1f else 0f),
                        centerY = pulled + 0.7f * BUBBLE_DP.dp.toPx(),
                        fill = fill,
                        accent = primary,
                        sparkle = sparkle,
                    )
                }
                // The finger, a step behind the drop, lifting off as it snaps free.
                val y = 12.dp + 64.dp * (travel.value.coerceAtMost(1f) + 0.6f * ((travel.value - 1f) / 0.6f).coerceIn(0f, 1f))
                Icon(
                    Icons.Rounded.TouchApp,
                    null,
                    Modifier
                        .offset(x = 22.dp, y = y + 30.dp)
                        .size(40.dp)
                        .graphicsLayer {
                            alpha = if (popped) 1f - snap.value else 1f
                            translationY = if (popped) -16.dp.toPx() * snap.value else 0f
                        },
                    tint = Color.White,
                )
            }
            Column(
                Modifier
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.pull_down_and_let_go), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.drag_down_anywhere_on_the_screen),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                TextButton(onClick = onSkip) { Text(stringResource(R.string.skip)) }
            }
        }
    }
}
