package app.thermida.ui.dashboard

import app.thermida.voice.VoiceLanguage
import app.thermida.voice.VoiceInput
import app.thermida.voice.SpeechText
import app.thermida.ui.coach.VoiceControls
import app.thermida.ui.coach.VoiceBar
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.ui.res.stringResource
import app.thermida.R
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.thermida.ai.AiItem
import app.thermida.ai.AnalysisResult
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import app.thermida.domain.MealType
import app.thermida.ui.components.PillSelector
import app.thermida.ui.components.RoundIconButton
import app.thermida.ui.components.rememberHaptics
import app.thermida.ui.components.format
import app.thermida.ui.theme.Numeric
import kotlinx.coroutines.CancellationException

/**
 * Logging a food on the day being viewed: describe it and let the AI work it out, or type it in
 * yourself (a name, its calories and, if you know them, its macros).
 */
@Composable
fun QuickAddDialog(
    onDismiss: () -> Unit,
    /** A typed-in food: name, total kcal, protein, carbs and fat in grams, and its meal. */
    onLogManual: (name: String, kcal: Double, protein: Double, carbs: Double, fat: Double, type: MealType) -> Unit,
    title: String = "Add food",
    /** Plain-language mode: the AI estimates what the user describes. Null opens straight on typing it in. */
    describe: (suspend (String, onSearching: () -> Unit) -> AnalysisResult)? = null,
    /** Logs a described meal: its name, items as corrected, meal type and where the estimate came from. */
    onLogEstimate: ((DescribedMeal) -> Unit)? = null,
    /** Foods to offer as starting points, such as the ones logged recently. */
    recent: (suspend () -> List<AiItem>)? = null,
    /** The language the mic listens for in the description box; null leaves the mic out. */
    voiceLanguage: VoiceLanguage? = null,
    onVoiceLanguage: (VoiceLanguage) -> Unit = {},
) {
    var manual by remember { mutableStateOf(describe == null) }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MealType.forTime()) }
    val focus = remember { FocusRequester() }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(manual) { runCatching { focus.requestFocus() } }

    // Slides down from the top edge, quick and eased, and back up when closed. The system's own
    // dialog animation and dim are switched off so only this motion is seen.
    val shown = remember { MutableTransitionState(false) }.apply { targetState = !closing }
    LaunchedEffect(shown.currentState, shown.isIdle) { if (closing && shown.isIdle && !shown.currentState) onDismiss() }
    Dialog(
        onDismissRequest = { closing = true },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { window ->
            SideEffect {
                window.setWindowAnimations(0)
                window.setDimAmount(0f)
            }
        }
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(shown, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { closing = true },
                )
            }
            AnimatedVisibility(
                shown,
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(120)),
                exit = slideOutVertically(tween(180, easing = FastOutLinearInEasing)) { -it } + fadeOut(tween(140, delayMillis = 40)),
            ) {
        Surface(
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    RoundIconButton(Icons.Rounded.Close, "Close", { closing = true })
                }
                val describeIt = describe
                if (manual || describeIt == null) {
                    ManualFood(
                        name = name,
                        onName = { name = it },
                        kcal = kcal, onKcal = { kcal = it },
                        protein = protein, onProtein = { protein = it },
                        carbs = carbs, onCarbs = { carbs = it },
                        fat = fat, onFat = { fat = it },
                        type = type, onType = { type = it },
                        focus = focus,
                        onDescribeInstead = if (describeIt != null) ({ manual = false }) else null,
                        onLog = {
                            onLogManual(name.trim().ifBlank { "Food" }, it.kcal, it.protein, it.carbs, it.fat, type)
                            closing = true
                        },
                    )
                } else {
                    DescribeFood(
                        describe = describeIt,
                        recent = recent,
                        onLogEstimate = onLogEstimate,
                        onEnterByHand = { manual = true },
                        onLogged = { closing = true },
                        voiceLanguage = voiceLanguage,
                        onVoiceLanguage = onVoiceLanguage,
                    )
                }
            }
        }
            }
        }
    }
}

/** What was typed into the manual form, as numbers; macros left empty count as 0. */
internal data class TypedFood(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

internal fun typedFood(kcal: String, protein: String, carbs: String, fat: String): TypedFood? {
    fun String.number() = trim().replace(',', '.').toDoubleOrNull()
    val calories = kcal.number()?.takeIf { it > 0 && it <= 10_000 } ?: return null
    fun macro(s: String) = (s.number() ?: 0.0).coerceIn(0.0, 1_000.0)
    return TypedFood(calories, macro(protein), macro(carbs), macro(fat))
}

/** Typing a food in by hand: a name, its calories, and its macros if you know them. */
@Composable
private fun ManualFood(
    name: String,
    onName: (String) -> Unit,
    kcal: String,
    onKcal: (String) -> Unit,
    protein: String,
    onProtein: (String) -> Unit,
    carbs: String,
    onCarbs: (String) -> Unit,
    fat: String,
    onFat: (String) -> Unit,
    type: MealType,
    onType: (MealType) -> Unit,
    focus: FocusRequester,
    /** Back to describing the food for the AI; null when that isn't offered. */
    onDescribeInstead: (() -> Unit)?,
    onLog: (TypedFood) -> Unit,
) {
    val food = typedFood(kcal, protein, carbs, fat)
    OutlinedTextField(
        name, onName,
        label = { Text(stringResource(R.string.food)) },
        placeholder = { Text(stringResource(R.string.e_g_homemade_lasagna)) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
    NumberBox("Calories (kcal)", kcal, onKcal, Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberBox("Protein g", protein, onProtein, Modifier.weight(1f))
        NumberBox("Carbs g", carbs, onCarbs, Modifier.weight(1f))
        NumberBox("Fat g", fat, onFat, Modifier.weight(1f))
    }
    Text(
        stringResource(R.string.totals_for_what_you_ate_macros),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PillSelector(MealType.entries, type, { it.title }, onType)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onDescribeInstead != null) {
            TextButton(onClick = onDescribeInstead, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Describe it instead") }
        }
        Spacer(Modifier.weight(1f))
        LogButton(enabled = food != null && name.isNotBlank()) { food?.let(onLog) }
    }
}

@Composable
private fun NumberBox(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}

@Composable
private fun LogButton(enabled: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            if (enabled) {
                haptics.click()
                onClick()
            }
        },
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.log_it), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A meal described in the Add food box, ready to log. [aiSource] is null when no AI estimate was used. */
data class DescribedMeal(
    val name: String,
    val items: List<AiItem>,
    val type: MealType,
    val aiSource: String?,
    val notes: String?,
)

/** One food line in the Add food box; the id keeps its grams field stable while others change. */
private class DescribeLine(val id: Long, val item: AiItem)

/** The portion choices, the same as on a scan's result. */
private val describePortions = listOf(0.5, 1.0, 1.5, 2.0)

/**
 * The Add food box: describe what you ate, then "Work it out with AI" estimates it. "Enter manually"
 * fills in the calories yourself. Recent foods can be added with a tap, grams and portion adjusted,
 * and the meal is logged as one.
 */
@Composable
private fun DescribeFood(
    describe: suspend (String, onSearching: () -> Unit) -> AnalysisResult,
    recent: (suspend () -> List<AiItem>)?,
    onLogEstimate: ((DescribedMeal) -> Unit)?,
    onEnterByHand: (() -> Unit)?,
    onLogged: () -> Unit,
    voiceLanguage: VoiceLanguage? = null,
    onVoiceLanguage: (VoiceLanguage) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf<String?>(null) }
    var aiSource by remember { mutableStateOf<String?>(null) }
    // Set while DeepSeek searches the web for a brand, chain or dish, and the sites it used afterwards.
    var searching by remember { mutableStateOf(false) }
    var webSources by remember { mutableStateOf<List<String>>(emptyList()) }
    var webNote by remember { mutableStateOf<String?>(null) }
    val lines = remember { mutableStateListOf<DescribeLine>() }
    var recents by remember { mutableStateOf<List<AiItem>>(emptyList()) }
    var type by remember { mutableStateOf(MealType.forTime()) }
    var portion by remember { mutableDoubleStateOf(1.0) }

    LaunchedEffect(recent) { recents = recent?.invoke() ?: emptyList() }

    // Saying what you ate: the words fill the box as they come, and when you pause the estimate
    // starts by itself, the same way a spoken question goes to the coach.
    val context = LocalContext.current
    val mic = remember(voiceLanguage != null) { if (voiceLanguage != null) VoiceInput(context) else null }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(mic, lifecycle) {
        // The mic closes with the dialog, and whenever Thermida leaves the screen.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) mic?.release() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mic?.release()
        }
    }

    fun estimate() {
        mic?.release()
        focusManager.clearFocus()
        keyboard?.hide()
        val description = text.trim()
        if (description.isEmpty() || busy) return
        busy = true
        problem = null
        scope.launch {
            try {
                val estimate = describe(description) { searching = true }
                if (estimate.meal.items.isEmpty()) {
                    problem = "No food found in that. Try naming what you ate, e.g. 2 eggs and toast."
                } else {
                    estimate.meal.items.forEach { lines.add(DescribeLine(System.nanoTime(), it)) }
                    if (name.isBlank()) name = estimate.meal.mealName
                    notes = estimate.meal.notes.takeIf { it.isNotBlank() }
                    webSources = estimate.webSources
                    webNote = estimate.webNote
                    aiSource = (estimate.sourceLabel ?: "${estimate.provider.title} · ${estimate.model}") +
                        if (estimate.webSources.isNotEmpty()) " + web" else ""
                    type = MealType.entries.firstOrNull { it.name.equals(estimate.meal.mealType, ignoreCase = true) } ?: MealType.forTime()
                    portion = 1.0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = e.message ?: "Couldn't estimate that."
            } finally {
                busy = false
                searching = false
            }
        }
    }

    fun listen() {
        val language = voiceLanguage ?: return
        val voice = mic ?: return
        focusManager.clearFocus()
        keyboard?.hide()
        problem = null
        // Anything already typed stays in front of what is said.
        val typed = text
        voice.start(
            language,
            onText = { heard -> text = SpeechText.join(typed, heard) },
            onDone = { heard ->
                text = SpeechText.join(typed, heard)
                estimate()
            },
            onError = { problem = it },
        )
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else problem = "Allow the microphone for Thermida to hear what you ate."
    }

    TextField(
        text,
        { text = it },
        placeholder = { Text(if (mic?.listening == true) "Listening…" else "Describe what you ate, e.g. 2 eggs, toast with butter and a latte") },
        trailingIcon = voiceLanguage?.let { language ->
            {
                VoiceControls(
                    VoiceBar(
                        listening = mic?.listening == true,
                        level = mic?.level ?: 0f,
                        language = language,
                        onMic = {
                            when {
                                mic?.listening == true -> mic.stop()
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listen()
                                else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onLanguage = { onVoiceLanguage(language.other()) },
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { estimate() }),
        minLines = 2,
        maxLines = 4,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

    if (recents.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Recent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recents.forEach { food ->
                    Surface(
                        onClick = { lines.add(DescribeLine(System.nanoTime(), food)) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            food.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (lines.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEachIndexed { index, line ->
                key(line.id) {
                    Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            line.item.name.ifBlank { "Food" },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        GramsField(line.item.grams) { grams ->
                            lines[index] = DescribeLine(line.id, line.item.copy(grams = grams))
                        }
                        Text(
                            "${(line.item.kcalPer100 * line.item.grams / 100 * portion).format()} kcal",
                            style = MaterialTheme.typography.labelLarge.merge(Numeric),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        IconButton(onClick = { lines.removeAt(index) }) {
                            Icon(Icons.Rounded.Close, "Remove ${line.item.name.ifBlank { "food" }}", Modifier.size(18.dp))
                        }
                    }
                    GramChips(line.item.grams, line.item.packageGrams) { grams ->
                        lines[index] = DescribeLine(line.id, line.item.copy(grams = grams))
                    }
                    }
                }
            }
        }
        PillSelector(
            describePortions,
            portion,
            { if (it == 0.5) "½×" else "${it.format(if (it % 1.0 == 0.0) 0 else 1)}×" },
            { portion = it },
        )
        val total = lines.sumOf { it.item.kcalPer100 * it.item.grams / 100 } * portion
        Text(
            "Total ${total.format()} kcal" + (notes?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Where the web search looked, or why it was skipped.
        (if (webSources.isNotEmpty()) "Checked the web: ${webSources.joinToString(", ")}" else webNote)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PillSelector(MealType.entries, type, { it.title }, { type = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    lines.clear()
                    notes = null
                    aiSource = null
                    webSources = emptyList()
                    webNote = null
                    portion = 1.0
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Clear") }
            Spacer(Modifier.weight(1f))
            LogButton(enabled = onLogEstimate != null && lines.isNotEmpty()) {
                onLogEstimate?.invoke(
                    DescribedMeal(
                        name = name.ifBlank { lines.first().item.name },
                        items = lines.map { it.item.copy(grams = it.item.grams * portion) },
                        type = type,
                        aiSource = aiSource,
                        notes = notes,
                    ),
                )
                onLogged()
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onEnterByHand != null) {
            TextButton(onClick = onEnterByHand, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Enter manually") }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = { estimate() }, enabled = text.isNotBlank() && !busy) {
            Text(
                when {
                    searching -> "Searching the web…"
                    busy -> "Working it out…"
                    else -> "Work it out with AI"
                },
            )
        }
    }
}

/** Grams for one food line; a typed value that isn't a number yet is kept on screen but not applied. */
@Composable
private fun GramsField(grams: Double, onChange: (Double) -> Unit) {
    var field by remember { mutableStateOf(gramsText(grams)) }
    // A gram chip changes the amount from outside the box: show the new amount there too.
    LaunchedEffect(grams) { gramsFieldUpdate(field, grams)?.let { field = it } }
    OutlinedTextField(
        value = field,
        onValueChange = { typed ->
            field = typed
            typed.replace(',', '.').toDoubleOrNull()?.let(onChange)
        },
        singleLine = true,
        suffix = { Text("g") },
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.width(120.dp),
    )
}

/**
 * What the grams box should show once the amount is [grams], or null to leave [field] as typed: it
 * already says that amount (perhaps half typed, like "12." or "12,5"), so rewriting it would move
 * the cursor or undo what the user is typing.
 */
internal fun gramsFieldUpdate(field: String, grams: Double): String? =
    if (field.replace(',', '.').toDoubleOrNull() == grams) null else gramsText(grams)

/** Whole grams with no thousands separator, so the box reads back what it shows (a "1,000" would read as 1). */
internal fun gramsText(grams: Double): String = grams.roundToInt().toString()

/** Quick gram choices for one food: 50, 100, 150 and 200 g, and the whole package when Open Food Facts knows it. */
@Composable
private fun GramChips(grams: Double, packageGrams: Double?, onPick: (Double) -> Unit) {
    val choices = buildList {
        packageGrams?.let { add(it to "Package ${it.format()} g") }
        listOf(50.0, 100.0, 150.0, 200.0).forEach { add(it to "${it.format()} g") }
    }
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        choices.forEach { (value, label) ->
            val selected = kotlin.math.abs(grams - value) < 0.5
            Surface(
                onClick = { onPick(value) },
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
