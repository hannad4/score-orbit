package com.simplescoring.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplescoring.android.model.Game
import com.simplescoring.android.viewmodel.AppScreen
import com.simplescoring.android.viewmodel.ScoreViewModel

private val SheetBg = Color(0xFF1C1C1E)
private val CardBg = Color(0xFF2C2C2E)
private val iOSBlue = Color(0xFF0A84FF)

// ---------------------------------------------------------------------------
// Score history + undo/redo (iOS screenshot 6)
// ---------------------------------------------------------------------------

@Composable
fun ScoreHistoryScreen(game: Game, viewModel: ScoreViewModel) {
    val canUndo = viewModel.undoStack.isNotEmpty()
    val canRedo = viewModel.redoStack.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Score History",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(
                onClick = { viewModel.go(AppScreen.Board) },
                modifier = Modifier.align(Alignment.CenterEnd).size(32.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
            }
        }

        if (game.entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No scores yet — swipe the ring to score.", color = Color.White.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(game.entries.asReversed(), key = { it.id }) { entry ->
                    val player = game.players.firstOrNull { it.id == entry.playerId }
                    val color = player?.let { Color(it.color) } ?: Color.White.copy(alpha = 0.5f)
                    Text(
                        text = if (entry.delta >= 0) "+${entry.delta}" else "${entry.delta}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = color,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            PillButton(
                label = "Undo",
                icon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = iOSBlue, modifier = Modifier.size(18.dp)) },
                enabled = canUndo,
                onClick = { viewModel.undo() },
            )
            PillButton(
                label = "Redo",
                icon = { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, tint = iOSBlue, modifier = Modifier.size(18.dp)) },
                enabled = canRedo,
                onClick = { viewModel.redo() },
            )
        }
    }
}

@Composable
private fun PillButton(label: String, icon: @Composable () -> Unit, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = iOSBlue,
            disabledContainerColor = Color.White.copy(alpha = 0.06f),
            disabledContentColor = Color.White.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
    }
}

// ---------------------------------------------------------------------------
// Game history (iOS screenshot 7)
// ---------------------------------------------------------------------------

@Composable
fun GameHistoryScreen(history: List<Game>, currentGame: Game?, viewModel: ScoreViewModel) {
    val groups = remember(history) {
        history.groupBy { dayLabel(it.finishedAt ?: it.createdAt) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetBg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = { viewModel.go(if (currentGame != null) AppScreen.Settings else AppScreen.Board) },
                modifier = Modifier.align(Alignment.CenterStart).size(32.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
            }
            Text(
                "Game History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Finished games will appear here.", color = Color.White.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                groups.forEach { (day, games) ->
                    item(key = "header-$day") {
                        Text(
                            day,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 14.dp, top = 6.dp),
                        )
                    }
                    item(key = "group-$day") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                games.forEachIndexed { gi, game ->
                                    if (gi > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                                    GameHistoryRow(
                                        game = game,
                                        onResume = { viewModel.resumeGame(game) },
                                        onDelete = { viewModel.deleteGame(game) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (currentGame != null) viewModel.restartWithSameSetup()
                else viewModel.startNewGame(
                    2,
                    listOf("Player 1", "Player 2"),
                    com.simplescoring.android.ui.theme.ScoreAnythingColors.PlayerColors.take(2),
                    1,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = iOSBlue),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("New Scoreboard")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun GameHistoryRow(game: Game, onResume: () -> Unit, onDelete: () -> Unit) {
    val winner = game.winner()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onResume() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            game.players.forEachIndexed { i, p ->
                if (i > 0) Text(" - ", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
                Text(
                    "${game.currentScore(p.id)}",
                    color = Color(p.color),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(17.dp))
        }
        Icon(
            Icons.Default.EmojiEvents,
            contentDescription = "Winner",
            tint = winner?.let { Color(it.color) } ?: Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(22.dp),
        )
    }
}
