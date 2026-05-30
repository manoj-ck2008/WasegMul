package com.agrelius.wasegmul.ui.classify

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.ui.components.PrimaryActionButton
import com.agrelius.wasegmul.viewmodel.ClassificationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassifyScreen(
    viewModel: ClassificationViewModel,
    onNavigateToResult: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analyze Waste") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Image Preview Placeholder Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Image Preview Placeholder")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryActionButton(
                text = "Analyze",
                onClick = {
                    // Future: Trigger TFLite Inference
                    onNavigateToResult()
                }
            )
        }
    }
}
