package com.agrelius.wasegmul.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.R

@Composable
fun ConfidenceBadge(
    confidence: Float,
    modifier: Modifier = Modifier,
    // User-tuned threshold from Settings: the "medium" bar follows it
    // (grades stay consistent with the verification alert the user set).
    userThreshold: Float = 0.70f
) {
    val mediumBar = userThreshold.coerceIn(0.20f, 0.90f)
    val (color, label) = when {
        confidence >= 0.90f -> MaterialTheme.colorScheme.primary to stringResource(R.string.confidence_high)
        confidence >= mediumBar -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.confidence_medium)
        else -> MaterialTheme.colorScheme.error to stringResource(R.string.confidence_low)
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.confidence_format, label, (confidence * 100).toInt()),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "ConfidenceBadge High", showBackground = true)
@Composable
private fun ConfidenceBadgePreview() {
    ConfidenceBadge(confidence = 0.95f)
}

