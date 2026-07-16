package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.agrelius.wasegmul.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FeedbackSection(
    initialFeedback: String?,
    initialCorrection: String?,
    onFeedbackSelected: (String) -> Unit,
    onCorrectionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    var showResults by remember { mutableStateOf(initialFeedback != null) }
    var step by remember { mutableIntStateOf(if (initialFeedback == "incorrect") 1 else 0) }

    LaunchedEffect(initialFeedback, initialCorrection) {
        showResults = initialFeedback != null
        step = if (initialFeedback == "incorrect") 1 else 0
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showResults && step != 1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.feedback_recorded),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (initialFeedback == "correct") stringResource(R.string.feedback_verified) 
                               else if (initialCorrection != null) stringResource(R.string.feedback_corrected_to, initialCorrection)
                               else stringResource(R.string.feedback_uncertain),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { 
                        showResults = false
                        step = 0
                    }) {
                        Text(stringResource(R.string.feedback_edit))
                    }
                }
            } else {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "feedback_steps"
                ) { targetStep ->
                    when (targetStep) {
                        0 -> {
                            InitialFeedbackView(
                                onCorrect = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFeedbackSelected("correct")
                                    showResults = true
                                },
                                onIncorrect = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFeedbackSelected("incorrect")
                                    step = 1
                                },
                                onNotSure = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFeedbackSelected("not_sure")
                                    showResults = true
                                }
                            )
                        }
                        1 -> {
                            CorrectionView(
                                onSelected = { choice ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCorrectionSelected(choice)
                                    step = 0
                                    showResults = true
                                },
                                onBack = { step = 0 }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InitialFeedbackView(
    onCorrect: () -> Unit,
    onIncorrect: () -> Unit,
    onNotSure: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.feedback_accurate_question),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeedbackButton(icon = Icons.Default.Check, label = stringResource(R.string.common_correct), onClick = onCorrect)
            FeedbackButton(icon = Icons.Default.Close, label = stringResource(R.string.common_incorrect), onClick = onIncorrect)
            FeedbackButton(icon = Icons.Default.QuestionMark, label = stringResource(R.string.feedback_not_sure), onClick = onNotSure)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CorrectionView(onSelected: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val options = context.resources.getStringArray(R.array.material_options).toList()
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_go_back)) }
            Text(
                text = stringResource(R.string.feedback_actual_material),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = false,
                    onClick = { onSelected(option) },
                    label = { Text(option) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FeedbackButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
