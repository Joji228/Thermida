package app.thermida.ui.settings

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thermida.R
import app.thermida.data.DEFAULT_PAUSE_MS
import app.thermida.data.MAX_PAUSE_MS
import app.thermida.data.MIN_PAUSE_MS
import app.thermida.data.DEFAULT_SPEECH_RATE
import app.thermida.data.MAX_SPEECH_RATE
import app.thermida.data.MIN_SPEECH_RATE
import app.thermida.ui.components.GroupedList
import app.thermida.ui.components.ListRow
import app.thermida.ui.components.PillSelector
import app.thermida.ui.components.RoundIconButton
import app.thermida.voice.SpeechText
import app.thermida.voice.VoiceLanguage
import app.thermida.voice.VoiceOutput
import java.util.Locale
import kotlin.math.roundToInt

/** The pauses offered before a spoken question is sent, in milliseconds. */
private val PAUSES = listOf(MIN_PAUSE_MS, DEFAULT_PAUSE_MS, MAX_PAUSE_MS)

/**
 * Settings → Coach: whether the coach is on, how it talks and listens, how long its answers are,
 * and what it sends to the AI engine. Everything here is also reachable from the chat itself
 * (the speaker, the EN / ΕΛ chip); this is where the rest lives.
 */
@Composable
fun CoachSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coachOn by viewModel.coachEnabled.collectAsStateWithLifecycle(initialValue = false)
    val speak by viewModel.coachSpeak.collectAsStateWithLifecycle(initialValue = false)
    val handsFree by viewModel.coachHandsFree.collectAsStateWithLifecycle(initialValue = true)
    val rate by viewModel.coachSpeechRate.collectAsStateWithLifecycle(initialValue = DEFAULT_SPEECH_RATE)
    val pause by viewModel.coachPauseMs.collectAsStateWithLifecycle(initialValue = DEFAULT_PAUSE_MS)
    val language by viewModel.voiceLanguage.collectAsStateWithLifecycle(initialValue = VoiceLanguage.pick(null))
    val detailed by viewModel.coachDetailed.collectAsStateWithLifecycle(initialValue = false)
    val followUps by viewModel.coachFollowUps.collectAsStateWithLifecycle(initialValue = true)
    val foodFacts by viewModel.useFoodFacts.collectAsStateWithLifecycle(initialValue = true)

    var turnOnDialog by remember { mutableStateOf(false) }
    var sendsDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var draftRate by remember(rate) { mutableFloatStateOf(rate) }

    // Only used for "Play a sample"; it stops when the screen closes.
    val context = LocalContext.current
    val sample = remember { VoiceOutput(context) }
    DisposableEffect(Unit) { onDispose { sample.shutdown() } }

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
            Text("Coach", style = MaterialTheme.typography.headlineSmall)
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            item { GroupLabel("Coach") }
            item {
                GroupedList(
                    rows = buildList {
                        add {
                            SwitchRow(
                                title = "AI coach (beta)",
                                subtitle = if (coachOn) {
                                    "On. Pull down on any screen and hold to open it"
                                } else {
                                    "A chat that sees your meals, water, weigh-ins and body composition and explains how you're doing. Uses your AI engine"
                                },
                                checked = coachOn,
                                // Turning it on asks first, since it sends your data to the AI engine; off needs no question.
                                onChange = { on -> if (on) turnOnDialog = true else viewModel.setCoachEnabled(false) },
                                icon = Icons.Rounded.Forum,
                            )
                        }
                        if (coachOn) {
                            add {
                                ListRow(
                                    "Show the pull-down hint again",
                                    subtitle = "The one-time tip on how to open the coach",
                                    icon = Icons.Rounded.Lightbulb,
                                    onClick = { viewModel.resetCoachTutorial() },
                                )
                            }
                        }
                    },
                )
            }

            if (!coachOn) {
                item {
                    Text(
                        "Turn the coach on to change how it talks and answers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else {
                item { GroupLabel("Voice") }
                item {
                    GroupedList(
                        rows = listOf(
                            {
                                SwitchRow(
                                    title = "Read answers aloud",
                                    subtitle = if (speak) {
                                        "On. Typed questions get a spoken answer too"
                                    } else {
                                        "Off. Answers to questions you speak are always read aloud"
                                    },
                                    checked = speak,
                                    onChange = viewModel::setCoachSpeak,
                                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                )
                            },
                            {
                                SwitchRow(
                                    title = "Hands-free conversation",
                                    subtitle = if (handsFree) {
                                        "On. After a spoken question, the mic opens again when the coach finishes talking"
                                    } else {
                                        "Off. Tap the mic for every question"
                                    },
                                    checked = handsFree,
                                    onChange = viewModel::setCoachHandsFree,
                                    icon = Icons.Rounded.Mic,
                                )
                            },
                            {
                                SettingBlock("Speaking speed", "How fast the coach talks", Icons.Rounded.Speed, value = speedLabel(draftRate)) {
                                    Slider(
                                        value = draftRate,
                                        onValueChange = { draftRate = (it * 10).roundToInt() / 10f },
                                        onValueChangeFinished = { viewModel.setCoachSpeechRate(draftRate) },
                                        valueRange = MIN_SPEECH_RATE..MAX_SPEECH_RATE,
                                        steps = ((MAX_SPEECH_RATE - MIN_SPEECH_RATE) * 10).roundToInt() - 1,
                                    )
                                    TextButton(onClick = {
                                        sample.rate = draftRate
                                        sample.stop()
                                        sample.speak(SpeechText.sample(language))
                                    }) { Text("Play a sample") }
                                }
                            },
                            {
                                SettingBlock("Mic language", "What you speak to the coach in", Icons.Rounded.Translate) {
                                    PillSelector(
                                        VoiceLanguage.entries,
                                        language,
                                        { if (it == VoiceLanguage.GREEK) "Ελληνικά" else "English" },
                                        viewModel::setVoiceLanguage,
                                    )
                                }
                            },
                            {
                                SettingBlock("Pause before sending", "How long you stay quiet before a spoken question is sent", Icons.Rounded.Timer) {
                                    PillSelector(
                                        PAUSES,
                                        PAUSES.minByOrNull { kotlin.math.abs(it - pause) } ?: DEFAULT_PAUSE_MS,
                                        { pauseLabel(it) },
                                        viewModel::setCoachPauseMs,
                                    )
                                }
                            },
                        ),
                    )
                }

                item { GroupLabel("Answers") }
                item {
                    GroupedList(
                        rows = listOf(
                            {
                                SettingBlock("Answer length", "Short is a few sentences; detailed allows about 150 words", Icons.Rounded.Subject) {
                                    PillSelector(
                                        listOf(false, true),
                                        detailed,
                                        { if (it) "Detailed" else "Short" },
                                        viewModel::setCoachDetailed,
                                    )
                                }
                            },
                            {
                                SwitchRow(
                                    title = "Suggested questions",
                                    subtitle = if (followUps) {
                                        "On. The coach offers a next question when it helps"
                                    } else {
                                        "Off. No questions are suggested under answers"
                                    },
                                    checked = followUps,
                                    onChange = viewModel::setCoachFollowUps,
                                    icon = Icons.Rounded.QuestionAnswer,
                                )
                            },
                            {
                                ListRow(
                                    "Product lookups",
                                    subtitle = "The coach checks packaged foods on Open Food Facts. Change it under Food in Settings",
                                    value = if (foodFacts) "On" else "Off",
                                    icon = Icons.Rounded.Search,
                                )
                            },
                        ),
                    )
                }

                item { GroupLabel("Privacy") }
                item {
                    GroupedList(
                        rows = listOf(
                            {
                                ListRow(
                                    "What the coach sends",
                                    subtitle = "Your data goes to ${state.ai.primary.title}, never photos or keys",
                                    icon = Icons.Rounded.PrivacyTip,
                                    onClick = { sendsDialog = true },
                                )
                            },
                            {
                                ListRow(
                                    "Clear chat history",
                                    subtitle = "Deletes the conversation on this phone",
                                    icon = Icons.Rounded.DeleteSweep,
                                    iconTint = MaterialTheme.colorScheme.error,
                                    onClick = { clearDialog = true },
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    if (turnOnDialog) {
        AlertDialog(
            onDismissRequest = { turnOnDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.turn_on_the_ai_coach)) },
            text = {
                Text(
                    stringResource(R.string.the_coach_is_a_chat_that) +
                        "water or a weigh-in, or to change a target, and it prepares the change for you to accept or decline.\n\n" +
                        sendsText(state.ai.primary.title) + "\n\n" +
                        "To open it, pull down on any screen (except Settings) and hold for a moment.\n\n" +
                        "It's a beta. Answers are general guidance, not medical advice.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCoachEnabled(true)
                    turnOnDialog = false
                }) { Text(stringResource(R.string.turn_on)) }
            },
            dismissButton = { TextButton(onClick = { turnOnDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (sendsDialog) {
        AlertDialog(
            onDismissRequest = { sendsDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("What the coach sends") },
            text = { Text(sendsText(state.ai.primary.title) + "\n\n" + VOICE_PRIVACY) },
            confirmButton = { TextButton(onClick = { sendsDialog = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.clear_this_chat)) },
            text = { Text(stringResource(R.string.the_whole_conversation_is_deleted_and)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCoachChat()
                    clearDialog = false
                }) { Text(stringResource(R.string.clear_chat), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun sendsText(engine: String) =
    "Each question sends a summary of your data to your AI engine ($engine): your profile " +
        "(sex, age, height and goals), daily totals, recent meals, water, weigh-ins and body composition. " +
        "Photos and API keys are never sent."

/** Where speech goes: only the words reach the AI engine, but the audio may go to Google first. */
private const val VOICE_PRIVACY =
    "Speaking: English is turned into text on your phone when it has the offline English pack. Greek, and " +
        "English without that pack, go through Google's speech service, which may send the audio to Google. " +
        "Either way only the text reaches your AI engine, and nothing is recorded or kept."

private fun speedLabel(rate: Float) = String.format(Locale.US, "%.1f×", rate)

private fun pauseLabel(ms: Int) = when {
    ms < DEFAULT_PAUSE_MS -> "Short"
    ms > DEFAULT_PAUSE_MS -> "Long"
    else -> "Normal"
}

/** A settings row whose control sits underneath it: a title and note with the same icon tile as the other rows. */
@Composable
private fun SettingBlock(
    title: String,
    subtitle: String,
    icon: ImageVector,
    value: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (value != null) {
                Spacer(Modifier.width(12.dp))
                Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.size(8.dp))
        content()
    }
}
