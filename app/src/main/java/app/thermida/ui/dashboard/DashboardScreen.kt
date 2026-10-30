package app.thermida.ui.dashboard

import app.thermida.voice.VoiceLanguage
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
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

/**
 * Sideways day switching. The page follows the finger; on release it either slides away and the
 * neighbouring day slides in from the other side, or springs back. Swiping right goes to the
 * earlier day, left to the later one. On today, swiping left moves on to the Scan tab instead.
 */
@Stable
private class DaySwipe(
    private val scope: CoroutineScope,
    private val listState: LazyListState,
    private val earlier: () -> Unit,
    private val later: () -> Unit,
    /** Swiping left on today: hands over to the Scan tab. */
    private val pastToday: () -> Unit,
) {
    var offset by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(1f)
    var canGoNext = false
    private var busy = false

    fun drag(delta: Float) {
        if (busy) return
        offset += delta
    }

    fun release(velocity: Float) {
        if (busy) return
        val far = width * 0.25f
        when {
            offset > far || velocity > FLING -> go(-1)
            offset < -far || velocity < -FLING -> if (canGoNext) go(1) else toScan()
            else -> settle()
        }
    }

    /** [direction] is -1 for the earlier day and 1 for the later one. */
    fun go(direction: Int) {
        if (busy) return
        if (direction > 0 && !canGoNext) return settle()
        busy = true
        scope.launch {
            val exit = if (direction < 0) width else -width
            animate(offset, exit, animationSpec = tween(150, easing = FastOutLinearInEasing)) { v, _ -> offset = v }
            if (direction < 0) earlier() else later()
            listState.scrollToItem(0)
            animate(-exit, 0f, animationSpec = tween(230, easing = FastOutSlowInEasing)) { v, _ -> offset = v }
            busy = false
        }
    }

    /**
     * The page keeps its offset while the tab transition slides it out the same way. If the switch
     * doesn't happen for some reason, it springs back after a moment instead of staying stuck.
     */
    private fun toScan() {
        busy = true
        pastToday()
        scope.launch {
            delay(600)
            animate(offset, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { v, _ -> offset = v }
            busy = false
        }
    }

    private fun settle() {
        busy = true
        scope.launch {
            animate(offset, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { v, _ -> offset = v }
            busy = false
        }
    }

    private companion object {
        const val FLING = 1500f
    }
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onScan: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    onSettings: () -> Unit,
    onOpenBody: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val voiceLanguage by viewModel.voiceLanguage.collectAsStateWithLifecycle(initialValue = VoiceLanguage.pick(null))
    val scaleEnabled by viewModel.scaleEnabled.collectAsStateWithLifecycle(initialValue = false)
    val foodFactsOn by viewModel.foodFactsEnabled.collectAsStateWithLifecycle(initialValue = false)
    var showQuickAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showWeightDialog by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val swipe = remember { DaySwipe(scope, listState, viewModel::previousDay, viewModel::nextDay, pastToday = onScan) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.problems.collect { snackbar.showSnackbar(it) } }
    val deleteWithUndo: (Long) -> Unit = { id ->
        scope.launch {
            val removed = viewModel.removeMeal(id) ?: return@launch
            val result = snackbar.showSnackbar("Meal deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreMeal(removed)
        }
    }
    swipe.canGoNext = state.date < state.today

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { swipe.width = it.width.toFloat().coerceAtLeast(1f) }
            // Swipe sideways anywhere on the page to move between days. Vertical scrolling is
            // untouched, and the system back gesture still owns the very edges of the screen.
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { swipe.drag(it) },
                onDragStopped = { swipe.release(it) },
            ),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .graphicsLayer {
                    translationX = swipe.offset
                    alpha = 1f - 0.5f * (abs(swipe.offset) / swipe.width).coerceAtMost(1f)
                },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Header(
                    date = state.date,
                    today = state.today,
                    onPrev = { swipe.go(-1) },
                    onNext = { swipe.go(1) },
                    onSettings = onSettings,
                    onQuickAdd = { showQuickAdd = true },
                )
            }
            // Until the first real numbers arrive, show nothing rather than placeholder targets.
            if (state.loading) return@LazyColumn
            item { CaloriesCard(state) }
            item {
                WaterCard(
                    ml = state.waterMl,
                    goalMl = state.targets?.waterMl ?: 2500,
                    onAdd = { viewModel.addWater(500) },
                    onUndo = viewModel::undoWater,
                )
            }
            item {
                WeightCard(
                    state,
                    scaleEnabled = scaleEnabled,
                    onLog = { showWeightDialog = true },
                    onHistory = { showHistory = true },
                    onOpen = onOpenBody,
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                // The + adds food to the day on screen, past days included (no camera for those).
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Text(stringResource(R.string.meals), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(
                        "${state.eaten.kcal.format()} kcal",
                        style = MaterialTheme.typography.titleSmall.merge(Numeric),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    LogFoodButton { showQuickAdd = true }
                }
            }
            if (state.meals.isEmpty() && !state.loading) {
                item { EmptyMeals(isToday = state.date == state.today, onScan = onScan, onAdd = { showQuickAdd = true }) }
            }
            val groups = state.meals.groupBy { MealType.parse(it.meal.mealType) }
            MealType.entries.forEach { type ->
                val meals = groups[type] ?: return@forEach
                item(key = "header_$type") {
                    Text(
                        "${type.title} · ${meals.sumOf { it.macros().kcal }.format()} kcal",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
                items(meals, key = { it.meal.id }) { meal ->
                    MealRow(meal, onClick = { onOpenMeal(meal.meal.id) }, onDelete = { deleteWithUndo(meal.meal.id) })
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }

    if (showHistory) {
        WeightHistoryDialog(
            weighIns = state.plan.weighIns,
            profile = state.profile,
            onDelete = viewModel::deleteWeight,
            onDismiss = { showHistory = false },
        )
    }
    if (showQuickAdd) {
        // Whatever day is on screen when it opens is the day the food goes to.
        val day = remember { state.date }
        val label = dayLabel(day, state.today)
        QuickAddDialog(
            search = null,
            describe = viewModel::describeFood,
            recent = viewModel::recentFoods,
            onLogEstimate = { meal ->
                viewModel.logDescribed(meal.name, meal.items, meal.type, day, meal.aiSource, meal.notes)
                val kcal = meal.items.sumOf { it.kcalPer100 * it.grams / 100 }.format()
                Toast.makeText(context, "Logged ${meal.name.ifBlank { "meal" }} · $kcal kcal · $label", Toast.LENGTH_SHORT).show()
            },
            title = "Add food · $label",
            voiceLanguage = voiceLanguage,
            onVoiceLanguage = viewModel::setVoiceLanguage,
            onDismiss = { showQuickAdd = false },
            onLog = { fact, name, grams, type ->
                viewModel.quickLog(fact, name, grams, type, day)
                val kcal = (fact.kcalPer100 * grams / 100).format()
                Toast.makeText(context, "Logged $name · $kcal kcal · $label", Toast.LENGTH_SHORT).show()
            },
            onLogManual = { name, kcal, protein, carbs, fat, type ->
                viewModel.manualLog(name, kcal, protein, carbs, fat, type, day)
                Toast.makeText(context, "Logged $name · ${kcal.format()} kcal · $label", Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (showWeightDialog) {
        val day = remember { state.date }
        WeightDialog(
            initial = state.currentWeight ?: 80.0,
            day = dayLabel(day, state.today),
            onDismiss = { showWeightDialog = false },
            onSave = {
                viewModel.logWeight(it, day)
                showWeightDialog = false
            },
        )
    }
}

/** "Today", "Yesterday" or "Sun 18 Oct": which day a new entry goes to. */
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}

@Composable
private fun Header(date: LocalDate, today: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onSettings: () -> Unit, onQuickAdd: (() -> Unit)?) {
    val title = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", LocalConfiguration.current.locales[0]))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", LocalConfiguration.current.locales[0])),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Shrinks to fit a long date next to the header buttons, so the day arrows are never
                // pushed off on a narrow phone.
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = MaterialTheme.typography.headlineMedium.fontSize),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, "Previous day", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onNext, enabled = date < today, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        "Next day",
                        tint = if (date < today) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
        onQuickAdd?.let {
            LogFoodButton(it)
            Spacer(Modifier.width(8.dp))
        }
        RoundIconButton(Icons.Rounded.Settings, "Settings", onSettings, size = 44.dp)
    }
}
