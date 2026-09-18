package com.agrelius.wasegmul.ui.result

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.R

/**
 * Civic issue-reporting banner.
 *
 * Contact numbers live in [CivicContacts] (Remote-Config-ready: a single swap
 * point when server-driven values land). All intents are guarded
 * (resolveActivity + try/catch). The WhatsApp action is hidden while location
 * is null — never fabricate an `Unknown,Unknown` GPS link.
 */
object CivicContacts {
    const val WHATSAPP_NUMBER = "919448197197"
    const val GBA_HELPLINE = "1533"
    const val BBMP_CONTROL_ROOM = "08022660000"
    const val SAAHAS_EWASTE = "18002586676"
    const val HASIRU_DALA = "+919986808866"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CivicReportingBanner(
    category: String,
    subclass: String,
    userLat: Double?,
    userLon: Double?,
    modifier: Modifier = Modifier,
    cityName: String = "Bengaluru"
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val cannotOpenText = stringResource(R.string.civic_cannot_open)

    fun guardedStart(intent: Intent) {
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, cannotOpenText, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, cannotOpenText, Toast.LENGTH_SHORT).show()
        }
    }

    val hasLocation = userLat != null && userLon != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.civic_report_title_fmt, cityName),
                onClick = { expanded = !expanded }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.civic_report_title_fmt, cityName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    if (!expanded) {
                        Text(
                            text = stringResource(R.string.civic_tap_to_report),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.civic_report_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!hasLocation) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.civic_no_location),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // WhatsApp requires a real GPS fix; hidden otherwise.
                        if (hasLocation) {
                            Button(
                                onClick = {
                                    val message = "WasegMul Waste Grievance Report\nDetected: $subclass ($category)\nGPS: https://maps.google.com/?q=$userLat,$userLon\n\nThere is improperly disposed waste at this location. Kindly arrange clearance."
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse(
                                            "https://wa.me/${CivicContacts.WHATSAPP_NUMBER}?text=${Uri.encode(message)}"
                                        )
                                    }
                                    guardedStart(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(stringResource(R.string.civic_whatsapp))
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                guardedStart(
                                    Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.parse("tel:${CivicContacts.GBA_HELPLINE}")
                                    )
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        ) {
                            Text(stringResource(R.string.civic_call_gba))
                        }

                        OutlinedButton(
                            onClick = {
                                guardedStart(
                                    Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.parse("tel:${CivicContacts.BBMP_CONTROL_ROOM}")
                                    )
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        ) {
                            Text(stringResource(R.string.civic_bbmp))
                        }

                        if (category.equals("E-Waste", ignoreCase = true)) {
                            OutlinedButton(
                                onClick = {
                                    guardedStart(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:${CivicContacts.SAAHAS_EWASTE}")
                                        )
                                    )
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                            ) {
                                Text(stringResource(R.string.civic_saahas))
                            }
                        }

                        if (category.equals("Recyclable", ignoreCase = true)) {
                            OutlinedButton(
                                onClick = {
                                    guardedStart(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:${CivicContacts.HASIRU_DALA}")
                                        )
                                    )
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                            ) {
                                Text(stringResource(R.string.civic_hasiru))
                            }
                        }
                        Text(
                            text = stringResource(R.string.civic_more_cities, cityName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
