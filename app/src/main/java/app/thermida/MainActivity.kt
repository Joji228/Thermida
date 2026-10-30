package app.thermida

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.thermida.data.ThemeMode
import app.thermida.ui.theme.AccentColor
import app.thermida.ui.body.BodyScreen
import app.thermida.ui.scale.AutoWeighIn
import app.thermida.ui.scan.HardwareShutter
import app.thermida.ui.body.BodyViewModel
import app.thermida.ui.coach.CoachHint
import app.thermida.ui.coach.CoachPullBubble
import app.thermida.ui.coach.CoachScreen
import app.thermida.ui.coach.CoachViewModel
import app.thermida.ui.components.rememberHaptics
import app.thermida.ui.coach.coachPull
import app.thermida.ui.coach.rememberCoachPull
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlinx.coroutines.coroutineScope
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.thermida.ui.coach.BUBBLE_DP
import app.thermida.ui.coach.bubbleCenterY
import kotlin.math.hypot
import kotlin.math.max
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.thermida.ui.dashboard.DashboardScreen
import app.thermida.ui.dashboard.DashboardViewModel
import app.thermida.ui.onboarding.OnboardingScreen
import app.thermida.ui.onboarding.OnboardingViewModel
import app.thermida.ui.result.ResultScreen
import app.thermida.ui.result.ResultViewModel
import app.thermida.ui.scan.ScanScreen
import app.thermida.ui.scan.ScanViewModel
import app.thermida.ui.settings.AiSettingsScreen
import app.thermida.ui.settings.CoachSettingsScreen
import app.thermida.ui.settings.DevicesScreen
import app.thermida.ui.settings.SettingsScreen
import app.thermida.ui.settings.SettingsViewModel
import app.thermida.ui.theme.ThermidaTheme
import app.thermida.ui.coach.CoachGate
import app.thermida.ui.update.UpdatePrompt
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

@Serializable object TodayRoute
@Serializable object ScanRoute
@Serializable data class ResultRoute(val mealId: Long = -1L)
@Serializable object SettingsRoute
@Serializable object AiSettingsRoute
@Serializable object DevicesRoute
@Serializable object CoachSettingsRoute
@Serializable data class OnboardingRoute(val edit: Boolean = false)
@Serializable object BodyRoute

class MainActivity : ComponentActivity() {
    private var ready = false

    /** On the Scan screen either volume key snaps and analyzes; anywhere else they change the volume. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val shutter = HardwareShutter.onPress
        if (shutter != null && !CoachGate.open && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.repeatCount == 0) shutter() // holding the key doesn't fire again
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (HardwareShutter.onPress != null && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        // Coming back after the phone slept through midnight: update "today" straight away.
        (application as ThermidaApp).container.clock.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        val container = (application as ThermidaApp).container

        setContent {
            val themeMode by container.settings.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
            val accent by container.settings.accent.collectAsStateWithLifecycle(initialValue = AccentColor.Default)
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.Transparent.toArgb())
                } else {
                    SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            var start by remember { mutableStateOf<Any?>(null) }
            LaunchedEffect(Unit) {
                start = if (container.settings.profile.first() == null) OnboardingRoute() else TodayRoute
                // Let the saved accent land before the splash goes away, so it never flashes another color first.
                container.settings.accent.first()
                ready = true
            }

            ThermidaTheme(dark = dark, accent = accent) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    start?.let { ThermidaNavHost(container, it) }
                    // The update popup, offered once per launch when a newer version exists.
                    if (start is TodayRoute) UpdatePrompt(container.updater)
                }
            }
        }
    }
}

// The bottom bar floats over the tab screens, which keep their own space for it (see barSpace), so
// the Scaffold's content padding is deliberately left unused.
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
private fun ThermidaNavHost(container: AppContainer, start: Any) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onToday = destination?.hasRoute<TodayRoute>() == true
    val onScan = destination?.hasRoute<ScanRoute>() == true

    // The AI coach (beta): pulled down from the top of any screen except Settings and setup.
    val coachOn by container.settings.coachEnabled.collectAsStateWithLifecycle(initialValue = false)
    val coachTutorialDone by container.settings.coachTutorialDone.collectAsStateWithLifecycle(initialValue = true)
    var coachOpen by rememberSaveable { mutableStateOf(false) }
    val coachHere = onToday || onScan || destination?.hasRoute<BodyRoute>() == true || destination?.hasRoute<ResultRoute>() == true
    LaunchedEffect(coachOn) { if (!coachOn) coachOpen = false }
    DisposableEffect(coachOpen) {
        CoachGate.open = coachOpen
        onDispose { CoachGate.open = false }
    }
    // Scan keeps its photos and note here, outside its page, so they're still there however you
    // leave Scan (a back swipe drops the page itself); the screen clears them once they're used.
    val appStore = checkNotNull(LocalViewModelStoreOwner.current)
    val pull = rememberCoachPull()
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    var bloomCenterY by remember { mutableFloatStateOf(0f) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .coachPull(
                pull,
                enabled = coachOn && coachHere && !coachOpen,
                // A light tick when the pull reaches the line, a firm click when the bubble pops.
                onArm = { haptics.tick() },
                onPop = {
                    haptics.click()
                    // The chat opens out of the bubble: remember where it is.
                    bloomCenterY = bubbleCenterY(statusTop, pull.pull, density)
                    // A beat for the pop to show before the chat slides down over it.
                    scope.launch {
                        delay(40)
                        coachOpen = true
                    }
                    if (!coachTutorialDone) scope.launch { container.settings.setCoachTutorialDone() }
                },
            ),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                AnimatedVisibility(
                    visible = onToday || onScan,
                    enter = slideInVertically(tween(MOTION_MS, easing = MotionEasing)) { it } + fadeIn(tween(MOTION_MS)),
                    exit = slideOutVertically(tween(MOTION_MS, easing = MotionEasing)) { it } + fadeOut(tween(MOTION_MS)),
                ) {
                    BottomBar(
                        scanSelected = onScan,
                        // Already on the diary looking at a past day? The tab brings you back to today.
                        onToday = {
                            if (onToday) container.viewedDay.value = container.clock.today.value else nav.switchTab(TodayRoute)
                        },
                        onScan = { nav.switchTab(ScanRoute) },
                    )
                }
            },
        ) { _ ->
            // The bar floats over the tab screens, which reserve a fixed space for it; padding the
            // whole NavHost instead would make pushed pages jump while the bar animates away.
            val barSpace = BAR_HEIGHT + 0.5.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            NavHost(
                navController = nav,
                startDestination = start,
                // iOS-style push/pop: the new page slides over, the old one drifts aside and dims.
                // Tabs slide side by side like pages. Everything moves horizontally, nothing scales,
                // and every transition shares one duration and curve so they all feel alike.
                enterTransition = { if (isTabSwitch()) tabEnter() else pushEnter() },
                exitTransition = { if (isTabSwitch()) tabExit() else pushExit() },
                popEnterTransition = { if (isTabSwitch()) tabEnter() else popEnter() },
                popExitTransition = { if (isTabSwitch()) tabExit() else popExit() },
                // The back swipe gesture uses these instead of the pop ones. It plays the exact same
                // animation as the matching button press, so Scan → Today swipes like a tab and
                // every other screen slides away left to right. Linear timing keeps the page locked
                // to the finger; letting go finishes the slide.
                predictivePopEnterTransition = { _ -> if (isTabSwitch()) tabEnter(GestureSpec) else popEnter(GestureSpec) },
                predictivePopExitTransition = { _ -> if (isTabSwitch()) tabExit(GestureSpec) else popExit(GestureSpec) },
            ) {
                composable<TodayRoute> {
                    Screen(bottom = barSpace) {
                        val vm = viewModel { DashboardViewModel(container) }
                        DashboardScreen(
                            viewModel = vm,
                            onScan = { nav.switchTab(ScanRoute) },
                            onOpenMeal = { nav.navigate(ResultRoute(it)) },
                            onSettings = { nav.navigate(SettingsRoute) },
                            onOpenBody = { nav.navigate(BodyRoute) },
                        )
                    }
                }
                composable<BodyRoute> {
                    Screen {
                        val vm = viewModel { BodyViewModel(container) }
                        BodyScreen(vm, onBack = { nav.popBackStack() })
                    }
                }
                composable<ScanRoute> {
                    Screen(bottom = barSpace) {
                        val vm = viewModel(appStore) { ScanViewModel(container) }
                        ScanScreen(
                            vm,
                            onResult = { nav.navigate(ResultRoute()) },
                            onToday = { nav.switchTab(TodayRoute) },
                            todayBehind = nav.previousBackStackEntry?.destination?.hasRoute<TodayRoute>() == true,
                            sliding = transition.isRunning || transition.targetState != EnterExitState.Visible,
                        )
                    }
                }
                composable<ResultRoute> { backStack ->
                    val route = backStack.toRoute<ResultRoute>()
                    Screen {
                        val vm = viewModel { ResultViewModel(container, route.mealId) }
                        ResultScreen(
                            viewModel = vm,
                            onClose = { nav.popBackStack() },
                            onSaved = {
                                // Pop back so the dashboard slides in from behind, like closing a sheet.
                                if (!nav.popBackStack<TodayRoute>(inclusive = false)) {
                                    nav.navigate(TodayRoute) { popUpTo(0) { inclusive = true } }
                                }
                            },
                        )
                    }
                }
                composable<SettingsRoute> {
                    Screen {
                        val vm = viewModel { SettingsViewModel(container) }
                        SettingsScreen(
                            viewModel = vm,
                            onBack = { nav.popBackStack() },
                            onEditProfile = { nav.navigate(OnboardingRoute(edit = true)) },
                            onAiEngine = { nav.navigate(AiSettingsRoute) },
                            onDevices = { nav.navigate(DevicesRoute) },
                            onCoach = { nav.navigate(CoachSettingsRoute) },
                            onReset = { nav.navigate(OnboardingRoute()) { popUpTo(0) { inclusive = true } } },
                        )
                    }
                }
                composable<CoachSettingsRoute> {
                    Screen {
                        val vm = viewModel { SettingsViewModel(container) }
                        CoachSettingsScreen(viewModel = vm, onBack = { nav.popBackStack() })
                    }
                }
                composable<DevicesRoute> {
                    Screen {
                        val vm = viewModel { SettingsViewModel(container) }
                        DevicesScreen(viewModel = vm, onBack = { nav.popBackStack() })
                    }
                }
                composable<AiSettingsRoute> {
                    Screen {
                        val vm = viewModel { SettingsViewModel(container) }
                        AiSettingsScreen(viewModel = vm, onBack = { nav.popBackStack() })
                    }
                }
                composable<OnboardingRoute> { backStack ->
                    val route = backStack.toRoute<OnboardingRoute>()
                    Screen {
                        val vm = viewModel { OnboardingViewModel(container, route.edit) }
                        val engines = viewModel { SettingsViewModel(container) }
                        OnboardingScreen(
                            viewModel = vm,
                            engines = engines,
                            onDone = {
                                if (route.edit) {
                                    nav.popBackStack()
                                } else {
                                    nav.navigate(TodayRoute) { popUpTo(0) { inclusive = true } }
                                }
                            },
                            onExit = { if (route.edit) nav.popBackStack() },
                        )
                    }
                }
            }
            // Drawn over every screen: the smart scale's live weight and saved weigh-ins.
            AutoWeighIn(container, onOpenBody = { nav.navigate(BodyRoute) { launchSingleTop = true } })
        }

        if (coachOn && !coachTutorialDone && coachHere && !coachOpen && !pull.popped) {
            CoachHint(onSkip = { scope.launch { container.settings.setCoachTutorialDone() } })
        }
        // The chat is a drop of liquid. It swells out of the pull bubble until it fills everything, and
        // on the way back shrinks into the bubble and is pulled up into the top edge again. The back gesture shrinks it with the finger (coachBack:
        // 0 in place, 1 back in the bubble); letting go finishes from where the finger stopped, and a
        // gesture given up on springs back. The chevron button plays the same thing by itself.
        var coachBack by remember { mutableFloatStateOf(0f) }
        var gestureLift by remember { mutableFloatStateOf(0f) }
        var closedByGesture by remember { mutableStateOf(false) }
        LaunchedEffect(coachOpen) {
            if (coachOpen) {
                closedByGesture = false
                coachBack = 0f
                gestureLift = 0f
            }
        }
        PredictiveBackHandler(enabled = coachOpen) { events ->
            try {
                events.collect { coachBack = it.progress }
                // Starts at the finger's speed and settles into the bubble; less to go, less time.
                val ms = (CLOSE_MS * (1f - coachBack)).toInt().coerceAtLeast(120)
                coroutineScope {
                    launch {
                        animate(coachBack, 1f, animationSpec = tween(ms, easing = LinearOutSlowInEasing)) { value, _ -> coachBack = value }
                    }
                    launch {
                        // The pull back up begins while the circle is still settling into the bubble.
                        delay((ms - LIFT_OVERLAP_MS).coerceAtLeast(0).toLong())
                        animate(0f, 1f, animationSpec = tween(LIFT_MS, easing = BloomEasing)) { value, _ -> gestureLift = value }
                    }
                }
                closedByGesture = true
                coachOpen = false
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    animate(coachBack, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> coachBack = value }
                    coachBack = 0f
                    gestureLift = 0f
                }
            }
        }
        AnimatedVisibility(
            visible = coachOpen,
            // Both directions are the drop below, so the transitions themselves do nothing.
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            // 1 once the chat is fully open, 0 while it is a drop the size of the bubble. It runs
            // forwards on opening and backwards on a button close; after the back gesture it has
            // already finished.
            val bloom by transition.animateFloat(
                transitionSpec = {
                    when {
                        targetState == EnterExitState.PostExit && closedByGesture -> snap()
                        targetState == EnterExitState.PostExit -> tween(CLOSE_MS, easing = BloomEasing)
                        else -> tween(BLOOM_MS, easing = BloomEasing)
                    }
                },
                label = "coach bloom",
            ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
            // Once it is back in the bubble, the bubble is pulled up into the top edge (0 to 1).
            val liftAnim by transition.animateFloat(
                transitionSpec = {
                    if (targetState == EnterExitState.PostExit && !closedByGesture) {
                        tween(LIFT_MS, delayMillis = CLOSE_MS - LIFT_OVERLAP_MS, easing = BloomEasing)
                    } else {
                        snap()
                    }
                },
                label = "coach lift",
            ) { state -> if (state == EnterExitState.PostExit) 1f else 0f }
            // How much of the chat shows: 1 fills the screen, 0 is back inside the bubble.
            val shown = { (bloom * (1f - coachBack)).coerceIn(0f, 1f) }
            val lifted = { max(liftAnim, gestureLift) }
            val bubbleRadius = with(density) { (BUBBLE_DP / 2).dp.toPx() }
            val bubbleColor = MaterialTheme.colorScheme.surfaceContainerHighest
            val pageColor = MaterialTheme.colorScheme.background
            val accent = MaterialTheme.colorScheme.primary
            Box(Modifier.fillMaxSize()) {
                // A soft glow around the drop, behind it: the only thing that marks its edge from outside.
                DropGlow(shown, lifted, bloomCenterY, bubbleRadius, accent)
                // A Surface, not a plain Box: the chat sits above the Scaffold, so it has to provide the
                // theme's text colour itself (without it, plain text falls back to black).
                Surface(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = shown()
                            val up = lifted()
                            // Grows from the bubble, so it scales about the bubble too.
                            val grow = 0.9f + 0.1f * max(s, up)
                            scaleX = grow
                            scaleY = grow
                            transformOrigin = TransformOrigin(0.5f, (bloomCenterY / size.height).coerceIn(0f, 1f))
                            alpha = 1f - ((up - 0.92f) / 0.08f).coerceIn(0f, 1f)
                            if (s < 1f) {
                                clip = true
                                shape = DropShape(bloomCenterY, bubbleRadius, s, up)
                            }
                        }
                        // The drop starts in the bubble's own colour and settles into the page's.
                        .drawBehind { drawRect(lerp(bubbleColor, pageColor, (shown() * 2.2f).coerceAtMost(1f))) },
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    // The chat itself rises a little and fades in a beat behind the drop.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = shown()
                                alpha = ((s - 0.12f) * 2.4f).coerceIn(0f, 1f)
                                translationY = (1f - s) * 40.dp.toPx()
                            },
                    ) {
                        val vm = viewModel { CoachViewModel(container) }
                        CoachScreen(vm, onBack = { coachOpen = false })
                    }
                }
                DropSheen(shown, lifted, bloomCenterY, bubbleRadius, accent)
            }
        }
        CoachPullBubble(pull, Modifier.align(Alignment.TopCenter))
    }
}

/** How far the drop has grown: from the bubble's radius at 0 to the screen's far corner at 1. */
private fun bloomRadius(size: Size, centerY: Float, startRadius: Float, progress: Float): Float {
    val cx = size.width / 2f
    val reach = hypot(max(cx, size.width - cx), max(centerY, size.height - centerY))
    return startRadius + (reach - startRadius) * progress
}

/** The drop's outline, and where its middle is. */
private class Drop(val center: Offset, val radius: Float, val path: Path)

/**
 * The drop: a circle that swells out of the bubble ([shown] 0 to 1). On the way back it shrinks into
 * the bubble and then [lifted] 0 to 1 pulls it up into the top edge until it is gone.
 */
private fun drop(size: Size, bubbleY: Float, startRadius: Float, shown: Float, lifted: Float): Drop {
    val cx = size.width / 2f
    val rise = FastOutSlowInEasing.transform(lifted.coerceIn(0f, 1f))
    val radius = bloomRadius(size, bubbleY, startRadius, shown) * (1f - 0.7f * rise)
    val cy = bubbleY + (-radius * 0.4f - bubbleY) * rise
    val center = Offset(cx, cy)
    return Drop(center, radius, Path().apply { addOval(Rect(center, radius)) })
}

private class DropShape(
    private val bubbleY: Float,
    private val startRadius: Float,
    private val shown: Float,
    private val lifted: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(drop(size, bubbleY, startRadius, shown, lifted).path)
}

/** A soft, blurred halo in the accent colour around the drop, drawn behind it so no line marks the edge. */
@Composable
private fun DropGlow(shown: () -> Float, lifted: () -> Float, bubbleY: Float, startRadius: Float, accent: Color) {
    val blur = with(LocalDensity.current) { 20.dp.toPx() }
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val s = shown()
                val up = lifted()
                // Blurring is the costly part: only while the drop is on its way.
                renderEffect = if (s in 0.001f..0.97f || up in 0.001f..0.999f) BlurEffect(blur, blur, TileMode.Decal) else null
            },
    ) {
        val s = shown()
        val up = lifted()
        if ((s <= 0.001f && up <= 0.001f) || (s >= 0.97f)) return@Canvas
        val d = drop(size, bubbleY, startRadius, s, up)
        drawPath(d.path, accent.copy(alpha = 0.55f * (1f - s) * (1f - up)))
    }
}

/**
 * What makes the drop look wet, drawn over it: light gathering just inside the edge (a gradient, never
 * a line), a small curved gleam on its upper left, and the bubble's sparkle swelling and fading as the
 * drop grows, and coming back as it closes.
 */
@Composable
private fun DropSheen(shown: () -> Float, lifted: () -> Float, bubbleY: Float, startRadius: Float, accent: Color) {
    val sparkle = rememberVectorPainter(Icons.Rounded.AutoAwesome)
    Canvas(Modifier.fillMaxSize()) {
        val s = shown()
        val up = lifted()
        if (s >= 0.999f) return@Canvas
        val d = drop(size, bubbleY, startRadius, s, up)
        val fade = (1f - s).coerceIn(0f, 1f) * (1f - up)
        val band = 18.dp.toPx()
        val inner = ((d.radius - band) / d.radius).coerceIn(0f, 0.97f)
        clipPath(d.path) {
            drawRect(
                Brush.radialGradient(
                    colorStops = arrayOf(0f to Color.Transparent, inner to Color.Transparent, 1f to accent.copy(alpha = 0.32f * fade)),
                    center = d.center,
                    radius = d.radius,
                ),
            )
        }
        val gleamRadius = d.radius - 8.dp.toPx()
        val gleam = (1f - s * 2.5f).coerceIn(0f, 1f) * (1f - up)
        if (gleamRadius > 6.dp.toPx() && gleam > 0f) {
            drawArc(
                color = Color.White.copy(alpha = 0.42f * gleam),
                startAngle = 198f,
                sweepAngle = 52f,
                useCenter = false,
                topLeft = Offset(d.center.x - gleamRadius, d.center.y - gleamRadius),
                size = Size(2f * gleamRadius, 2f * gleamRadius),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        val sparkleAlpha = (1f - s * 3.2f).coerceIn(0f, 1f) * (1f - up)
        if (sparkleAlpha > 0f) {
            val px = 24.dp.toPx() * (1f + 2.4f * s) * (1f - 0.5f * up)
            translate(d.center.x - px / 2f, d.center.y - px / 2f) {
                with(sparkle) { draw(Size(px, px), alpha = sparkleAlpha, colorFilter = ColorFilter.tint(accent)) }
            }
        }
    }
}

/** The drop takes a little longer to open than to close, which should feel snappy; then it is pulled up. */
private const val BLOOM_MS = 620
private const val CLOSE_MS = 420
private const val LIFT_MS = 300
private const val LIFT_OVERLAP_MS = 140

/** Slow start so the drop is seen leaving the bubble, then a soft landing at the corners. */
private val BloomEasing = FastOutSlowInEasing

/** One duration and curve for every screen change: quick, with a soft landing. */
private const val MOTION_MS = 280
private val MotionEasing = FastOutSlowInEasing
private val MotionSpec = tween<IntOffset>(MOTION_MS, easing = MotionEasing)
private val GestureSpec = tween<IntOffset>(MOTION_MS, easing = LinearEasing)

/** Opening a screen: it slides in from the right while the old one drifts left and dims. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter() = slideIntoContainer(SlideDirection.Start, MotionSpec)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit() =
    slideOutOfContainer(SlideDirection.Start, MotionSpec) { it / 4 } + fadeOut(tween(MOTION_MS), targetAlpha = 0.6f)

/** Going back, always left to right: the top screen slides off to the right and the one below returns. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(spec: FiniteAnimationSpec<IntOffset> = MotionSpec) =
    slideIntoContainer(SlideDirection.End, spec) { it / 4 } + fadeIn(tween(MOTION_MS, easing = LinearEasing), initialAlpha = 0.6f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(spec: FiniteAnimationSpec<IntOffset> = MotionSpec) =
    slideOutOfContainer(SlideDirection.End, spec)

/**
 * Today and Scan are tabs, so they slide side by side like pages: both move together with no
 * dimming or shadow. Scan sits to the right of Today.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.towardScan() = targetState.destination.hasRoute<ScanRoute>()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(spec: FiniteAnimationSpec<IntOffset> = MotionSpec) =
    slideIntoContainer(if (towardScan()) SlideDirection.Start else SlideDirection.End, spec)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(spec: FiniteAnimationSpec<IntOffset> = MotionSpec) =
    slideOutOfContainer(if (towardScan()) SlideDirection.Start else SlideDirection.End, spec)

private fun NavDestination?.isTab() = this?.hasRoute<TodayRoute>() == true || this?.hasRoute<ScanRoute>() == true

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.isTab() && targetState.destination.isTab()

/** Every page paints its own background so two pages never show through each other mid-slide. */
@Composable
private fun Screen(bottom: Dp = 0.dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = bottom),
    ) { content() }
}

private val BAR_HEIGHT = 68.dp

private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(TodayRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(scanSelected: Boolean, onToday: () -> Unit, onScan: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(BAR_HEIGHT)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // "Diary" rather than "Today": it's right on every day you swipe back to.
                BarItem(Icons.Rounded.SpaceDashboard, "Diary", !scanSelected, onToday)
                BarItem(Icons.Rounded.CenterFocusStrong, "Scan", scanSelected, onScan)
            }
        }
    }
}

@Composable
private fun BarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
