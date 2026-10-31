package app.thermida.ui.settings

import app.thermida.domain.Nutrition
import androidx.compose.ui.res.stringResource
import app.thermida.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import app.thermida.data.DEFAULT_SPEECH_RATE
import app.thermida.data.PHOTO_DAYS
import app.thermida.data.formatBytes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thermida.data.Provider
import app.thermida.data.ThemeMode
import app.thermida.update.UpdateState
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.semantics.Role
import app.thermida.ui.theme.AccentColor
import app.thermida.domain.Sex
import app.thermida.ui.components.GroupedList
import app.thermida.ui.components.ListRow
import app.thermida.ui.components.NumberStepper
import app.thermida.ui.components.rememberHaptics
import app.thermida.ui.components.PillSelector
import app.thermida.ui.components.RoundIconButton
import app.thermida.ui.components.format
import app.thermida.ui.theme.Nutrient
import androidx.compose.material.icons.rounded.Restaurant
import app.thermida.ui.update.UpdateDialog
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.rounded.ShowChart

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onAiEngine: () -> Unit,
    onDevices: () -> Unit,
    onCoach: () -> Unit,
    onReset: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val showUpdate by viewModel.updateDialog.collectAsStateWithLifecycle()
    val updateState by viewModel.updates.collectAsStateWithLifecycle()
    var targetDialog by remember { mutableStateOf(false) }
    var waterDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }
    var photoDialog by remember { mutableStateOf(false) }
    var cacheDialog by remember { mutableStateOf(false) }
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val photoStorage by viewModel.photoStorage.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.export(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(context, it) }
    }

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
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            item { GroupLabel("Profile and goals") }
            item {
                val p = state.profile
                GroupedList(
                    rows = listOf(
                        {
                            ListRow(
                                "Profile and goal",
                                subtitle = p?.let {
                                    "${if (it.sex == Sex.MALE) "Male" else "Female"} · ${it.age} y · ${it.heightCm.format()} cm · goal ${it.goalWeightKg.format(1)} kg"
                                },
                                icon = Icons.Rounded.Person,
                                onClick = onEditProfile,
                            )
                        },
                        {
                            ListRow(
                                "Daily calories",
                                subtitle = when {
                                    p?.customKcal != null -> "Custom target"
                                    state.estimate?.active == true -> "Smart: tuned to your real results"
                                    else -> "Calculated from your profile"
                                },
                                value = state.targets?.let { "${it.kcal.format()} kcal" },
                                icon = Icons.Rounded.LocalFireDepartment,
                                iconTint = Nutrient.Protein,
                                onClick = { targetDialog = true },
                            )
                        },
                        {
                            val estimate = state.estimate
                            SwitchRow(
                                title = "Smart target",
                                subtitle = when {
                                    p?.smartTarget == false -> "Off. Your calories use the standard formula only"
                                    p?.customKcal != null -> "Paused while you use your own calorie target"
                                    estimate == null -> "Tunes your daily calories to how your body really responds"
                                    estimate.active -> "Your real burn is about ${estimate.measuredTdee!!.format()} kcal a day, " +
                                        "measured from your meals and weigh-ins (${(estimate.confidence * 100).roundToInt()}% trusted). " +
                                        "It adjusts your target only when it's off by 100 kcal or more, once a week at most."
                                    else -> "Learning your real burn. ${estimate.waitingFor} " +
                                        "(${estimate.daysLogged} of ${estimate.daysNeeded} days of meals logged)"
                                } + (state.smartLast?.takeIf { p?.smartTarget == true }?.let { last ->
                                    " Last change, ${last.on.format(DateTimeFormatter.ofPattern("MMM d"))}: " +
                                        "${last.oldKcal.format()} → ${last.newKcal.format()} kcal."
                                } ?: ""),
                                checked = p?.smartTarget != false,
                                onChange = { on -> viewModel.updateProfile { it.copy(smartTarget = on) } },
                                icon = Icons.Rounded.Psychology,
                                iconTint = Nutrient.Fat,
                            )
                        },
                        {
                            ListRow(
                                "Water goal",
                                subtitle = if (p?.customWaterMl != null) "Custom target" else "Calculated from your weight and activity",
                                value = state.targets?.let { "${(it.waterMl / 1000.0).format(2).trimEnd('0').trimEnd('.', ',')} L" },
                                icon = Icons.Rounded.WaterDrop,
                                iconTint = Nutrient.Water,
                                onClick = { waterDialog = true },
                            )
                        },
                    ),
                )
            }

            item { GroupLabel("Food") }
            item {
                GroupedList(
                    rows = listOf(
                        {
                            val greek by viewModel.greekTuning.collectAsStateWithLifecycle(initialValue = true)
                            SwitchRow(
                                title = "Regional food hints",
                                subtitle = if (greek) {
                                    "On. Hints for Greek and European foods. International dishes and brands still work"
                                } else {
                                    "Off. Foods are estimated with no regional hints"
                                },
                                checked = greek,
                                onChange = viewModel::setGreekTuning,
                                icon = Icons.Rounded.Restaurant,
                                iconTint = Nutrient.Protein,
                            )
                        },
                        {
                            val foodFactsOn by viewModel.useFoodFacts.collectAsStateWithLifecycle(initialValue = false)
                            SwitchRow(
                                title = "Use Open Food Facts",
                                subtitle = if (foodFactsOn) {
                                    "On. Packaged foods in scans are checked against their real labels, and Add item can search products. Adds a couple of seconds to scans"
                                } else {
                                    "Free, open product database. Checks packaged foods in scans and lets you search products when adding food. Adds a couple of seconds; only food names are sent, never photos"
                                },
                                checked = foodFactsOn,
                                onChange = viewModel::setUseFoodFacts,
                                icon = Icons.Rounded.Search,
                            )
                        },
                    ),
                )
            }

            item { GroupLabel("AI") }
            item {
                val ai = state.ai
                GroupedList(
                    rows = listOf(
                        {
                            ListRow(
                                "AI engine",
                                subtitle = if (ai.isReady(ai.primary)) {
                                    val model = ai.modelFor(ai.primary).ifBlank { if (ai.primary == Provider.CUSTOM) "no model chosen" else "automatic model" }
                                    "${ai.primary.title} · $model" + (ai.fallbackTarget?.let { " · backup: ${it.title}" } ?: "")
                                } else {
                                    "Add an API key to start scanning meals"
                                },
                                icon = Icons.Rounded.AutoAwesome,
                                onClick = onAiEngine,
                            )
                        },
                        {
                            val coachOn by viewModel.coachEnabled.collectAsStateWithLifecycle(initialValue = false)
                            val handsFree by viewModel.coachHandsFree.collectAsStateWithLifecycle(initialValue = true)
                            val rate by viewModel.coachSpeechRate.collectAsStateWithLifecycle(initialValue = DEFAULT_SPEECH_RATE)
                            ListRow(
                                "Coach (beta)",
                                subtitle = if (coachOn) {
                                    "On" + (if (handsFree) " · hands-free voice" else "") + " · voice speed ${rate.toDouble().format(1)}×"
                                } else {
                                    "Off. A chat that knows your meals, water and weigh-ins"
                                },
                                icon = Icons.Rounded.Forum,
                                onClick = onCoach,
                            )
                        },
                    ),
                )
            }

            item { GroupLabel("Appearance") }
            item {
                GroupedList(
                    rows = listOf(
                        {
                            PillSelector(
                                ThemeMode.entries,
                                state.theme,
                                { it.title },
                                viewModel::setTheme,
                                Modifier.padding(14.dp),
                            )
                        },
                        {
                            AccentPicker(state.accent, viewModel::setAccent, Modifier.padding(14.dp))
                        },
                        {
                            val smooth by viewModel.smoothCharts.collectAsStateWithLifecycle(initialValue = true)
                            SwitchRow(
                                title = "Smooth chart lines",
                                subtitle = if (smooth) {
                                    "On. Weight and body charts draw your trend, so daily swings don't zigzag the line"
                                } else {
                                    "Off. The line joins your exact readings, swings and all"
                                },
                                checked = smooth,
                                onChange = viewModel::setSmoothCharts,
                                icon = Icons.Rounded.ShowChart,
                            )
                        },
                    ),
                )
            }

            item { GroupLabel("General") }
            item {
                GroupedList(
                    rows = listOf(
                        {
                            val hapticsOn by viewModel.hapticsEnabled.collectAsStateWithLifecycle(initialValue = true)
                            val haptics = rememberHaptics()
                            SwitchRow(
                                title = "Haptic feedback",
                                subtitle = if (hapticsOn) "On. Taps, switches and gestures vibrate" else "Off. Nothing vibrates",
                                checked = hapticsOn,
                                onChange = { on ->
                                    viewModel.setHapticsEnabled(on)
                                    // Turning it on confirms itself; turning it off is silent.
                                    if (on) haptics.click()
                                },
                                icon = Icons.Rounded.Vibration,
                                haptic = false,
                            )
                        },
                    ),
                )
            }

            item { GroupLabel("Devices") }
            item {
                GroupedList(
                    rows = listOf(
                        {
                            // The smart scale lives one level down: it only matters to people with that exact scale.
                            ListRow(
                                "Devices",
                                subtitle = "Smart scale: ${if (state.scaleEnabled) "on" else "off"}",
                                icon = Icons.Rounded.MonitorWeight,
                                onClick = onDevices,
                            )
                        },
                    ),
                )
            }

            item { GroupLabel("Data") }
            item {
                GroupedList(
                    rows = listOf(
                        {
                            ListRow(
                                "Export backup",
                                subtitle = "Save all your data to a file",
                                icon = Icons.Rounded.Upload,
                                onClick = { exportLauncher.launch("thermida-backup-${LocalDate.now()}.json") },
                            )
                        },
                        {
                            ListRow(
                                "Import backup",
                                subtitle = "Replaces the data on this phone",
                                icon = Icons.Rounded.Download,
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            )
                        },
                        {
                            ListRow(
                                "Storage and photos",
                                subtitle = (photoStorage?.second?.let { if (it == 1) "1 photo · " else "$it photos · " } ?: "") +
                                    "photos are deleted after $PHOTO_DAYS days, meals stay logged. Tap to delete photos now",
                                value = photoStorage?.first,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { photoDialog = true },
                            )
                        },
                        {
                            ListRow(
                                "Total app storage",
                                subtitle = storage?.let {
                                    "App ${formatBytes(it.appBytes)} · your data ${formatBytes(it.keptBytes)} · cache ${formatBytes(it.cacheBytes)}. Tap to clear the cache"
                                } ?: "Counting…",
                                value = storage?.let { formatBytes(it.totalBytes) },
                                icon = Icons.Rounded.Storage,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { cacheDialog = true },
                            )
                        },
                    ),
                )
            }

            item { GroupLabel("About") }
            item {
                val update by viewModel.updates.collectAsStateWithLifecycle()
                val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
                val check = { viewModel.checkForUpdates() }
                val download = { viewModel.downloadUpdate(context) }
                val install = { viewModel.installUpdate(context) }
                GroupedList(
                    rows = listOf(
                        {
                            when (val u = update) {
                                UpdateState.Idle -> ListRow(
                                    "Check for updates",
                                    subtitle = "Looks on GitHub each time you open Thermida",
                                    icon = Icons.Rounded.Download,
                                    onClick = check,
                                )
                                UpdateState.Checking -> ListRow(
                                    "Checking for updates…",
                                    icon = Icons.Rounded.Download,
                                    enabled = false,
                                )
                                UpdateState.UpToDate -> ListRow(
                                    "Thermida is up to date",
                                    subtitle = "Version $installed. Tap to check again",
                                    icon = Icons.Rounded.Download,
                                    onClick = check,
                                )
                                UpdateState.NoRelease -> ListRow(
                                    "Updates are installed by hand",
                                    subtitle = "Thermida can't see new versions on GitHub right now. Tap to check again",
                                    icon = Icons.Rounded.Download,
                                    onClick = check,
                                )
                                is UpdateState.Available -> ListRow(
                                    "Version ${u.release.version} is available",
                                    subtitle = "You have $installed. Tap to download and install",
                                    icon = Icons.Rounded.Download,
                                    onClick = download,
                                )
                                is UpdateState.Downloading -> ListRow(
                                    "Downloading version ${u.release.version}",
                                    value = "${(u.fraction * 100).roundToInt()}%",
                                    icon = Icons.Rounded.Download,
                                    enabled = false,
                                )
                                is UpdateState.Ready -> ListRow(
                                    "Install version ${u.release.version}",
                                    subtitle = "Android asks you to confirm once",
                                    icon = Icons.Rounded.Download,
                                    onClick = install,
                                )
                                is UpdateState.Failed -> ListRow(
                                    "Couldn't update",
                                    subtitle = "${u.message}. Tap to try again",
                                    icon = Icons.Rounded.Download,
                                    iconTint = MaterialTheme.colorScheme.error,
                                    onClick = check,
                                )
                            }
                        },
                    ),
                )
            }

            item {
                Text(
                    "Thermida ${context.packageManager.getPackageInfo(context.packageName, 0).versionName} · " +
                        "Your data stays on this phone. Photos are sent only to the AI provider you choose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }

            item { GroupLabel("Danger zone") }
            item {
                GroupedList(
                    rows = listOf(
                        {
                            ListRow(
                                "Erase everything and start over",
                                subtitle = "Deletes meals, water, weight, photos and your profile",
                                icon = Icons.Rounded.DeleteForever,
                                iconTint = MaterialTheme.colorScheme.error,
                                onClick = { resetDialog = true },
                            )
                        },
                    ),
                )
            }


        }
    }

    if (showUpdate) {
        UpdateDialog(viewModel.updater, updateState) { viewModel.closeUpdateDialog() }
    }
    if (targetDialog) {
        val profile = state.profile
        var kcal by remember { mutableDoubleStateOf((state.targets?.kcal ?: 2000).toDouble()) }
        AlertDialog(
            onDismissRequest = { targetDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.daily_calories)) },
            text = {
                Column {
                    NumberStepper("Target", kcal, { kcal = it }, 800.0..6000.0, 50.0, "kcal")
                    // Still allowed (a doctor may prescribe it), but never without saying so.
                    val minimum = Nutrition.minimumKcal(profile?.sex ?: Sex.MALE)
                    if (kcal < minimum) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "That's below ${minimum.format()} kcal, the usual safe minimum for " +
                                "${if (profile?.sex == Sex.FEMALE) "women" else "men"}. Only eat this little with a doctor's guidance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateProfile { it.copy(customKcal = kcal.toInt()) }
                    targetDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                if (profile?.customKcal != null) {
                    TextButton(onClick = {
                        viewModel.updateProfile { it.copy(customKcal = null) }
                        targetDialog = false
                    }) { Text(stringResource(R.string.use_automatic)) }
                } else {
                    TextButton(onClick = { targetDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
    if (waterDialog) {
        var ml by remember { mutableDoubleStateOf((state.targets?.waterMl ?: 2500).toDouble()) }
        AlertDialog(
            onDismissRequest = { waterDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.water_goal)) },
            text = { NumberStepper("Per day", ml, { ml = it }, 500.0..6000.0, 250.0, "ml") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateProfile { it.copy(customWaterMl = ml.toInt()) }
                    waterDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                if (state.profile?.customWaterMl != null) {
                    TextButton(onClick = {
                        viewModel.updateProfile { it.copy(customWaterMl = null) }
                        waterDialog = false
                    }) { Text(stringResource(R.string.use_automatic)) }
                } else {
                    TextButton(onClick = { waterDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
    if (photoDialog) {
        val count = photoStorage?.second ?: 0
        AlertDialog(
            onDismissRequest = { photoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(if (count == 0) "No meal photos" else "Delete meal photos?") },
            text = {
                Text(
                    if (count == 0) {
                        "There are no meal photos saved right now."
                    } else {
                        "Removes ${if (count == 1) "1 photo" else "all $count photos"} (${photoStorage?.first}). " +
                            "Every meal stays logged with its calories and macros. This can't be undone."
                    },
                )
            },
            confirmButton = {
                if (count == 0) {
                    TextButton(onClick = { photoDialog = false }) { Text(stringResource(R.string.ok)) }
                } else {
                    TextButton(onClick = {
                        photoDialog = false
                        viewModel.deleteAllPhotos()
                    }) { Text(stringResource(R.string.delete_photos), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                if (count > 0) TextButton(onClick = { photoDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (cacheDialog) {
        val cache = storage?.cacheBytes ?: 0L
        AlertDialog(
            onDismissRequest = { cacheDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Clear the cache?") },
            text = {
                Text(
                    "The app keeps ${formatBytes(cache)} of temporary files it can make again when it needs them. " +
                        "Clearing them frees the space.\n\n" +
                        "Your meals, meal photos, settings and API keys stay, and so do photos you're still scanning " +
                        "and an update waiting to be installed.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCache()
                    cacheDialog = false
                }) { Text("Clear cache") }
            },
            dismissButton = { TextButton(onClick = { cacheDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    pendingImport?.let { pending ->
        val b = pending.backup
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Replace your data?") },
            text = {
                Text(
                    "The backup has ${plural(b.meals.size, "meal")}, ${plural(b.weights.size, "weigh-in")} and " +
                        "${plural(b.water.size, "water entry", "water entries")}. It replaces everything on this phone, " +
                        "including your ${plural(pending.mealsNow, "meal")}. Meal photos aren't in backups. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelImport() }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (resetDialog) {
        ResetDialog(
            onDismiss = { resetDialog = false },
            onConfirm = { keepKeys ->
                resetDialog = false
                viewModel.resetAll(keepKeys, onReset)
            },
        )
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { viewModel.message.value = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { viewModel.message.value = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    /** False for the haptics switch itself, which plays (or withholds) its own feedback. */
    haptic: Boolean = true,
) {
    // Firm when a switch goes on, light when it goes off, whether the row or the switch was tapped.
    val haptics = rememberHaptics()
    val change = { on: Boolean ->
        if (haptic) haptics.toggle(on)
        onChange(on)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { change(!checked) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Same icon tile as ListRow, so switch rows line up with the rows around them.
        if (icon != null) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = iconTint)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = change,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/** "1 meal", "12 meals". */
private fun plural(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

/** Needs two taps on the red button, so a stray tap can't wipe months of data. */
@Composable
private fun ResetDialog(onDismiss: () -> Unit, onConfirm: (keepKeys: Boolean) -> Unit) {
    var removeKeys by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.erase_everything)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.all_meals_water_weight_history_photos) +
                        "This can't be undone. Export a backup first if you might want it back.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { removeKeys = !removeKeys },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = removeKeys, onCheckedChange = { removeKeys = it })
                    Text(stringResource(R.string.also_remove_my_api_keys), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (armed) onConfirm(!removeKeys) else armed = true }) {
                Text(if (armed) "Tap again to erase" else "Erase", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** The accent colors in one row; the chosen one carries a check mark and a ring. */
@Composable
private fun AccentPicker(selected: AccentColor, onSelect: (AccentColor) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(selected.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AccentColor.entries.chunked(5).forEach { rowColors ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowColors.forEach { accent ->
                    val isSelected = accent == selected
                    val color = if (dark) accent.dark else accent.light
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(color)
                            .semantics { contentDescription = accent.title }
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    haptics.tick()
                                    onSelect(accent)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = ", selected",
                                tint = if (dark) Color(0xFF0A0A0A) else Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
