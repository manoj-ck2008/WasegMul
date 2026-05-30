package com.agrelius.wasegmul.ui.result

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.ui.classify.ClassificationViewModel
import com.agrelius.wasegmul.ui.components.*
import com.agrelius.wasegmul.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ClassificationViewModel,
    onNavigateToHome: () -> Unit
) {
    val result by viewModel.classificationResult.collectAsState()
    var showContent by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        showContent = true
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "SYNTHESIS REPORT", 
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldVibrant,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    IconButton(onClick = onNavigateToHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = TextPrimary)
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
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                if (result != null) {
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
                                            text = result!!.subclass,
                                            style = MaterialTheme.typography.displaySmall,
                                            fontWeight = FontWeight.Black,
                                            color = if (result!!.category == "Uncertain") LowConfidence else TextPrimary,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = if (result!!.category == "Uncertain") "LOW CONFIDENCE MATCH" else result!!.category.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (result!!.category == "Uncertain") LowConfidence else EmeraldVibrant,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        )
                                    }
                                    ConfidenceBadge(confidence = result!!.confidence)
                                }
                            }

                            if (result!!.category == "Uncertain") {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = LowConfidence.copy(alpha = 0.1f))
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = LowConfidence)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "Neural Variance Detected: The visual signature is outside standard thresholds. Please verify manually.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }

                            SectionTitle(text = "Neural Insights")
                            
                            InsightCard(
                                title = "Prediction Variance",
                                content = if (result!!.topPredictions.isEmpty()) "Single signature detected." 
                                          else result!!.topPredictions.joinToString("\n") { 
                                              "${it.first}: ${(it.second * 100).toInt()}% match" 
                                          },
                                icon = Icons.Default.QueryStats
                            )

                            InsightCard(
                                title = "Disposal Protocol",
                                content = result!!.disposalGuide,
                                icon = Icons.Default.VerifiedUser
                            )

                            InsightCard(
                                title = "Environmental Infographic",
                                content = result!!.environmentalImpact,
                                icon = Icons.Default.AutoGraph
                            )
                            
                            InsightCard(
                                title = "Verification Sources",
                                content = "Data verified against IPCC & EPA Sustainability Frameworks.",
                                icon = Icons.Default.Science
                            )

                            SectionTitle(text = "Validation")
                            
                            FeedbackSection(
                                onFeedbackSelected = { feedback ->
                                    viewModel.setFeedback(feedback)
                                },
                                onCorrectionSelected = { correction ->
                                    viewModel.setCorrection(correction)
                                }
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            GradientActionButton(
                                text = "Acknowledge & Close",
                                onClick = onNavigateToHome,
                                containerColor = ForestGreen
                            )
                            
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    }
                }
            }
        }
    }
}
