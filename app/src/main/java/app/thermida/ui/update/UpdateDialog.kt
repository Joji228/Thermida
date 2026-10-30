package app.thermida.ui.update

import androidx.compose.ui.res.stringResource
import app.thermida.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thermida.update.AppUpdater
import app.thermida.update.InstallStart
import app.thermida.update.UpdateState
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * The update popup: what's new, then Download, a progress bar, and Install. Shown on the main
 * screen after the check on launch, and from Settings after a manual check. [onDismiss] hides it;
 * a download that is already running keeps going and shows in Settings.
 */
@Composable
fun UpdateDialog(updater: AppUpdater, state: UpdateState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refusal by remember { mutableStateOf<String?>(null) }

    val installNow: (File) -> Unit = { file ->
        refusal = null
        when (val result = updater.install(file)) {
            InstallStart.Started -> onDismiss()
            InstallStart.NeedsPermission -> context.startActivity(updater.permissionIntent())
            is InstallStart.Refused -> refusal = result.message
        }
    }

    // Once the APK has downloaded and checked, Android's install prompt opens by itself.
    val readyFile = (state as? UpdateState.Ready)?.file
    LaunchedEffect(readyFile) {
        readyFile?.let(installNow)
    }

    when (state) {
        is UpdateState.Available -> {
            val release = state.release
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text("Thermida ${release.version} is available") },
                text = {
                    Column {
                        Text(
                            "You have ${updater.installedVersion}. What's new:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                release.notes.ifBlank { "No notes for this version." },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { updater.startDownload() }) { Text(stringResource(R.string.download)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
            )
        }

        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text("Downloading ${state.release.version}") },
                text = {
                    Column {
                        LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${(state.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hide)) } },
            )
        }

        is UpdateState.Ready -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text("Install ${state.release.version}") },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.android_asks_you_to_confirm_once),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        refusal?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { installNow(state.file) }) { Text(stringResource(R.string.install)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.later)) } },
            )
        }

        is UpdateState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(stringResource(R.string.the_update_didn_t_finish)) },
                text = { Text(state.message, style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    Button(onClick = { scope.launch { updater.check() } }) { Text(stringResource(R.string.try_again)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
            )
        }

        else -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(stringResource(R.string.thermida_is_up_to_date)) },
                text = { Text("Version ${updater.installedVersion} is the latest.", style = MaterialTheme.typography.bodyMedium) },
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
            )
        }
    }
}

/** The main-screen popup: shown after the check on launch when a newer version exists. */
@Composable
fun UpdatePrompt(updater: AppUpdater) {
    val prompt by updater.prompt.collectAsStateWithLifecycle()
    val state by updater.state.collectAsStateWithLifecycle()
    if (prompt != null) {
        UpdateDialog(updater, state) { updater.dismissPrompt() }
    }
}
