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
import com.agrelius.wasegmul.ml.Detection
import com.agrelius.wasegmul.ml.YoloDetector
import com.agrelius.wasegmul.ml.ModelInitException
import com.agrelius.wasegmul.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class YoloViewModel : ViewModel() {
    @Volatile
    private var detector: YoloDetector? = null
    private val initGuard = kotlinx.coroutines.sync.Mutex()
    private val detectMutex = kotlinx.coroutines.sync.Mutex()
    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections
    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @Volatile
    private var frameCount = 0
    @Volatile
    private var lastFpsTime = System.currentTimeMillis()

    suspend fun initDetector(context: android.content.Context) {
        initGuard.withLock {
            if (detector != null) return
            try {
                val d = YoloDetector(context.applicationContext)
                d.ensureInitialized()
                detector = d
                _error.value = null
            } catch (e: Exception) {
                detector = null
                _error.value = "YOLO model not available. Check bundled models and storage."
                Log.w("YoloVM", "YOLO init failed: ${e.message}")
            }
        }
    }

    suspend fun detectFrame(bitmap: Bitmap) {
        // Drop frames when busy instead of queueing (prevents multi-second lag/OOM).
        if (!detectMutex.tryLock()) return
        try {
            val d = detector ?: return
            try {
                val results = d.detect(bitmap)
                _detections.value = results

                frameCount++
                val now = System.currentTimeMillis()
                val elapsed = now - lastFpsTime
                if (elapsed >= 1000) {
                    _fps.value = frameCount * 1000f / elapsed
                    frameCount = 0
                    lastFpsTime = now
                }
            } catch (e: Exception) {
                Log.e("YoloVM", "Detection failed", e)
            }
        } finally {
            detectMutex.unlock()
        }
    }

    fun clearError() {
        _error.value = null
        detector?.close()
        detector = null
    }

    override fun onCleared() {
        super.onCleared()
        detector?.close()
        detector = null
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return YoloViewModel() as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoloScreen(
    onBack: () -> Unit,
    onCaptureAndClassify: (Bitmap) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val yoloViewModel: YoloViewModel = viewModel(factory = YoloViewModel.Factory())

    val detections by yoloViewModel.detections.collectAsState()
    val fps by yoloViewModel.fps.collectAsState()
    val error by yoloViewModel.error.collectAsState()
    val captureRequested = remember { AtomicBoolean(false) }
    var cameraFrameWidth by remember { mutableIntStateOf(0) }
    var cameraFrameHeight by remember { mutableIntStateOf(0) }
    var selectedDetection by remember { mutableStateOf<Detection?>(null) }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
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
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.yolo_camera_permission), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.common_grant_permission))
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
                        if (cameraFrameWidth != bitmap.width || cameraFrameHeight != bitmap.height) {
                            cameraFrameWidth = bitmap.width
                            cameraFrameHeight = bitmap.height
                        }
                        if (captureRequested.compareAndSet(true, false)) {
                            val target = selectedDetection ?: detections.maxByOrNull { it.confidence }
                            val finalBitmap = if (target != null) {
                                cropRoi(bitmap, target.boundingBox)
                            } else {
                                bitmap.copy(Bitmap.Config.ARGB_8888, true)
                            }
                            scope.launch(Dispatchers.Main) {
                                onCaptureAndClassify(finalBitmap)
                            }
                        }
                        // Ownership stays with CameraX pipeline; YoloDetector copies
                        // internally via createScaledBitmap. NEVER recycle here:
                        // detect() reads pixels async and recycling causes native crash.
                        scope.launch {
                            yoloViewModel.detectFrame(bitmap)
                        }
                    }
                )

                DetectionOverlay(
                    detections = detections,
                    frameWidth = cameraFrameWidth,
                    frameHeight = cameraFrameHeight,
                    selectedDetection = selectedDetection,
                    onDetectionTapped = { selectedDetection = it },
                    modifier = Modifier.fillMaxSize()
                )

                if (detections.isNotEmpty()) {
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
                                "DETECTED: ${detections.size} object${if (detections.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            detections.take(3).forEach { det ->
                                val isSel = det == selectedDetection
                                Text(
                                    "${if (isSel) "★ " else ""}${det.label} ${(det.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            if (detections.size > 3) {
                                Text("+${detections.size - 3} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                        }
                    }
                }

                if (selectedDetection != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Target: ${selectedDetection?.label} (${((selectedDetection?.confidence ?: 0f) * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { selectedDetection = null },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Deselect", modifier = Modifier.size(12.dp))
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
                            contentColor = Color.Black
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
                            text = if (selectedDetection != null) "Classify ${selectedDetection?.label}" else stringResource(R.string.yolo_capture_classify),
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
            delay(1200)
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
                                isProcessing.set(false)
                                imageProxy.close()
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

@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
    frameWidth: Int,
    frameHeight: Int,
    selectedDetection: Detection?,
    onDetectionTapped: (Detection?) -> Unit,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 0, 0, 0)
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }
    val bgPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
        }
    }

    val overlayColors = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MintAccent, MaterialTheme.colorScheme.tertiary,
        Color(0xFFE74C3C), Color(0xFF3498DB), Color(0xFFF39C12), Color(0xFF9B59B6)
    )

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

        detections.forEach { detection ->
            val isSelected = detection == selectedDetection
            val box = detection.boundingBox
            val left = offsetX + box.left * scaledW
            val top = offsetY + box.top * scaledH
            val w = (box.right - box.left) * scaledW
            val h = (box.bottom - box.top) * scaledH

            val color = if (isSelected) Color(0xFF00FFB2) else overlayColors[detection.classIndex % overlayColors.size]

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = if (isSelected) 6f else 3f)
            )

            val label = if (isSelected) {
                "★ ${detection.label} ${(detection.confidence * 100).toInt()}%"
            } else {
                "${detection.label} ${(detection.confidence * 100).toInt()}%"
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


