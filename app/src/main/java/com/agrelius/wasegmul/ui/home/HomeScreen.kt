package com.agrelius.wasegmul.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.agrelius.wasegmul.BuildConfig
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.EcoImpactCalculator
import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.ui.components.*
import com.agrelius.wasegmul.ui.theme.*
import com.agrelius.wasegmul.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onImageSelected: (Bitmap) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToYolo: () -> Unit = {},
    onNavigateToGuide: () -> Unit = {},
    onNavigateToBarcode: () -> Unit = {}
) {
    val context = LocalContext.current
    val recentHistory by viewModel.recentHistory.collectAsState()
    val allHistory by viewModel.allHistory.collectAsState()
    var showImpactDetail by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var section1Visible by remember { mutableStateOf(false) }
    var section2Visible by remember { mutableStateOf(false) }
    var section3Visible by remember { mutableStateOf(false) }
    var section4Visible by remember { mutableStateOf(false) }

    val totalImpact = remember(allHistory) {
        // Show real cumulative CO2 prevented across all scans using full EcoImpact logic.
        EcoImpactCalculator.calculate(allHistory).co2PreventedKg
    }

    LaunchedEffect(Unit) {
        section1Visible = true
        kotlinx.coroutines.delay(150)
        section2Visible = true
        kotlinx.coroutines.delay(150)
        section3Visible = true
        kotlinx.coroutines.delay(150)
        section4Visible = true
    }

    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeSampledBitmap(context, it, 1024, 1024)
                }
                if (bitmap != null) {
                    onImageSelected(bitmap)
                }
            }
        }
    }

    var tempPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val fullCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempPhotoUri?.let { uri ->
                scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        decodeSampledBitmap(context, uri, 1024, 1024)
                    }
                    if (bitmap != null) {
                        onImageSelected(bitmap)
                    }
                }
            }
        }
    }

    fun launchCamera() {
        try {
            val photoFile = java.io.File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempPhotoUri = uri
            fullCameraLauncher.launch(uri)
        } catch (e: Exception) {
            android.util.Log.e("HomeScreen", "Failed to launch camera via FileProvider, falling back to gallery", e)
            galleryLauncher.launch("image/*")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            showPermissionRationale = true
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
                        listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                    )
                )
                .padding(innerPadding)
        ) {
            OrganicBackground()
            BackgroundGlows()

            if (showPermissionRationale) {
                AlertDialog(
                    onDismissRequest = { showPermissionRationale = false },
                    title = { Text("Camera Permission Required") },
                    text = { Text("Camera access is needed to scan and classify waste items. Please grant the permission to use the scanner.") },
                    confirmButton = {
                        TextButton(onClick = { showPermissionRationale = false; permissionLauncher.launch(android.Manifest.permission.CAMERA) }) {
                            Text("Try Again")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermissionRationale = false; galleryLauncher.launch("image/*") }) {
                            Text("Use Gallery Instead")
                        }
                    }
                )
            }

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
                            onNavigateToSettings()
                        },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedVisibility(
                    visible = section1Visible,
                    enter = fadeIn(tween(800)) + slideInVertically(tween(800, easing = FastOutSlowInEasing)) { 30 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppLogo(size = 150.dp)
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = "WasegMul",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                AnimatedVisibility(
                    visible = section2Visible,
                    enter = fadeIn(tween(700)) + slideInVertically(tween(700, easing = FastOutSlowInEasing)) { 30 }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    GlassCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.clickable {
                            showImpactDetail = true
                        }) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "${totalImpact.format(3)}kg", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = stringResource(R.string.home_carbon_offset), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        }
                    }
                    GlassCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.clickable {
                            onNavigateToHistory()
                        }) {
                            Icon(Icons.Default.Dataset, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = recentHistory.size.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = stringResource(R.string.home_neural_scans), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        }
                    }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedVisibility(
                    visible = section3Visible,
                    enter = fadeIn(tween(700)) + slideInVertically(tween(700, easing = FastOutSlowInEasing)) { 30 }
                ) {
                    Column {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onNavigateToYolo() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    stringResource(R.string.home_yolo_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.home_yolo_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onNavigateToGuide() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    stringResource(R.string.home_guide_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.home_guide_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                HeroCard {
                    Text(
                        text = stringResource(R.string.home_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                AnimatedVisibility(
                    visible = section4Visible,
                    enter = fadeIn(tween(700)) + slideInVertically(tween(700, easing = FastOutSlowInEasing)) { 30 }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                    GradientActionButton(
                        text = stringResource(R.string.home_launch_scanner),
                        icon = Icons.Default.CameraAlt,
                        onClick = {
                            permissionLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FilledTonalButton(
                        onClick = onNavigateToBarcode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = BorderStroke(1.dp, LocalGlassColors.current.border)
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.home_scan_barcode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, LocalGlassColors.current.border)
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.home_import_device),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    if (recentHistory.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = stringResource(R.string.home_latest_activity),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                            )
                            recentHistory.take(3).forEach { record ->
                                val isBarcodeScan = record.featureVector?.startsWith("barcode:") == true
                                val barcodeMeta = if (isBarcodeScan) record.featureVector?.removePrefix("barcode:") else null
                                val barcodeParts = barcodeMeta?.split("|", limit = 2)
                                val productName = barcodeParts?.getOrNull(1)?.takeIf { it.isNotBlank() }
                                val displayName = productName ?: record.subclass
                                RecentItem(
                                    name = displayName,
                                    time = record.timestamp.toRelativeTime(),
                                    type = record.category,
                                    feedback = record.feedback,
                                    isBarcode = isBarcodeScan
                                )
                            }
                        }
                    }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = stringResource(R.string.app_version_info, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (showImpactDetail) {
                ImpactDetailDialog(
                    onDismiss = { showImpactDetail = false },
                    history = allHistory
                )
            }
        }
    }
}

@Composable
fun ImpactDetailDialog(onDismiss: () -> Unit, history: List<WasteRecord>) {
    val metrics = remember(history) { EcoImpactCalculator.calculate(history) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Eco,
                    contentDescription = null,
                    tint = ForestGreen,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.home_impact_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.home_impact_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Metric Cards Grid (2x3)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImpactStatCard(
                        title = stringResource(R.string.impact_metric_co2),
                        value = "${metrics.co2PreventedKg.format(2)} kg",
                        icon = Icons.Default.Cloud,
                        iconTint = ForestGreen,
                        modifier = Modifier.weight(1f)
                    )
                    ImpactStatCard(
                        title = stringResource(R.string.impact_metric_water),
                        value = "${metrics.waterSavedLiters.format(1)} L",
                        icon = Icons.Default.WaterDrop,
                        iconTint = SkyBlueDeep,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImpactStatCard(
                        title = stringResource(R.string.impact_metric_energy),
                        value = "${metrics.energySavedKwh.format(2)} kWh",
                        icon = Icons.Default.Bolt,
                        iconTint = Color(0xFFFFB300),
                        modifier = Modifier.weight(1f)
                    )
                    ImpactStatCard(
                        title = stringResource(R.string.impact_metric_trees),
                        value = "${metrics.treeYearEquivalent.format(2)} yr",
                        icon = Icons.Default.Park,
                        iconTint = HighConfidenceGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImpactStatCard(
                        title = stringResource(R.string.impact_metric_items),
                        value = "${metrics.totalItems}",
                        icon = Icons.Default.Numbers,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    val accuracy = metrics.accuracyPercentage
                    ImpactStatCard(
                        title = stringResource(R.string.impact_metric_accuracy),
                        value = if (accuracy != null) "${accuracy.toInt()}%" else "-",
                        icon = Icons.Default.CheckCircle,
                        iconTint = MintAccent,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = LocalGlassColors.current.border)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "MATERIAL BREAKDOWN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val groups = history.groupBy { it.category }
                groups.forEach { (cat, items) ->
                    val weight = items.sumOf { it.estimatedWeight }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(cat, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("${weight.format(3)} kg (${items.size})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = LocalGlassColors.current.border
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.home_cumulative_recovery), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                    Text("${metrics.totalWeightKg.format(3)} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_acknowledge), color = MaterialTheme.colorScheme.tertiary) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun ImpactStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, LocalGlassColors.current.border, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun Long.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = (now - this).coerceAtLeast(0L)
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(this))
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(Locale.US, this)

fun Long.toIso8601(): String {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(this))
}

fun String.csvEscape(): String = "\"${replace("\"", "\"\"")}\""

@Composable
fun RecentItem(
    name: String,
    time: String,
    type: String,
    feedback: String?,
    isBarcode: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .border(1.dp, LocalGlassColors.current.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBarcode) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (feedback != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (feedback == "correct") Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (feedback == "correct") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BackgroundGlows() {
    val primaryColor = MaterialTheme.colorScheme.primary
    val topGlow = remember(primaryColor) { Brush.radialGradient(colors = listOf(primaryColor.copy(alpha = 0.10f), Color.Transparent)) }
    val bottomGlow = remember(primaryColor) { Brush.radialGradient(colors = listOf(primaryColor.copy(alpha = 0.06f), Color.Transparent)) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.TopEnd)
                .offset(x = 120.dp, y = (-80).dp)
                .background(topGlow)
        )
        Box(
            modifier = Modifier
                .size(500.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-180).dp, y = 120.dp)
                .background(bottomGlow)
        )
    }
}

private fun decodeSampledBitmap(context: android.content.Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = android.media.ExifInterface(stream)
                exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
            } ?: android.media.ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            android.media.ExifInterface.ORIENTATION_NORMAL
        }

        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(rawBitmap, 90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(rawBitmap, 180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(rawBitmap, 270f)
            else -> rawBitmap
        }
    } catch (e: Exception) {
        android.util.Log.e("HomeScreen", "Failed to decode sampled bitmap", e)
        null
    }
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

@androidx.compose.ui.tooling.preview.Preview(name = "RecentItem Preview", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun RecentItemPreview() {
    com.agrelius.wasegmul.ui.theme.WasegMulTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            RecentItem(
                name = "cardboard_box",
                time = "5m ago",
                type = "Recyclable",
                feedback = "correct"
            )
        }
    }
}

