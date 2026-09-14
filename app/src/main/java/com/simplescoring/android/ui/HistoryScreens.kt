package com.simplescoring.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simplescoring.android.model.Game
import com.simplescoring.android.viewmodel.AppScreen
import com.simplescoring.android.viewmodel.ScoreViewModel


// ---------------------------------------------------------------------------
// Score history + undo/redo
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScoreHistoryScreen(game: Game, viewModel: ScoreViewModel) {
    val canUndo = viewModel.undoStack.isNotEmpty()
    val canRedo = viewModel.redoStack.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MediumTopAppBar(
                title = { Text("Score History") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.go(AppScreen.Board) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (game.entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No scores yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Swipe the ring to score.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(game.entries.asReversed(), key = { it.id }) { entry ->
                        val player = game.players.firstOrNull { it.id == entry.playerId }
                        val color = player?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = if (entry.delta >= 0) "+${entry.delta}" else "${entry.delta}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color,
                                )
                            },
                            supportingContent = {
                                Text(player?.name ?: "Unknown player")
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.animateItemPlacement(),
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    enabled = canUndo,
                    onClick = { viewModel.undo() },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(28.dp))
                }
                FilledTonalButton(
                    enabled = canRedo,
                    onClick = { viewModel.redo() },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
