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
import androidx.compose.ui.res.stringResource
import com.agrelius.wasegmul.R
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
                        stringResource(R.string.result_title), 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHome) {
                        Icon(Icons.Default.Home, contentDescription = stringResource(R.string.common_home), tint = MaterialTheme.colorScheme.onSurface)
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
                                            text = if (r.category == "Uncertain") stringResource(R.string.result_low_confidence_match) else r.category.uppercase(),
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
                                                stringResource(R.string.result_neural_variance)
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

                            SectionTitle(text = stringResource(R.string.result_environmental_insights))
                            
                            InsightCard(
                                title = stringResource(R.string.result_disposal_protocol),
                                content = r.disposalGuide,
                                icon = Icons.Default.VerifiedUser
                            )

                            InsightCard(
                                title = stringResource(R.string.result_ecological_footprint),
                                content = r.environmentalImpact,
                                icon = Icons.Default.AutoGraph
                            )

                            InsightCard(
                                title = stringResource(R.string.result_recycling_benefits),
                                content = r.recyclingBenefits,
                                icon = Icons.Default.Recycling
                            )

                            InsightCard(
                                title = stringResource(R.string.result_verification_sources),
                                content = r.sources,
                                icon = Icons.Default.Science
                            )

                            // Top model predictions + storage metadata.
                            if (r.topPredictions.isNotEmpty()) {
                                SectionTitle(text = stringResource(R.string.result_confidence_breakdown))
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
                                                text = if (record?.imagePath != null) stringResource(R.string.result_storage_image) else stringResource(R.string.result_storage_metadata),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            SectionTitle(text = stringResource(R.string.result_validation))
                            
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
                                text = stringResource(R.string.result_acknowledge_close),
                                onClick = onNavigateToHome,
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                        }
                    }
                } else {
                    val error by viewModel.error.collectAsState()
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (error != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 32.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = onBack) { Text("Go Back") }
                            }
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
