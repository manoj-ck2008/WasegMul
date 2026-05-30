package com.agrelius.wasegmul.ui.result

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.ui.components.*
import com.agrelius.wasegmul.viewmodel.ResultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    onNavigateToHome: () -> Unit
) {
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showContent = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Material Analysis Report", 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    ) 
                },
                actions = {
                    IconButton(onClick = onNavigateToHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = MaterialTheme.colorScheme.onSurface)
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { 50 }
            ) {
                Column {
                    // Hero Result Card with Glass Effect
                    HeroCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Plastic",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "PET Bottle • Type 01",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            ConfidenceBadge(confidence = 0.985f)
                        }
                    }

                    SectionTitle(text = "AI Insights")
                    
                    InsightCard(
                        title = "Prediction Confidence",
                        content = "PET Bottle (98%)\nGlass Bottle (1.2%)\nMetal Can (0.3%)",
                        icon = Icons.Default.Assessment
                    )

                    InsightCard(
                        title = "Disposal Protocol",
                        content = "Rinse thoroughly. Compress to save space. Remove label if possible. Deposit in Category A Recycling.",
                        icon = Icons.Default.TipsAndUpdates
                    )

                    InsightCard(
                        title = "Ecological Footprint",
                        content = "Estimated decomposition: 450 years.\nRecycling saves 75% energy vs virgin production.",
                        icon = Icons.Default.Public
                    )

                    SectionTitle(text = "Spatial Analysis")
                    
                    // Future YOLO Placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                MaterialTheme.shapes.large
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Dashboard, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Real-time Object Map Placeholder",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    GradientActionButton(
                        text = "Finish Session",
                        onClick = onNavigateToHome
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}
