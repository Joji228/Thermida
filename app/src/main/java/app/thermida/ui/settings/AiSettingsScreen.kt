package app.thermida.ui.settings

import androidx.compose.ui.res.stringResource
import app.thermida.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thermida.ai.ApiBalances
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import app.thermida.ai.OpenRouterModels
import app.thermida.data.AiSettings
import app.thermida.data.Provider
import app.thermida.data.ThinkingLevel
import app.thermida.data.TokenBudget
import app.thermida.data.TokenUsage
import app.thermida.ui.components.PillSelector
import app.thermida.ui.components.Pill
import app.thermida.ui.components.RoundIconButton
import app.thermida.ui.components.rememberHaptics
import app.thermida.ui.theme.Numeric
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Where the user picks which AI reads their meal photos and enters their own API key for it.
 * The credit left on the scan engine's account sits on top (for services that report it);
 * below, one card per engine. Every choice opens a sheet that slides up from the bottom.
 */
@Composable
fun AiSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val ai = state.ai
    val haptics = rememberHaptics()

    var serviceSheet by remember { mutableStateOf<Provider?>(null) }
    var keySheet by remember { mutableStateOf<Provider?>(null) }
    var modelSheet by remember { mutableStateOf<Provider?>(null) }
    var thinkingSheet by remember { mutableStateOf(false) }
    var backupSheet by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Provider?>(null) }
    var addSheet by remember { mutableStateOf(false) }

    // A new scan engine, key or address: check its balance again.
    LaunchedEffect(ai.primary, ai.keyFor(ai.primary), ai.customBaseUrl) { viewModel.refreshBalance() }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.ai_engine), style = MaterialTheme.typography.headlineSmall)
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            item { BalanceCard(ai.primary, ai, balance, onRefresh = viewModel::refreshBalance) }
            item { UsageCard(viewModel) }

            item {
                Text(
                    stringResource(R.string.your_photos_go_only_to_the),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }

            item { GroupLabel("Your engines") }
            if (ai.activeEngines.size > 1) {
                item {
                    Text(
                        stringResource(R.string.tap_an_engine_to_use_it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
            items(ai.activeEngines, key = { it.name }) { provider ->
                EngineCard(
                    provider = provider,
                    ai = ai,
                    onUse = { viewModel.setPrimary(provider) },
                    onService = { serviceSheet = provider },
                    onKey = { keySheet = provider },
                    onModel = { modelSheet = provider },
                    onThinking = { thinkingSheet = true },
                    onRemove = if (ai.activeEngines.size > 1) ({ removing = provider }) else null,
                )
            }
            item {
                if (ai.addableEngines.isNotEmpty()) {
                    Surface(
                        onClick = {
                            haptics.tick()
                            addSheet = true
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_engine), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    Text(
                        "You can set up to ${AiSettings.MAX_ENGINES} engines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }

            item { GroupLabel("Backup engine") }
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        SwitchRow(
                            title = "Use a backup",
                            subtitle = when {
                                !ai.fallback -> "Off. Only ${ai.primary.title} is used. If it fails, you'll see why."
                                ai.fallbackTarget == null -> "On, but no other engine has a key yet"
                                else -> "If ${ai.primary.title} fails, ${ai.fallbackTarget!!.title} tries the same photo"
                            },
                            checked = ai.fallback,
                            onChange = viewModel::setFallback,
                        )
                        if (ai.fallback) {
                            SettingRow(
                                icon = Icons.Rounded.AutoAwesome,
                                title = "Backup engine",
                                value = ai.fallbackTarget?.title ?: "None",
                                onClick = { backupSheet = true },
                            )
                        }
                    }
                }
            }
        }
    }

    serviceSheet?.let { provider ->
        ServiceSheet(provider, ai, onPick = { viewModel.changeEngine(provider, it) }, onDismiss = { serviceSheet = null })
    }
    keySheet?.let { provider ->
        KeySheet(
            provider = provider,
            initialKey = ai.keyFor(provider),
            initialUrl = ai.customBaseUrl,
            viewModel = viewModel,
            onDismiss = {
                keySheet = null
                viewModel.resetKeyCheck()
            },
        )
    }
    if (addSheet) {
        AddEngineSheet(
            addable = ai.addableEngines,
            viewModel = viewModel,
            onDismiss = {
                addSheet = false
                viewModel.resetKeyCheck()
            },
        )
    }
    modelSheet?.let { provider ->
        ModelSheet(
            provider = provider,
            selected = ai.modelFor(provider),
            viewModel = viewModel,
            onDismiss = {
                modelSheet = null
                viewModel.resetKeyCheck()
            },
        )
    }
    if (thinkingSheet) {
        Sheet(
            title = "DeepSeek thinking",
            subtitle = "How long DeepSeek reasons before answering. Off or Low is usually plenty for reading a meal and makes scans faster.",
            onDismiss = { thinkingSheet = false },
        ) { close ->
            ThinkingLevel.entries.forEach { level ->
                Option(level.title, level.about, selected = level == ai.deepseekThinking) {
                    viewModel.setDeepseekThinking(level)
                    close()
                }
            }
        }
    }
    if (backupSheet) {
        Sheet(title = "Backup engine", subtitle = "Tries the same photo if your scan engine fails.", onDismiss = { backupSheet = false }) { close ->
            ai.activeEngines.filter { it != ai.primary }.forEach { provider ->
                val ready = ai.isReady(provider)
                Option(provider.title, if (ready) provider.byline else "Add a key first", selected = provider == ai.fallbackTarget, enabled = ready) {
                    viewModel.setFallbackProvider(provider)
                    close()
                }
            }
        }
    }
    removing?.let { provider ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Remove ${provider.title}?") },
            text = {
                Text(
                    stringResource(R.string.its_saved_api_key_is_deleted) +
                        if (provider == ai.primary) " Scans will use ${(ai.activeEngines - provider).first().title} instead." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeEngine(provider)
                    removing = null
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Credit left on the scan engine's account, with a refresh button. DeepSeek and OpenRouter report
 * it; for others the card just says the service doesn't share it.
 */
@Composable
private fun BalanceCard(provider: Provider, ai: AiSettings, state: BalanceState, onRefresh: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val canCheck = ApiBalances.supported(provider, ai)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, primary.copy(alpha = if (canCheck) 0.3f else 0.12f)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(primary.copy(alpha = if (canCheck) 0.12f else 0.04f), Color.Transparent)))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(primary.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Wallet, null, Modifier.size(20.dp), tint = primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.credit_left),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
                        color = primary,
                    )
                    Text("${provider.title} account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canCheck && ai.isReady(provider)) RefreshButton(spinning = state is BalanceState.Loading, onClick = onRefresh)
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(targetState = state, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "balance") { s ->
                Column {
                    when {
                        !canCheck -> Text(
                            "${provider.title} doesn't share your balance with apps. Check it on its website. DeepSeek and OpenRouter keys show it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        !ai.isReady(provider) -> Text(
                            "Add your ${provider.title} key below to see what's left.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        s is BalanceState.Ok -> {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(s.balance.amounts.first().text, style = MaterialTheme.typography.displaySmall.merge(Numeric))
                                s.balance.amounts.drop(1).forEach {
                                    Spacer(Modifier.width(10.dp))
                                    Text(it.text, style = MaterialTheme.typography.titleMedium.merge(Numeric), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                listOfNotNull(s.balance.detail, "updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.at))}").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        s is BalanceState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        else -> Text(stringResource(R.string.checking), style = MaterialTheme.typography.displaySmall.merge(Numeric), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshButton(spinning: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val turn = rememberInfiniteTransition(label = "refresh")
    val angle by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Surface(
        onClick = {
            if (!spinning) {
                haptics.tick()
                onClick()
            }
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Refresh,
                "Refresh balance",
                Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = if (spinning) angle else 0f },
            )
        }
    }
}

/**
 * One engine. Tapping anywhere on the card makes it the one Thermida uses (scans and the coach);
 * its rows open their own sheets, and the name opens the service list.
 */
@Composable
private fun EngineCard(
    provider: Provider,
    ai: AiSettings,
    onUse: () -> Unit,
    onService: () -> Unit,
    onKey: () -> Unit,
    onModel: () -> Unit,
    onThinking: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val used = provider == ai.primary
    val primary = MaterialTheme.colorScheme.primary
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            // Picking an engine is a real change; tapping the one already in use isn't.
            if (!used) haptics.click()
            onUse()
        },
        shape = RoundedCornerShape(22.dp),
        color = if (used) primary.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surfaceContainer) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (used) 1.5.dp else 1.dp, if (used) primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A letter badge for the service, accent-coloured when it reads the photos.
                Box(
                    Modifier
                        .size(40.dp)
                        .background(if (used) primary else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        provider.title.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (used) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // Plain text: the whole card picks the engine, and the Service row below switches it.
                    // (A small tappable name here grabbed taps meant for the card.)
                    Text(provider.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (used) provider.byline ?: "Reads your photos and runs the coach" else "Tap to use this engine",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (used) {
                    Row(
                        Modifier
                            .background(primary, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.in_use), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Rounded.DeleteOutline, "Remove ${provider.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SettingRow(icon = Icons.Rounded.SwapHoriz, title = "Service", value = provider.title, onClick = onService)
            val key = ai.keyFor(provider)
            val missingUrl = provider.needsBaseUrl && ai.customBaseUrl.isBlank()
            SettingRow(
                icon = if (provider.needsBaseUrl) Icons.Rounded.Link else Icons.Rounded.Key,
                title = "API key",
                value = when {
                    key.isBlank() -> null
                    provider.needsBaseUrl && !missingUrl -> "••••${key.takeLast(4)} · ${ai.customBaseUrl.removePrefix("https://").removePrefix("http://")}"
                    else -> "••••${key.takeLast(4)}"
                },
                status = when {
                    key.isBlank() -> Status("Not set", MaterialTheme.colorScheme.error)
                    missingUrl -> Status("Address missing", MaterialTheme.colorScheme.error)
                    else -> Status("Saved", primary)
                },
                onClick = onKey,
            )
            SettingRow(
                icon = if (provider == Provider.DEEPSEEK) Icons.Rounded.Memory else Icons.Rounded.AutoAwesome,
                title = "Model",
                value = ai.modelFor(provider).ifBlank { if (provider == Provider.CUSTOM) "Not chosen" else "Automatic" },
                onClick = onModel,
            )
            if (provider == Provider.DEEPSEEK) {
                SettingRow(icon = Icons.Rounded.Psychology, title = "Thinking", value = ai.deepseekThinking.title, onClick = onThinking)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private data class Status(val text: String, val color: Color)

/** A row in an engine card: icon, name, its current value and a chevron. */
@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit, status: Status? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            value?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        status?.let {
            Spacer(Modifier.width(8.dp))
            Pill(it.text, it.color)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The token limits, on this screen: today's bar and this week's bar. Each has a small menu to change
 * its cap. The week counts from Sunday 9:00 and resets each Sunday at 9:00.
 */
@Composable
private fun UsageCard(viewModel: SettingsViewModel) {
    val usage by viewModel.usage.collectAsStateWithLifecycle(initialValue = TokenUsage(0, 0, 0, 0))
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LimitBar(
                label = "Today",
                used = usage.today,
                limit = usage.daily,
                choices = TokenBudget.dailyChoices,
                onPick = { viewModel.setDailyLimit(it) },
                note = "Resets at midnight",
            )
            LimitBar(
                label = "This week",
                used = usage.week,
                limit = usage.weekly,
                choices = TokenBudget.weeklyChoices,
                onPick = { viewModel.setWeeklyLimit(it) },
                note = "Counts from Sunday 9:00, resets each Sunday at 9:00",
            )
        }
    }
}

/** One limit: its name, the choice of cap in a small menu, how much is used, and a thin bar. */
@Composable
private fun LimitBar(
    label: String,
    used: Long,
    limit: Long,
    choices: List<Long>,
    onPick: (Long) -> Unit,
    note: String,
) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Box {
                Surface(
                    onClick = { menu = true },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        "Limit ${TokenBudget.label(limit)} ▾",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    choices.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(TokenBudget.label(choice)) },
                            onClick = {
                                onPick(choice)
                                menu = false
                            },
                        )
                    }
                }
            }
        }
        Text(
            if (limit > 0) "${TokenBudget.grouped(used)} of ${TokenBudget.grouped(limit)} tokens"
            else "${TokenBudget.grouped(used)} tokens, no limit",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (limit > 0) UsageBar((used.toFloat() / limit).coerceIn(0f, 1f))
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A thin bar showing how much of a limit is used. No end dot. */
@Composable
private fun UsageBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
