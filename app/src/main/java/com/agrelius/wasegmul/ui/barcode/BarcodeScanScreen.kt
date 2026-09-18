package com.agrelius.wasegmul.ui.barcode

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.agrelius.wasegmul.ui.theme.categoryColor
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
            modelManager = (LocalContext.current.applicationContext as WasegMulApp).modelManager,
            settingsManager = (LocalContext.current.applicationContext as WasegMulApp).settingsManager
        )
    )
) {
    val context = LocalContext.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    // Back collapses the bottom sheet first (resume scanning); only a bare
    // viewfinder pops the screen. Deep-link ready via wasegmul://barcode.
    BackHandler(enabled = uiState !is BarcodeScanState.Scanning) {
        viewModel.resumeScanning()
    }

    // Permission check-first with rationale + Settings redirect (no blind
    // auto-fire, no permanent-denial loop).
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var showPermissionRationale by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionAsked = true
        if (!granted) showPermissionRationale = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionAsked) {
            permissionAsked = true
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
    // Rotation-safe form state.
    var showManualInputDialog by rememberSaveable { mutableStateOf(false) }
    var manualBarcode by rememberSaveable { mutableStateOf("") }
    var manualError by rememberSaveable { mutableStateOf<String?>(null) }
    val manualInvalidText = stringResource(R.string.barcode_manual_invalid)

    fun submitManual(code: String) {
        // EAN-8..GTIN-14 digit check (UI-side parity with VM validation).
        val clean = code.trim()
        if (!clean.matches(Regex("^[0-9]{8,14}$"))) {
            manualError = manualInvalidText
            return
        }
        manualError = null
        showManualInputDialog = false
        manualBarcode = ""
        // The VM only accepts detections from Scanning; manual entry must be
        // reachable from NotFound/Error too, so resume first (public VM API,
        // no logic duplicated).
        viewModel.resumeScanning()
        viewModel.onBarcodeDetected(clean)
    }

    if (showManualInputDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showManualInputDialog = false; manualError = null },
            title = { Text(stringResource(R.string.barcode_manual_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = manualBarcode,
                        onValueChange = { manualBarcode = it; manualError = null },
                        label = { Text(stringResource(R.string.barcode_manual_label)) },
                        singleLine = true,
                        isError = manualError != null,
                        supportingText = manualError?.let { { Text(it) } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { submitManual(manualBarcode) }) {
                    Text(stringResource(R.string.barcode_manual_search))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false; manualError = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.barcode_scanner_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Back collapses the sheet first (see BackHandler).
                        if (uiState !is BarcodeScanState.Scanning) viewModel.resumeScanning()
                        else onBack()
                    }) {
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
                            contentDescription = stringResource(R.string.barcode_manual_search_cd),
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
                    showRationale = showPermissionRationale,
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
                                ResolvingCardContent(
                                    barcode = state.barcode,
                                    onCancel = { viewModel.resumeScanning() }
                                )
                            }
                            is BarcodeScanState.Resolved -> {
                                ResolvedCardContent(
                                    product = state.product,
                                    components = state.components,
                                    snackbarHostState = snackbarHostState,
                                    onConfirmAndSave = {
                                        viewModel.confirmAndSave(state.product)
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
                                    onManualEntry = { showManualInputDialog = true },
                                    onFallbackToCamera = onFallbackToCamera,
                                    onResumeScanning = { viewModel.resumeScanning() }
                                )
                            }
                            is BarcodeScanState.Error -> {
                                QuickClassifierCardContent(
                                    barcode = "Unindexed Code",
                                    errorMessage = state.message,
                                    onConfirm = { name, cat, sub ->
                                        // persistCache=false: error-state pseudo-codes must
                                        // never land in the barcode cache as junk keys.
                                        viewModel.quickClassifyAndSave("code_${System.currentTimeMillis()}", name, cat, sub, persistCache = false)
                                    },
                                    onManualEntry = { showManualInputDialog = true },
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
    // Camera-bind failures must surface as UI, never as an uncaught throw on
    // the main executor (abrupt "app keeps stopping" crash).
    var cameraError by remember { mutableStateOf<String?>(null) }
    // Bump to re-run the PreviewView factory (full rebind) on Retry.
    var cameraKey by remember { mutableStateOf(0) }

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
        androidx.compose.runtime.key(cameraKey) {
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
                        // get() throws outside the bind try below (missing camera /
                        // SecurityException): catch it here so a provider failure
                        // shows the error panel instead of crashing the process.
                        val cp = try {
                            cameraProviderFuture.get()
                        } catch (e: Exception) {
                            Log.e(TAG, "Camera provider failed: ${e.message}")
                            cameraError = "Camera unavailable on this device. You can still enter the code manually."
                            return@addListener
                        }
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
                                } catch (e: OutOfMemoryError) {
                                    Log.w(TAG, "Barcode frame too large; frame dropped")
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
                            cameraError = null
                            onFlashSupported(boundCamera.cameraInfo.hasFlashUnit())
                        } catch (e: Exception) {
                            Log.e(TAG, "Camera bind failed: ${e.message}")
                            cameraError = "Camera unavailable on this device. You can still enter the code manually."
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        }

        cameraError?.let { message ->
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    cameraError = null
                    cameraKey++
                }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }

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
                    text = stringResource(R.string.barcode_point_hint),
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
    val statusText = if (isOnline) stringResource(R.string.network_online) else stringResource(R.string.network_offline)

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
private fun ResolvingCardContent(
    barcode: String,
    onCancel: () -> Unit
) {
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
            text = stringResource(R.string.barcode_resolving),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.barcode_looking_up, barcode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        // Cancellable: the OFF cascade can take ~60s; never trap the user.
        TextButton(onClick = onCancel) {
            Text(
                stringResource(R.string.barcode_cancel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Content displayed in the bottom card when a barcode is resolved.
 */
@Composable
private fun ResolvedCardContent(
    product: BarcodeProduct,
    components: List<ResolvedPackagingComponent>,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onConfirmAndSave: suspend () -> Unit,
    onResumeScanning: () -> Unit
) {
    var isSaving by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveFailedText = stringResource(R.string.barcode_save_failed)

    // Single category-color map (was a local palette incl. Trash->red drift).
    val categoryColor = categoryColor(product.category)

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
            text = stringResource(R.string.barcode_packaging_breakdown),
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
                                text = stringResource(R.string.barcode_weight_fmt, weight),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    // try/catch + reset: a failed save must never wedge the
                    // button in a permanently disabled state.
                    scope.launch {
                        isSaving = true
                        try {
                            onConfirmAndSave()
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(saveFailedText)
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.barcode_saving), fontWeight = FontWeight.Bold)
            } else {
                Text(stringResource(R.string.barcode_confirm_guide), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onResumeScanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.barcode_scan_another),
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
    onResumeScanning: () -> Unit,
    onManualEntry: () -> Unit = {}
) {
    // Rotation-safe form; nothing pre-selected (a one-tap default once
    // misfiled cardboard as e-waste).
    var productName by rememberSaveable { mutableStateOf("") }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionError by rememberSaveable { mutableStateOf<String?>(null) }
    val nameRequiredText = stringResource(R.string.barcode_name_required)
    val selectionRequiredText = stringResource(R.string.barcode_select_required)

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
                // Was Plastic (Recyclable): contradictory category/subclass
                // pair. Miscellaneous Trash is the mapped Trash subclass.
                subclass = "Miscellaneous Trash",
                icon = Icons.Default.DeleteOutline,
                accentColor = LowConfidence
            )
        )
    }

    val selectedOption = selectedIndex?.let { options.getOrNull(it) }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.barcode_quick_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.barcode_code_full,
                            if (barcode.length > 20) barcode.take(18) + "..." else barcode
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Manual entry is reachable from error states too (no dead end).
            TextButton(onClick = onManualEntry) {
                Text(
                    stringResource(R.string.barcode_manual_entry),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Selected-category badge (nothing pre-selected: hidden until chosen).
        selectedOption?.let { selected ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = selected.accentColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, selected.accentColor.copy(alpha = 0.4f)),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = selected.category,
                    color = selected.accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Full code in the body (header truncates past 20 chars).
        if (barcode.length > 20) {
            Text(
                text = barcode,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = productName,
            onValueChange = { productName = it; nameError = null },
            label = { Text(stringResource(R.string.barcode_manual_label)) },
            placeholder = { Text(stringResource(R.string.barcode_manual_entry)) },
            singleLine = true,
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.barcode_select_category),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        selectionError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
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
                        .clickable {
                            selectedIndex = if (isSelected) null else index
                            selectionError = null
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = opt.icon,
                            contentDescription = opt.label,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                // Validated: blank names no longer save the option label as
                // the product name, and a category must be chosen.
                val chosen = selectedOption
                var valid = true
                if (productName.isBlank()) {
                    nameError = nameRequiredText
                    valid = false
                }
                if (chosen == null) {
                    selectionError = selectionRequiredText
                    valid = false
                }
                if (valid && chosen != null) {
                    onConfirm(productName.trim(), chosen.category, chosen.subclass)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.barcode_confirm_guide), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onFallbackToCamera) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.barcode_camera), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onResumeScanning) {
                Text(stringResource(R.string.barcode_scan_another), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Prompt displayed when camera permission has not yet been granted.
 */
@Composable
private fun CameraPermissionRequired(
    showRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
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
        if (showRationale) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.barcode_permission_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(stringResource(R.string.common_grant_permission), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = {
            try {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.packageName)
                    )
                )
            } catch (_: Exception) { }
        }) {
            Text(stringResource(R.string.home_open_settings))
        }
    }
}
