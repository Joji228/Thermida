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
import app.thermida.ui.components.Pill
import app.thermida.ui.components.RoundIconButton
import app.thermida.ui.components.rememberHaptics
import app.thermida.ui.theme.Numeric
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * A sheet that slides up from the bottom with a drag handle, a title and a short explanation.
 * Choosing something closes it with the same slide; [content] gets a `close` to call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Sheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    /**
     * Whether the whole sheet scrolls. A sheet with its own scrolling list (the model picker) turns
     * this off: a list inside a scrolling sheet inside a draggable sheet made the three fight over
     * every upward swipe, so the list bounced up and down.
     */
    scrollable: Boolean = true,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            content(close)
        }
    }
}

/** A choice in a sheet: rounded, the picked one tinted with the accent colour and ticked. */
@Composable
internal fun Option(title: String, subtitle: String? = null, selected: Boolean, enabled: Boolean = true, badge: String? = null, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) primary.copy(alpha = 0.14f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            badge?.let {
                Spacer(Modifier.width(8.dp))
                Pill(it, primary)
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.CheckCircle, null, tint = primary)
            }
        }
    }
}

@Composable
internal fun ServiceSheet(provider: Provider, ai: AiSettings, onPick: (Provider) -> Unit, onDismiss: () -> Unit) {
    Sheet(title = "Choose a service", subtitle = "Each service needs its own API key. Keys you saved before stay on this phone.", onDismiss = onDismiss) { close ->
        Provider.entries.forEach { option ->
            val taken = option != provider && option in ai.activeEngines
            Option(option.title, if (taken) "Already added" else option.byline, selected = option == provider, enabled = !taken) {
                if (option != provider) onPick(option)
                close()
            }
        }
    }
}

@Composable
internal fun KeySheet(provider: Provider, initialKey: String, initialUrl: String, viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(initialKey) }
    var url by remember { mutableStateOf(initialUrl) }
    var visible by remember { mutableStateOf(false) }
    val check by viewModel.keyCheck.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Sheet(title = "${provider.title} API key", subtitle = provider.keyHelp, onDismiss = onDismiss) { close ->
        GetKeyButton(provider)
        PasteKeyOnReturn(provider, key) { key = it }
        if (provider.needsBaseUrl) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text(stringResource(R.string.service_address)) },
                placeholder = { Text(stringResource(R.string.https_openrouter_ai_api_v1)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.paste_your_key)) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                KeyFieldIcons(
                    visible = visible,
                    onToggle = { visible = !visible },
                    onPaste = { clipboardText(context)?.trim()?.takeIf { it.isNotEmpty() }?.let { key = it } },
                )
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        CheckStatus(check)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetButton("Done", filled = false, modifier = Modifier.weight(1f), onClick = close)
            SheetButton(if (key.isBlank()) "Remove key" else "Save and test", filled = true, modifier = Modifier.weight(1f)) {
                viewModel.saveKey(provider, key, url)
            }
        }
    }
}

@Composable
internal fun SheetButton(label: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.click()
            onClick()
        },
        shape = RoundedCornerShape(16.dp),
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(50.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** A few plain words about each service, so the choice in step 1 makes sense without reading any docs. */
private fun Provider.blurb(): String = when (this) {
    Provider.DEEPSEEK -> "Low cost, reads food photos well"
    Provider.GEMINI -> "Has a free tier"
    Provider.OPENAI -> "Pay as you go"
    Provider.ANTHROPIC -> "Pay as you go"
    Provider.OPENROUTER -> "One key for many models, some free"
    Provider.CUSTOM -> "Any OpenAI-compatible service"
}

/**
 * Connecting an AI engine in four steps: pick a service, open its key page, paste the key, then save
 * and check it. A key copied on that page pastes itself in when you come back to Thermida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddEngineSheet(addable: List<Provider>, viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var provider by remember { mutableStateOf<Provider?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf(false) }
    val check by viewModel.keyCheck.collectAsStateWithLifecycle()
    val chosen = provider
    val connected = check is KeyCheck.Ok
    Sheet(
        title = "Connect an AI engine",
        subtitle = "Photo scans and descriptions use your own key from one service. Your key stays on this phone.",
        onDismiss = onDismiss,
    ) { close ->
        Step(1, "Pick a service") {
            ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                OutlinedTextField(
                    value = chosen?.title.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Service") },
                    placeholder = { Text("Tap to choose") },
                    supportingText = { chosen?.let { Text(it.blurb()) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    addable.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        option.blurb(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                provider = option
                                key = ""
                                url = ""
                                pasted = false
                                viewModel.resetKeyCheck()
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }

        if (chosen != null) {
            Step(2, "Get a key") {
                if (chosen.keyUrl != null) {
                    GetKeyButton(chosen)
                    Text(
                        "Sign in on that page, create a key and copy it. When you come back, it pastes in by itself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Copy a key from your service, and type its web address in the next step.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Step(3, "Paste the key") {
                PasteKeyOnReturn(chosen, key) {
                    key = it
                    pasted = true
                }
                if (chosen.needsBaseUrl) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        label = { Text("Web address") },
                        placeholder = { Text("https://example.com/v1") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = {
                        key = it
                        pasted = false
                    },
                    singleLine = true,
                    label = { Text("Key") },
                    placeholder = { Text("Paste your key here") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        KeyFieldIcons(
                            visible = visible,
                            onToggle = { visible = !visible },
                            onPaste = {
                                clipboardText(context)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                    key = it
                                    pasted = true
                                }
                            },
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pasted && key.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Pasted from your clipboard", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Step(4, "Save and check") {
                if (connected) {
                    Text(
                        "Connected. ${chosen.title} answered, so scans and descriptions can use it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    CheckStatus(check)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (connected) {
                        SheetButton("Done", filled = true, modifier = Modifier.weight(1f), onClick = close)
                    } else {
                        SheetButton("Close", filled = false, modifier = Modifier.weight(1f), onClick = close)
                        SheetButton("Save and check", filled = true, modifier = Modifier.weight(1f)) {
                            viewModel.addEngine(chosen)
                            viewModel.saveKey(chosen, key, url)
                        }
                    }
                }
            }
        }
    }
}

/** A numbered step in a sheet: the number in a dot, a title, then what the step needs. */
@Composable
internal fun ColumnScope.Step(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
    Spacer(Modifier.height(10.dp))
    content()
}

/** Opens the page where the service makes its key, in the browser. Nothing for a custom service. */
@Composable
internal fun GetKeyButton(provider: Provider) {
    val uriHandler = LocalUriHandler.current
    val url = provider.keyUrl ?: return
    SheetButton("Open ${provider.title} to get a key", filled = false, modifier = Modifier.fillMaxWidth()) {
        uriHandler.openUri(url)
    }
    Spacer(Modifier.height(12.dp))
}

/** The clipboard's text, or null when it holds none. */
internal fun clipboardText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()

/**
 * When Thermida comes back to the front (say, from the key page in the browser), a key on the
 * clipboard that this service issues fills the empty key field by itself. A key already typed is
 * never overwritten.
 */
@Composable
internal fun PasteKeyOnReturn(provider: Provider, key: String, onPasted: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentKey by rememberUpdatedState(key)
    val paste by rememberUpdatedState(onPasted)
    // Counts each time Thermida comes back to the front, say from the key page in the browser.
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Once when the sheet opens, then after each return. The short wait lets the window get focus first.
    LaunchedEffect(provider, resumes) {
        delay(300)
        if (currentKey.isNotBlank()) return@LaunchedEffect
        clipboardText(context)?.trim()?.takeIf { provider.looksLikeKey(it) }?.let(paste)
    }
}

/** The key field's icons: paste the clipboard's key, and show or hide the key. */
@Composable
internal fun KeyFieldIcons(visible: Boolean, onToggle: () -> Unit, onPaste: () -> Unit) {
    Row {
        IconButton(onClick = onPaste) {
            Icon(Icons.Rounded.ContentPaste, "Paste key from clipboard")
        }
        IconButton(onClick = onToggle) {
            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Hide key" else "Show key")
        }
    }
}

@Composable
internal fun CheckStatus(check: KeyCheck) {
    when (check) {
        KeyCheck.Idle -> Unit
        KeyCheck.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.checking), style = MaterialTheme.typography.bodyMedium)
        }
        is KeyCheck.Ok -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("Key works · ${check.models.size} models available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        is KeyCheck.Failed -> Text(check.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun ModelSheet(provider: Provider, selected: String, viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val check by viewModel.keyCheck.collectAsStateWithLifecycle()
    var typed by remember { mutableStateOf(selected) }
    LaunchedEffect(provider) { viewModel.loadModels(provider) }
    Sheet(
        title = "${provider.title} model",
        subtitle = when (provider) {
            Provider.DEEPSEEK -> "Pick a model that accepts images. Automatic prefers deepseek-flash (V4.1 Flash)."
            Provider.GEMINI -> "Flash models are fast and on the free tier."
            Provider.OPENAI -> "Automatic picks a recent mini model: fast, cheap and able to read photos."
            Provider.ANTHROPIC -> "Automatic picks a Haiku model: fast, cheap and able to read photos."
            Provider.OPENROUTER -> "Only models that can read photos are listed. Free ones cost nothing but have daily limits and can be busy."
            Provider.CUSTOM -> "Name a model that can read images, exactly as the service spells it."
        },
        onDismiss = onDismiss,
        scrollable = false,
    ) { close ->
        val ok = check as? KeyCheck.Ok
        val models = ok?.models.orEmpty()
        val canFilterFree = provider == Provider.OPENROUTER
        var query by remember { mutableStateOf("") }
        var freeOnly by remember { mutableStateOf(false) }
        // Long lists (OpenRouter has hundreds) get a search box, and OpenRouter a Free filter.
        if (models.size > 12 || canFilterFree) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search ${models.size} models") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (canFilterFree) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChipButton("All", selected = !freeOnly) { freeOnly = false }
                    FilterChipButton("Free only · ${models.count(OpenRouterModels::isFree)}", selected = freeOnly) { freeOnly = true }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        val shown = models.filter { m ->
            (!freeOnly || OpenRouterModels.isFree(m)) && words.all { it in m.lowercase() }
        }
        // The only thing that scrolls in this sheet; it gives up room when the keyboard is open.
        LazyColumn(
            Modifier
                .weight(1f, fill = false)
                .heightIn(max = 420.dp),
        ) {
            if (provider != Provider.CUSTOM && words.isEmpty() && !freeOnly) {
                item {
                    Option(ok?.automatic?.let { "Automatic ($it)" } ?: "Automatic", "Picks a good model for you", selected = selected.isBlank()) {
                        viewModel.setModel(provider, "")
                        close()
                    }
                }
            }
            items(shown, key = { it }) { model ->
                Option(model, selected = model == selected, badge = if (canFilterFree && OpenRouterModels.isFree(model)) "Free" else null) {
                    viewModel.setModel(provider, model)
                    close()
                }
            }
            if (ok != null && shown.isEmpty()) {
                item {
                    Text(
                        if (freeOnly) "No free models match." else "No models match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
        if (check !is KeyCheck.Ok) {
            Spacer(Modifier.height(8.dp))
            CheckStatus(check)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            label = { Text(stringResource(R.string.or_type_a_model_name)) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (typed.isNotBlank() && typed != selected) {
            Spacer(Modifier.height(12.dp))
            SheetButton("Use this name", filled = true, modifier = Modifier.fillMaxWidth()) {
                viewModel.setModel(provider, typed)
                close()
            }
        }
    }
}

/** A small pill toggle, filled with the accent colour when on. */
@Composable
internal fun FilterChipButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}
