package app.thermida.ui.coach

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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.animation.core.tween
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
import app.thermida.data.CoachStarters
import java.time.LocalDate
import app.thermida.ui.components.Pill
import app.thermida.ui.components.rememberHaptics
import app.thermida.ui.components.RoundIconButton
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import app.thermida.voice.SpeechText
import app.thermida.data.DEFAULT_PAUSE_MS
import app.thermida.data.DEFAULT_SPEECH_RATE
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import app.thermida.voice.VoiceInput
import app.thermida.voice.VoiceLanguage
import app.thermida.voice.VoiceOutput
import app.thermida.voice.VoiceReply
import kotlinx.coroutines.delay

internal const val TYPE_CHARS_PER_SECOND = 140L

/** Questions offered on an empty chat when nothing better fits today; tapping one asks it. */
val CoachSuggestions = CoachStarters.DEFAULTS

@Composable
fun CoachScreen(viewModel: CoachViewModel, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val thinking by viewModel.thinking.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val typing by viewModel.typing.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val starters by viewModel.starters.collectAsStateWithLifecycle()
    val restored by viewModel.restored.collectAsStateWithLifecycle()
    val speak by viewModel.speak.collectAsStateWithLifecycle(initialValue = false)
    val language by viewModel.voiceLanguage.collectAsStateWithLifecycle(initialValue = VoiceLanguage.pick(null))
    val handsFree by viewModel.handsFree.collectAsStateWithLifecycle(initialValue = true)
    val speechRate by viewModel.speechRate.collectAsStateWithLifecycle(initialValue = DEFAULT_SPEECH_RATE)
    val pauseMs by viewModel.pauseMs.collectAsStateWithLifecycle(initialValue = DEFAULT_PAUSE_MS)
    val context = LocalContext.current
    val voiceIn = remember { VoiceInput(context) }
    val voiceOut = remember { VoiceOutput(context) }
    DisposableEffect(Unit) {
        onDispose {
            voiceIn.release()
            voiceOut.shutdown()
        }
    }
    SideEffect { voiceOut.rate = speechRate }
    // A back-and-forth by voice: a spoken question starts it, and while it lasts each answer is read
    // aloud and the mic opens again afterwards (also after a proposal, so "yes" or "no" answers it).
    // Typing, tapping the text box, ✕, an error or silence ends it. Typed questions are read only
    // with the speaker on.
    var conversation by remember { mutableStateOf(false) }
    var listenAgain by remember { mutableIntStateOf(0) }
    // Why the voice couldn't read an answer, shown under the chat for a moment.
    var voiceProblem by remember { mutableStateOf<String?>(null) }
    val latestHandsFree by rememberUpdatedState(handsFree)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    LaunchedEffect(error) { if (error != null) conversation = false }
    // Leaving Thermida (Home, another app, the screen turning off) silences the coach at once: the
    // voice stops mid-sentence, the mic closes and the conversation ends, so nothing talks or
    // listens while the app isn't on screen.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                voiceIn.release()
                voiceOut.stop()
                conversation = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var spokenAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(typing, messages) {
        val at = typing ?: return@LaunchedEffect
        if (at == spokenAt) return@LaunchedEffect
        val answer = messages.firstOrNull { it.at == at } ?: return@LaunchedEffect
        spokenAt = at
        val talkBack = conversation
        if (!speak && !talkBack) return@LaunchedEffect
        voiceOut.speak(
            answer.text,
            onFinished = if (!talkBack) null else {
                {
                    scope.launch {
                        // A beat, so the mic doesn't hear the end of the voice.
                        delay(300)
                        if (conversation && latestHandsFree && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            listenAgain++
                        } else {
                            conversation = false
                        }
                    }
                }
            },
            onFailed = { reason ->
                conversation = false
                voiceProblem = reason
            },
        )
    }
    CoachContent(
        messages = messages,
        thinking = thinking,
        error = error,
        loaded = loaded,
        onSend = viewModel::send,
        onRetry = viewModel::retry,
        onNewChat = viewModel::newChat,
        onBack = onBack,
        typingAt = typing,
        activity = activity,
        onTyped = viewModel::typed,
        onAccept = viewModel::accept,
        onDecline = viewModel::decline,
        onGrams = viewModel::setGrams,
        starters = starters,
        onStop = viewModel::stop,
        restoredInput = restored,
        onRestoredShown = viewModel::restoredShown,
        voice = CoachVoice(
            input = voiceIn,
            output = voiceOut,
            language = language,
            speak = speak,
            onLanguage = viewModel::setVoiceLanguage,
            onSpokenQuestion = { conversation = true },
            onSpeak = { on ->
                if (!on) voiceOut.stop()
                // Turning the speaker off while the coach talks ends the conversation.
                if (voiceOut.speaking) conversation = false
                viewModel.setSpeak(on)
            },
            conversation = conversation,
            listenAgain = listenAgain,
            pauseMs = pauseMs.toLong(),
            onLoopEnd = { conversation = false },
            problem = voiceProblem,
            onProblemShown = { voiceProblem = null },
        ),
    )
}

/** The whole chat, driven only by its arguments (so it can be tested without an AI engine). */
@Composable
fun CoachContent(
    messages: List<CoachMessage>,
    thinking: Boolean,
    error: String?,
    loaded: Boolean,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    onNewChat: () -> Unit,
    onBack: () -> Unit,
    /** The answer (by its time) to type out letter by letter; older ones show in full. */
    typingAt: Long? = null,
    /** Shown instead of "Thinking…" while the coach does something else, like a product lookup. */
    activity: String? = null,
    onTyped: () -> Unit = {},
    /** Accept or Decline on a proposed change: the message it belongs to (by time) and its position. */
    onAccept: (Long, Int) -> Unit = { _, _ -> },
    onDecline: (Long, Int) -> Unit = { _, _ -> },
    /** A new amount picked for one food on a card: message time, card position, food position, grams. */
    onGrams: (Long, Int, Int, Double) -> Unit = { _, _, _, _ -> },
    /** The questions offered while the chat is empty. */
    starters: List<String> = CoachSuggestions,
    /** The mic and the speaker; null leaves them out. */
    voice: CoachVoice? = null,
    /** Stop was tapped while the coach was thinking or typing out an answer. */
    onStop: () -> Unit = {},
    /** A question taken back by Stop; it goes back into the text box. */
    restoredInput: String? = null,
    onRestoredShown: () -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf("") }
    // Typed, tapped (starters, follow-up pills) or sent by hand: the voice conversation is over.
    val send: (String) -> Unit = { question ->
        voice?.onLoopEnd?.invoke()
        onSend(question)
    }
    // Why the mic stopped without words (no connection, nothing heard…), shown for a few seconds.
    var voiceNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(voiceNote) {
        if (voiceNote != null) {
            delay(4_000)
            voiceNote = null
        }
    }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val haptics = rememberHaptics()
    // The words heard so far, shown above the waveform while listening.
    var heardLive by remember { mutableStateOf("") }
    // The voice couldn't read an answer: say why where voice notes go.
    LaunchedEffect(voice?.problem) {
        voice?.problem?.let {
            voiceNote = it
            voice?.onProblemShown()
        }
    }
    val latestMessages by rememberUpdatedState(messages)
    val latestOnAccept by rememberUpdatedState(onAccept)
    val latestOnDecline by rememberUpdatedState(onDecline)

    /**
     * A short spoken "yes" or "no" while the newest answer still has proposal cards waiting:
     * accepts or declines all of them, and the conversation ends there. True when it was one.
     */
    fun answerCards(heard: String): Boolean {
        val last = latestMessages.lastOrNull()?.takeIf { !it.fromUser } ?: return false
        val waiting = last.actions.withIndex().filter { it.value.status == ActionStatus.PENDING }
        if (waiting.isEmpty()) return false
        val reply = VoiceReply.of(heard) ?: return false
        waiting.forEach { (index, _) -> if (reply == VoiceReply.ACCEPT) latestOnAccept(last.at, index) else latestOnDecline(last.at, index) }
        haptics.click()
        voiceNote = when {
            reply == VoiceReply.DECLINE -> if (waiting.size == 1) "Declined." else "Declined all ${waiting.size}."
            waiting.size == 1 -> "Accepted."
            else -> "Accepted all ${waiting.size}."
        }
        voice?.onLoopEnd?.invoke()
        return true
    }

    fun listen() {
        val v = voice ?: return
        v.output.stop()
        voiceNote = null
        heardLive = ""
        keyboard?.hide()
        focus.clearFocus()
        // Anything already typed goes first; the spoken sentence is sent as soon as it ends.
        val typed = input
        v.input.start(
            v.language,
            pauseMs = v.pauseMs,
            onText = { heardLive = it },
            onDone = { heard ->
                heardLive = ""
                if (typed.isBlank() && answerCards(heard)) return@start
                val question = SpeechText.join(typed, heard)
                if (loaded && !thinking) {
                    input = ""
                    v.onSpokenQuestion()
                    onSend(question)
                } else {
                    input = question
                }
            },
            onError = {
                heardLive = ""
                voiceNote = it
                v.onLoopEnd()
            },
        )
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else voiceNote = "Allow the microphone for Thermida to talk to the coach."
    }
    var confirmNew by remember { mutableStateOf(false) }
    LaunchedEffect(restoredInput) {
        if (restoredInput != null) {
            input = restoredInput
            onRestoredShown()
        }
    }
    var seenListenAgain by remember { mutableIntStateOf(voice?.listenAgain ?: 0) }
    LaunchedEffect(voice?.listenAgain) {
        val n = voice?.listenAgain ?: return@LaunchedEffect
        if (n == seenListenAgain) return@LaunchedEffect
        seenListenAgain = n
        haptics.tick()
        listen()
    }
    val listState = rememberLazyListState()
    val rows = messages.size + (if (thinking || error != null) 1 else 0)
    // Each new message scrolls to its start, so a long answer is read from the top.
    LaunchedEffect(rows) {
        if (rows > 0) listState.animateScrollToItem(rows - 1)
    }
    // The keyboard coming up shrinks the chat from below. Scroll up by as much, so what was at the
    // bottom (usually the latest answer) stays in view instead of going under the keyboard.
    LaunchedEffect(listState) {
        var height = 0
        snapshotFlow { listState.layoutInfo.viewportSize.height }.collect { now ->
            val shrunk = height - now
            height = now
            if (now > 0 && shrunk > 0 && listState.canScrollForward) {
                try {
                    listState.scrollBy(shrunk.toFloat())
                } catch (_: CancellationException) {
                    // A finger or another scroll took over.
                }
            }
        }
    }
    // While an answer types itself out, the list follows it down so the newest line stays in view.
    // Putting a finger on the list stops the following until the next answer.
    LaunchedEffect(typingAt) {
        if (typingAt == null) return@LaunchedEffect
        var follow = true
        launch {
            listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false }
        }
        while (follow) {
            withFrameMillis { }
            if (follow && !listState.isScrollInProgress && listState.canScrollForward) {
                try {
                    // Exactly as far as the bottom of the last row: a huge distance throws the list off.
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()
                    if (last == null || last.index < info.totalItemsCount - 1) {
                        listState.scrollToItem(info.totalItemsCount - 1)
                    } else {
                        val below = last.offset + last.size - (info.viewportEndOffset - info.afterContentPadding)
                        if (below > 0) listState.scrollBy(below.toFloat())
                    }
                } catch (_: CancellationException) {
                    // The finger took over.
                    follow = false
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The chat slides down from the top, so it closes upwards.
            RoundIconButton(Icons.Rounded.KeyboardArrowUp, "Close", onBack)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.coach), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Pill("Beta", MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            if (voice != null) {
                RoundIconButton(
                    if (voice.speak) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    if (voice.speak) "Stop reading answers aloud" else "Read answers aloud",
                    {
                        haptics.tick()
                        voice.onSpeak(!voice.speak)
                    },
                    container = if (voice.speak) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = if (voice.speak) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
            }
            if (messages.isNotEmpty()) {
                // A broom-and-bin icon: this wipes the conversation, it doesn't open a second one.
                RoundIconButton(Icons.Rounded.DeleteSweep, "Clear chat", { confirmNew = true })
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (loaded && messages.isEmpty() && !thinking && error == null) {
                EmptyCoach(starters, onPick = send)
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(messages) { message ->
                        if (message.fromUser) {
                            UserBubble(message.text)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CoachText(message.text, typing = message.at == typingAt, onTyped = onTyped)
                                message.actions.filter { it.status != ActionStatus.PENDING }.forEach { ActionOutcome(it) }
                            }
                        }
                    }
                    if (thinking) {
                        item { Thinking(activity ?: "Thinking…") }
                    } else if (error != null) {
                        item { ErrorRow(error, onRetry) }
                    }
                }
            }
        }

        // The newest answer's proposals wait above the text box until each is accepted or declined.
        // They appear once the answer has finished typing out.
        val last = messages.lastOrNull()?.takeIf { !it.fromUser && it.at != typingAt }
        val pending = last?.actions?.withIndex()?.filter { it.value.status == ActionStatus.PENDING }.orEmpty()
        AnimatedVisibility(
            visible = pending.isNotEmpty() && !thinking,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pending.forEach { (index, action) ->
                    ActionCard(
                        action,
                        onAccept = { last?.let { onAccept(it.at, index) } },
                        onDecline = { last?.let { onDecline(it.at, index) } },
                        onGrams = { food, grams -> last?.let { onGrams(it.at, index, food, grams) } },
                    )
                }
            }
        }

        // Follow-up questions from the newest answer: small pills in a sideways-scrolling row, kept
        // quiet next to the proposal cards. They step aside while the user types or a card waits.
        val followUps = last?.suggestions.orEmpty()
        AnimatedVisibility(
            visible = followUps.isNotEmpty() && pending.isEmpty() && !thinking && error == null && input.isBlank() && voice?.input?.listening != true,
            enter = fadeIn(tween(220)) + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(120)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            FollowUpPills(followUps, onPick = { question ->
                haptics.tick()
                send(question)
            })
        }

        AnimatedVisibility(visible = voiceNote != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(
                voiceNote.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
        }
        val listeningNow = voice?.input?.listening == true
        AnimatedVisibility(visible = listeningNow, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(
                heardLive.ifBlank { if (voice?.language == VoiceLanguage.GREEK) "Μιλήστε…" else "Listening…" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (heardLive.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        AnimatedVisibility(
            visible = voice?.output?.speaking == true && !listeningNow,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                onClick = {
                    haptics.tick()
                    voice?.output?.stop()
                    // In a conversation, cutting in means it's the user's turn: the mic opens at once.
                    if (voice?.conversation == true) listen() else voice?.onLoopEnd?.invoke()
                },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                Text(
                    if (voice?.conversation == true) "Speaking… tap to talk" else "Speaking… tap to stop",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        if (voice != null && listeningNow) {
            VoiceModeBar(
                level = voice.input.level,
                language = voice.language,
                onCancel = {
                    haptics.tick()
                    voice.input.release()
                    voice.onLoopEnd()
                    heardLive = ""
                },
                onSendNow = {
                    haptics.tick()
                    voice.input.stop()
                },
            )
        } else InputBar(
            value = input,
            onChange = { input = it },
            canSend = input.isNotBlank() && !thinking && loaded,
            onSend = {
                voice?.input?.release()
                send(input)
                input = ""
            },
            onFocus = { voice?.onLoopEnd?.invoke() },
            busy = thinking || typingAt != null || voice?.output?.speaking == true,
            onStop = {
                haptics.tick()
                // Everything goes quiet: the voice, the mic, the conversation, and the request itself.
                voice?.output?.stop()
                voice?.input?.release()
                voice?.onLoopEnd?.invoke()
                onStop()
            },
            voice = voice?.let { v ->
                VoiceBar(
                    listening = v.input.listening,
                    level = v.input.level,
                    language = v.language,
                    onMic = {
                        haptics.tick()
                        when {
                            v.input.listening -> v.input.stop()
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listen()
                            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onLanguage = {
                        haptics.tick()
                        v.onLanguage(v.language.other())
                    },
                )
            },
        )
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.clear_this_chat)) },
            text = { Text(stringResource(R.string.the_whole_conversation_is_deleted_and)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.click()
                    confirmNew = false
                    onNewChat()
                }) { Text(stringResource(R.string.clear_chat), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * "How much?" for one food: the coach's ready-made amounts as chips (the picked one in the accent
 * colour), plus Other for typing any weight. Every pick updates the card's totals straight away.
 */

/**
 * The little formatting models use in chat: **bold**, bullet lines ("- " or "* ") and "#" headings
 * (shown as bold lines). Anything else is left as written.
 */
internal fun coachMarkup(raw: String, strong: Color): AnnotatedString = buildAnnotatedString {
    val lines = raw.trim().lines()
    lines.forEachIndexed { index, original ->
        var line = original.trimEnd()
        val heading = line.trimStart().startsWith("#")
        line = when {
            heading -> line.trimStart().trimStart('#').trim()
            Regex("^\\s*[-*•]\\s+").containsMatchIn(line) -> line.replace(Regex("^(\\s*)[-*•]\\s+"), "$1•  ")
            else -> line
        }
        if (heading) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { appendBold(line.replace("**", ""), strong) }
        } else {
            appendBold(line, strong)
        }
        if (index < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendBold(line: String, strong: Color) {
    // Models sometimes bold with __text__ instead of **text**.
    val text = line.replace(Regex("""__(\S(?:.*?\S)?)__"""), "**$1**")
    val parts = text.split("**")
    // An odd count means every ** has a partner; otherwise treat the asterisks as plain text.
    if (parts.size % 2 == 0) {
        appendNumbers(text, strong)
        return
    }
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(part) } else appendNumbers(part, strong)
    }
}

/**
 * Numbers with a unit (121.5 kg, 1,800 kcal, 140 g, 2 L, 25 %) are bold even when the model forgot
 * to mark them, so the key figures always stand out.
 */
private fun AnnotatedString.Builder.appendNumbers(text: String, strong: Color) {
    var last = 0
    KEY_NUMBER.findAll(text).forEach { match ->
        append(text.substring(last, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(match.value) }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

private val KEY_NUMBER = Regex(
    """(?<![\w.,])[-+−~]?\d+(?:[.,]\d+)*\s?(?:%|(?:kcal|cal|kg|lbs?|ml|mg|g|l)(?![\p{L}\d]))""",
    RegexOption.IGNORE_CASE,
)
