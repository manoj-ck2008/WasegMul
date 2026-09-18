package com.agrelius.wasegmul.ui.history

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.ui.home.toRelativeTime
import com.agrelius.wasegmul.ui.home.toIso8601
import com.agrelius.wasegmul.ui.home.csvEscape
import com.agrelius.wasegmul.viewmodel.HomeViewModel

import com.agrelius.wasegmul.WasteMapping
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyRow
import com.agrelius.wasegmul.ui.history.HistoryDashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HistorySortOrder {
    NEWEST, OLDEST, CONFIDENCE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onNavigateToResult: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allHistory by viewModel.allHistory.collectAsState()
    var editingRecord by remember { mutableStateOf<WasteRecord?>(null) }
    var recordToDelete by remember { mutableStateOf<WasteRecord?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var sortOrder by remember { mutableStateOf(HistorySortOrder.NEWEST) }
    val categories = listOf("All", "E-Waste", "Recyclable", "Organic", "Trash", "Hazardous")

    val filteredHistory = remember(allHistory, searchQuery, selectedCategory, sortOrder) {
        val filtered = allHistory.filter { record ->
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Hazardous" -> WasteMapping.isHazardous(record.subclass)
                else -> record.category.equals(selectedCategory, ignoreCase = true)
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val sq = searchQuery.trim()
                record.subclass.contains(sq, ignoreCase = true) ||
                record.category.contains(sq, ignoreCase = true) ||
                (record.correctedSubclass?.contains(sq, ignoreCase = true) == true) ||
                (record.featureVector?.contains(sq, ignoreCase = true) == true)
            }
            matchesCategory && matchesSearch
        }

        when (sortOrder) {
            HistorySortOrder.NEWEST -> filtered.sortedByDescending { it.timestamp }
            HistorySortOrder.OLDEST -> filtered.sortedBy { it.timestamp }
            HistorySortOrder.CONFIDENCE -> filtered.sortedByDescending { it.confidence }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (allHistory.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    // Clean up old cached export files
                                    context.cacheDir.listFiles { _, name -> name.startsWith("waseg_history_") }?.forEach { it.delete() }
                                    val csv = buildString {
                                        appendLine("ID,TimestampISO,TimestampEpoch,Subclass,Category,Confidence,WeightKg,Feedback,Correction")
                                        allHistory.forEach { rec ->
                                            appendLine(
                                                "${rec.id},${rec.timestamp.toIso8601()},${rec.timestamp}," +
                                                    "${rec.subclass.csvEscape()},${rec.category.csvEscape()}," +
                                                    "${"%.4f".format(java.util.Locale.US, rec.confidence)}," +
                                                    "${"%.4f".format(java.util.Locale.US, rec.estimatedWeight)}," +
                                                    "${rec.feedback.orEmpty().csvEscape()},${rec.correctedSubclass.orEmpty().csvEscape()}"
                                            )
                                        }
                                    }
                                    val file = java.io.File(context.cacheDir, "waseg_history_${System.currentTimeMillis()}.csv")
                                    file.writeText(csv)
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file
                                    )
                                    withContext(Dispatchers.Main) {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_SUBJECT, "WasegMul Waste History Export")
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            type = "text/csv"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Export History"))
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("HistoryExport", "Export failed", e)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.history_export_csv), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showClearAllConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.common_delete_all), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (allHistory.isEmpty()) {
                EmptyHistoryState()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    HistoryDashboard(allHistory = allHistory)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Search text field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.history_search_hint), style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Filter chips row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories.size) { index ->
                            val cat = categories[index]
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (cat == "Hazardous") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = if (cat == "Hazardous") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Sort order chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sort:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilterChip(
                            selected = sortOrder == HistorySortOrder.NEWEST,
                            onClick = { sortOrder = HistorySortOrder.NEWEST },
                            label = { Text(stringResource(R.string.history_sort_newest), style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = sortOrder == HistorySortOrder.OLDEST,
                            onClick = { sortOrder = HistorySortOrder.OLDEST },
                            label = { Text(stringResource(R.string.history_sort_oldest), style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = sortOrder == HistorySortOrder.CONFIDENCE,
                            onClick = { sortOrder = HistorySortOrder.CONFIDENCE },
                            label = { Text(stringResource(R.string.history_sort_confidence), style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.history_no_search_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            items(filteredHistory, key = { it.id }) { record ->
                                HistoryCard(
                                    record = record,
                                    onEditFeedback = { editingRecord = record },
                                    onDelete = { recordToDelete = record },
                                    onClick = { onNavigateToResult(record.id) }
                                )
                            }
                        }
                    }
                }
            }

            editingRecord?.let { recordToEdit ->
                FeedbackDialog(
                    record = recordToEdit,
                    onDismiss = { editingRecord = null },
                    onFeedbackSelected = { feedback, correction ->
                        viewModel.updateFeedback(recordToEdit.id, feedback)
                        if (correction != null) {
                            viewModel.updateCorrection(recordToEdit.id, correction)
                        }
                        editingRecord = null
                    }
                )
            }

            recordToDelete?.let { rec ->
                AlertDialog(
                    onDismissRequest = { recordToDelete = null },
                    title = { Text(stringResource(R.string.history_delete_item_title)) },
                    text = { Text(stringResource(R.string.history_delete_item_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteRecord(rec.id)
                            recordToDelete = null
                        }) {
                            Text(stringResource(R.string.common_delete_all), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { recordToDelete = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp)
                )
            }

            if (showClearAllConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearAllConfirm = false },
                    title = { Text(stringResource(R.string.history_clear_title)) },
                    text = { Text(stringResource(R.string.history_clear_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.clearHistory()
                            showClearAllConfirm = false
                        }) {
                            Text(stringResource(R.string.common_delete_all), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearAllConfirm = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp)
                )
            }
        }
    }
}

@Composable
fun HistoryCard(
    record: WasteRecord,
    onEditFeedback: () -> Unit,
    onDelete: () -> Unit = {},
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val isBarcode = record.featureVector?.startsWith("barcode:") == true
                val barcodeMeta = if (isBarcode) record.featureVector?.removePrefix("barcode:") else null
                val barcodeParts = barcodeMeta?.split("|", limit = 2)
                val barcodeCode = barcodeParts?.getOrNull(0)
                val productName = barcodeParts?.getOrNull(1)?.takeIf { it.isNotBlank() }

                if (isBarcode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = barcodeCode ?: "Barcode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = when {
                        productName != null -> productName
                        record.feedback == "incorrect" && record.correctedSubclass != null ->
                            "${record.subclass} ➔ ${record.correctedSubclass}"
                        else -> record.subclass
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (productName != null) {
                    Text(
                        text = if (record.feedback == "incorrect" && record.correctedSubclass != null)
                            "${record.subclass} ➔ ${record.correctedSubclass}"
                        else record.subclass,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = record.timestamp.toRelativeTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                FeedbackBadge(feedback = record.feedback)
                IconButton(onClick = onEditFeedback) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit_feedback), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete item", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = record.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun FeedbackBadge(feedback: String?) {
    val (color, icon) = when(feedback) {
        "correct" -> MaterialTheme.colorScheme.primary to Icons.Default.CheckCircle
        "incorrect" -> MaterialTheme.colorScheme.error to Icons.Default.Cancel
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) to Icons.Default.QuestionMark
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
fun FeedbackDialog(
    record: WasteRecord,
    onDismiss: () -> Unit,
    onFeedbackSelected: (String, String?) -> Unit
) {
    var showCorrection by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val options = context.resources.getStringArray(R.array.material_options).toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!showCorrection) stringResource(R.string.history_refine) else stringResource(R.string.history_select_material)) },
        text = {
            if (!showCorrection) {
                Text(stringResource(R.string.history_feedback_help, record.subclass))
            } else {
                Column {
                    options.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { option ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onFeedbackSelected("incorrect", option) },
                                    label = { Text(option) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!showCorrection) {
                TextButton(onClick = { onFeedbackSelected("correct", null) }) {
                    Text(stringResource(R.string.common_correct), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            if (!showCorrection) {
                Row {
                    TextButton(onClick = { showCorrection = true }) {
                        Text(stringResource(R.string.common_incorrect), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            } else {
                TextButton(onClick = { showCorrection = false }) {
                    Text(stringResource(R.string.common_back))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun EmptyHistoryState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CloudOff, 
            contentDescription = null, 
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "HistoryCard Preview", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun HistoryCardPreview() {
    com.agrelius.wasegmul.ui.theme.WasegMulTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            HistoryCard(
                record = WasteRecord(
                    id = 1L,
                    category = "Recyclable",
                    subclass = "plastic_bottle",
                    confidence = 0.94f,
                    timestamp = System.currentTimeMillis(),
                    estimatedWeight = 0.05
                ),
                onEditFeedback = {},
                onDelete = {},
                onClick = {}
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "EmptyHistoryState Preview", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun EmptyHistoryStatePreview() {
    com.agrelius.wasegmul.ui.theme.WasegMulTheme {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            EmptyHistoryState()
        }
    }
}

