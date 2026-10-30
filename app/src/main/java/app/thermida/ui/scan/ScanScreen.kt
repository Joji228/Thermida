package app.thermida.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.util.Size
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thermida.R
import app.thermida.ui.components.rememberHaptics
import coil3.compose.AsyncImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything drawn over the camera is light on dark, whatever the app theme. */
private val OnCamera = Color(0xFFF5F5F7)
private val OnCameraSoft = Color(0xFFF5F5F7).copy(alpha = 0.72f)
private val Glass = Color.White.copy(alpha = 0.14f)
private val GlassEdge = Color.White.copy(alpha = 0.20f)

/**
 * The camera, edge to edge, with its controls floating over it: a title and the flash at the top,
 * a framing guide in the middle (with a chip while barcodes are being read), and at the
 * bottom the photos taken, the gallery, the shutter, the meal note and the Analyze button.
 */
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onResult: () -> Unit,
    onToday: () -> Unit,
    /** Today is the page under Scan, so a swipe can pull Scan off it like the system back swipe. */
    todayBehind: Boolean = false,
    /** The page is sliding in or out with the navigation (a tab tap, a back swipe). */
    sliding: Boolean = false,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ScanViewModel.MAX_PHOTOS),
    ) { uris -> viewModel.importUris(uris) }

    LightStatusIcons()

    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    var showNote by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Photos taken but still being written to storage. The Analyze button shows up the moment the
    // shutter is pressed; a tap before the photo is saved starts the analysis as soon as it is.
    var saving by remember { mutableIntStateOf(0) }

    /** Takes a photo: shutter flash and haptic right away, added to the photos once it's saved. */
    fun capture() {
        haptics.click()
        scope.launch {
            flash.snapTo(0.8f)
            flash.animateTo(0f, tween(150, easing = FastOutLinearInEasing))
        }
        val file = viewModel.newCaptureFile()
        saving++
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewModel.addPhoto(file)
                    saving--
                }

                override fun onError(exception: ImageCaptureException) {
                    saving--
                    viewModel.error = "Couldn't take the photo. ${exception.message.orEmpty()}"
                }
            },
        )
    }
    var analyzeWhenSaved by remember { mutableStateOf(false) }

    // Either volume key: take a photo and analyze it (with any taken before) as soon as it's saved.
    val volumeShutter by rememberUpdatedState {
        when {
            viewModel.analyzing || showNote -> Unit
            hasPermission && viewModel.photos.size + saving < ScanViewModel.MAX_PHOTOS -> {
                capture()
                analyzeWhenSaved = true
            }
            saving > 0 -> analyzeWhenSaved = true
            viewModel.canAnalyze -> viewModel.analyze(onResult)
        }
    }
    DisposableEffect(Unit) {
        val press = { volumeShutter() }
        HardwareShutter.onPress = press
        onDispose { if (HardwareShutter.onPress === press) HardwareShutter.onPress = null }
    }
    LaunchedEffect(saving, analyzeWhenSaved) {
        if (analyzeWhenSaved && saving == 0) {
            analyzeWhenSaved = false
            viewModel.analyze(onResult)
        }
    }
    // Swiping right goes back to Today. The swipe is handed to the navigation as a back gesture, so
    // it plays the very animation the system back swipe does: Today slides in beside the page under
    // the finger, and letting go finishes or cancels that one animation, with nothing to hand over.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val swipeBack = todayBehind && backDispatcher != null
    var swipeWidth by remember { mutableFloatStateOf(1f) }
    var swipe by remember { mutableFloatStateOf(0f) }
    fun backEvent() = BackEventCompat(swipe, 0f, swipe / swipeWidth, BackEventCompat.EDGE_LEFT)
    val swipeState = rememberDraggableState { delta ->
        swipe = (swipe + delta).coerceIn(0f, swipeWidth)
        if (swipeBack) backDispatcher?.dispatchOnBackProgressed(backEvent())
    }
    // A live camera view trails behind a moving page and shows a strip of picture beside it, so
    // whenever the page moves (dragged, or sliding with a tab tap or back swipe) it carries a still
    // of the viewfinder instead, and the live picture comes back once the page has settled.
    val still = remember { CameraStill() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(dragging || sliding) { if (dragging || sliding) still.freeze() else still.thaw() }

    val foodFactsOn by viewModel.foodFactsEnabled.collectAsStateWithLifecycle(initialValue = false)
    // Battery guard: barcodes are watched for the first minute on this screen; coming back restarts it.
    var watchWindowOpen by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(60_000)
        watchWindowOpen = false
    }
    val scanLabel = when (viewModel.photos.size + saving) {
        0 -> if (viewModel.note.isBlank()) "Tap to scan" else "Analyze description"
        1 -> "Analyze photo"
        else -> "Analyze ${viewModel.photos.size + saving} photos"
    }
    val watchBarcodes = hasPermission && foodFactsOn && watchWindowOpen && viewModel.photos.isEmpty() && saving == 0 && !viewModel.analyzing && !showNote

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { swipeWidth = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = swipeState,
                enabled = !viewModel.analyzing,
                onDragStarted = {
                    swipe = 0f
                    if (swipeBack) {
                        dragging = true
                        // Right away, before the first frame of the slide.
                        still.freeze()
                        backDispatcher?.dispatchOnBackStarted(backEvent())
                    }
                },
                onDragStopped = { velocity ->
                    val go = swipe > swipeWidth * 0.25f || velocity > 1500f
                    when {
                        !swipeBack -> if (go) onToday()
                        go -> backDispatcher?.onBackPressed()
                        else -> backDispatcher?.dispatchOnBackCancelled()
                    }
                    dragging = false
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // The camera runs under the status bar down to the tab bar.
                .background(Color(0xFF0A0A0C)),
        ) {
            if (hasPermission) {
                CameraPreview(
                    imageCapture,
                    viewModel.torch,
                    Modifier.fillMaxSize(),
                    still = still,
                    barcodes = foodFactsOn,
                    onBarcode = if (watchBarcodes) {
                        { code -> if (viewModel.barcodeSeen(code, onResult)) haptics.click() }
                    } else {
                        null
                    },
                )
            } else {
                NoPermission(onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) })
            }

            // Soft shade at the top and bottom so the controls read over any picture.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)))),
            )

            if (hasPermission) {
                FramingGuide(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-48).dp),
                )
            }

            // Shutter flash: appears the instant the button is pressed and fades out fast.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = flash.value }
                    .background(Color.White),
            )

            TopBar(
                torch = viewModel.torch,
                onTorch = { viewModel.torch = !viewModel.torch },
                barcodes = foodFactsOn,
                noted = viewModel.note.isNotBlank(),
                onDescribe = { showNote = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars),
            )

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    viewModel.photos.isNotEmpty(),
                    enter = fadeIn(tween(160)) + expandVertically(tween(180, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160, easing = FastOutSlowInEasing)),
                ) {
                    PhotoTray(viewModel, Modifier.padding(bottom = 14.dp))
                }

                // Gallery, scan, another photo. The big button in the middle scans in one tap: it takes
                // the photo and analyzes it, or analyzes the photos (or description) already there.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassButton(
                        icon = Icons.Rounded.PhotoLibrary,
                        description = "Choose from gallery",
                        onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        size = 52.dp,
                    )
                    ScanButton(
                        enabled = !analyzeWhenSaved && (hasPermission || viewModel.canAnalyze || saving > 0),
                        description = scanLabel,
                        onClick = {
                            when {
                                // The usual single plate photo: take it, then analyze as soon as it's saved.
                                viewModel.photos.isEmpty() && saving == 0 && viewModel.note.isBlank() -> if (hasPermission) {
                                    capture()
                                    analyzeWhenSaved = true
                                }
                                saving > 0 -> analyzeWhenSaved = true
                                else -> viewModel.analyze(onResult)
                            }
                        },
                    )
                    AddPhotoButton(
                        enabled = hasPermission && viewModel.photos.size + saving < ScanViewModel.MAX_PHOTOS,
                        photos = viewModel.photos.size,
                        limit = ScanViewModel.MAX_PHOTOS,
                        onClick = ::capture,
                    )
                }
                // What the big button will do, said quietly under it.
                Text(
                    scanLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = OnCamera,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        AnimatedVisibility(viewModel.analyzing, enter = fadeIn(), exit = fadeOut()) {
            AnalyzingOverlay(photo = viewModel.photos.lastOrNull(), onCancel = viewModel::cancelAnalysis)
        }
    }

    if (showNote) {
        NoteDialog(
            initial = viewModel.note,
            onDismiss = { showNote = false },
            onSave = {
                viewModel.note = it
                showNote = false
            },
            onManual = {
                showNote = false
                viewModel.manual(onResult)
            },
        )
    }

    viewModel.error?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.error = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.couldn_t_analyze)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.error = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.error = null
                    viewModel.manual(onResult)
                }) { Text(stringResource(R.string.enter_manually)) }
            },
        )
    }
}

/** Light status-bar icons while the camera is on screen (it is dark in either theme); put back after. */
@Composable
private fun LightStatusIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val before = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = before }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** What the camera reads, in a small glass chip on the left; describe and the flash on the right. */
@Composable
private fun TopBar(torch: Boolean, onTorch: () -> Unit, barcodes: Boolean, noted: Boolean, onDescribe: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Glass)
                .border(1.dp, GlassEdge, RoundedCornerShape(50))
                .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), tint = OnCamera)
            Spacer(Modifier.width(7.dp))
            // With Open Food Facts on, the viewfinder also reads barcodes; said here, quietly.
            Text(
                if (barcodes) "Plate, label or barcode" else "Plate or nutrition label",
                style = MaterialTheme.typography.labelMedium,
                color = OnCamera,
            )
        }
        Spacer(Modifier.weight(1f))
        GlassButton(
            icon = Icons.Rounded.EditNote,
            description = if (noted) "Edit the meal description" else "Describe the meal",
            onClick = onDescribe,
            size = 40.dp,
            active = noted,
        )
        Spacer(Modifier.width(10.dp))
        GlassButton(
            icon = if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
            description = if (torch) "Turn flash off" else "Turn flash on",
            onClick = onTorch,
            size = 40.dp,
            active = torch,
        )
    }
}

/** A round, see-through button for use over the camera; [active] fills it with the accent. */
@Composable
private fun GlassButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, size: Dp, active: Boolean = false) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else Glass,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else OnCamera,
        border = if (active) null else BorderStroke(1.dp, GlassEdge),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(size * 0.46f))
        }
    }
}

/**
 * Where to put the food: four soft corners that breathe a little.
 */
@Composable
private fun FramingGuide(modifier: Modifier) {
    val motion = rememberInfiniteTransition(label = "guide")
    val breathe by motion.animateFloat(1f, 1.025f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(width = 252.dp, height = 232.dp)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                },
        ) {
            val w = size.width
            val h = size.height
            val len = 30.dp.toPx()
            val r = 18.dp.toPx()
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                val path = Path().apply {
                    moveTo(x, y + dy * len)
                    lineTo(x, y + dy * r)
                    quadraticTo(x, y, x + dx * r, y)
                    lineTo(x + dx * len, y)
                }
                drawPath(path, Color.White.copy(alpha = 0.8f), style = stroke)
            }
            corner(0f, 0f, 1f, 1f)
            corner(w, 0f, -1f, 1f)
            corner(0f, h, 1f, -1f)
            corner(w, h, -1f, -1f)
        }
    }
}

/** The photos taken so far as small thumbnails, each with a remove button (the shutter counts them). */
@Composable
private fun PhotoTray(viewModel: ScanViewModel, modifier: Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        viewModel.photos.forEach { file ->
            Box {
                AsyncImage(
                    model = file,
                    contentDescription = "Captured photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(14.dp)),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp, GlassEdge, CircleShape)
                        .clickable { viewModel.removePhoto(file) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "Remove photo", Modifier.size(12.dp), tint = OnCamera)
                }
            }
        }
    }
}

/**
 * The main button: a large accent disc in a white ring with a sparkle. One tap scans: it takes the
 * photo and analyzes it, or analyzes what's already there. It sinks a little while pressed.
 */
@Composable
private fun ScanButton(enabled: Boolean, description: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, tween(110), label = "scan")
    Box(
        Modifier
            .size(84.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .border(3.dp, Color.White, CircleShape)
            .padding(6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                haptics.click()
                onClick()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/**
 * Takes another photo without analyzing yet, for a meal that needs more than one. A small badge
 * counts the photos against the limit.
 */
@Composable
private fun AddPhotoButton(enabled: Boolean, photos: Int, limit: Int, onClick: () -> Unit) {
    val description = when {
        photos >= limit -> "Photo limit reached"
        photos == 0 -> "Take a photo without scanning yet"
        else -> "Add another photo, $photos of $limit taken"
    }
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.alpha(if (enabled) 1f else 0.4f)) {
            GlassButton(
                icon = if (photos == 0) Icons.Rounded.PhotoCamera else Icons.Rounded.Add,
                description = description,
                onClick = { if (enabled) onClick() },
                size = 52.dp,
            )
        }
        if (photos > 0) {
            Text(
                "$photos/$limit",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    torch: Boolean,
    modifier: Modifier,
    still: CameraStill,
    /** Set up barcode watching at all (Open Food Facts on). Changing it rebuilds the camera, so it's kept steady. */
    barcodes: Boolean = false,
    /** Whether to look at frames right now; null pauses watching without touching the camera. */
    onBarcode: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    // Live barcode watching runs on its own thread, only while asked for; it stops with the screen.
    val latestOnBarcode by rememberUpdatedState(onBarcode)
    val scanning = barcodes
    // Read from the camera thread: paused frames are dropped at once, at almost no cost.
    val watching = remember { AtomicBoolean(false) }
    watching.set(onBarcode != null)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    LaunchedEffect(lifecycleOwner, scanning) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = if (scanning) {
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .build()
                .also { use ->
                    val main = ContextCompat.getMainExecutor(context)
                    use.setAnalyzer(analysisExecutor, LiveBarcodeAnalyzer(isActive = { watching.get() }) { code -> main.execute { latestOnBarcode?.invoke(code) } })
                }
        } else {
            null
        }
        provider.unbindAll()
        camera = runCatching<Camera> {
            if (analysis != null) {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
            } else {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }
        }.getOrElse {
            // A camera that can't run all three at once still gets the normal viewfinder.
            runCatching<Camera> { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture) }.getOrNull()
        }
    }
    LaunchedEffect(camera, torch) {
        camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch) }
    }

    DisposableEffect(previewView) {
        still.view = previewView
        onDispose { still.view = null }
    }

    Box(modifier) {
        val image = still.image
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = { it.visibility = if (image != null) View.INVISIBLE else View.VISIBLE },
        )
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    }
}

/**
 * A still of the viewfinder, shown in its place while the page is swiped: the live view is drawn
 * outside Compose and trails the page as it moves, showing a strip of picture beside it.
 */
@Stable
private class CameraStill {
    var view: PreviewView? = null
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    fun freeze() {
        if (image == null) image = view?.bitmap?.asImageBitmap()
    }

    fun thaw() {
        image = null
    }
}

@Composable
private fun NoPermission(onAllow: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Glass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.NoPhotography, null, Modifier.size(34.dp), tint = OnCamera)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.thermida_needs_the_camera_to_scan),
            style = MaterialTheme.typography.bodyMedium,
            color = OnCamera,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Surface(onClick = onAllow, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
            Text(
                stringResource(R.string.allow_camera),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * While the meal is estimated: the last photo stays on screen, dimmed, small sparkles drift down
 * through the middle of it, and a card at the bottom says what's happening, with Cancel.
 */
@Composable
private fun AnalyzingOverlay(photo: java.io.File?, onCancel: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val motion = rememberInfiniteTransition(label = "analyzing")
    val fall by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(SPARKLE_FALL_MS, easing = LinearEasing)), label = "fall")
    val pulse by motion.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
            .clickable(enabled = true, onClick = {}),
    ) {
        if (photo != null) {
            AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.5f))
        }
        Canvas(Modifier.fillMaxSize()) {
            val top = size.height * 0.16f
            val travel = size.height * 0.42f
            val spread = 130.dp.toPx()
            Sparkles.forEach { s ->
                // Each sparkle has its own place in the loop, so they never fall in step.
                val t = (fall * s.speed + s.phase) % 1f
                val fade = kotlin.math.sin(Math.PI * t).toFloat()
                val x = size.width / 2f + s.x * spread + kotlin.math.sin((t * 2f + s.phase) * Math.PI).toFloat() * 10.dp.toPx() * s.sway
                val y = top + t * travel
                val r = s.size.dp.toPx() * (0.7f + 0.3f * fade)
                rotate(s.spin * 180f * t, pivot = Offset(x, y)) {
                    drawPath(sparkle(x, y, r), (if (s.light) Color.White else accent).copy(alpha = 0.9f * fade))
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF151518).copy(alpha = 0.92f))
                .border(1.dp, GlassEdge, RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(26.dp), tint = accent)
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.estimating_your_meal), style = MaterialTheme.typography.titleMedium, color = OnCamera)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.this_usually_takes_a_few_seconds), style = MaterialTheme.typography.bodySmall, color = OnCameraSoft)
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = onCancel,
                shape = RoundedCornerShape(50),
                color = Glass,
                contentColor = OnCamera,
                border = BorderStroke(1.dp, GlassEdge),
            ) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 26.dp, vertical = 10.dp))
            }
        }
    }
}

private const val SPARKLE_FALL_MS = 3200

/** One falling sparkle: where across (-1 to 1), when in the loop, how big, fast, swaying and turning. */
private class Sparkle(val x: Float, val phase: Float, val size: Float, val speed: Float, val sway: Float, val spin: Float, val light: Boolean)

/** A fixed set, so the shower looks the same every time and nothing is random per frame. */
private val Sparkles = listOf(
    Sparkle(-0.85f, 0.00f, 7f, 1f, 1.0f, 1f, false),
    Sparkle(-0.55f, 0.37f, 11f, 1f, -0.8f, -1f, true),
    Sparkle(-0.30f, 0.71f, 6f, 2f, 0.6f, 1f, false),
    Sparkle(-0.10f, 0.15f, 14f, 1f, -1.0f, 1f, false),
    Sparkle(0.12f, 0.52f, 8f, 1f, 0.9f, -1f, true),
    Sparkle(0.33f, 0.88f, 12f, 1f, -0.6f, 1f, false),
    Sparkle(0.58f, 0.26f, 6f, 2f, 1.0f, -1f, true),
    Sparkle(0.80f, 0.63f, 10f, 1f, -0.9f, 1f, false),
    Sparkle(-0.70f, 0.82f, 9f, 2f, 0.7f, -1f, true),
    Sparkle(0.00f, 0.44f, 9f, 2f, -0.7f, 1f, true),
    Sparkle(0.45f, 0.05f, 7f, 2f, 0.8f, -1f, false),
    Sparkle(-0.42f, 0.58f, 13f, 1f, 0.5f, 1f, false),
)

/** A four-pointed star with curved sides, like the app's sparkle icon. */
private fun sparkle(cx: Float, cy: Float, r: Float) = Path().apply {
    moveTo(cx, cy - r)
    quadraticTo(cx, cy, cx + r, cy)
    quadraticTo(cx, cy, cx, cy + r)
    quadraticTo(cx, cy, cx - r, cy)
    quadraticTo(cx, cy, cx, cy - r)
    close()
}

@Composable
private fun NoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onManual: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.describe_the_meal)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.add_details_the_photo_can_t),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.s_200_g_chicken_rice_cooked_in)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onManual) { Text(stringResource(R.string.skip_ai_and_log_manually)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
