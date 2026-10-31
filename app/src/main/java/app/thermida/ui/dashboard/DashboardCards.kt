package app.thermida.ui.dashboard

import androidx.compose.ui.res.stringResource
import app.thermida.R
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import app.thermida.ui.components.repeatOnHold
import app.thermida.ui.components.RepeatIconButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.foundation.layout.heightIn
import app.thermida.data.db.WeightEntity
import java.time.Instant
import java.time.ZoneId
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thermida.data.db.MealWithItems
import app.thermida.data.db.macros
import app.thermida.domain.MealType
import app.thermida.domain.BodyCompositionCalc
import app.thermida.domain.UserProfile
import app.thermida.ui.components.MacroStat
import app.thermida.ui.components.ThermidaCard
import app.thermida.ui.components.NumberStepper
import app.thermida.ui.components.Pill
import app.thermida.ui.components.PrimaryButton
import app.thermida.ui.components.ProgressLine
import app.thermida.ui.components.RoundIconButton
import app.thermida.ui.components.rememberHaptics
import app.thermida.ui.components.SecondaryButton
import app.thermida.ui.components.SectionTitle
import app.thermida.ui.components.WeightChart
import app.thermida.ui.components.format
import app.thermida.ui.result.PhotoViewer
import app.thermida.ui.theme.Numeric
import app.thermida.ui.theme.Nutrient
import coil3.compose.AsyncImage
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun CaloriesCard(state: DashboardState) {
    val goal = state.targets?.kcal ?: 2000
    val eaten = state.eaten.kcal
    val left = goal - eaten
    val over = left < 0
    ThermidaCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.calories), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.plan.estimate?.active == true && state.profile?.customKcal == null) {
                        Spacer(Modifier.width(8.dp))
                        Pill("Smart", MaterialTheme.colorScheme.primary)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(eaten.format(), style = MaterialTheme.typography.displaySmall.merge(Numeric))
                    Text(
                        " / ${goal.format()} kcal",
                        style = MaterialTheme.typography.bodyMedium.merge(Numeric),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    abs(left).format(),
                    style = MaterialTheme.typography.titleLarge.merge(Numeric),
                    color = if (over) Nutrient.Protein else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (over) "over" else "left",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ProgressLine((eaten / goal).toFloat(), if (over) Nutrient.Protein else MaterialTheme.colorScheme.primary, height = 10.dp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MacroStat("Protein", state.eaten.protein, state.targets?.protein ?: 0, Nutrient.Protein, Modifier.weight(1f))
            MacroStat("Carbs", state.eaten.carbs, state.targets?.carbs ?: 0, Nutrient.Carbs, Modifier.weight(1f))
            MacroStat("Fat", state.eaten.fat, state.targets?.fat ?: 0, Nutrient.Fat, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun WaterCard(ml: Int, goalMl: Int, onAdd: () -> Unit, onUndo: () -> Unit) {
    ThermidaCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Nutrient.Water.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.WaterDrop, null, tint = Nutrient.Water, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (ml < 1000) "$ml ml" else (ml / 1000.0).format(2).trimEnd('0').trimEnd('.', ',') + " L",
                        style = MaterialTheme.typography.titleMedium.merge(Numeric),
                    )
                    Text(
                        " / ${(goalMl / 1000.0).format(1)} L",
                        style = MaterialTheme.typography.labelMedium.merge(Numeric),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ProgressLine(ml.toFloat() / goalMl.coerceAtLeast(1), Nutrient.Water, height = 5.dp)
            }
            Spacer(Modifier.width(12.dp))
            // Same press-and-hold behavior as every other +/− in the app, just slower: one step
            // here is half a litre, so a faster repeat would overshoot in a heartbeat.
            RepeatIconButton(
                Icons.Rounded.Remove, "Undo last water", size = 36.dp,
                firstDelayMs = 500, startIntervalMs = 480, minIntervalMs = 320, onStep = onUndo,
            )
            Spacer(Modifier.width(8.dp))
            var addPressed by remember { mutableStateOf(false) }
            val addStep by rememberUpdatedState(onAdd)
            Row(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Nutrient.Water.copy(alpha = if (addPressed) 0.30f else 0.18f))
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Add 500 ml of water"
                        role = Role.Button
                        onClick { addStep(); true }
                    }
                    .repeatOnHold(
                        firstDelayMs = 500, startIntervalMs = 480, minIntervalMs = 320,
                        onPressedChange = { addPressed = it }, onStep = onAdd,
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = Nutrient.Water)
                Text(stringResource(R.string.s_500_ml), style = MaterialTheme.typography.labelLarge, color = Nutrient.Water)
            }
        }
    }
}

@Composable
internal fun WeightCard(
    state: DashboardState,
    scaleEnabled: Boolean,
    onLog: () -> Unit,
    onHistory: () -> Unit,
    onOpen: () -> Unit,
    /** Draw the smoothed trend rather than a line through the exact weigh-ins. */
    smooth: Boolean = true,
) {
    val profile = state.profile ?: return
    val current = state.currentWeight ?: profile.startWeightKg
    val change = current - profile.startWeightKg
    val towardsGoal = (profile.goalWeightKg - profile.startWeightKg) * change >= 0
    val startDate = runCatching { LocalDate.parse(profile.startDate) }.getOrNull()
    val chart = remember(state.plan.trend, startDate, profile.startWeightKg, state.today, smooth) {
        weightLine(state.plan.trend, startDate, profile.startWeightKg, state.today, smooth = smooth)
    }
    // With the scale turned on, tapping anywhere outside the buttons opens the Body screen.
    ThermidaCard(onClick = if (scaleEnabled) onOpen else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.weight), style = MaterialTheme.typography.titleMedium)
            if (abs(change) >= 0.05) {
                Spacer(Modifier.width(10.dp))
                Pill(
                    (if (change > 0) "+" else "−") + abs(change).format(1) + " kg",
                    if (towardsGoal) MaterialTheme.colorScheme.primary else Nutrient.Protein,
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.plan.weighIns.isNotEmpty()) {
                RoundIconButton(Icons.Rounded.History, "Weigh-in history", onHistory, size = 40.dp)
            }
            // Kept in the header so the floating camera button never covers it. With the smart scale
            // on, weigh-ins are picked up on their own, so manual logging is the only button needed.
            Spacer(Modifier.width(8.dp))
            SecondaryButton("Log", onLog, icon = Icons.Rounded.Add)
        }
        Spacer(Modifier.height(14.dp))
        Box(contentAlignment = Alignment.Center) {
            WeightChart(
                line = chart.points,
                weighIns = chart.weighIns,
                goal = profile.goalWeightKg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )
            if (!chart.isLine) {
                Text(
                    if (chart.hasWeighIns) "Log another weigh-in to draw your line" else "Log your weight to see your progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )
            }
        }
        if (chart.hasWeighIns && chart.isLine) {
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    smooth && chart.windowed -> "Last $WINDOW_DAYS days · Line is your trend, dots are weigh-ins"
                    smooth -> "Line is your trend, dots are your weigh-ins"
                    chart.windowed -> "Last $WINDOW_DAYS days · Line joins your weigh-ins"
                    else -> "Line joins your weigh-ins · Trend smooths daily swings"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeightStat("Start", profile.startWeightKg, Modifier.weight(1f))
            WeightStat("Trend", current, Modifier.weight(1f), highlight = true)
            WeightStat("Goal", profile.goalWeightKg, Modifier.weight(1f))
        }
        if (scaleEnabled) {
            // The latest weigh-in that came from the smart scale carries body composition.
            state.plan.weighIns.lastOrNull { it.impedance != null }?.compositionLine(profile)?.let { line ->
                Spacer(Modifier.height(14.dp))
                Text(line, style = MaterialTheme.typography.bodySmall.merge(Numeric), color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bmi_body_composition_and_trends), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun WeightHistoryDialog(weighIns: List<WeightEntity>, profile: UserProfile?, onDelete: (Long) -> Unit, onDismiss: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.weigh_ins)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.delete_any_reading_that_s_wrong),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(weighIns.asReversed(), key = { it.id }) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${entry.kg.format(1)} kg", style = MaterialTheme.typography.titleSmall.merge(Numeric))
                                Text(
                                    Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(formatter),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                entry.compositionLine(profile)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onDelete(entry.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, "Delete reading", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
internal fun WeightStat(label: String, kg: Double, modifier: Modifier, highlight: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            kg.format(1),
            style = MaterialTheme.typography.titleMedium.merge(Numeric),
            color = if (highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A logged meal. Tap to edit it; press and hold for a small menu with Delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MealRow(meal: MealWithItems, onClick: () -> Unit, onDelete: () -> Unit) {
    val macros = meal.macros()
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        ThermidaCard(onClick = onClick, onLongClick = { menuOpen = true }, padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    val photo = meal.meal.photoPath
                    if (photo != null) {
                        var viewing by remember { mutableStateOf(false) }
                        val haptics = rememberHaptics()
                        AsyncImage(
                            model = File(photo),
                            contentDescription = "Meal photo",
                            contentScale = ContentScale.Crop,
                            // Holding the thumbnail opens the same menu as holding the rest of the row.
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = { viewing = true },
                                    onLongClick = {
                                        haptics.click()
                                        menuOpen = true
                                    },
                                ),
                        )
                        if (viewing) PhotoViewer(File(photo), onDismiss = { viewing = false })
                    } else {
                        Icon(Icons.Rounded.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        meal.meal.name.ifBlank { "Meal" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MacroTag("P", macros.protein, Nutrient.Protein)
                        MacroTag("C", macros.carbs, Nutrient.Carbs)
                        MacroTag("F", macros.fat, Nutrient.Fat)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(macros.kcal.format(), style = MaterialTheme.typography.titleMedium.merge(Numeric))
                    Text(stringResource(R.string.kcal_2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // The menu opens from the card's lower right corner, under the finger's usual resting place.
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp)) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_meal), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
internal fun MacroTag(letter: String, grams: Double, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "$letter ${grams.format()}g",
            style = MaterialTheme.typography.labelMedium.merge(Numeric),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EmptyMeals(isToday: Boolean, onScan: () -> Unit, onAdd: () -> Unit) {
    ThermidaCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Restaurant, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (isToday) "Log your first meal" else "Nothing logged this day",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isToday) "Snap a photo of your plate and Thermida estimates the rest." else "Forgot something? Add it to this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (isToday) PrimaryButton("Open camera", onScan) else PrimaryButton("Add food", onAdd)
        }
    }
}

@Composable
internal fun WeightDialog(initial: Double, day: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var kg by remember { mutableDoubleStateOf(initial) }
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.log_weight)) },
        text = {
            NumberStepper(
                label = day,
                value = kg,
                onValueChange = { kg = it },
                range = 30.0..250.0,
                step = 0.1,
                unit = "kg",
                decimals = 1,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                haptics.click()
                onSave(kg)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * A short one-line summary of a weigh-in's composition, or null for a manual entry. Worked out
 * again from the stored impedance so it always agrees with the Body screen.
 */
internal fun WeightEntity.compositionLine(profile: UserProfile?): String? {
    val c = profile?.let { BodyCompositionCalc.compute(kg, impedance, it) } ?: return null
    return "Fat ${c.fatPct.format(1)} % · Muscle ${c.musclePct(kg).format(1)} % · Water ${c.waterPct.format(1)} %"
}

/** The small blue "Log food" button: opens the Add food box. */
@Composable
internal fun LogFoodButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            "Log food",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
