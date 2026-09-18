package com.agrelius.wasegmul.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.R

private const val COLLAPSED_MAX_LINES = 4
private const val COLLAPSIBLE_LENGTH = 160

@Composable
fun InsightCard(
    title: String,
    content: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    // Collapse affordance for long Knowledge Base bodies: collapsed preview
    // plus an explicit button (TalkBack announces expanded state).
    val collapsible = content.length > COLLAPSIBLE_LENGTH
    var expanded by rememberSaveable(title) { mutableStateOf(!collapsible) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = if (collapsible && !expanded) COLLAPSED_MAX_LINES else Int.MAX_VALUE,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (collapsible) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (expanded) R.string.insight_show_less
                                else R.string.insight_show_more
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "InsightCard Preview", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun InsightCardPreview() {
    com.agrelius.wasegmul.ui.theme.WasegMulTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            InsightCard(
                title = "Recycling Tip",
                content = "Rinse all plastic containers before disposal to prevent contamination.",
                icon = androidx.compose.material.icons.Icons.Default.Info
            )
        }
    }
}
