package com.scoreorbit.android.ui

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
 * the top three rows get full gold/silver/bronze backgrounds.
 *
 * Team games split into two ranked sections — Teams (collective totals,
 * members listed underneath) and Players (solos) — while solo-only games
 * keep the single plain list.
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
    fun ordered(scores: List<Pair<Int, Int>>): List<Pair<Int, Int>> =
        scores.sortedWith(
            if (lowestWins) compareBy { (_, s) -> s }
            else compareByDescending { (_, s) -> s }
        )
    fun rankOf(orderedScores: List<Int>, score: Int): Int =
        orderedScores.count { s -> if (lowestWins) s < score else s > score } + 1
    val totals = remember(game.entries, game.players) { game.scoresMap() }
    val teamTotals = remember(game.entries, game.players, game.teams) { game.teamScoresMap() }
    // Section indexes into the roster: team sections hold team indexes,
    // the solo section holds player indexes.
    val teamOrder = remember(game.teams, lowestWins, teamTotals) {
        ordered(game.teams.mapIndexed { i, t -> i to (teamTotals[t.id] ?: 0) })
    }
    val soloOrder = remember(game.players, lowestWins, totals) {
        ordered(game.players.mapIndexedNotNull { i, p -> if (p.teamId == null) i to (totals[p.id] ?: 0) else null })
    }
    val hasTeams = teamOrder.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val subtitle = if (game.name.isNotBlank()) {
        "${game.name} • ${if (lowestWins) "Lowest wins" else "Highest wins"}"
    } else {
        if (lowestWins) "Lowest wins" else "Highest wins"
    }

    @Composable
    fun StandingRow(
        name: String,
        supporting: String?,
        score: Int,
        color: Color,
        rank: Int,
    ) {
        val medal = medalFor(rank)
        val onMedal = medal != null
        val darkContent = Color(0xFF1A1A1A)
        ScoreCard(
            containerColor = medal
                ?: MaterialTheme.colorScheme.surfaceContainer,
        ) {
            ScoreListItem(
                playerName = name,
                supportingText = supporting,
                trailingText = "$score",
                trailingColor = if (onMedal) darkContent else color,
                contentColor = if (onMedal) darkContent else null,
                leadingBadge = {
                    ListLeadingBadge(
                        rank = rank,
                        color = color,
                    )
                },
            )
        }
    }

    @Composable
    fun SectionLabel(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
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
            if (game.players.isEmpty()) {
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
                    if (hasTeams && soloOrder.isNotEmpty()) {
                        item { SectionLabel("Teams") }
                    }
                    items(
                        count = teamOrder.size,
                        key = { index -> "team-${game.teams[teamOrder[index].first].id}" },
                    ) { index ->
                        val (teamIdx, score) = teamOrder[index]
                        val team = game.teams[teamIdx]
                        val members = game.players.filter { it.teamId == team.id }
                        StandingRow(
                            name = team.name,
                            supporting = members.joinToString(" \u2022 ") { it.name },
                            score = score,
                            color = Color(team.color),
                            rank = rankOf(teamOrder.map { it.second }, score),
                        )
                    }
                    if (hasTeams && soloOrder.isNotEmpty()) {
                        item { SectionLabel("Players") }
                    }
                    items(
                        count = soloOrder.size,
                        key = { index -> game.players[soloOrder[index].first].id },
                    ) { index ->
                        val (playerIdx, score) = soloOrder[index]
                        val player = game.players[playerIdx]
                        StandingRow(
                            name = player.name,
                            supporting = null,
                            score = score,
                            color = Color(player.color),
                            rank = rankOf(soloOrder.map { it.second }, score),
                        )
                    }
                }
            }
            // Winner mode at bottom, mirroring the History Undo/Redo row:
            // current mode filled, tap either to switch. Same winMetric as
            // the Settings page, so both stay in sync either direction.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                @Composable
                fun ModeButton(label: String, metric: WinMetric, selected: Boolean) {
                    val buttonModifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                    val content: @Composable RowScope.() -> Unit = {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                    if (selected) {
                        Button(
                            onClick = { viewModel.setWinMetric(metric) },
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = buttonModifier,
                            content = content,
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { viewModel.setWinMetric(metric) },
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = buttonModifier,
                            content = content,
                        )
                    }
                }
                ModeButton("Lowest", WinMetric.LOWEST, selected = lowestWins)
                ModeButton("Highest", WinMetric.HIGHEST, selected = !lowestWins)
            }
        }
    }
}