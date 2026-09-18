package com.agrelius.wasegmul.ui.barcode

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.ResolvedPackagingComponent
import com.agrelius.wasegmul.WasegMulApp
import com.agrelius.wasegmul.data.BarcodeProduct
import com.agrelius.wasegmul.ui.theme.GrassGreenLustrous
import com.agrelius.wasegmul.ui.theme.HighConfidenceGreen
import com.agrelius.wasegmul.ui.theme.LocalGlassColors
import com.agrelius.wasegmul.ui.theme.LowConfidence
import com.agrelius.wasegmul.ui.theme.MediumConfidenceYellow
import com.agrelius.wasegmul.ui.theme.SkyBlueDeep
import com.agrelius.wasegmul.utils.ConnectivityChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "BarcodeScanScreen"

/**
 * Barcode scanning screen integrating CameraX preview, ML Kit barcode detection,
 * targeting reticle with animations, online/offline status, and resolved packaging sheet.
 *
 * @param onBack Callback invoked when navigating back from the scanner.
 * @param onNavigateToResult Callback invoked when a waste record is saved, receiving record ID.
 * @param onFallbackToCamera Callback invoked when an unknown barcode falls back to visual classification.
 * @param viewModel State manager for barcode detection and resolution.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanScreen(
    onBack: () -> Unit,
    onNavigateToResult: (Long) -> Unit,
    onFallbackToCamera: () -> Unit = {},
    viewModel: BarcodeScanViewModel = viewModel(
        factory = BarcodeScanViewModel.Factory(
            barcodeRepository = (LocalContext.current.applicationContext as WasegMulApp).barcodeRepository,
            wasteRepository = (LocalContext.current.applicationContext as WasegMulApp).repository,
            modelManager = (LocalContext.current.applicationContext as WasegMulApp).modelManager
        )
    )
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateToResult.collect { recordId ->
            onNavigateToResult(recordId)
        }
    }

    var isOnline by remember { mutableStateOf(ConnectivityChecker.isOnline(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }

            override fun onLost(network: Network) {
                isOnline = ConnectivityChecker.isOnline(context)
            }
        }
        try {
            cm?.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "NetworkCallback registration failed: ${e.message}")
        }
        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "NetworkCallback unregister failed: ${e.message}")
            }
        }
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf("") }

    if (showManualInputDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            title = { Text("Manual Barcode Entry") },
            text = {
                OutlinedTextField(
                    value = manualBarcode,
                    onValueChange = { manualBarcode = it },
                    label = { Text("Enter Barcode (e.g., 12 or 13 digits)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManualInputDialog = false
                        if (manualBarcode.isNotBlank()) {
                            viewModel.onBarcodeDetected(manualBarcode.trim())
                        }
                    }
                ) {
                    Text("Search")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Barcode Scanner",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    NetworkStatusBadge(
                        isOnline = isOnline,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    IconButton(onClick = { showManualInputDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Manual Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasFlash) {
                        IconButton(onClick = { isTorchOn = !isTorchOn }) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = if (isTorchOn) stringResource(R.string.yolo_torch_off) else stringResource(R.string.yolo_torch_on),
                                tint = if (isTorchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!hasCameraPermission) {
                CameraPermissionRequired(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            } else {
                CameraPreviewWithBarcodeScanner(
                    viewModel = viewModel,
                    isTorchOn = isTorchOn,
                    onFlashSupported = { hasFlash = it }
                )

                val isDetected = uiState is BarcodeScanState.Resolving || uiState is BarcodeScanState.Resolved
                ReticleOverlay(
                    isDetected = isDetected,
                    modifier = Modifier.fillMaxSize()
                )

                AnimatedVisibility(
                    visible = uiState !is BarcodeScanState.Scanning,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, LocalGlassColors.current.border),
                        shadowElevation = 16.dp
                    ) {
                        when (val state = uiState) {
                            is BarcodeScanState.Resolving -> {
                                ResolvingCardContent(barcode = state.barcode)
                            }
                            is BarcodeScanState.Resolved -> {
                                ResolvedCardContent(
                                    product = state.product,
                                    components = state.components,
                                    onConfirmAndSave = {
                                        scope.launch {
                                            viewModel.confirmAndSave(state.product)
                                        }
                                    },
                                    onResumeScanning = { viewModel.resumeScanning() }
                                )
                            }
                            is BarcodeScanState.NotFound -> {
                                QuickClassifierCardContent(
                                    barcode = state.barcode,
                                    onConfirm = { name, cat, sub ->
                                        viewModel.quickClassifyAndSave(state.barcode, name, cat, sub)
                                    },
                                    onFallbackToCamera = onFallbackToCamera,
                                    onResumeScanning = { viewModel.resumeScanning() }
                                )
                            }
                            is BarcodeScanState.Error -> {
                                QuickClassifierCardContent(
                                    barcode = "Unindexed Code",
                                    errorMessage = state.message,
                                    onConfirm = { name, cat, sub ->
                                        viewModel.quickClassifyAndSave("code_${System.currentTimeMillis()}", name, cat, sub)
                                    },
                                    onFallbackToCamera = onFallbackToCamera,
                                    onResumeScanning = { viewModel.resumeScanning() }
                                )
                            }
                            BarcodeScanState.Scanning -> Unit
                        }
                    }
                }
            }
        }
    }
}

/**
 * CameraX preview and ImageAnalysis analyzer pipeline for barcode detection.
 */
@Composable
private fun CameraPreviewWithBarcodeScanner(
    viewModel: BarcodeScanViewModel,
    isTorchOn: Boolean,
    onFlashSupported: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val isProcessing = remember { AtomicBoolean(false) }

    val uiState by viewModel.uiState.collectAsState()
    val currentUiStateRef = rememberUpdatedState(uiState)

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
                        val action = FocusMeteringAction.Builder(
                            point,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                        ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                        cam.cameraControl.startFocusAndMetering(action)
                        focusPoint = offset
                    } catch (e: Exception) {
                        Log.w(TAG, "Focus metering failed: ${e.message}")
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
                            .build()

                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            if (currentUiStateRef.value !is BarcodeScanState.Scanning ||
                                !isProcessing.compareAndSet(false, true)
                            ) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                try {
                                    val result = viewModel.barcodeScanner.scan(imageProxy)
                                    if (result != null) {
                                        viewModel.onBarcodeDetected(result.rawValue, result.frameBitmap)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Barcode scan failed: ${e.message}")
                                } finally {
                                    imageProxy.close()
                                    isProcessing.set(false)
                                }
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
                            Log.e(TAG, "Camera bind failed: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        focusPoint?.let { pt ->
            FocusIndicatorRing(center = pt)
        }
    }
}

/**
 * Animated focusing reticle drawn when the user taps on the camera viewfinder.
 */
@Composable
private fun FocusIndicatorRing(center: Offset) {
    val infiniteTransition = rememberInfiniteTransition(label = "focusRing")
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
            color = HighConfidenceGreen.copy(alpha = alpha),
            radius = 36.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = HighConfidenceGreen.copy(alpha = alpha),
            radius = 4.dp.toPx(),
            center = center
        )
    }
}

/**
 * Targeting reticle overlay with centered scanning box, animated laser sweep line,
 * corner brackets, and helper guidance text.
 */
@Composable
private fun ReticleOverlay(
    isDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "reticleAnimation")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserProgress"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val reticleColor = if (isDetected) HighConfidenceGreen else MaterialTheme.colorScheme.primary

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxWidth = 280.dp.toPx()
            val boxHeight = 180.dp.toPx()
            val cornerRadius = 18.dp.toPx()
            val left = (size.width - boxWidth) / 2f
            val top = (size.height - boxHeight) / 2f - 40.dp.toPx()
            val right = left + boxWidth
            val bottom = top + boxHeight

            // Dark translucent scrim outside the reticle box
            val scrimPath = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, right, bottom),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                )
                fillType = PathFillType.EvenOdd
            }
            drawPath(scrimPath, color = Color.Black.copy(alpha = 0.55f))

            // Reticle box border
            drawRoundRect(
                color = if (isDetected) reticleColor else reticleColor.copy(alpha = pulseAlpha),
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = if (isDetected) 3.5.dp.toPx() else 2.dp.toPx())
            )

            // High-tech corner bracket accents
            val bracketLength = 24.dp.toPx()
            val bracketStroke = 4.dp.toPx()

            // Top-Left bracket
            drawLine(
                color = reticleColor,
                start = Offset(left, top + bracketLength),
                end = Offset(left, top + cornerRadius),
                strokeWidth = bracketStroke
            )
            drawArc(
                color = reticleColor,
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(cornerRadius * 2, cornerRadius * 2),
                style = Stroke(width = bracketStroke)
            )
            drawLine(
                color = reticleColor,
                start = Offset(left + cornerRadius, top),
                end = Offset(left + bracketLength, top),
                strokeWidth = bracketStroke
            )

            // Top-Right bracket
            drawLine(
                color = reticleColor,
                start = Offset(right - bracketLength, top),
                end = Offset(right - cornerRadius, top),
                strokeWidth = bracketStroke
            )
            drawArc(
                color = reticleColor,
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(right - cornerRadius * 2, top),
                size = Size(cornerRadius * 2, cornerRadius * 2),
                style = Stroke(width = bracketStroke)
            )
            drawLine(
                color = reticleColor,
                start = Offset(right, top + cornerRadius),
                end = Offset(right, top + bracketLength),
                strokeWidth = bracketStroke
            )

            // Bottom-Left bracket
            drawLine(
                color = reticleColor,
                start = Offset(left, bottom - bracketLength),
                end = Offset(left, bottom - cornerRadius),
                strokeWidth = bracketStroke
            )
            drawArc(
                color = reticleColor,
                startAngle = 90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(left, bottom - cornerRadius * 2),
                size = Size(cornerRadius * 2, cornerRadius * 2),
                style = Stroke(width = bracketStroke)
            )
            drawLine(
                color = reticleColor,
                start = Offset(left + cornerRadius, bottom),
                end = Offset(left + bracketLength, bottom),
                strokeWidth = bracketStroke
            )

            // Bottom-Right bracket
            drawLine(
                color = reticleColor,
                start = Offset(right - bracketLength, bottom),
                end = Offset(right - cornerRadius, bottom),
                strokeWidth = bracketStroke
            )
            drawArc(
                color = reticleColor,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(right - cornerRadius * 2, bottom - cornerRadius * 2),
                size = Size(cornerRadius * 2, cornerRadius * 2),
                style = Stroke(width = bracketStroke)
            )
            drawLine(
                color = reticleColor,
                start = Offset(right, bottom - cornerRadius),
                end = Offset(right, bottom - bracketLength),
                strokeWidth = bracketStroke
            )

            // Animated Laser Sweep Line (active while scanning)
            if (!isDetected) {
                val laserY = top + (boxHeight * laserProgress)
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            reticleColor.copy(alpha = 0.3f),
                            reticleColor,
                            reticleColor.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        startX = left,
                        endX = right
                    ),
                    start = Offset(left + 8.dp.toPx(), laserY),
                    end = Offset(right - 8.dp.toPx(), laserY),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }

        // Helper guidance pill
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Text(
                    text = "Point camera at product barcode",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Top bar badge indicating real-time online or offline network status.
 */
@Composable
private fun NetworkStatusBadge(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val indicatorColor = if (isOnline) HighConfidenceGreen else MediumConfidenceYellow
    val statusText = if (isOnline) "Online" else "Offline"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = indicatorColor.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, indicatorColor.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(indicatorColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = indicatorColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Content displayed in the bottom card while resolving a scanned barcode.
 */
@Composable
private fun ResolvingCardContent(barcode: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Resolving Product...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Looking up barcode: $barcode",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Content displayed in the bottom card when a barcode is resolved.
 */
@Composable
private fun ResolvedCardContent(
    product: BarcodeProduct,
    components: List<ResolvedPackagingComponent>,
    onConfirmAndSave: () -> Unit,
    onResumeScanning: () -> Unit
) {
    var isSaving by remember { mutableStateOf(false) }

    val categoryColor = when (product.category) {
        "Recyclable" -> HighConfidenceGreen
        "Organic" -> GrassGreenLustrous
        "E-Waste" -> SkyBlueDeep
        "Trash" -> LowConfidence
        else -> Color(0xFFFFA726)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.productName?.ifBlank { null } ?: "Unknown Product",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!product.brand.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = product.brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = categoryColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = product.category,
                    color = categoryColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = LocalGlassColors.current.border)
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "PACKAGING BREAKDOWN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp)
        ) {
            items(components) { comp ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${comp.shape}: ${comp.material}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val weight = comp.weightGrams
                        if (weight != null && weight > 0.0) {
                            Text(
                                text = "Weight: ${weight}g",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                    DisposalBadge(action = comp.disposalAction)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (!isSaving) {
                    isSaving = true
                    onConfirmAndSave()
                }
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            )
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saving...", fontWeight = FontWeight.Bold)
            } else {
                Text("View Disposal Guide", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onResumeScanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Scan Another",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Chip indicating the disposal action (e.g., "Recycle", "Discard", "Compost").
 */
@Composable
private fun DisposalBadge(
    action: String,
    modifier: Modifier = Modifier
) {
    val badgeColor = when (action.lowercase()) {
        "recycle" -> HighConfidenceGreen
        "compost" -> GrassGreenLustrous
        else -> LowConfidence
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = badgeColor.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Text(
            text = action,
            color = badgeColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private data class QuickCategoryOption(
    val label: String,
    val description: String,
    val category: String,
    val subclass: String,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Interactive card displayed when a scanned barcode or QR code is not in online databases.
 * Allows one-tap waste classification for general items (laptops, Uno cards, washing machines, etc.).
 */
@Composable
private fun QuickClassifierCardContent(
    barcode: String,
    errorMessage: String? = null,
    onConfirm: (productName: String, category: String, subclass: String) -> Unit,
    onFallbackToCamera: () -> Unit,
    onResumeScanning: () -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }

    val options = remember {
        listOf(
            QuickCategoryOption(
                label = "E-Waste / Electronics",
                description = "Laptops, Washers, Gadgets, Cables",
                category = "E-Waste",
                subclass = "Electronic Device",
                icon = Icons.Default.Devices,
                accentColor = Color(0xFF4FC3F7)
            ),
            QuickCategoryOption(
                label = "Cardboard / Paper Box",
                description = "Uno Box, Game Box, Cartons, Books",
                category = "Recyclable",
                subclass = "Cardboard",
                icon = Icons.Default.Inventory,
                accentColor = HighConfidenceGreen
            ),
            QuickCategoryOption(
                label = "Plastic Packaging",
                description = "Bottles, Containers, Plastic Shells",
                category = "Recyclable",
                subclass = "Plastic",
                icon = Icons.Default.Recycling,
                accentColor = HighConfidenceGreen
            ),
            QuickCategoryOption(
                label = "Metal / Aluminum",
                description = "Appliance Panels, Cans, Foil",
                category = "Recyclable",
                subclass = "Metal",
                icon = Icons.Default.Build,
                accentColor = HighConfidenceGreen
            ),
            QuickCategoryOption(
                label = "Glass",
                description = "Bottles, Jars, Glassware",
                category = "Recyclable",
                subclass = "Glass",
                icon = Icons.Default.LocalDrink,
                accentColor = HighConfidenceGreen
            ),
            QuickCategoryOption(
                label = "General Trash",
                description = "Composite Packaging, Wrappers",
                category = "Trash",
                subclass = "Plastic",
                icon = Icons.Default.DeleteOutline,
                accentColor = LowConfidence
            )
        )
    }

    val selectedOption = options[selectedIndex]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Quick Product Classifier",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Code: ${if (barcode.length > 20) barcode.take(18) + "..." else barcode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = selectedOption.accentColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, selectedOption.accentColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = selectedOption.category,
                    color = selectedOption.accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = productName,
            onValueChange = { productName = it },
            label = { Text("Product / Item Name (e.g. Uno Box, Laptop)") },
            placeholder = { Text("Name this item...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "SELECT WASTE CATEGORY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
        ) {
            itemsIndexed(options) { index, opt ->
                val isSelected = index == selectedIndex
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) opt.accentColor.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) opt.accentColor else Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { selectedIndex = index }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = opt.icon,
                            contentDescription = null,
                            tint = if (isSelected) opt.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = opt.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = opt.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = opt.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                val finalName = productName.ifBlank { selectedOption.label }
                onConfirm(finalName, selectedOption.category, selectedOption.subclass)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Confirm & View Disposal Guide", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onFallbackToCamera) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Analyze with Camera", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onResumeScanning) {
                Text("Scan Another", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Prompt displayed when camera permission has not yet been granted.
 */
@Composable
private fun CameraPermissionRequired(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.yolo_camera_permission),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            )
        ) {
            Text(stringResource(R.string.common_grant_permission), fontWeight = FontWeight.Bold)
        }
    }
}
