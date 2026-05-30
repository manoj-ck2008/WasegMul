package com.agrelius.wasegmul.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.ui.components.PrimaryActionButton
import com.agrelius.wasegmul.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToClassify: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo Placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Text("LOGO", color = MaterialTheme.colorScheme.onPrimaryContainer)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "WasegMul",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Offline AI-powered waste classification platform for a cleaner environment.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        PrimaryActionButton(
            text = "Take Photo",
            onClick = { /* Future: Launch Camera */ onNavigateToClassify() }
        )

        PrimaryActionButton(
            text = "Choose From Gallery",
            onClick = { /* Future: Launch Gallery */ onNavigateToClassify() }
        )
    }
}
