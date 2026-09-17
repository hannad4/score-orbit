package com.scoreorbit.android.ui

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.model.WinMetric
import com.scoreorbit.android.viewmodel.AppScreen
import com.scoreorbit.android.viewmodel.ScoreViewModel

/**
 * Player standings ordered highest score to lowest. Ties share a rank;
 * the leader row is highlighted with a trophy.
 *
 * M3 Expressive: MediumTopAppBar with game subtitle, extra-large rounded
 * cards, rank badge in an icon container, emphasized title/score type.
 * Shares layout components with ScoreHistoryScreen for visual consistency.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val subtitle = if (game.name.isNotBlank()) {
        "${game.name} • ${if (lowestWins) "Lowest wins" else "Highest wins"}"
    } else {
        if (lowestWins) "Lowest wins" else "Highest wins"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SharedTopAppBar(
                title = "Leaderboard",
                viewModel = viewModel,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (standings.isEmpty()) {
                EmptyState(
                    icon = { Icon(Icons.Default.Leaderboard, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp)) },
                    title = "No standings yet",
                    subtitle = "Add players to see rankings.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        count = standings.size,
                        key = { index -> standings[index].first.id },
                    ) { index ->
                        val (player, score) = standings[index]
                        val rank = ranks[index]
                        val medal = medalFor(rank)
                        val containerColor = medal?.copy(alpha = 0.18f)
                            ?: MaterialTheme.colorScheme.surfaceContainer
                        ScoreCard(
                            containerColor = containerColor,
                        ) {
                            ScoreListItem(
                                playerName = player.name,
                                supportingText = if (rank == 1) "Leader • $score pts"
                                else "${rankLabel(rank)} place • $score pts",
                                trailingText = "$score",
                                trailingColor = Color(player.color),
                                leadingBadge = {
                                    ListLeadingBadge(
                                        rank = rank,
                                        color = Color(player.color),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}