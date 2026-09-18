package com.agrelius.wasegmul.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.EcoImpactCalculator
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.ui.components.GlassCard
import com.agrelius.wasegmul.ui.theme.categoryColor
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun HistoryDashboard(allHistory: List<WasteRecord>) {
    if (allHistory.isEmpty()) return

    val categoryData = remember(allHistory) {
        val data = mutableMapOf<String, Int>()
        allHistory.forEach { record ->
            val count = data.getOrDefault(record.category, 0)
            data[record.category] = count + 1
        }
        data
    }

    var showChart by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showChart = true
        delay(150)
        showStats = true
        delay(150)
        showCategories = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(
            visible = showChart,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 50 }
        ) {
            AnimatedDonutChart(categoryData = categoryData)
        }

        AnimatedVisibility(
            visible = showStats,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 50 }
        ) {
            DashboardStatsRow(history = allHistory)
        }

        AnimatedVisibility(
            visible = showCategories,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 50 }
        ) {
            CategoryBreakdownRow(
                categoryData = categoryData,
                totalCount = allHistory.size
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimatedDonutChart(categoryData: Map<String, Int>) {
    val totalCount = categoryData.values.sum()
    var startAnimation by remember { mutableStateOf(false) }

    // Resolve slice colors in composition (never inside the Canvas draw
    // scope or a stdlib-transform lambda, neither of which is a
    // @Composable context — hence the plain for-loop).
    val sortedEntries = remember(categoryData) {
        categoryData.entries.sortedByDescending { it.value }
    }
    val sliceColors = mutableMapOf<String, androidx.compose.ui.graphics.Color>()
    for ((category, _) in sortedEntries) {
        sliceColors[category] = categoryColor(category)
    }
    // Hoisted: MaterialTheme is @Composable and unusable in draw scope.
    val fallbackSliceColor = MaterialTheme.colorScheme.primary

    val sweepProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "sweepProgress"
    )

    val animatedCount by animateIntAsState(
        targetValue = if (startAnimation) totalCount else 0,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "animatedCount"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    val strokeWidth = 24.dp.toPx()
                    // No gap for a single slice (a full ring with a notch is
                    // a lie); gaps only separate 2+ slices.
                    val gap = if (categoryData.size < 2) 0f else 3f
                    // Inset by half-stroke so round caps are not clipped at canvas edges.
                    val halfStroke = strokeWidth / 2f

                    val totalSweep = 360f - (categoryData.size * gap)

                    sortedEntries.forEach { (category, count) ->
                        val proportion = count.toFloat() / totalCount
                        val sweepAngle = proportion * totalSweep * sweepProgress

                        drawArc(
                            color = sliceColors[category] ?: fallbackSliceColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            size = Size(size.width - strokeWidth, size.height - strokeWidth),
                            topLeft = Offset(halfStroke, halfStroke)
                        )
                        startAngle += sweepAngle + gap
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = animatedCount.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.dashboard_total_scans),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Legend (foundation FlowRow directly; color + text, never color alone)
            @OptIn(ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                categoryData.entries.sortedByDescending { it.value }.forEach { (category, count) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(categoryColor(category), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$category ($count)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardStatsRow(history: List<WasteRecord>) {
    // estimatedWeight is non-null Double (shared model); no stale Elvis needed.
    val totalWeight = history.sumOf { it.estimatedWeight }
    val co2Prevented = EcoImpactCalculator.calculate(history).co2PreventedKg

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.dashboard_total_scans),
            value = history.size.toFloat(),
            icon = Icons.Default.Analytics,
            isInteger = true,
            unit = ""
        )
        StatCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.dashboard_total_weight),
            value = totalWeight.toFloat(),
            icon = Icons.Default.Scale,
            isInteger = false,
            unit = "kg"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.dashboard_co2_offset),
            value = co2Prevented.toFloat(),
            icon = Icons.Default.Cloud,
            isInteger = false,
            unit = "kg"
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: Float,
    icon: ImageVector,
    isInteger: Boolean,
    unit: String
) {
    var startAnimation by remember { mutableStateOf(false) }

    val animatedValue by animateFloatAsState(
        targetValue = if (startAnimation) value else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "animatedStatValue"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    GlassCard(modifier = modifier.semantics { contentDescription = "$title: $value $unit" }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            val formattedValue = if (isInteger) {
                animatedValue.toInt().toString()
            } else {
                String.format(Locale.getDefault(), "%.2f", animatedValue)
            }
            
            Text(
                text = "$formattedValue $unit".trim(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CategoryBreakdownRow(categoryData: Map<String, Int>, totalCount: Int) {
    val sortedCategories = categoryData.entries.sortedByDescending { it.value }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Category breakdown" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(sortedCategories) { (category, count) ->
            val percentage = if (totalCount > 0) (count.toFloat() / totalCount * 100) else 0f
            val color = categoryColor(category)
            
            Box(
                modifier = Modifier
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$category ${String.format(Locale.getDefault(), "%.1f%%", percentage)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryDashboardPreview() {
    MaterialTheme {
        val dummyData = listOf(
            WasteRecord(id = 1L, category = "Recyclable", subclass = "Plastic", confidence = 0.95f, estimatedWeight = 0.5, timestamp = 0L),
            WasteRecord(id = 2L, category = "Organic", subclass = "Food", confidence = 0.85f, estimatedWeight = 1.2, timestamp = 0L),
            WasteRecord(id = 3L, category = "E-Waste", subclass = "Phone", confidence = 0.99f, estimatedWeight = 0.2, timestamp = 0L),
            WasteRecord(id = 4L, category = "Recyclable", subclass = "Paper", confidence = 0.92f, estimatedWeight = 0.3, timestamp = 0L),
            WasteRecord(id = 5L, category = "Trash", subclass = "Wrapper", confidence = 0.88f, estimatedWeight = 0.1, timestamp = 0L)
        )
        Box(modifier = Modifier.background(Color(0xFF020408))) {
            HistoryDashboard(allHistory = dummyData)
        }
    }
}
