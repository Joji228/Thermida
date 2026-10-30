package app.thermida.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thermida.R
import app.thermida.ui.components.GroupedList
import app.thermida.ui.components.RoundIconButton

/**
 * Settings → Devices: the Xiaomi smart scale, kept off the main Settings page because it only
 * matters to people who own that exact scale. Off by default, as before.
 */
@Composable
fun DevicesScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var scaleConfirm by remember { mutableStateOf(false) }
    // Hands-free weigh-ins need the Bluetooth scan permission; asked right when the scale is turned on.
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
            Text("Devices", style = MaterialTheme.typography.headlineSmall)
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            item { GroupLabel("Smart scale") }
            item {
                GroupedList(
                    rows = listOf(
                        {
                            SwitchRow(
                                title = "Xiaomi smart scale",
                                subtitle = if (state.scaleEnabled) {
                                    "On. Open Thermida and step on the scale; weigh-ins save by themselves"
                                } else {
                                    "For the Mi Body Composition Scale 2 (XMTZC05HM). Adds weigh-ins over Bluetooth and a Body screen"
                                },
                                checked = state.scaleEnabled,
                                // Turning it on asks first; turning it off doesn't need to.
                                onChange = { on -> if (on) scaleConfirm = true else viewModel.setScaleEnabled(false) },
                                icon = Icons.Rounded.MonitorWeight,
                            )
                        },
                    ),
                )
            }
        }
    }

    if (scaleConfirm) {
        AlertDialog(
            onDismissRequest = { scaleConfirm = false },
            title = { Text(stringResource(R.string.turn_on_the_smart_scale)) },
            text = {
                Text(
                    stringResource(R.string.this_works_only_with_the_xiaomi) +
                        "with metal pads on top. Other Xiaomi scales won't be recognized.\n\n" +
                        "While Thermida is open on your screen, it listens for the scale and saves each weigh-in by itself, " +
                        "with no button to press. It never listens in the background. There's nothing to pair, it never needs " +
                        "your location, and the Zepp Life app keeps working as before. A weigh-in more than 5 kg from your " +
                        "last one is checked with you first, in case someone else stepped on.\n\n" +
                        "You'll also get a Body screen with body fat, muscle, water and more. " +
                        "These are estimates for following trends, not medical measurements.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setScaleEnabled(true)
                    scaleConfirm = false
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        askBluetooth.launch(Manifest.permission.BLUETOOTH_SCAN)
                    }
                }) { Text(stringResource(R.string.turn_on)) }
            },
            dismissButton = { TextButton(onClick = { scaleConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
