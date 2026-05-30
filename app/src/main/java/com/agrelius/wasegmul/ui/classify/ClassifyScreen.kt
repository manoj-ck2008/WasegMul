package com.agrelius.wasegmul.ui.classify

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.WasegMulApp
import com.agrelius.wasegmul.ui.components.GradientActionButton
import com.agrelius.wasegmul.ui.components.OrganicBackground
import com.agrelius.wasegmul.ui.theme.*
import com.agrelius.wasegmul.utils.EcoThoughts
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassifyScreen(
    viewModel: ClassificationViewModel,
    onNavigateToResult: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val soundManager = (context.applicationContext as WasegMulApp).soundManager
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val classificationResult by viewModel.classificationResult.collectAsState()
    val error by viewModel.error.collectAsState()
    val loadingThought = remember { EcoThoughts.getRandom() }

    LaunchedEffect(Unit) {
        viewModel.initModel(context)
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            while (isLoading) {
                soundManager.playAnalyzingPulse()
                delay(800)
            }
        }
    }

    LaunchedEffect(classificationResult) {
        if (classificationResult != null) {
            soundManager.playNeuralLock()
            onNavigateToResult()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "SCAN INTERFACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldVibrant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = TextPrimary
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
            OrganicBackground()
            
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
                        .background(DeepCharcoal.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            color = ForestGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (capturedBitmap != null) {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
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
                                tint = EmeraldVibrant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Awaiting Visual Input",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(color = EmeraldVibrant)
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = loadingThought,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                        ScanningOverlay()
                    }
                }

                if (error != null) {
                    Card(
                        modifier = Modifier.padding(top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                GradientActionButton(
                    text = if (isLoading) "Processing..." else "Launch Scanner",
                    icon = Icons.Default.AutoAwesome,
                    onClick = {
                        soundManager.playTick()
                        viewModel.classify(context)
                    },
                    modifier = Modifier.alpha(if (isLoading) 0.7f else 1f),
                    containerColor = ForestGreen
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = SageGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Retake Image",
                        style = MaterialTheme.typography.labelLarge,
                        color = SageGreen
                    )
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

    Canvas(modifier = Modifier.fillMaxSize()) {
        val y = size.height * scanY
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    EmeraldVibrant,
                    Color.Transparent
                )
            ),
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = 4.dp.toPx()
        )
        
        drawRect(
            brush = Brush.verticalGradient(
                0f to EmeraldVibrant.copy(alpha = 0.1f),
                scanY to Color.Transparent,
                startY = y - 100f,
                endY = y
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, y - 100f),
            size = androidx.compose.ui.geometry.Size(size.width, 100f)
        )
    }
}
