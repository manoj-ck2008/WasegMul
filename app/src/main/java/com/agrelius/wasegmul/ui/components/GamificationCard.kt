package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.gamification.EcoLevel
import com.agrelius.wasegmul.gamification.GamificationState

/**
 * Level/XP card: level badge + title (from `GamificationManager.LEVELS`),
 * total accumulated XP, and a progress bar to the next level.
 *
 * Compact on purpose: the surrounding [GlassCard] already pads 20dp, and
 * this card shares a uniform stats row — no extra inner padding, no fixed
 * heights, so both row cards measure equal.
 */
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
    val nextLevelXp = state.nextLevel?.xpRequired ?: state.currentLevel.xpRequired

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.home_impact_title),
                onClick = onClick
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.home_level_badge,
                        state.currentLevel.level
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = state.currentLevel.iconEmoji, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = state.currentLevel.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.gamification_xp_progress,
                    state.currentXp,
                    nextLevelXp
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

            Text(
                text = if (state.nextLevel != null) {
                    stringResource(
                        R.string.home_xp_to_next,
                        state.xpToNextLevel,
                        state.nextLevel.name
                    )
                } else {
                    stringResource(R.string.home_max_level)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                // Two lines so long level names ("Waste Warrior") never clip
                // mid-word; the stats row is height-matched, so both cards
                // stay equal regardless.
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.gamification_co2_fmt, totalCo2Kg),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.gamification_carbon_offset),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
