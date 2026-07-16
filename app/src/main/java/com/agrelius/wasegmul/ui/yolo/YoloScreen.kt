package com.agrelius.wasegmul.ui.yolo

import android.Manifest
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
                detector = YoloDetector(context.applicationContext)
                detector?.ensureInitialized()
            } catch (e: ModelInitException) {
                _error.value = "YOLO model not available. Place yolov8n.tflite in assets/."
                Log.w("YoloVM", "YOLO init failed: ${e.message}")
            }
        }
    }

    suspend fun detectFrame(bitmap: Bitmap) {
        detectMutex.withLock {
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
        }
    }

    fun clearError() { _error.value = null }

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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val yoloViewModel: YoloViewModel = viewModel(factory = YoloViewModel.Factory())

    val detections by yoloViewModel.detections.collectAsState()
    val fps by yoloViewModel.fps.collectAsState()
    val error by yoloViewModel.error.collectAsState()

    var hasCameraPermission by remember { mutableStateOf(false) }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,                         contentDescription = stringResource(R.string.common_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
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
                    onFrameCaptured = { bitmap ->
                        scope.launch {
                            yoloViewModel.detectFrame(bitmap)
                            bitmap.recycle()
                        }
                    }
                )

                DetectionOverlay(
                    detections = detections,
                    modifier = Modifier.fillMaxSize()
                )

                if (detections.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
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
                            detections.take(5).forEach { det ->
                                Text(
                                    "${det.label} ${(det.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 10.sp
                                )
                            }
                            if (detections.size > 5) {
                                Text("+${detections.size - 5} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithDetection(
    onFrameCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val isProcessing = remember { AtomicBoolean(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdownNow()
            cameraProvider?.unbindAll()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER

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
                        try {
                            val bitmap = imageProxy.toBitmap()
                            onFrameCaptured(bitmap)
                        } catch (e: Exception) {
                            Log.e("YoloScreen", "Frame analysis failed", e)
                        } finally {
                            isProcessing.set(false)
                            imageProxy.close()
                        }
                    }

                    try {
                        cp.unbindAll()
                        cp.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("YoloScreen", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
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

    Canvas(modifier = modifier) {
        detections.forEach { detection ->
            val box = detection.boundingBox
            val left = box.left * size.width
            val top = box.top * size.height
            val w = (box.right - box.left) * size.width
            val h = (box.bottom - box.top) * size.height

            val color = overlayColors[detection.classIndex % overlayColors.size]

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = 3f)
            )

            val label = "${detection.label} ${(detection.confidence * 100).toInt()}%"
            val textWidth = labelPaint.measureText(label)

            bgPaint.color = android.graphics.Color.argb(180, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())

            drawContext.canvas.nativeCanvas.apply {
                drawRect(left, top - 36f, left + textWidth + 8f, top, bgPaint)
                drawText(label, left + 4f, top - 8f, labelPaint)
            }
        }
    }
}
