package com.agrelius.wasegmul.ui.home

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.WasegMulApp
import com.agrelius.wasegmul.ui.components.*
import com.agrelius.wasegmul.ui.theme.*
import com.agrelius.wasegmul.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onImageSelected: (Bitmap) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val soundManager = (context.applicationContext as WasegMulApp).soundManager
    val recentHistory by viewModel.recentHistory.collectAsState()
    var showImpactDetail by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    
    val totalImpact by remember(recentHistory) {
        derivedStateOf { recentHistory.sumOf { it.estimatedWeight } }
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            soundManager.playTick()
            val source = ImageDecoder.createSource(context.contentResolver, it)
            val bitmap = ImageDecoder.decodeBitmap(source)
            onImageSelected(bitmap.copy(Bitmap.Config.ARGB_8888, true))
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            soundManager.playTick()
            onImageSelected(it.copy(Bitmap.Config.ARGB_8888, true))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(DarkBackground, DeepCharcoal)
                    )
                )
                .padding(innerPadding)
        ) {
            OrganicBackground() // Animated Leaf Pattern
            BackgroundGlows()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    IconButton(
                        onClick = {
                            soundManager.playTick()
                            onNavigateToSettings()
                        },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(1200)) + slideInVertically(tween(1200)) { -40 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppLogo(size = 140.dp)
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "WasegMul",
                            style = MaterialTheme.typography.displaySmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Waste Segregation Multi-model AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldVibrant,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Stats Dashboard
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QuickStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Carbon Offset",
                        value = "${totalImpact.format(3)}kg",
                        icon = Icons.Default.Public,
                        containerColor = OliveDeep,
                        onClick = {
                            soundManager.playTick()
                            showImpactDetail = true
                        }
                    )
                    QuickStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Neural Scans",
                        value = recentHistory.size.toString(),
                        icon = Icons.Default.Dataset,
                        containerColor = MossEarthy,
                        onClick = {
                            soundManager.playTick()
                            onNavigateToHistory()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                YoloEntryButton(onClick = { soundManager.playWarning() })

                Spacer(modifier = Modifier.height(24.dp))

                HeroCard {
                    Text(
                        text = "Industrial material analysis powered by EfficientNet architecture. Identify material signatures with precise chemical confidence.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                GradientActionButton(
                    text = "Launch Scanner",
                    icon = Icons.Default.CameraAlt,
                    onClick = {
                        soundManager.playTick()
                        permissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    containerColor = ForestGreen
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Redesigned Device Import Action
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceGray.copy(alpha = 0.9f),
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Icon(Icons.Default.Collections, contentDescription = null, tint = SageGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Import from Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                if (recentHistory.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "LATEST ACTIVITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldVibrant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                        )
                        recentHistory.take(3).forEach { record ->
                            RecentItem(
                                name = record.subclass,
                                time = record.timestamp.toRelativeTime(),
                                type = record.category,
                                feedback = record.feedback
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))

                Text(
                    text = "V 2.2.0 • agrelius industrial AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (showImpactDetail) {
                ImpactDetailDialog(
                    onDismiss = { showImpactDetail = false },
                    history = recentHistory
                )
            }
        }
    }
}

@Composable
fun ImpactDetailDialog(onDismiss: () -> Unit, history: List<com.agrelius.wasegmul.data.WasteRecord>) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impact Analysis", color = EmeraldVibrant) },
        text = {
            Column {
                Text(
                    "Weight-indexed material recovery data. Each gram represents direct landfill diversion.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(20.dp))
                
                val groups = history.groupBy { it.category }
                groups.forEach { (cat, items) ->
                    val weight = items.sumOf { it.estimatedWeight }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(cat, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SageGreen)
                        Text("${weight.format(3)} kg", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = GlassBorder
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cumulative Recovery", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                    Text("${history.sumOf { it.estimatedWeight }.format(3)} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = EmeraldVibrant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ACKNOWLEDGE", color = ForestGreen) }
        },
        containerColor = DeepCharcoal,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun YoloEntryButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(listOf(MossEarthy.copy(alpha = 0.3f), DeepCharcoal))
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(EmeraldVibrant.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = EmeraldVibrant)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    "Real-time YOLO Detect",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "Launch live multi-object tracking",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

fun Long.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(this))
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

@Composable
fun RecentItem(name: String, time: String, type: String, feedback: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DeepCharcoal.copy(alpha = 0.4f))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = time, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                if (feedback != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (feedback == "correct") Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (feedback == "correct") EmeraldVibrant else LowConfidence
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(EmeraldVibrant.copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = type, style = MaterialTheme.typography.labelSmall, color = EmeraldVibrant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QuickStatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, containerColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor.copy(alpha = 0.15f))
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .clickable(onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            })
            .padding(20.dp)
    ) {
        Column {
            Icon(imageVector = icon, contentDescription = null, tint = EmeraldVibrant.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = TextPrimary)
            Text(text = title.uppercase(), style = MaterialTheme.typography.labelSmall, color = SageGreen, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun BackgroundGlows() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.TopEnd)
                .offset(x = 150.dp, y = (-100).dp)
                .background(Brush.radialGradient(colors = listOf(ForestGreen.copy(alpha = 0.08f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(500.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-200).dp, y = 150.dp)
                .background(Brush.radialGradient(colors = listOf(OchreSand.copy(alpha = 0.05f), Color.Transparent)))
        )
    }
}
