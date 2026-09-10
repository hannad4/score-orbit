package com.simplescoring.android.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simplescoring.android.model.Game
import com.simplescoring.android.model.Player
import com.simplescoring.android.model.WinMetric
import com.simplescoring.android.ui.theme.ScoreAnythingColors
import com.simplescoring.android.util.RotationUtils
import com.simplescoring.android.viewmodel.ScoreViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreAnythingApp(viewModel: ScoreViewModel) {
    val currentGame by viewModel.currentGame
    val showHistory by viewModel.showHistory
    val history = viewModel.history

    Scaffold(
        containerColor = ScoreAnythingColors.BackgroundDark,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                showHistory -> {
                    GameHistoryScreen(
                        history = history,
                        onNewGame = { viewModel.dismissHistory() },
                        onResume = { viewModel.resumeGame(it) },
                        onDelete = { viewModel.deleteGame(it) },
                        onClearAll = { viewModel.clearHistory() },
                    )
                }
                currentGame == null -> {
                    SetupScreen(
                        onStart = { count, names, colors, step, boardName, metric ->
                            viewModel.startNewGame(count, names, colors, step, 0, boardName, metric)
                        },
                        onViewHistory = { viewModel.showHistoryScreen() },
                        historyEmpty = history.isEmpty(),
                    )
                }
                else -> {
                    currentGame?.let { game ->
                        BoardScreen(game = game, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onStart: (Int, List<String>, List<Int>, Int, String, WinMetric) -> Unit,
    onViewHistory: () -> Unit,
    historyEmpty: Boolean,
) {
    var boardName by remember { mutableStateOf("") }
    var playerCount by remember { mutableIntStateOf(2) }
    var step by remember { mutableIntStateOf(1) }
    var winMetric by remember { mutableStateOf(WinMetric.HIGHEST) }
    val names = remember {
        mutableStateListOf<String>().apply { repeat(12) { add("Player ${it + 1}") } }
    }
    val colors = remember {
        mutableStateListOf<Int>().apply {
            addAll(List(12) { i -> ScoreAnythingColors.PlayerColors[i % ScoreAnythingColors.PlayerColors.size] })
        }
    }

    Scaffold(
        containerColor = ScoreAnythingColors.BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("New Scoreboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScoreAnythingColors.BackgroundDark,
                    titleContentColor = ScoreAnythingColors.OnBackground,
                    actionIconContentColor = ScoreAnythingColors.OnBackground,
                ),
                actions = {
                    IconButton(onClick = onViewHistory) {
                        Icon(Icons.Default.History, contentDescription = "Scoreboards")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = boardName,
                onValueChange = { boardName = it },
                label = { Text("Scoreboard name") },
                placeholder = { Text("e.g. Catan night") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = setupFieldColors(),
            )

            // Player count stepper (1..12)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Players",
                    style = MaterialTheme.typography.labelLarge,
                    color = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { playerCount = (playerCount - 1).coerceAtLeast(1) },
                    enabled = playerCount > 1,
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Fewer players")
                }
                Text(
                    "$playerCount",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ScoreAnythingColors.OnBackground,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    onClick = { playerCount = (playerCount + 1).coerceAtMost(12) },
                    enabled = playerCount < 12,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "More players")
                }
            }

            // Per-player name + color rows
            repeat(playerCount) { i ->
                PlayerSetupRow(
                    index = i,
                    name = names[i],
                    color = colors[i],
                    usedColors = colors.take(playerCount),
                    onNameChange = { names[i] = it },
                    onColorChange = { colors[i] = it },
                )
            }

            // Points per step stepper
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Points per tap",
                        style = MaterialTheme.typography.labelLarge,
                        color = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        "One full dial turn scores this much",
                        style = MaterialTheme.typography.bodySmall,
                        color = ScoreAnythingColors.OnSurface.copy(alpha = 0.5f),
                    )
                }
                IconButton(
                    onClick = { step = (step - 1).coerceAtLeast(1) },
                    enabled = step > 1,
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease step")
                }
                Text(
                    "$step",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ScoreAnythingColors.OnBackground,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { step = (step + 1).coerceAtMost(100) }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase step")
                }
            }

            // Win metric
            Text(
                "Winner",
                style = MaterialTheme.typography.labelLarge,
                color = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = winMetric == WinMetric.HIGHEST,
                    onClick = { winMetric = WinMetric.HIGHEST },
                    label = { Text("Highest wins") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ScoreAnythingColors.Accent,
                        selectedLabelColor = ScoreAnythingColors.OnBackground,
                    )
                )
                FilterChip(
                    selected = winMetric == WinMetric.LOWEST,
                    onClick = { winMetric = WinMetric.LOWEST },
                    label = { Text("Lowest wins") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ScoreAnythingColors.Accent,
                        selectedLabelColor = ScoreAnythingColors.OnBackground,
                    )
                )
            }

            Button(
                onClick = {
                    onStart(
                        playerCount,
                        List(playerCount) { i -> names[i].ifBlank { "Player ${i + 1}" } },
                        List(playerCount) { i -> colors[i] },
                        step,
                        boardName.ifBlank { defaultBoardName() },
                        winMetric,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ScoreAnythingColors.Accent)
            ) {
                Text("Start Scoring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (!historyEmpty) {
                OutlinedButton(onClick = onViewHistory, modifier = Modifier.fillMaxWidth()) {
                    Text("Saved Scoreboards")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun setupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ScoreAnythingColors.OnBackground,
    unfocusedTextColor = ScoreAnythingColors.OnBackground,
    focusedContainerColor = ScoreAnythingColors.SurfaceDark,
    unfocusedContainerColor = ScoreAnythingColors.SurfaceDark,
    focusedBorderColor = ScoreAnythingColors.Accent,
    unfocusedBorderColor = ScoreAnythingColors.OnSurface.copy(alpha = 0.3f),
    focusedLabelColor = ScoreAnythingColors.Accent,
    unfocusedLabelColor = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f),
    cursorColor = ScoreAnythingColors.Accent,
)

@Composable
fun PlayerSetupRow(
    index: Int,
    name: String,
    color: Int,
    usedColors: List<Int>,
    onNameChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScoreAnythingColors.SurfaceDark),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Player ${index + 1}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = setupFieldColors(),
            )
            // 24-color palette in rows of 8; a dot warns if the color is taken twice.
            ScoreAnythingColors.PlayerColors.chunked(8).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        val selected = c == color
                        val duplicated = usedColors.count { it == c } > 1 && selected
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = when {
                                        selected -> Color.White
                                        else -> Color.White.copy(alpha = 0.25f)
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { onColorChange(c) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (duplicated) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun defaultBoardName(): String {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    return "Game · ${fmt.format(Date())}"
}

// ---------------------------------------------------------------------------
// Board
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(game: Game, viewModel: ScoreViewModel) {
    val canUndo = viewModel.undoStack.isNotEmpty()
    val winner = game.winner()
    val columns = if (game.players.size == 1) 1 else 2

    Scaffold(
        containerColor = ScoreAnythingColors.BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        game.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.showHistoryScreen() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Scoreboards")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScoreAnythingColors.BackgroundDark,
                    titleContentColor = ScoreAnythingColors.OnBackground,
                    navigationIconContentColor = ScoreAnythingColors.OnBackground,
                    actionIconContentColor = ScoreAnythingColors.OnBackground,
                ),
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo last score")
                    }
                    IconButton(onClick = { viewModel.finishGame() }) {
                        Icon(Icons.Default.Check, contentDescription = "Finish game")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            if (winner != null) {
                WinnerBanner(
                    winnerName = winner.name,
                    metric = game.winMetric,
                    onNewRound = { viewModel.resetAllScores() },
                    onFinish = { viewModel.finishGame() },
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(game.players, key = { it.id }) { player ->
                    PlayerDialTile(
                        player = player,
                        score = game.currentScore(player.id),
                        lastDelta = game.entries.lastOrNull { it.playerId == player.id }?.delta,
                        step = game.step,
                        isWinner = winner?.id == player.id,
                        onScore = { viewModel.addScore(player.id, it) },
                        onRotate = { viewModel.rotatePlayer(player.id) },
                        onReset = { viewModel.resetPlayerScore(player.id) },
                    )
                }
            }
            ScoreStrip(
                game = game,
                canUndo = canUndo,
                onUndo = { viewModel.undo() },
                onClearAll = { viewModel.resetAllScores() },
            )
        }
    }
}

@Composable
fun WinnerBanner(
    winnerName: String,
    metric: WinMetric,
    onNewRound: () -> Unit,
    onFinish: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = ScoreAnythingColors.WinnerGold.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\uD83C\uDFC6 $winnerName ${if (metric == WinMetric.HIGHEST) "leads" else "leads (low)"}!",
                color = ScoreAnythingColors.WinnerGold,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onNewRound) { Text("New round", color = ScoreAnythingColors.WinnerGold) }
            TextButton(onClick = onFinish) { Text("Finish", color = ScoreAnythingColors.WinnerGold) }
        }
    }
}

/**
 * Rotary-dial scoring tile.
 *
 * Circular drag around the dial accumulates angle: each full clockwise turn
 * scores +step, each full counter-clockwise turn scores -step (like a rotary
 * phone / iPod click wheel). A plain tap scores +step. Haptics tick each
 * quarter turn and thump on a completed turn.
 */
@Composable
fun PlayerDialTile(
    player: Player,
    score: Int,
    lastDelta: Int?,
    step: Int,
    isWinner: Boolean,
    onScore: (Int) -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
) {
    val ctx = LocalContext.current
    var accumulated by remember(player.id) { mutableFloatStateOf(0f) }
    var lastAngle by remember(player.id) { mutableFloatStateOf(Float.NaN) }
    var quarterTicks by remember(player.id) { mutableIntStateOf(0) }
    var dialPx by remember { mutableFloatStateOf(1f) }

    fun angleOf(position: Offset): Float {
        val dx = (position.x - dialPx / 2.0).toFloat()
        val dy = (position.y - dialPx / 2.0).toFloat()
        return atan2(dy.toDouble(), dx.toDouble()).toFloat()
    }

    val dialColor = if (isWinner) ScoreAnythingColors.WinnerGold else Color(player.color)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isWinner)
                ScoreAnythingColors.WinnerGold.copy(alpha = 0.12f)
            else
                ScoreAnythingColors.SurfaceDark
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Scorekeeper header (always upright): name + rotate + reset.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isWinner) ScoreAnythingColors.WinnerGold else ScoreAnythingColors.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRotate, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Rotate to face player",
                        tint = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Reset player score",
                        tint = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Dial with live progress. Tapping scores one step.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .onSizeChanged { dialPx = min(it.width, it.height).toFloat().coerceAtLeast(1f) }
                    .clip(CircleShape)
                    .background(dialColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            buzz(ctx, 12)
                            accumulated = 0f
                            quarterTicks = 0
                            onScore(step)
                        },
                    )
                    .pointerInput(player.id, step) {
                        detectDragGestures(
                            onDragStart = { offset -> lastAngle = angleOf(offset) },
                            onDragEnd = {
                                accumulated = 0f
                                lastAngle = Float.NaN
                                quarterTicks = 0
                            },
                            onDragCancel = {
                                accumulated = 0f
                                lastAngle = Float.NaN
                                quarterTicks = 0
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (lastAngle.isNaN()) {
                                    lastAngle = angleOf(change.position)
                                    return@detectDragGestures
                                }
                                val angle = angleOf(change.position)
                                var delta = angle - lastAngle
                                while (delta > PI.toFloat()) delta -= 2f * PI.toFloat()
                                while (delta < -PI.toFloat()) delta += 2f * PI.toFloat()
                                // Finger jumped across the dial: re-anchor instead of
                                // crediting a huge phantom turn.
                                if (abs(delta) > PI.toFloat() / 2f) {
                                    lastAngle = angle
                                    return@detectDragGestures
                                }
                                lastAngle = angle
                                accumulated += delta
                                val quarters = (abs(accumulated) / (PI.toFloat() / 2f)).toInt()
                                if (quarters != quarterTicks) {
                                    quarterTicks = quarters
                                    buzz(ctx, 8)
                                }
                                val fullTurn = 2f * PI.toFloat()
                                while (accumulated >= fullTurn) {
                                    accumulated -= fullTurn
                                    quarterTicks = 0
                                    buzz(ctx, 25)
                                    onScore(step)
                                }
                                while (accumulated <= -fullTurn) {
                                    accumulated += fullTurn
                                    quarterTicks = 0
                                    buzz(ctx, 25)
                                    onScore(-step)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    // Tick ring (rotary-phone holes).
                    repeat(12) { i ->
                        val a = (i * 30.0 - 90.0) * PI / 180.0
                        val outer = radius * 0.94f
                        val inner = radius * 0.80f
                        drawLine(
                            color = Color.White.copy(alpha = 0.55f),
                            start = Offset(
                                center.x + cos(a).toFloat() * inner,
                                center.y + sin(a).toFloat() * inner,
                            ),
                            end = Offset(
                                center.x + cos(a).toFloat() * outer,
                                center.y + sin(a).toFloat() * outer,
                            ),
                            strokeWidth = size.minDimension * 0.022f,
                        )
                    }
                    // Progress arc for the in-progress turn.
                    if (abs(accumulated) > 0.02f) {
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = (accumulated * 180f / PI.toFloat()).coerceIn(-360f, 360f),
                            useCenter = false,
                            style = Stroke(width = size.minDimension * 0.045f),
                        )
                    }
                    // Knob dot tracking the finger's accumulated angle.
                    val ka = accumulated - PI.toFloat() / 2f
                    val kr = radius * 0.62f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = size.minDimension * 0.05f,
                        center = Offset(
                            center.x + cos(ka) * kr,
                            center.y + sin(ka) * kr,
                        ),
                    )
                }
                Text(
                    text = "+$step",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }

            // Score block rotates to face its player (tabletop mode).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.rotate(RotationUtils.degrees(player.rotation)),
            ) {
                Text(
                    text = "$score",
                    style = scoreTextStyle(score),
                    fontWeight = FontWeight.Bold,
                    color = if (isWinner) ScoreAnythingColors.WinnerGold else ScoreAnythingColors.OnBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = when {
                        lastDelta == null -> "drag dial or tap +$step"
                        lastDelta >= 0 -> "last +$lastDelta"
                        else -> "last $lastDelta"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ScoreAnythingColors.OnSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun scoreTextStyle(score: Int) = when (abs(score).toString().length) {
    in 0..3 -> MaterialTheme.typography.displayMedium
    in 4..5 -> MaterialTheme.typography.displaySmall
    else -> MaterialTheme.typography.headlineLarge
}

@Composable
fun ScoreStrip(
    game: Game,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
) {
    val recent = remember(game.entries) { game.entries.takeLast(8).reversed() }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (recent.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(recent, key = { it.id }) { entry ->
                    val player = game.players.firstOrNull { it.id == entry.playerId }
                    val label = "${player?.name ?: "?"} ${if (entry.delta >= 0) "+" else ""}${entry.delta}"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = ScoreAnythingColors.OnBackground,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Undo")
            }
            OutlinedButton(onClick = onClearAll, modifier = Modifier.weight(1f)) {
                Text("New round")
            }
        }
    }
}

fun buzz(ctx: Context, millis: Long) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(millis)
        }
    } catch (_: Exception) { }
}

// ---------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistoryScreen(
    history: List<Game>,
    onNewGame: () -> Unit,
    onResume: (Game) -> Unit,
    onDelete: (Game) -> Unit,
    onClearAll: () -> Unit,
) {
    Scaffold(
        containerColor = ScoreAnythingColors.BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Scoreboards") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScoreAnythingColors.BackgroundDark,
                    titleContentColor = ScoreAnythingColors.OnBackground,
                    actionIconContentColor = ScoreAnythingColors.OnBackground,
                ),
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Clear", color = ScoreAnythingColors.OnBackground)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No scoreboards yet.", color = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onNewGame,
                        colors = ButtonDefaults.buttonColors(containerColor = ScoreAnythingColors.Accent),
                    ) {
                        Text("Create one")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Button(
                        onClick = onNewGame,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ScoreAnythingColors.Accent),
                    ) {
                        Text("New Scoreboard")
                    }
                }
                items(history, key = { it.id }) { game ->
                    HistoryCard(game = game, onResume = { onResume(game) }, onDelete = { onDelete(game) })
                }
            }
        }
    }
}

@Composable
fun HistoryCard(game: Game, onResume: () -> Unit, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }
    val date = remember(game) {
        fmt.format(Date(game.finishedAt ?: game.createdAt))
    }
    val winner = game.winner()
    Card(
        colors = CardDefaults.cardColors(containerColor = ScoreAnythingColors.SurfaceDark),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        game.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ScoreAnythingColors.OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$date · ${game.players.size} players · step ${game.step}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ScoreAnythingColors.OnSurface.copy(alpha = 0.6f),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete scoreboard",
                        tint = ScoreAnythingColors.OnSurface.copy(alpha = 0.6f),
                    )
                }
            }
            game.players.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(p.color))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        p.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ScoreAnythingColors.OnBackground,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${game.currentScore(p.id)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ScoreAnythingColors.OnBackground,
                    )
                }
            }
            if (winner != null) {
                Text(
                    "\uD83C\uDFC6 ${winner.name} wins (${game.currentScore(winner.id)})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ScoreAnythingColors.WinnerGold,
                )
            }
            OutlinedButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Text("Resume")
            }
        }
    }
}
