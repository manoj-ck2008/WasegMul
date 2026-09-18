package com.agrelius.wasegmul.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.DecayTimeData
import com.agrelius.wasegmul.ui.components.GlassCard
import kotlinx.coroutines.delay

@Composable
fun DecayTimeSection(
    subclass: String,
    category: String,
    modifier: Modifier = Modifier
) {
    val decayInfo = remember(subclass, category) {
        DecayTimeData.getDecayInfo(subclass, category)
    }

    var isVisible by remember { mutableStateOf(false) }
    var isWarningExpanded by remember { mutableStateOf(false) }
    var currentYear by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }
    
    val targetYear = remember(decayInfo.minYears) {
        if (decayInfo.minYears >= 1000000.0) 1000000
        else decayInfo.minYears.toInt()
    }

    val animatedYear by animateIntAsState(
        targetValue = if (isVisible) targetYear else 0,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "YearCountAnimation"
    )

    val severityColor = when (decayInfo.severityLevel) {
        0 -> Color(0xFF2ECC71)
        1 -> Color(0xFFF1C40F)
        2 -> Color(0xFFE67E22)
        3 -> Color(0xFFE74C3C)
        else -> Color.Gray
    }

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPulse"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { it / 4 }
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⏳ TIME TO DECOMPOSE",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(severityColor.copy(alpha = glowAlpha))
                    )

                    Text(
                        text = if (decayInfo.minYears >= 1000000.0) {
                            "1,000,000+ YEARS"
                        } else if (animatedYear == targetYear) {
                            decayInfo.displayText.uppercase()
                        } else {
                            "$animatedYear YEARS"
                        },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = DecayTimeData.getComparison(decayInfo.minYears),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontStyle = FontStyle.Italic
                        ),
                        color = severityColor
                    )
                    
                    DecayTimeScaleBar(decayInfo.minYears)

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isWarningExpanded = !isWarningExpanded }
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = severityColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Environmental Impact",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isWarningExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle warning",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = isWarningExpanded) {
                            Text(
                                text = decayInfo.impactWarning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecayTimeScaleBar(minYears: Double) {
    val barColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(barColor)
        ) {
            val fraction = when {
                minYears < 80.0 -> (minYears / 80.0) * 0.3
                minYears < 4500.0 -> 0.3 + ((minYears - 80) / (4500 - 80)) * 0.4
                else -> 0.7 + (minYears.coerceAtMost(1000000.0) / 1000000.0) * 0.3
            }.toFloat().coerceIn(0f, 1f)
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Human (80y)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Pyramids (4500y)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
