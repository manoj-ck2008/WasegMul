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
import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.ui.home.RecentItem
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
                title = { Text("Neural History", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (allHistory.isNotEmpty()) {
                        IconButton(onClick = { showClearAllConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = MaterialTheme.colorScheme.error)
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

            if (editingRecord != null) {
                val recordToEdit = editingRecord!!
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
                    title = { Text("Clear All Records?") },
                    text = { Text("This will permanently delete all classification history from this device. This action cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.clearHistory()
                            showClearAllConfirm = false
                        }) {
                            Text("Delete All", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearAllConfirm = false }) {
                            Text("Cancel")
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
                    fontWeight = FontWeight.Bold
                )
                Text(text = record.timestamp.toRelativeTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                FeedbackBadge(feedback = record.feedback)
                IconButton(onClick = onEditFeedback) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Feedback", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
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
        "correct" -> Color(0xFF2ECC71) to Icons.Default.CheckCircle
        "incorrect" -> Color(0xFFE74C3C) to Icons.Default.Cancel
        else -> Color.Gray.copy(alpha = 0.5f) to Icons.Default.QuestionMark
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
    val options = listOf("Plastic", "Metal", "Glass", "Paper", "Cardboard", "E-Waste", "Organic", "Trash")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!showCorrection) "Refine Prediction" else "Select Actual Material") },
        text = {
            if (!showCorrection) {
                Text("Help us improve. Was the classification of '${record.subclass}' correct?")
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
                    Text("Correct", color = Color(0xFF2ECC71))
                }
            }
        },
        dismissButton = {
            if (!showCorrection) {
                Row {
                    TextButton(onClick = { showCorrection = true }) {
                        Text("Incorrect", color = Color(0xFFE74C3C))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            } else {
                TextButton(onClick = { showCorrection = false }) {
                    Text("Back")
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
        Text("No classification records found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
