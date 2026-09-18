package com.agrelius.wasegmul.ui.result

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ClassificationViewModel,
    onNavigateToHome: () -> Unit,
    onBack: () -> Unit = {},
    // True when navigated with no recordId (recordId == -1): render the
    // error state instead of an indeterminate spinner.
    invalidRecordId: Boolean = false
) {
    val context = LocalContext.current
    val app = context.applicationContext as? com.agrelius.wasegmul.WasegMulApp
    val result by viewModel.classificationResult.collectAsState()
    val record by viewModel.currentRecord.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showContent by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val userThreshold by app?.settingsManager?.confidenceThreshold?.collectAsState(initial = 0.70f)
        ?: remember { mutableStateOf(0.70f) }
    val hapticsEnabled by app?.settingsManager?.hapticsEnabled?.collectAsState(initial = true)
        ?: remember { mutableStateOf(true) }

    val isFreshScan by viewModel.isFreshScan.collectAsState()
    val lastXpGain by viewModel.lastXpGain.collectAsState()
    // Celebration gating (consume-once, rotation-safe): uncertain / unknown /
    // zero-XP scans never celebrate. Dismissal is persisted per record id so
    // rotation cannot replay overlays.
    val isUncertainRecord = record?.let {
        it.category == WasteMapping.UNCERTAIN || it.category == WasteMapping.UNKNOWN
    } ?: (result?.category == WasteMapping.UNCERTAIN || result?.category == WasteMapping.UNKNOWN)
    val xpEarned = lastXpGain?.xpEarned ?: 0
    val canCelebrate = isFreshScan && !isUncertainRecord && xpEarned > 0
    var thankYouDismissed by rememberSaveable(record?.id) { mutableStateOf(false) }
    var levelUpDismissed by rememberSaveable(record?.id) { mutableStateOf(false) }
    val showThankYou = canCelebrate && !thankYouDismissed
    val showLevelUp = !showThankYou && lastXpGain?.didLevelUp == true &&
        !levelUpDismissed && !isUncertainRecord && xpEarned > 0

    fun dismissThankYou() {
        thankYouDismissed = true
        if (lastXpGain?.didLevelUp != true) viewModel.consumeXpGain()
        viewModel.consumeFreshScan()
    }
    fun dismissLevelUp() {
        levelUpDismissed = true
        viewModel.consumeXpGain()
        viewModel.consumeFreshScan()
    }
    // System back dismisses a visible overlay first (TalkBack-consistent).
    BackHandler(enabled = showThankYou || showLevelUp) {
        if (showThankYou) dismissThankYou() else dismissLevelUp()
    }

    // History image: Coil is not a dependency (build files frozen), so decode
    // the persisted imagePath off-Main. Captured bitmap wins when present.
    val recordImagePath = record?.imagePath
    val recordBitmap by produceState<Bitmap?>(initialValue = null, key1 = recordImagePath) {
        value = if (recordImagePath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.Options().run {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        BitmapFactory.decodeFile(recordImagePath, this)
                    }
                }.getOrNull()
            }
        }
    }
    val displayBitmap = capturedBitmap ?: recordBitmap

    val allCenters = remember(context) { DisposalDatabase.loadCenters(context) }
    var nearbyCenters by remember { mutableStateOf<List<NearbyCenterMatch>>(emptyList()) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var locationRequested by rememberSaveable { mutableStateOf(false) }
    val usingDefaultLocation = userLocation == null

    fun fetchLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) userLocation = location
            }
        } catch (_: Exception) {
            // Ignored: default-location disclosure covers the fallback.
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants.values.any { it }
        locationRequested = true
        if (grants.values.any { it }) fetchLocation()
    }

    LaunchedEffect(Unit) {
        showContent = true
        if (hasLocationPermission) {
            fetchLocation()
        } else if (!locationRequested) {
            locationRequested = true
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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

    // Timeout for the null-result state (no spinner-forever).
    var loadTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(result, error, invalidRecordId) {
        loadTimedOut = false
        if (result == null && error == null && !invalidRecordId) {
            delay(8000)
            loadTimedOut = true
        }
    }
    val loadingText = stringResource(R.string.common_processing)

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
                        // Single-parser rule for the share sheet.
                        val display = parseBarcodeDisplay(
                            record?.source, record?.barcode,
                            record?.productName, record?.featureVector
                        )
                        val productName = display?.productName
                        val barcodeCode = display?.code?.takeIf { it.isNotBlank() }

                        val shareSubject = if (productName != null) {
                            "$productName - ${r.subclass} (${r.category})"
                        } else {
                            context.getString(R.string.result_share_subject, r.subclass, r.category)
                        }
                        val shareText = buildString {
                            appendLine(context.getString(R.string.result_share_header))
                            if (productName != null) {
                                appendLine("Product: $productName")
                                if (!barcodeCode.isNullOrBlank()) {
                                    appendLine("Barcode: $barcodeCode")
                                }
                            }
                            appendLine("Item: ${humanizeLabel(r.subclass)}")
                            appendLine("Category: ${r.category}")
                            appendLine("Confidence: ${(r.confidence.coerceIn(0f, 1f) * 100f).roundToInt()}%")
                            if (WasteMapping.isHazardous(r.subclass)) {
                                appendLine()
                                appendLine(context.getString(R.string.result_share_hazard))
                            }
                            appendLine()
                            appendLine("${context.getString(R.string.result_disposal_protocol)}:")
                            appendLine(r.disposalGuide)
                            appendLine()
                            appendLine("${context.getString(R.string.result_ecological_footprint)}:")
                            appendLine(r.environmentalImpact)
                            appendLine()
                            appendLine("${context.getString(R.string.result_recycling_benefits)}:")
                            appendLine(r.recyclingBenefits)
                            appendLine()
                            appendLine("${context.getString(R.string.result_verification_sources)}:")
                            appendLine(r.sources)
                            appendLine()
                            appendLine(context.getString(R.string.result_share_footer))
                        }

                        IconButton(onClick = {
                            try {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(
                                    sendIntent,
                                    context.getString(R.string.result_share_title)
                                )
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
            OrganicBackground(animate = !reduceMotion)

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
                            displayBitmap?.let { bmp ->
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
                                        val display = parseBarcodeDisplay(
                                            record?.source, record?.barcode,
                                            record?.productName, record?.featureVector
                                        )
                                        val barcodeCode = display?.code?.takeIf { it.isNotBlank() }
                                        val prodName = display?.productName

                                        if (display != null) {
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

                                        if (prodName != null) {
                                            Text(
                                                text = prodName,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${humanizeLabel(r.subclass)} (${r.category})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.5.sp
                                            )
                                        } else {
                                            Text(
                                                text = humanizeLabel(r.subclass),
                                                style = MaterialTheme.typography.displaySmall,
                                                fontWeight = FontWeight.Black,
                                                color = if (isUncertain) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = if (isUncertain) stringResource(R.string.result_low_confidence_match)
                                                    else r.category.uppercase(Locale.ROOT),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUncertain) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp
                                            )
                                        }
                                    }
                                    ConfidenceBadge(
                                        confidence = r.confidence,
                                        userThreshold = userThreshold
                                    )
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
                                                contentDescription = stringResource(R.string.result_cd_hazard),
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
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = stringResource(R.string.result_cd_hazard),
                                            tint = MaterialTheme.colorScheme.error
                                        )
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

                            // Location honesty: request access; while the fix is
                            // unknown, disclose the default instead of titling
                            // it "Nearest".
                            if (usingDefaultLocation) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.LocationOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (!hasLocationPermission && locationRequested) {
                                                stringResource(R.string.result_location_denied)
                                            } else {
                                                stringResource(R.string.result_default_location)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!hasLocationPermission) {
                                            TextButton(
                                                onClick = {
                                                    locationLauncher.launch(
                                                        arrayOf(
                                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                                        )
                                                    )
                                                }
                                            ) {
                                                Text(stringResource(R.string.result_enable_location))
                                            }
                                        }
                                    }
                                }
                            }

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
                                                    text = humanizeLabel(label),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${(conf * 100f).roundToInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
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
                                hapticsEnabled = hapticsEnabled,
                                onFeedbackSelected = { feedback ->
                                    viewModel.setFeedback(feedback)
                                },
                                onCorrectionSelected = { correctedSubclass ->
                                    viewModel.setCorrection(correctedSubclass)
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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(
                                        stringResource(R.string.result_acknowledge_close),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(48.dp))
                        }
                        }
                    }
                } else {
                    // Bounded content in the scroll (no fillMaxSize-in-scroll).
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp)
                            .semantics { contentDescription = loadingText },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            invalidRecordId -> {
                                ResultErrorState(
                                    message = stringResource(R.string.result_invalid_record),
                                    onBack = onBack
                                )
                            }
                            error != null -> {
                                ResultErrorState(
                                    message = error.orEmpty(),
                                    onBack = onBack
                                )
                            }
                            loadTimedOut -> {
                                ResultErrorState(
                                    message = stringResource(R.string.result_load_timeout),
                                    onBack = onBack
                                )
                            }
                            else -> {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

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
                onDismiss = { dismissThankYou() }
            )
        }
    } else if (showLevelUp) {
        lastXpGain?.let { gain ->
            LevelUpOverlay(
                newLevel = gain.newLevel,
                xpEarned = gain.xpEarned,
                onDismiss = { dismissLevelUp() }
            )
        }
    }
} // ends Box(fillMaxSize)
} // ends ResultScreen

@Composable
private fun ResultErrorState(
    message: String,
    onBack: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text(stringResource(R.string.common_go_back)) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Result Loading Preview", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ResultLoadingPreview() {
    WasegMulTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
