package app.thermida.ui.coach

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.res.stringResource
import app.thermida.R
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thermida.data.CoachMessage
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import app.thermida.data.ActionFood
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.thermida.ui.theme.Numeric
import app.thermida.ui.theme.Nutrient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WaterDrop
import app.thermida.data.ActionStatus
import app.thermida.data.ActionType
import app.thermida.data.CoachAction
import app.thermida.data.CoachActions
import java.time.LocalDate
import app.thermida.ui.components.Pill
import app.thermida.ui.components.rememberHaptics
import app.thermida.ui.components.RoundIconButton

@Composable
internal fun EmptyCoach(questions: List<String>, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ask_about_your_progress), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.your_coach_sees_your_meals_water),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            questions.forEach { question ->
                Surface(
                    onClick = { onPick(question) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        question,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            modifier = Modifier
                .padding(start = 48.dp)
                .widthIn(max = 320.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The coach writes plain text on the page, no bubble; long-press selects it for copying. A new
 * answer types itself out, quickly enough that even a long one takes under two seconds.
 */
@Composable
internal fun CoachText(text: String, typing: Boolean = false, onTyped: () -> Unit = {}) {
    val strong = MaterialTheme.colorScheme.onSurface
    val full = remember(text, strong) { coachMarkup(text, strong) }
    var shown by remember(text) { mutableIntStateOf(if (typing) 0 else full.length) }
    if (typing) {
        LaunchedEffect(text) {
            val duration = (full.length * 1000L / TYPE_CHARS_PER_SECOND).coerceIn(400L, 1_800L)
            val start = withFrameMillis { it }
            while (shown < full.length) {
                val elapsed = withFrameMillis { it } - start
                shown = (full.length * elapsed / duration).toInt().coerceAtMost(full.length)
            }
            onTyped()
        }
    }
    SelectionContainer {
        Text(
            if (shown >= full.length) full else full.subSequence(0, shown),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 24.dp),
        )
    }
}

@Composable
internal fun Thinking(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ErrorRow(message: String, onRetry: () -> Unit) {
    Column {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.retry)) }
    }
}

/** A change the coach offers to make, waiting for Accept or Decline. */
@Composable
internal fun ActionCard(action: CoachAction, onAccept: () -> Unit, onDecline: () -> Unit, onGrams: (food: Int, grams: Double) -> Unit = { _, _ -> }) {
    val today = remember { LocalDate.now() }
    val deleting = action.type == ActionType.DELETE_MEAL
    // The accent colour, except for deleting, which is red so it can't be mistaken for logging.
    val tint = if (deleting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val day = remember(action) {
        action.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it != today }?.let {
            if (it == today.minusDays(1)) "Yesterday" else it.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        }
    }
    val haptics = rememberHaptics()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        shadowElevation = 6.dp,
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.12f), Color.Transparent)))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(tint.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(action.type.icon, null, Modifier.size(22.dp), tint = tint)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.coach_suggests),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
                        color = tint,
                    )
                    Text(CoachActions.title(action), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                day?.let {
                    Spacer(Modifier.width(8.dp))
                    Pill(it, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (action.type) {
                ActionType.LOG_FOOD -> {
                    val m = action.macros
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.kcal.roundToInt().toString(), style = MaterialTheme.typography.headlineSmall.merge(Numeric))
                        Text(stringResource(R.string.kcal), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        MacroChip("P", m.protein, Nutrient.Protein)
                        Spacer(Modifier.width(6.dp))
                        MacroChip("C", m.carbs, Nutrient.Carbs)
                        Spacer(Modifier.width(6.dp))
                        MacroChip("F", m.fat, Nutrient.Fat)
                    }
                    // Foods with amounts to choose from get a row of one-tap options; the rest
                    // are listed as before.
                    val fixed = action.foods.filter { it.portions.isEmpty() }
                    if (fixed.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            fixed.joinToString(" · ") { "${it.name} ${it.grams.roundToInt()} g" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    action.foods.forEachIndexed { index, food ->
                        if (food.portions.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            PortionPicker(food, tint, onPick = { onGrams(index, it) })
                        }
                    }
                    // Label values checked against the product database, not estimated.
                    if (action.foods.any { it.offCode != null }) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Verified, null, Modifier.size(14.dp), tint = Nutrient.Water)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (action.foods.all { it.offCode != null }) "Label values from Open Food Facts"
                                else "Branded items use Open Food Facts label values",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                ActionType.DELETE_MEAL -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOfNotNull(day ?: "Today", action.kcal?.let { "$it kcal" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Unit
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CardButton(
                    "Decline", Icons.Rounded.Close,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.tick()
                        onDecline()
                    },
                )
                CardButton(
                    if (deleting) "Delete" else "Accept", Icons.Rounded.Check,
                    container = tint,
                    content = if (deleting) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.click()
                        onAccept()
                    },
                )
            }
        }
    }
}

@Composable
internal fun PortionPicker(food: ActionFood, tint: Color, onPick: (Double) -> Unit) {
    val haptics = rememberHaptics()
    var typing by remember(food.name) { mutableStateOf(false) }
    var typed by remember(food.name) { mutableStateOf("") }
    val custom = food.portions.none { it.grams == food.grams }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(food.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
            Text(
                stringResource(R.string.how_much),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        // One line per food, swiped sideways if the amounts don't all fit, so the card stays short
        // enough to show Accept and Decline without scrolling.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            food.portions.forEach { p ->
                PortionChip(p.label, "${p.grams.roundToInt()} g", selected = !typing && p.grams == food.grams, tint) {
                    haptics.tick()
                    typing = false
                    onPick(p.grams)
                }
            }
            PortionChip("Other", if (custom) "${food.grams.roundToInt()} g" else "grams", selected = typing || custom, tint) {
                haptics.tick()
                typing = true
                typed = food.grams.roundToInt().toString()
            }
        }
        if (typing) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { value ->
                    typed = value.filter { it.isDigit() }.take(4)
                    typed.toDoubleOrNull()?.takeIf { it > 0 }?.let(onPick)
                },
                label = { Text(stringResource(R.string.grams)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.width(140.dp),
            )
        }
    }
}

@Composable
internal fun PortionChip(label: String, grams: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    // Name above, weight below: narrow enough that three amounts and Other share one line.
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) tint else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(grams, style = MaterialTheme.typography.labelSmall.merge(Numeric), maxLines = 1)
        }
    }
}

/** P / C / F amount on a meal card, with the same colours as everywhere else in the app. */
@Composable
internal fun MacroChip(letter: String, grams: Double, color: Color) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(letter, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text("${grams.roundToInt()} g", style = MaterialTheme.typography.labelMedium.merge(Numeric))
    }
}

/** A big, easy-to-hit button for the cards: Decline on the left, Accept on the right. */
@Composable
internal fun CardButton(label: String, icon: ImageVector, container: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        modifier = modifier.height(50.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Under the coach's message once a proposal is answered: what was done, or that it was declined. */
@Composable
internal fun ActionOutcome(action: CoachAction) {
    val (icon, color, text) = when (action.status) {
        ActionStatus.ACCEPTED -> Triple(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary, action.result ?: CoachActions.title(action))
        ActionStatus.FAILED -> Triple(Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error, "Couldn't do it: ${action.result}")
        else -> Triple(Icons.Rounded.Block, MaterialTheme.colorScheme.onSurfaceVariant, "Declined: ${CoachActions.title(action)}")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal val ActionType.icon
    get() = when (this) {
        ActionType.LOG_FOOD -> Icons.Rounded.Restaurant
        ActionType.DELETE_MEAL -> Icons.Rounded.DeleteOutline
        ActionType.ADD_WATER, ActionType.SET_WATER_GOAL -> Icons.Rounded.WaterDrop
        ActionType.LOG_WEIGHT -> Icons.Rounded.MonitorWeight
        ActionType.SET_CALORIES -> Icons.Rounded.LocalFireDepartment
        ActionType.SET_GOAL_WEIGHT -> Icons.Rounded.Flag
    }

@Composable
internal fun InputBar(value: String, onChange: (String) -> Unit, canSend: Boolean, onSend: () -> Unit, voice: VoiceBar? = null, onFocus: () -> Unit = {}, busy: Boolean = false, onStop: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            value,
            onChange,
            placeholder = { Text(if (voice?.listening == true) "Listening…" else stringResource(R.string.ask_your_coach)) },
            trailingIcon = voice?.let { bar -> { VoiceControls(bar) } },
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) onFocus() },
        )
        Spacer(Modifier.width(8.dp))
        // While the coach thinks, types or talks, the send button is a Stop button.
        if (busy) RoundIconButton(
            Icons.Rounded.Stop,
            "Stop",
            onStop,
            modifier = Modifier.padding(bottom = 4.dp),
            size = 48.dp,
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
        ) else RoundIconButton(
            Icons.Rounded.ArrowUpward,
            "Send",
            { if (canSend) onSend() },
            modifier = Modifier.padding(bottom = 4.dp),
            size = 48.dp,
            container = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            content = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The coach's suggested next questions: plain outlined pills in one row that scrolls sideways,
 * deliberately quieter than the proposal cards. Tapping one asks it.
 */
@Composable
internal fun FollowUpPills(questions: List<String>, onPick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
    ) {
        items(questions) { question ->
            Surface(
                onClick = { onPick(question) },
                shape = RoundedCornerShape(50),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    question,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

