package com.agrelius.wasegmul.ui.result

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    onNavigateToHome: () -> Unit,
    onBack: () -> Unit = {}
) {
    val result by viewModel.classificationResult.collectAsState()
    val record by viewModel.currentRecord.collectAsState()
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
                        "ANALYSIS REPORT", 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
                        result?.let { r ->
                        Column {
                            HeroCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = r.subclass,
                                            style = MaterialTheme.typography.displaySmall,
                                            fontWeight = FontWeight.Black,
                                            color = if (r.category == "Uncertain") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = if (r.category == "Uncertain") "LOW CONFIDENCE MATCH" else r.category.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (r.category == "Uncertain") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        )
                                    }
                                    ConfidenceBadge(confidence = r.confidence)
                                }
                            }

                            if (r.category == "Uncertain") {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = r.classificationMessage.ifBlank {
                                                "Neural Variance: The visual signature is outside standard thresholds. Manual verification required."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            } else if (r.classificationMessage.isNotBlank()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                        Icon(
                                            Icons.Default.AutoGraph,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = r.classificationMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            SectionTitle(text = "Environmental Insights")
                            
                            InsightCard(
                                title = "Disposal Protocol",
                                content = r.disposalGuide,
                                icon = Icons.Default.VerifiedUser
                            )

                            InsightCard(
                                title = "Ecological Footprint",
                                content = r.environmentalImpact,
                                icon = Icons.Default.AutoGraph
                            )

                            InsightCard(
                                title = "Recycling Benefits",
                                content = r.recyclingBenefits,
                                icon = Icons.Default.Recycling
                            )

                            InsightCard(
                                title = "Verification Sources",
                                content = r.sources,
                                icon = Icons.Default.Science
                            )

                            // Top model predictions + storage metadata.
                            if (r.topPredictions.isNotEmpty()) {
                                SectionTitle(text = "Model Confidence Breakdown")
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        r.topPredictions.forEach { (label, conf) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${(conf * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                )
                                            }
                                        }
                                        if (record != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Storage: ${if (record?.imagePath != null) "Image saved for review" else "Metadata only"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            SectionTitle(text = "Validation")
                            
                            FeedbackSection(
                                initialFeedback = record?.feedback,
                                initialCorrection = record?.correctedSubclass,
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
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                        }
                    }
                }
            }
        }
    }
}
