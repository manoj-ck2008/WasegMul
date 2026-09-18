package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.gamification.EcoLevel
import com.agrelius.wasegmul.gamification.GamificationState

@Composable
fun GamificationCard(
    state: GamificationState,
    totalCo2Kg: Double,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progressToNextLevel,
        animationSpec = tween(1000),
        label = "xpProgress"
    )

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.home_impact_title),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.currentLevel.iconEmoji,
                fontSize = 36.sp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = state.currentLevel.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                val nextLevelXp = state.nextLevel?.xpRequired ?: state.currentLevel.xpRequired
                Text(
                    text = stringResource(
                        R.string.gamification_xp_progress,
                        state.currentXp,
                        nextLevelXp
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = stringResource(R.string.gamification_co2_fmt, totalCo2Kg),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.gamification_carbon_offset),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020408)
@Composable
fun PreviewGamificationCard() {
    MaterialTheme {
        GamificationCard(
            state = GamificationState(
                currentXp = 1234,
                currentLevel = EcoLevel(5, 1500, "Green Guardian", "🛡️"),
                nextLevel = EcoLevel(6, 3000, "Earth Protector", "🌍"),
                progressToNextLevel = 0.45f,
                xpToNextLevel = 1766
            ),
            totalCo2Kg = 0.450,
            onClick = {}
        )
    }
}
