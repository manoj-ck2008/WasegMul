package com.agrelius.wasegmul.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.agrelius.wasegmul.BuildConfig
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.utils.ThemeMode
import kotlinx.coroutines.launch
import com.agrelius.wasegmul.viewmodel.HomeViewModel
import kotlin.math.roundToInt

import android.os.Build
import androidx.compose.ui.tooling.preview.Preview
import com.agrelius.wasegmul.ui.theme.WasegMulTheme
import com.agrelius.wasegmul.ui.theme.AppThemeMode
import com.agrelius.wasegmul.ui.theme.appThemeSwatches

@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as? com.agrelius.wasegmul.WasegMulApp
    if (app == null) {
        // Error UI instead of a blank screen on a bad Application cast.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.home_app_error),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) { Text(stringResource(R.string.common_go_back)) }
            }
        }
        return
    }
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(app.settingsManager)
    )

    val themeMode by settingsViewModel.themeMode.collectAsState()
    val dynamicColor by settingsViewModel.dynamicColor.collectAsState()
    val confidenceThreshold by settingsViewModel.confidenceThreshold.collectAsState()
    val hapticsEnabled by settingsViewModel.hapticsEnabled.collectAsState()
    val homeError by homeViewModel.error.collectAsState()

    SettingsContent(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        confidenceThreshold = confidenceThreshold,
        hapticsEnabled = hapticsEnabled,
        homeError = homeError,
        onHomeErrorConsumed = { homeViewModel.clearError() },
        onThemeModeChange = { settingsViewModel.setThemeMode(it) },
        onDynamicColorChange = { settingsViewModel.setDynamicColor(it) },
        onConfidenceThresholdChange = { settingsViewModel.setConfidenceThreshold(it) },
        onHapticsChange = { settingsViewModel.setHapticsEnabled(it) },
        onResetDefaults = { settingsViewModel.resetToDefaults() },
        onPurgeHistory = { onComplete ->
            homeViewModel.clearHistory(onComplete)
        },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    confidenceThreshold: Float,
    hapticsEnabled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onConfidenceThresholdChange: (Float) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onPurgeHistory: (onComplete: () -> Unit) -> Unit,
    onBack: () -> Unit,
    homeError: String? = null,
    onHomeErrorConsumed: () -> Unit = {},
    onResetDefaults: () -> Unit = {}
) {
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showLicensesDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val purgeFailedText = stringResource(R.string.settings_purge_failed)
    val resetDoneText = stringResource(R.string.settings_reset_done)

    // Purge failures were silent; surface HomeViewModel errors here.
    LaunchedEffect(homeError) {
        homeError?.let {
            snackbarHostState.showSnackbar(it.ifBlank { purgeFailedText })
            onHomeErrorConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.settings_interface_preference),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .selectableGroup()
                ) {
                    listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.COLOUR, ThemeMode.SYSTEM).forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = themeMode == mode,
                                    role = Role.RadioButton,
                                    onClick = { onThemeModeChange(mode) }
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = null
                            )
                            Text(
                                text = when(mode) {
                                    ThemeMode.DARK -> stringResource(R.string.settings_dark_mode)
                                    ThemeMode.LIGHT -> stringResource(R.string.settings_light_mode)
                                    ThemeMode.COLOUR -> stringResource(R.string.settings_colour_mode)
                                    ThemeMode.SYSTEM -> stringResource(R.string.settings_system_default)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // Systematic swatches (primary / secondary / tertiary)
                            // from the single appThemeSwatches source, so the
                            // picker preview can never drift from the scheme.
                            // SYSTEM follows the OS; no fixed swatch to show.
                            val swatchMode = when (mode) {
                                ThemeMode.DARK -> AppThemeMode.DARK
                                ThemeMode.LIGHT -> AppThemeMode.LIGHT
                                ThemeMode.COLOUR -> AppThemeMode.COLOUR
                                else -> null
                            }
                            if (swatchMode != null) {
                                Spacer(modifier = Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    appThemeSwatches(swatchMode).forEach { swatch ->
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_dynamic_color),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_dynamic_color_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = onDynamicColorChange
                            )
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.settings_theme_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                stringResource(R.string.settings_detection_accuracy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Debounced slider: drag updates local state only; the
                    // ViewModel (DataStore) is hit once on release, coerced.
                    var sliderValue by remember(confidenceThreshold) {
                        mutableStateOf(confidenceThreshold)
                    }
                    Text(
                        text = stringResource(R.string.settings_confidence_threshold, (sliderValue * 100).roundToInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_confidence_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it.coerceIn(0.20f, 0.90f) },
                        onValueChangeFinished = { onConfidenceThresholdChange(sliderValue) },
                        valueRange = 0.20f..0.90f,
                        steps = 13
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_haptics),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_haptics_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hapticsEnabled,
                            onCheckedChange = onHapticsChange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                stringResource(R.string.settings_data_archive),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.settings_purge_history))
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    onResetDefaults()
                    scope.launch { snackbarHostState.showSnackbar(resetDoneText) }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.settings_reset_defaults))
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                stringResource(R.string.settings_legal_section),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedButton(
                onClick = { showLicensesDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.settings_licenses_button))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.settings_off_attribution_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Open Food Facts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_off_attribution_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                uriHandler.openUri("https://world.openfoodfacts.org")
                            } catch (e: Exception) {
                                android.util.Log.e("SettingsScreen", "Failed to open Open Food Facts URL", e)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_off_visit_button))
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.settings_purge_title)) },
                text = { Text(stringResource(R.string.settings_purge_message)) },
                confirmButton = {
                    val purgeSnackbarText = stringResource(R.string.settings_purged_snackbar)
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onPurgeHistory {
                            scope.launch {
                                snackbarHostState.showSnackbar(purgeSnackbarText)
                            }
                        }
                    }) {
                        Text(stringResource(R.string.settings_execute_purge), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.common_abort))
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (showLicensesDialog) {
            AlertDialog(
                onDismissRequest = { showLicensesDialog = false },
                title = { Text(stringResource(R.string.settings_licenses_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "WasegMul Application",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            "Licensed under Apache License 2.0.\n",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            "TACO Dataset",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            "Pedro F. Proença and Pedro M. Simões (2020).\nLicensed under Creative Commons Attribution 4.0 International (CC BY 4.0).\n",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            "TrashNet Dataset",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            "Gary Thung and Mindy Yang (2016).\nLicensed under MIT License.\n",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            "YOLOv8 & Ultralytics",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            "Ultralytics YOLOv8 is licensed under AGPL-3.0.\nComplete source code and notices are available in the project repository under NOTICE and docs/ATTRIBUTION.md.\n",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            "Open Food Facts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            "Product packaging data provided by Open Food Facts.\nLicensed under the Open Database License (ODbL) v1.0.\nhttps://world.openfoodfacts.org",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLicensesDialog = false }) {
                        Text(stringResource(R.string.settings_close))
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

@Preview(name = "SettingsScreen Preview", showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    WasegMulTheme {
        SettingsContent(
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            confidenceThreshold = 0.50f,
            hapticsEnabled = true,
            onThemeModeChange = {},
            onDynamicColorChange = {},
            onConfidenceThresholdChange = {},
            onHapticsChange = {},
            onPurgeHistory = {},
            onBack = {}
        )
    }
}

