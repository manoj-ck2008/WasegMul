package com.agrelius.wasegmul.ui.yolo

import android.Manifest
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.WasteMapping
import com.agrelius.wasegmul.ml.Detection
import com.agrelius.wasegmul.ui.result.humanizeLabel
import com.agrelius.wasegmul.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoloScreen(
    onBack: () -> Unit,
    onCaptureAndClassify: (Bitmap) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as? com.agrelius.wasegmul.WasegMulApp
    val scope = rememberCoroutineScope()
    val yoloViewModel: YoloViewModel = viewModel(factory = YoloViewModel.Factory())
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val detections by yoloViewModel.detections.collectAsState()
    val fps by yoloViewModel.fps.collectAsState()
    val error by yoloViewModel.error.collectAsState()
    // Wire the user-tuned confidence threshold into the detector.
    val userThreshold by app?.settingsManager?.confidenceThreshold?.collectAsState(initial = 0.45f)
        ?: remember { mutableStateOf(0.45f) }
    LaunchedEffect(userThreshold) {
        yoloViewModel.applyConfidenceThreshold(userThreshold)
    }
    val captureRequested = remember { AtomicBoolean(false) }
    var cameraFrameWidth by remember { mutableIntStateOf(0) }
    var cameraFrameHeight by remember { mutableIntStateOf(0) }
    // Camera-frame-relative selection cannot meaningfully survive rotation
    // (frames reset); the stable key restores the same label when present.
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDetection = remember(detections, selectedKey) {
        selectedKey?.let { key -> detections.firstOrNull { detectionKey(it) == key } }
    }
    fun selectDetection(det: Detection?) {
        selectedKey = det?.let { detectionKey(it) }
    }

    // Permission check-first: never blind-fire the system dialog, and offer
    // the Settings deep-link on permanent denial.
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionRationale by rememberSaveable { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) showPermissionRationale = true
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            hasCameraPermission = true
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            yoloViewModel.initDetector(context)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.yolo_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (hasFlash) {
                        IconButton(onClick = { isTorchOn = !isTorchOn }) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = if (isTorchOn) stringResource(R.string.yolo_torch_off) else stringResource(R.string.yolo_torch_on),
                                tint = if (isTorchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.yolo_fps_format, fps),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (fps > 15f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!hasCameraPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.yolo_camera_permission),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (showPermissionRationale) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.yolo_camera_rationale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.common_grant_permission))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        } catch (_: Exception) { }
                    }) {
                        Text(stringResource(R.string.home_open_settings))
                    }
                }
            } else if (error != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        yoloViewModel.clearError()
                        scope.launch { yoloViewModel.initDetector(context) }
                    }) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            } else {
                CameraPreviewWithDetection(
                    isTorchOn = isTorchOn,
                    onFlashSupported = { hasFlash = it },
                    onFrameCaptured = { bitmap ->
                        // Snapshot writes MUST happen on Main (this callback
                        // runs on the analyzer executor).
                        val w = bitmap.width
                        val h = bitmap.height
                        if (cameraFrameWidth != w || cameraFrameHeight != h) {
                            mainHandler.post {
                                cameraFrameWidth = w
                                cameraFrameHeight = h
                            }
                        }
                        // Snapshot the current selection for the capture path
                        // (avoids racing the next detection update).
                        val targetAtCapture = selectedKey?.let { key ->
                            detections.firstOrNull { detectionKey(it) == key }
                        } ?: detections.maxByOrNull { it.confidence }
                        if (captureRequested.compareAndSet(true, false)) {
                            val raw = if (targetAtCapture != null) {
                                cropRoi(bitmap, targetAtCapture.boundingBox)
                            } else {
                                bitmap.copy(Bitmap.Config.ARGB_8888, true)
                            }
                            // Bound the handoff: a full 12 MP frame is ~48 MB and
                            // killed the process in setBitmap before the VM's own
                            // 1024 bound could engage. Downscale here, recycle the
                            // oversized intermediate (never the CameraX-owned frame).
                            val finalBitmap = downscaleForHandoff(raw)
                            if (finalBitmap !== raw) raw.recycle()
                            mainHandler.post { onCaptureAndClassify(finalBitmap) }
                        }
                        // Ownership stays with CameraX pipeline; YoloDetector copies
                        // internally via createScaledBitmap. NEVER recycle here:
                        // detect() reads pixels async and recycling causes native crash.
                        // Inference dispatched off-Main (Dispatchers.Default).
                        scope.launch(Dispatchers.Default) {
                            yoloViewModel.detectFrame(bitmap)
                        }
                    }
                )

                DetectionOverlay(
                    detections = detections,
                    frameWidth = cameraFrameWidth,
                    frameHeight = cameraFrameHeight,
                    selectedDetection = selectedDetection,
                    onDetectionTapped = { selectDetection(it) },
                    modifier = Modifier.fillMaxSize()
                )

                if (detections.isEmpty()) {
                    // No-objects hint (previously a blank viewfinder).
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .padding(bottom = 80.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.yolo_no_objects_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (detections.isNotEmpty()) {
                    // Per-detection guidance cards: Category chip (single
                    // CategoryColors map) + disposal action + one-line
                    // handling tip from WasteKnowledgeBase. Remembered per
                    // emission so composition does no per-frame lookups.
                    val guidanceCards = remember(detections) {
                        detections.take(3).map { det -> det to guidanceForDetection(det.label) }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .padding(bottom = 80.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                stringResource(
                                    R.string.yolo_detected_count,
                                    detections.size,
                                    if (detections.size > 1) "s" else ""
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            guidanceCards.forEach { (det, guidance) ->
                                val isSel = detectionKey(det) == selectedKey
                                val chipColor = categoryColor(guidance.category)
                                Text(
                                    "${if (isSel) "★ " else ""}${humanizeLabel(det.label)} ${(det.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = guidance.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = chipColor,
                                        modifier = Modifier
                                            .background(
                                                chipColor.copy(alpha = 0.18f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${guidance.disposalAction} • ${guidance.tip}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (detections.size > 3) {
                                Text(
                                    stringResource(R.string.yolo_more, detections.size - 3),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (selectedDetection != null) {
                    val selectedGuidance = remember(selectedDetection) {
                        selectedDetection?.let { guidanceForDetection(it.label) }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    stringResource(
                                        R.string.yolo_target_format,
                                        humanizeLabel(selectedDetection?.label.orEmpty()),
                                        ((selectedDetection?.confidence ?: 0f) * 100).toInt()
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selectedGuidance != null) {
                                    Text(
                                        text = "${selectedGuidance.category} • ${selectedGuidance.disposalAction} • ${selectedGuidance.tip}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // 48dp minimum dismiss target (was 16dp).
                            IconButton(
                                onClick = { selectDetection(null) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.yolo_deselect),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Button(
                        onClick = { captureRequested.set(true) },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.yolo_capture_classify),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedDetection != null) "Classify ${humanizeLabel(selectedDetection?.label.orEmpty())}" else stringResource(R.string.yolo_capture_classify),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithDetection(
    isTorchOn: Boolean,
    onFlashSupported: (Boolean) -> Unit,
    onFrameCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val isProcessing = remember { AtomicBoolean(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdownNow()
            cameraProvider?.unbindAll()
        }
    }

    LaunchedEffect(isTorchOn, camera) {
        val cam = camera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(isTorchOn)
        }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            // Matches the AF auto-cancel duration (3s) below.
            delay(3000)
            focusPoint = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(camera, previewViewRef) {
                detectTapGestures { offset ->
                    val pv = previewViewRef ?: return@detectTapGestures
                    val cam = camera ?: return@detectTapGestures
                    try {
                        val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        cam.cameraControl.startFocusAndMetering(action)
                        focusPoint = offset
                    } catch (e: Exception) {
                        Log.w("YoloScreen", "Focus metering failed: ${e.message}")
                    }
                }
            }
            .pointerInput(camera) {
                detectTransformGestures { _, _, zoom, _ ->
                    val cam = camera ?: return@detectTransformGestures
                    val zoomState = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                    val currentRatio = zoomState.zoomRatio
                    val newRatio = (currentRatio * zoom).coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                    cam.cameraControl.setZoomRatio(newRatio)
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewRef = this

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cp = cameraProviderFuture.get()
                        cameraProvider = cp

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()

                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            if (!isProcessing.compareAndSet(false, true)) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val bitmap = try {
                                imageProxy.toBitmap().copy(Bitmap.Config.ARGB_8888, false)
                            } catch (e: Exception) {
                                Log.e("YoloScreen", "Frame capture failed", e)
                                null
                            } finally {
                                // Guard released AFTER the proxy is closed:
                                // releasing first lets the next frame overlap
                                // and defeats KEEP_ONLY_LATEST backpressure.
                                imageProxy.close()
                                isProcessing.set(false)
                            }
                            if (bitmap != null) {
                                onFrameCaptured(bitmap)
                            }
                        }

                        try {
                            cp.unbindAll()
                            val boundCamera = cp.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            camera = boundCamera
                            onFlashSupported(boundCamera.cameraInfo.hasFlashUnit())
                        } catch (e: Exception) {
                            Log.e("YoloScreen", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        focusPoint?.let { pt ->
            FocusRing(center = pt)
        }
    }
}

@Composable
private fun FocusRing(center: Offset) {
    val infiniteTransition = rememberInfiniteTransition(label = "focus")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = Color(0xFF00FFB2).copy(alpha = alpha),
            radius = 36.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF00FFB2).copy(alpha = alpha),
            radius = 4.dp.toPx(),
            center = center
        )
    }
}

/** Stable selection key for a detection (label + quantized box). */
private fun detectionKey(det: Detection): String {
    val b = det.boundingBox
    return "${det.label}|${(b.left * 100).toInt()},${(b.top * 100).toInt()}," +
        "${(b.right * 100).toInt()},${(b.bottom * 100).toInt()}"
}

/**
 * Crops a region of interest for handoff to classification.
 *
 * Contract: [box] MUST be normalized [0,1] coordinates relative to [src]
 * (as emitted by [com.agrelius.wasegmul.ml.YoloDetector]). Pixel-space boxes
 * would be mis-scaled here — callers must normalize first.
 */
private fun cropRoi(src: Bitmap, box: RectF, padFrac: Float = 0.08f): Bitmap {
    val padX = (box.right - box.left) * padFrac
    val padY = (box.bottom - box.top) * padFrac
    val l = ((box.left - padX) * src.width).toInt().coerceIn(0, src.width - 1)
    val t = ((box.top - padY) * src.height).toInt().coerceIn(0, src.height - 1)
    val r = ((box.right + padX) * src.width).toInt().coerceIn(l + 1, src.width)
    val b = ((box.bottom + padY) * src.height).toInt().coerceIn(t + 1, src.height)
    return try {
        Bitmap.createBitmap(src, l, t, r - l, b - t)
    } catch (e: Exception) {
        src.copy(Bitmap.Config.ARGB_8888, true)
    }
}

/**
 * Bounds a capture-handoff bitmap to [maxLongEdge] (default 1024) preserving
 * aspect ratio. Returns [src] unchanged when already small enough; otherwise
 * returns a new scaled bitmap (caller recycles [src] if it owns it).
 *
 * Mirrors `ClassificationViewModel.downscaleTargetSize()` — the VM keeps its own
 * bound as defense-in-depth, but shrinking here avoids ever handing a ~48 MB
 * frame across the boundary in the first place.
 */
private fun downscaleForHandoff(src: Bitmap, maxLongEdge: Int = 1024): Bitmap {
    val longEdge = maxOf(src.width, src.height)
    if (longEdge <= maxLongEdge || src.isRecycled) return src
    val scale = maxLongEdge.toFloat() / longEdge.toFloat()
    return try {
        Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } catch (e: OutOfMemoryError) {
        src // better an oversized handoff (VM bounds it) than a kill here
    } catch (e: Exception) {
        src
    }
}

@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
    frameWidth: Int,
    frameHeight: Int,
    selectedDetection: Detection?,
    onDetectionTapped: (Detection?) -> Unit,
    modifier: Modifier = Modifier
) {
    // sp-based overlay text (was a fixed 28px paint, density-ignorant).
    val density = LocalContext.current.resources.displayMetrics.density
    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 0, 0, 0)
            textSize = 12 * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }
    val bgPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
        }
    }

    // Per-Category colors via the single category-color map (was a
    // classIndex rainbow with no waste semantics). Resolved with a plain
    // for-loop in composition (map/remember lambdas are not @Composable
    // contexts).
    val fallbackPalette = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary,
        MintAccent, MaterialTheme.colorScheme.tertiary
    )
    val categoryPalette = mutableListOf<androidx.compose.ui.graphics.Color?>()
    for (det in detections) {
        val cat = runCatching { WasteMapping.getCategory(det.label) }.getOrNull()
        categoryPalette.add(if (cat != null) categoryColor(cat) else null)
    }

    Canvas(
        modifier = modifier.pointerInput(detections, frameWidth, frameHeight) {
            detectTapGestures { tapOffset ->
                val (frameW, frameH) = if (frameWidth > 0 && frameHeight > 0) {
                    frameWidth.toFloat() to frameHeight.toFloat()
                } else {
                    size.width.toFloat() to size.height.toFloat()
                }
                val scale = maxOf(size.width / frameW, size.height / frameH)
                val scaledW = frameW * scale
                val scaledH = frameH * scale
                val offsetX = (size.width - scaledW) / 2f
                val offsetY = (size.height - scaledH) / 2f

                val tapped = detections.firstOrNull { det ->
                    val box = det.boundingBox
                    val left = offsetX + box.left * scaledW
                    val top = offsetY + box.top * scaledH
                    val right = offsetX + box.right * scaledW
                    val bottom = offsetY + box.bottom * scaledH
                    tapOffset.x in left..right && tapOffset.y in top..bottom
                }
                onDetectionTapped(if (selectedDetection == tapped) null else tapped)
            }
        }
    ) {
        val (frameW, frameH) = if (frameWidth > 0 && frameHeight > 0) {
            frameWidth.toFloat() to frameHeight.toFloat()
        } else {
            size.width to size.height
        }
        val scale = maxOf(size.width / frameW, size.height / frameH)
        val scaledW = frameW * scale
        val scaledH = frameH * scale
        val offsetX = (size.width - scaledW) / 2f
        val offsetY = (size.height - scaledH) / 2f

        detections.forEachIndexed { index, detection ->
            val isSelected = detection == selectedDetection
            val box = detection.boundingBox
            val left = offsetX + box.left * scaledW
            val top = offsetY + box.top * scaledH
            val w = (box.right - box.left) * scaledW
            val h = (box.bottom - box.top) * scaledH

            val color = if (isSelected) Color(0xFF00FFB2) else
                (categoryPalette.getOrNull(index)
                    ?: fallbackPalette[detection.classIndex % fallbackPalette.size])

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = if (isSelected) 6f else 3f)
            )

            val label = if (isSelected) {
                "★ ${humanizeLabel(detection.label)} ${(detection.confidence * 100).toInt()}%"
            } else {
                "${humanizeLabel(detection.label)} ${(detection.confidence * 100).toInt()}%"
            }
            val textWidth = labelPaint.measureText(label)

            bgPaint.color = if (isSelected) {
                android.graphics.Color.argb(220, 0, 200, 140)
            } else {
                android.graphics.Color.argb(180, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
            }

            drawContext.canvas.nativeCanvas.apply {
                drawRect(left, top - 36f, left + textWidth + 8f, top, bgPaint)
                drawText(label, left + 4f, top - 8f, labelPaint)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "DetectionOverlay Preview", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DetectionOverlayPreview() {
    com.agrelius.wasegmul.ui.theme.WasegMulTheme {
        Box(modifier = Modifier.size(300.dp)) {
            DetectionOverlay(
                detections = listOf(
                    com.agrelius.wasegmul.ml.Detection(
                        boundingBox = android.graphics.RectF(0.2f, 0.2f, 0.8f, 0.7f),
                        label = "plastic_bottle",
                        confidence = 0.91f,
                        classIndex = 0
                    )
                ),
                frameWidth = 1080,
                frameHeight = 1920,
                selectedDetection = null,
                onDetectionTapped = {}
            )
        }
    }
}


