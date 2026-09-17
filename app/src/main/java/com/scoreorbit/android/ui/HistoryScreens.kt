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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.unit.sp
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.viewmodel.AppScreen
import com.scoreorbit.android.viewmodel.ScoreViewModel

// Shared medal colors for leaderboard
internal fun medalFor(rank: Int): Color? = when (rank) {
    1 -> Color(0xFFFFD54F)
    2 -> Color(0xFFC0C0C0)
    3 -> Color(0xFFCD7F32)
    else -> null
}

// Shared leading badge for lists - consistent across leaderboard & history
@Composable
internal fun ListLeadingBadge(
    rank: Int? = null,
    shotNumber: Int? = null,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val medal = rank?.let { medalFor(it) }
    val showMedal = medal != null
    Box(
        modifier = modifier
            .size(48.dp)
            .background(
                if (showMedal) medal!!
                else color.copy(alpha = 0.18f),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showMedal) {
            if (rank == 1) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFF1A1A1A),
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                )
            }
        } else if (shotNumber != null) {
            Text(
                "#$shotNumber",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

// Shared empty state
@Composable
internal fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// Shared list item content
@Composable
internal fun ScoreListItem(
    playerName: String,
    supportingText: String?,
    trailingText: String,
    trailingColor: Color,
    leadingBadge: @Composable () -> Unit,
    showTrailing: Boolean = true,
) {
    ListItem(
        headlineContent = {
            Text(
                playerName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = supportingText?.let { text ->
            {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = leadingBadge,
        trailingContent = if (showTrailing) {
            {
                Text(
                    trailingText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = trailingColor,
                )
            }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// Shared card wrapper with M3 Expressive extraLarge shape
@Composable
internal fun ScoreCard(
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
    ) {
        content()
    }
}

// Shared top app bar with consistent layout
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedTopAppBar(
    title: String,
    viewModel: ScoreViewModel,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    MediumTopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = { viewModel.go(AppScreen.Board) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Full ledger, newest first, with undo/redo.
 *
 * M3 Expressive: same MediumTopAppBar + extra-large cards + empty state as
 * [LeaderboardScreen], so the two lists read as one family. Name stays the
 * headline and the delta stays the trailing value — mirroring leaderboard's
 * name + score rows.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScoreHistoryScreen(game: Game, viewModel: ScoreViewModel) {
    val canUndo = viewModel.undoStack.isNotEmpty()
    val canRedo = viewModel.redoStack.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val subtitle = if (game.name.isNotBlank()) {
        "${game.name} • ${game.entries.size} ${if (game.entries.size == 1) "entry" else "entries"}"
    } else {
        "${game.entries.size} ${if (game.entries.size == 1) "entry" else "entries"}"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SharedTopAppBar(
                title = "Score History",
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
            if (game.entries.isEmpty()) {
                EmptyState(
                    icon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp)) },
                    title = "No scores yet",
                    subtitle = "Swipe the ring to score.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(game.entries.asReversed(), key = { it.id }) { entry ->
                        val player = game.players.firstOrNull { it.id == entry.playerId }
                        val color = player?.let { Color(it.color) }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant
                        val shotNumber = game.entries.indexOfFirst { it.id == entry.id } + 1
                        ScoreCard(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            ScoreListItem(
                                playerName = player?.name ?: "Unknown player",
                                supportingText = "Shot #$shotNumber",
                                trailingText = if (entry.delta >= 0) "+${entry.delta}" else "${entry.delta}",
                                trailingColor = color,
                                leadingBadge = {
                                    ListLeadingBadge(
                                        shotNumber = shotNumber,
                                        color = color,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            // Undo/Redo actions at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    enabled = canUndo,
                    onClick = { viewModel.undo() },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Undo", style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalButton(
                    enabled = canRedo,
                    onClick = { viewModel.redo() },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Redo", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
