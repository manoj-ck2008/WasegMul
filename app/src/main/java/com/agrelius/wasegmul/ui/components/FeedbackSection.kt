package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.WasegMulApp

@Composable
fun FeedbackSection(
    onFeedbackSelected: (String) -> Unit,
    onCorrectionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val soundManager = (context.applicationContext as WasegMulApp).soundManager
    
    var step by remember { mutableIntStateOf(0) } // 0: initial, 1: correction, 2: thank you

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
                                soundManager.playTick()
                                onFeedbackSelected("correct")
                                step = 2
                            },
                            onIncorrect = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                soundManager.playWarning()
                                onFeedbackSelected("incorrect")
                                step = 1
                            },
                            onNotSure = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                soundManager.playTick()
                                onFeedbackSelected("not_sure")
                                step = 2
                            }
                        )
                    }
                    1 -> {
                        CorrectionView(
                            onSelected = { choice ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                soundManager.playTick()
                                onCorrectionSelected(choice)
                                step = 2
                            }
                        )
                    }
                    2 -> {
                        Text(
                            text = "Thank you! Your feedback helps refine our neural network.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
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
            text = "Was this prediction accurate?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeedbackButton(icon = Icons.Default.Check, label = "Correct", onClick = onCorrect)
            FeedbackButton(icon = Icons.Default.Close, label = "Incorrect", onClick = onIncorrect)
            FeedbackButton(icon = Icons.Default.QuestionMark, label = "Not Sure", onClick = onNotSure)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CorrectionView(onSelected: (String) -> Unit) {
    val options = listOf("Plastic", "Metal", "Glass", "Paper", "Cardboard", "E-Waste", "Organic", "Trash")
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "What was the actual material?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
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
