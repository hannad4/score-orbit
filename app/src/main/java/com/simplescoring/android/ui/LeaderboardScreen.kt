package com.simplescoring.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simplescoring.android.model.Game
import com.simplescoring.android.model.WinMetric
import com.simplescoring.android.viewmodel.AppScreen
import com.simplescoring.android.viewmodel.ScoreViewModel

/** Medal tints for the top three ranks; null past third. */
private fun medalFor(rank: Int): Color? = when (rank) {
    1 -> Color(0xFFFFD54F)
    2 -> Color(0xFFC0C0C0)
    3 -> Color(0xFFCD7F32)
    else -> null
}

/**
 * Player standings ordered highest score to lowest. Ties share a rank;
 * the leader row is highlighted with a trophy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(game: Game, viewModel: ScoreViewModel) {
    // Rank 1 means "winning": highest score normally, lowest score when
    // the board plays lowest-wins. Ties share a rank.
    val lowestWins = game.winMetric == WinMetric.LOWEST
    val standings = remember(game) {
        game.players
            .map { player -> player to game.currentScore(player.id) }
            .sortedWith(
                if (lowestWins) compareBy { (_, score) -> score }
                else compareByDescending { (_, score) -> score }
            )
    }
    val ranks = remember(standings, lowestWins) {
        standings.map { (_, score) ->
            standings.count { (_, s) -> if (lowestWins) s < score else s > score } + 1
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.go(AppScreen.Board) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        if (standings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No players yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(standings.size) { index ->
                    val (player, score) = standings[index]
                    val rank = ranks[index]
                    val medal = medalFor(rank)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = medal?.copy(alpha = 0.22f)
                                ?: MaterialTheme.colorScheme.surfaceContainer
                        ),
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    player.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingContent = {
                                Text(
                                    "$rank",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = medal ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                Text(
                                    "$score",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(player.color),
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}
