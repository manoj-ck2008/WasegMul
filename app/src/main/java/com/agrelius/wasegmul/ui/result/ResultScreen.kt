package com.agrelius.wasegmul.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.ui.components.PrimaryActionButton
import com.agrelius.wasegmul.ui.components.ResultCard
import com.agrelius.wasegmul.ui.components.SectionHeader
import com.agrelius.wasegmul.viewmodel.ResultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    onNavigateToHome: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Classification Results") },
                actions = {
                    IconButton(onClick = onNavigateToHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(text = "Primary Detection")

            ResultCard(title = "Category", content = "Plastic (Placeholder)")
            ResultCard(title = "Subclass", content = "PET Bottle (Placeholder)")
            ResultCard(title = "Confidence", content = "98.5% (Placeholder)")

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(text = "Insights")

            ResultCard(title = "Top Predictions", content = "1. PET Bottle (98%)\n2. Glass Bottle (1.2%)\n3. Metal Can (0.3%)")
            ResultCard(title = "Disposal Guide", content = "Rinse with water, remove the cap, and place in the yellow recycling bin.")
            ResultCard(title = "Environmental Impact", content = "Plastic takes up to 450 years to decompose in landfills.")
            ResultCard(title = "Recycling Benefits", content = "Recycling one plastic bottle saves enough energy to power a 60W light bulb for 3 hours.")

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryActionButton(
                text = "Back to Home",
                onClick = onNavigateToHome
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
