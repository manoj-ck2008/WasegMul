package com.agrelius.wasegmul.ui.result

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.data.disposal.DisposalCenterType
import com.agrelius.wasegmul.data.disposal.NearbyCenterMatch
import kotlinx.coroutines.delay

@Composable
fun DisposalLocatorSection(
    centers: List<NearbyCenterMatch>,
    category: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.disposal_nearest_for_category, category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (centers.isEmpty()) {
            // Empty state instead of the previous silent return.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.disposal_empty, category),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        centers.forEachIndexed { index, match ->
            // Keyed per item so the stagger replays correctly per list.
            key(match.center.name, match.center.latitude, match.center.longitude) {
                var visible by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(index * 100L)
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400)) + slideInVertically(
                        initialOffsetY = { 50 },
                        animationSpec = tween(durationMillis = 400)
                    )
                ) {
                    DisposalCenterCard(
                        match = match,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DisposalCenterCard(
    match: NearbyCenterMatch,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val center = match.center
    val noMapsText = stringResource(R.string.disposal_no_maps)
    val cannotCallText = stringResource(R.string.disposal_cannot_call)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypeBadge(type = center.type)
                Text(
                    text = stringResource(
                        R.string.disposal_distance_km,
                        match.distanceKm
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = center.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = center.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = center.operatingHours,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            val label = Uri.encode(center.name)
                            val uri = Uri.parse(
                                "geo:0,0?q=${center.latitude},${center.longitude}($label)"
                            )
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                val fallback = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        "geo:${center.latitude},${center.longitude}?q=${Uri.encode(center.name)}"
                                    )
                                )
                                if (fallback.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(
                                        Intent.createChooser(
                                            fallback,
                                            context.getString(R.string.disposal_navigate)
                                        )
                                    )
                                } else {
                                    Toast.makeText(context, noMapsText, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, noMapsText, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.disposal_navigate))
                }

                if (center.phone != null) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_DIAL,
                                    Uri.parse("tel:${Uri.encode(center.phone)}")
                                )
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, cannotCallText, Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, cannotCallText, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.disposal_call))
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: DisposalCenterType) {
    // Mid-tone hues readable on both light and dark surfaces (no
    // light-blue-on-light / neon-on-white pairs).
    val (backgroundColor, textColor, label) = when (type) {
        DisposalCenterType.DWCC -> Triple(Color(0xFF1565C0).copy(alpha = 0.15f), Color(0xFF1565C0), "DWCC")
        DisposalCenterType.E_WASTE -> Triple(Color(0xFFC0392B).copy(alpha = 0.15f), Color(0xFFC0392B), "E-Waste")
        DisposalCenterType.COMPOST -> Triple(Color(0xFF1E8E4D).copy(alpha = 0.15f), Color(0xFF1E8E4D), "Compost")
        DisposalCenterType.WARD_OFFICE -> Triple(Color(0xFF9A7B0A).copy(alpha = 0.18f), Color(0xFF7D6305), "Ward Office")
        DisposalCenterType.RETAILER -> Triple(Color(0xFF6A1B9A).copy(alpha = 0.15f), Color(0xFF6A1B9A), "Retailer")
        DisposalCenterType.HAZARDOUS -> Triple(Color(0xFFE65100).copy(alpha = 0.18f), Color(0xFFE65100), "Hazardous")
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}
