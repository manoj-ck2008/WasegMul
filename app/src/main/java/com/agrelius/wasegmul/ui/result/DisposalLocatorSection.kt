package com.agrelius.wasegmul.ui.result

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.data.disposal.DisposalCenterType
import com.agrelius.wasegmul.data.disposal.NearbyCenterMatch
import kotlinx.coroutines.delay

@Composable
fun DisposalLocatorSection(
    centers: List<NearbyCenterMatch>,
    category: String,
    modifier: Modifier = Modifier
) {
    if (centers.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Nearest Disposal Facilities",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        centers.forEachIndexed { index, match ->
            var visible by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                delay(index * 100L)
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
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

@Composable
private fun DisposalCenterCard(
    match: NearbyCenterMatch,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val center = match.center
    
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
                    text = String.format("%.1f km", match.distanceKm),
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
                        val uri = Uri.parse("geo:0,0?q=${center.latitude},${center.longitude}(${center.name})")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        intent.setPackage("com.google.android.apps.maps")
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(fallbackIntent)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF94),
                        contentColor = Color(0xFF020408)
                    )
                ) {
                    Text("Navigate")
                }
                
                if (center.phone != null) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${center.phone}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Call")
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: DisposalCenterType) {
    val (backgroundColor, textColor, label) = when (type) {
        DisposalCenterType.DWCC -> Triple(Color(0xFF1E88E5).copy(alpha = 0.2f), Color(0xFF64B5F6), "DWCC")
        DisposalCenterType.E_WASTE -> Triple(Color(0xFFE53935).copy(alpha = 0.2f), Color(0xFFE57373), "E-Waste")
        DisposalCenterType.COMPOST -> Triple(Color(0xFF43A047).copy(alpha = 0.2f), Color(0xFF81C784), "Compost")
        DisposalCenterType.WARD_OFFICE -> Triple(Color(0xFFFFB300).copy(alpha = 0.2f), Color(0xFFFFD54F), "Ward Office")
        DisposalCenterType.RETAILER -> Triple(Color(0xFF8E24AA).copy(alpha = 0.2f), Color(0xFFBA68C8), "Retailer")
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
