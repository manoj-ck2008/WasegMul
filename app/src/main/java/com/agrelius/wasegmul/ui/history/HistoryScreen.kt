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
import com.agrelius.wasegmul.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onNavigateToResult: (Long) -> Unit
) {
    val allHistory by viewModel.allHistory.collectAsState()
    var editingRecord by remember { mutableStateOf<WasteRecord?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(allHistory) { record ->
                        HistoryCard(
                            record = record,
                            onEditFeedback = { editingRecord = record },
                            onClick = { onNavigateToResult(record.id) }
                        )
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
                Text(
                    text = if (record.feedback == "incorrect" && record.correctedSubclass != null)
                        "${record.subclass} ➔ ${record.correctedSubclass}"
                        else record.subclass,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = record.timestamp.toRelativeTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                FeedbackBadge(feedback = record.feedback)
                IconButton(onClick = onEditFeedback) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit_feedback), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
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
