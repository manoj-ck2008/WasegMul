package com.agrelius.wasegmul.ui.result

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.WasteMapping
import com.agrelius.wasegmul.ui.classify.ClassificationViewModel
import com.agrelius.wasegmul.ui.components.*
import com.agrelius.wasegmul.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.agrelius.wasegmul.EcoImpactCalculator
import com.agrelius.wasegmul.data.disposal.DisposalDatabase
import com.agrelius.wasegmul.data.disposal.NearbyCenterMatch
import com.agrelius.wasegmul.ui.result.CivicReportingBanner
import com.agrelius.wasegmul.ui.result.DecayTimeSection
import com.agrelius.wasegmul.ui.result.DisposalLocatorSection
import com.agrelius.wasegmul.ui.result.ThankYouOverlay
import com.google.android.gms.location.LocationServices
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ClassificationViewModel,
    onNavigateToHome: () -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.classificationResult.collectAsState()
    val record by viewModel.currentRecord.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showContent by remember { mutableStateOf(false) }

    val isFreshScan by viewModel.isFreshScan.collectAsState()
    val lastXpGain by viewModel.lastXpGain.collectAsState()
    var showThankYou by remember(isFreshScan) { mutableStateOf(isFreshScan) }
    var showLevelUp by remember(lastXpGain) { mutableStateOf(lastXpGain?.didLevelUp == true) }

    val allCenters = remember(context) { DisposalDatabase.loadCenters(context) }
    var nearbyCenters by remember { mutableStateOf<List<NearbyCenterMatch>>(emptyList()) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    
    LaunchedEffect(Unit) {
        showContent = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        userLocation = location
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(result, userLocation, allCenters) {
        val cat = result?.category ?: return@LaunchedEffect
        val lat = userLocation?.latitude ?: 12.9716
        val lng = userLocation?.longitude ?: 77.5946
        nearbyCenters = DisposalDatabase.findNearest(allCenters, lat, lng, cat, limit = 3)
    }

    LaunchedEffect(error) {
        error?.let { msg ->
            if (result != null) {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearError()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.result_title), 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    result?.let { r ->
                        val isBarcode = record?.featureVector?.startsWith("barcode:") == true
                        val barcodeMeta = if (isBarcode) record?.featureVector?.removePrefix("barcode:") else null
                        val barcodeParts = barcodeMeta?.split("|", limit = 2)
                        val barcodeCode = barcodeParts?.getOrNull(0)
                        val productName = barcodeParts?.getOrNull(1)?.takeIf { it.isNotBlank() }

                        val shareSubject = if (productName != null) {
                            "$productName - ${r.subclass} (${r.category})"
                        } else {
                            stringResource(R.string.result_share_subject, r.subclass, r.category)
                        }
                        val shareText = buildString {
                            appendLine("🌿 WasegMul Waste Analysis Report")
                            appendLine("═══════════════════════════════")
                            if (productName != null) {
                                appendLine("Product: $productName")
                                if (!barcodeCode.isNullOrBlank()) {
                                    appendLine("Barcode: $barcodeCode")
                                }
                            }
                            appendLine("Item: ${r.subclass}")
                            appendLine("Category: ${r.category}")
                            appendLine("Confidence: ${(r.confidence.coerceIn(0f, 1f) * 100f).roundToInt()}%")
                            if (WasteMapping.isHazardous(r.subclass)) {
                                appendLine("\n⚠️ HAZARDOUS MATERIAL ALERT: Requires specialist drop-off!")
                            }
                            appendLine("\n📋 Disposal Protocol:")
                            appendLine(r.disposalGuide)
                            appendLine("\n🌍 Ecological Footprint:")
                            appendLine(r.environmentalImpact)
                            appendLine("\n♻️ Recycling Benefits:")
                            appendLine(r.recyclingBenefits)
                            appendLine("\n📚 Verification Sources:")
                            appendLine(r.sources)
                            appendLine("\nClassified on-device with WasegMul AI.")
                        }

                        IconButton(onClick = {
                            try {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Waste Analysis")
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                android.util.Log.e("ResultShare", "Share failed", e)
                            }
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.result_share_report),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToHome) {
                        Icon(Icons.Default.Home, contentDescription = stringResource(R.string.common_home), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OrganicBackground()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                if (result != null) {
                    AnimatedVisibility(
                        visible = showContent,
                        enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { 50 }
                    ) {
                        result?.let { r ->
                        Column {
                            capturedBitmap?.let { bmp ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .padding(bottom = 12.dp),
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = stringResource(R.string.result_scanned_image),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            HeroCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val isUncertain = r.category == WasteMapping.UNCERTAIN ||
                                            r.category == WasteMapping.UNKNOWN
                                        val isBarcode = record?.featureVector?.startsWith("barcode:") == true
                                        val barcodeMeta = if (isBarcode) record?.featureVector?.removePrefix("barcode:") else null
                                        val barcodeParts = barcodeMeta?.split("|", limit = 2)
                                        val barcodeCode = barcodeParts?.getOrNull(0)
                                        val productName = barcodeParts?.getOrNull(1)?.takeIf { it.isNotBlank() }

                                        if (isBarcode) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.QrCodeScanner,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = barcodeCode ?: "BARCODE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        if (productName != null) {
                                            Text(
                                                text = productName,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${r.subclass.uppercase()} (${r.category.uppercase()})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.5.sp
                                            )
                                        } else {
                                            Text(
                                                text = r.subclass,
                                                style = MaterialTheme.typography.displaySmall,
                                                fontWeight = FontWeight.Black,
                                                color = if (isUncertain) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = if (isUncertain) stringResource(R.string.result_low_confidence_match) else r.category.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUncertain) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp
                                            )
                                        }
                                    }
                                    ConfidenceBadge(confidence = r.confidence)
                                }
                            }

                            if (WasteMapping.isHazardous(r.subclass)) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.result_hazard_title),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.result_hazard_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            if (r.category == WasteMapping.UNCERTAIN || r.category == WasteMapping.UNKNOWN) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = r.classificationMessage.ifBlank {
                                                stringResource(R.string.result_neural_variance)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            } else if (r.classificationMessage.isNotBlank()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                        Icon(
                                            Icons.Default.AutoGraph,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = r.classificationMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            SectionTitle(text = stringResource(R.string.result_environmental_insights))
                            
                            InsightCard(
                                title = stringResource(R.string.result_disposal_protocol),
                                content = r.disposalGuide,
                                icon = Icons.Default.VerifiedUser
                            )

                            InsightCard(
                                title = stringResource(R.string.result_ecological_footprint),
                                content = r.environmentalImpact,
                                icon = Icons.Default.AutoGraph
                            )

                            InsightCard(
                                title = stringResource(R.string.result_recycling_benefits),
                                content = r.recyclingBenefits,
                                icon = Icons.Default.Recycling
                            )

                            InsightCard(
                                title = stringResource(R.string.result_verification_sources),
                                content = r.sources,
                                icon = Icons.Default.Science
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            DisposalLocatorSection(
                                centers = nearbyCenters,
                                category = r.category
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            CivicReportingBanner(
                                category = r.category,
                                subclass = r.subclass,
                                userLat = userLocation?.latitude,
                                userLon = userLocation?.longitude,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Top model predictions + storage metadata.
                            if (r.topPredictions.isNotEmpty()) {
                                SectionTitle(text = stringResource(R.string.result_confidence_breakdown))
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        r.topPredictions.forEach { (label, conf) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${(conf * 100f).roundToInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                )
                                            }
                                        }
                                        if (record != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = if (record?.imagePath != null) stringResource(R.string.result_storage_image) else stringResource(R.string.result_storage_metadata),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            SectionTitle(text = stringResource(R.string.result_validation))
                            
                            FeedbackSection(
                                initialFeedback = record?.feedback,
                                initialCorrection = record?.correctedSubclass,
                                onFeedbackSelected = { feedback ->
                                    viewModel.setFeedback(feedback)
                                },
                                onCorrectionSelected = { correction ->
                                    viewModel.setCorrection(correction)
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            DecayTimeSection(subclass = r.subclass, category = r.category)

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.result_scan_another), fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = onNavigateToHome,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        stringResource(R.string.result_acknowledge_close),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (error != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 32.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = onBack) { Text(stringResource(R.string.common_go_back)) }
                            }
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } // ends Box inside else
                } // ends if/else
            } // ends Column
        } // ends Box(innerPadding)
    } // ends Scaffold
    
    if (showThankYou) {
        lastXpGain?.let { gain ->
            val impact = remember(record) {
                record?.let { EcoImpactCalculator.calculate(listOf(it)) }
            }
            val co2Grams = if (gain.co2PreventedGrams > 0.0) {
                gain.co2PreventedGrams
            } else {
                (impact?.co2PreventedKg ?: 0.0) * 1000.0
            }
            val waterMl = (impact?.waterSavedLiters ?: 0.0) * 1000.0

            ThankYouOverlay(
                xpEarned = gain.xpEarned,
                co2PreventedGrams = co2Grams,
                waterSavedMl = waterMl,
                onDismiss = {
                    showThankYou = false
                    if (!gain.didLevelUp) {
                        viewModel.consumeXpGain()
                    }
                }
            )
        }
    } else if (showLevelUp) {
        lastXpGain?.let { gain ->
            LevelUpOverlay(
                newLevel = gain.newLevel,
                xpEarned = gain.xpEarned,
                onDismiss = {
                    viewModel.consumeXpGain()
                    showLevelUp = false
                }
            )
        }
    }
} // ends Box(fillMaxSize)
} // ends ResultScreen

@androidx.compose.ui.tooling.preview.Preview(name = "Result Loading Preview", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ResultLoadingPreview() {
    WasegMulTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

