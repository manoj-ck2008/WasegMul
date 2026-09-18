package com.agrelius.wasegmul.ui.classify

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.ui.components.GradientActionButton
import com.agrelius.wasegmul.ui.components.OrganicBackground
import com.agrelius.wasegmul.ui.components.decodeSampledBitmap
import com.agrelius.wasegmul.ui.components.rememberReduceMotion
import com.agrelius.wasegmul.ui.theme.*
import com.agrelius.wasegmul.utils.EcoThoughts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassifyScreen(
    viewModel: ClassificationViewModel,
    onNavigateToResult: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as? com.agrelius.wasegmul.WasegMulApp
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val classificationResult by viewModel.classificationResult.collectAsState()
    val error by viewModel.error.collectAsState()
    val modelInitError by viewModel.modelInitError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    // A fresh thought per classification run (not one per composition).
    var loadingThought by rememberSaveable { mutableStateOf(EcoThoughts.getRandom()) }
    LaunchedEffect(isLoading) {
        if (isLoading) loadingThought = EcoThoughts.getRandom()
    }

    // Empty-state recovery: picking here restores Classify without going Home.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeSampledBitmap(context, it, 1024, 1024)
                }
                if (bitmap != null) {
                    viewModel.setBitmap(bitmap)
                } else {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.home_image_load_failed)
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initModel(context, app?.modelManager)
    }

    // System back during inference cancels the job first (no half-written
    // state, no orphaned XP); otherwise it behaves like the top-bar back.
    BackHandler {
        if (isLoading) viewModel.cancelClassification()
        onBack()
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToResult.collect { recordId ->
            onNavigateToResult(recordId)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.classify_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ) 
                },
                navigationIcon = {
                    // Always enabled: system/tap back cancels inference first.
                    IconButton(onClick = {
                        if (isLoading) viewModel.cancelClassification()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OrganicBackground(animate = !reduceMotion)
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Immersive Preview Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = capturedBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Captured Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.classify_awaiting),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Dead-end fix: a pick CTA right in the empty state.
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                                Icon(
                                    Icons.Default.ImageSearch,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.classify_pick_image))
                            }
                        }
                    }

                    if (isLoading) {
                        // Single scrim (theme-aware) + scan line; no double dim.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(24.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = loadingThought,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                        ScanningOverlay()
                    }
                }

                if (modelInitError != null) {
                    Card(
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = modelInitError.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { viewModel.retryInitModel() }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.common_retry), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                if (error != null) {
                    Card(
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = error.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    viewModel.clearError()
                                    viewModel.classify()
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.common_retry), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                GradientActionButton(
                    text = if (isLoading) stringResource(R.string.common_processing) else stringResource(R.string.classify_action_analyze),
                    icon = Icons.Default.AutoAwesome,
                    iconContentDescription = stringResource(R.string.classify_action_analyze),
                    onClick = {
                        viewModel.classify()
                    },
                    modifier = Modifier.alpha(if (isLoading || capturedBitmap == null) 0.5f else 1f),
                    enabled = (capturedBitmap != null && !isLoading),
                    containerColor = MaterialTheme.colorScheme.tertiary
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    // Cancellable inference: visible Cancel, not a dead Retake.
                    OutlinedButton(
                        onClick = { viewModel.cancelClassification() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.classify_cancel),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.classify_retake),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScanningOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val scanLineBrush = remember(primaryColor) { Brush.horizontalGradient(colors = listOf(Color.Transparent, primaryColor, Color.Transparent)) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val y = size.height * scanY
        drawLine(
            brush = scanLineBrush,
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = 4.dp.toPx()
        )

        drawRect(
            brush = Brush.verticalGradient(
                0f to primaryColor.copy(alpha = 0.1f),
                scanY to Color.Transparent,
                startY = y - 100f,
                endY = y
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, y - 100f),
            size = androidx.compose.ui.geometry.Size(size.width, 100f)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "ScanningOverlay Preview", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ScanningOverlayPreview() {
    WasegMulTheme {
        Box(modifier = Modifier.size(300.dp)) {
            ScanningOverlay()
        }
    }
}

