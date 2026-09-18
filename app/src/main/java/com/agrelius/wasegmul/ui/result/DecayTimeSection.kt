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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.DecayTimeData
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.ui.components.GlassCard
import java.util.Locale
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
    var isWarningExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }

    // Severity is encoded as color AND an explicit text label (color-blind
    // safe: labels/patterns, never color alone). Tones are readable on both
    // light and dark surfaces.
    val severityLabel = stringResource(
        when (decayInfo.severityLevel) {
            0 -> R.string.decay_sev_low
            1 -> R.string.decay_sev_moderate
            2 -> R.string.decay_sev_high
            else -> R.string.decay_sev_extreme
        }
    )
    val severityColor = when (decayInfo.severityLevel) {
        0 -> Color(0xFF1E8E4D)
        1 -> Color(0xFF9A7B0A)
        2 -> Color(0xFFC2570B)
        3 -> Color(0xFFC0392B)
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
                text = stringResource(R.string.decay_time_header),
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

                    // Truthful throughout the entrance animation: always the
                    // Knowledge Base display text (no "0 YEARS" flash for
                    // sub-year items, no truncation mid-count).
                    Text(
                        text = decayInfo.displayText.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(
                            R.string.decay_persistence_fmt,
                            severityLabel
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontStyle = FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = DecayTimeData.getComparison(decayInfo.minYears),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DecayTimeScaleBar(decayInfo.minYears)

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.decay_environmental_warning),
                                onClick = { isWarningExpanded = !isWarningExpanded }
                            )
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = stringResource(R.string.result_cd_hazard),
                                tint = severityColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.decay_environmental_warning),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isWarningExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.decay_toggle_warning),
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
