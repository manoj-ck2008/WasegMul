package com.agrelius.wasegmul.ui.classify

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.ui.components.GradientActionButton
import com.agrelius.wasegmul.viewmodel.ClassificationViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassifyScreen(
    viewModel: ClassificationViewModel,
    onNavigateToResult: () -> Unit,
    onBack: () -> Unit
) {
    var isAnalyzing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Neural Scanner",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive Scan Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Awaiting Visual Input",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isAnalyzing) {
                    ScanningOverlay()
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            GradientActionButton(
                text = if (isAnalyzing) "Analyzing..." else "Analyze Material",
                icon = Icons.Default.AutoAwesome,
                onClick = {
                    isAnalyzing = true
                    // Simulate processing
                    scope.apply {
                        // In a real app, this would be a launched effect or handled in VM
                    }
                },
                modifier = Modifier.alpha(if (isAnalyzing) 0.7f else 1f)
            )

            if (isAnalyzing) {
                LaunchedEffect(Unit) {
                    delay(2500)
                    onNavigateToResult()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAnalyzing
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Retake Photo",
                    style = MaterialTheme.typography.labelLarge
                )
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
                    Color(0xFF2ECC71),
                    Color.Transparent
                )
            ),
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = 4.dp.toPx()
        )
        
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF2ECC71).copy(alpha = 0.1f),
                scanY to Color.Transparent,
                startY = y - 100f,
                endY = y
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, y - 100f),
            size = androidx.compose.ui.geometry.Size(size.width, 100f)
        )
    }
}
