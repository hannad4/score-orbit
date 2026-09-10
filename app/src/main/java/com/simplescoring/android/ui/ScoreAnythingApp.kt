package com.simplescoring.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.simplescoring.android.model.Game
import com.simplescoring.android.model.Player
import com.simplescoring.android.viewmodel.ScoreViewModel
import com.simplescoring.android.ui.theme.ScoreAnythingColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreAnythingApp(viewModel: ScoreViewModel) {
    val currentGame by viewModel.currentGame
    val showHistory by viewModel.showHistory
    val history = viewModel.history

    Scaffold(
        containerColor = ScoreAnythingColors.BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Score Anything") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScoreAnythingColors.BackgroundDark,
                    titleContentColor = ScoreAnythingColors.OnBackground
                ),
                actions = {
                    IconButton(onClick = { viewModel.showHistoryScreen() }) {
                        Icon(Icons.Default.Share, contentDescription = "History", tint = ScoreAnythingColors.OnBackground)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                currentGame == null && !showHistory -> {
                    NewGameScreen(
                        onStart = { playerCount, names, colors, step, timerSeconds ->
                            viewModel.startNewGame(playerCount, names, colors, step, timerSeconds)
                        },
                        onViewHistory = { viewModel.showHistoryScreen() }
                    )
                }
                showHistory -> {
                    GameHistoryScreen(
                        history = history,
                        onDismiss = { viewModel.dismissHistory() }
                    )
                }
                else -> {
                    currentGame?.let { game ->
                        GameBoardScreen(
                            game = game,
                            viewModel = viewModel,
                            onBack = { viewModel.showHistoryScreen() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(
    onStart: (Int, List<String>, List<Int>, Int, Int) -> Unit,
    onViewHistory: () -> Unit
) {
    val ctx = LocalContext.current
    val vibrator = remember { ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    var playerCount by remember { mutableIntStateOf(2) }
    var names by remember { mutableStateOf("Player 1, Player 2") }
    var globalStep by remember { mutableIntStateOf(5) }
    var timerSeconds by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = ScoreAnythingColors.BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("New Game") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScoreAnythingColors.BackgroundDark,
                    titleContentColor = ScoreAnythingColors.OnBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onViewHistory) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ScoreAnythingColors.OnBackground)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Set Up Your Game",
                style = MaterialTheme.typography.headlineSmall,
                color = ScoreAnythingColors.OnBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Text("Number of Players", style = MaterialTheme.typography.labelLarge, color = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(12) { i ->
                    FilterChip(
                        selected = playerCount == i + 1,
                        onClick = { playerCount = i + 1 },
                        label = { Text((i + 1).toString()) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScoreAnythingColors.Accent,
                            selectedLabelColor = ScoreAnythingColors.OnBackground
                        )
                    )
                }
            }

            Text("Player Colors — drag the colored circle like a rotary dial", style = MaterialTheme.typography.labelLarge, color = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                repeat(playerCount.coerceAtMost(6)) { i ->
                    val color = ScoreAnythingColors.PlayerColors.getOrElse(i) { 0xFF5B9BD5.toInt() }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(2.dp, ScoreAnythingColors.OnSurface.copy(alpha = 0.3f), CircleShape)
                    )
                }
            }

            if (playerCount > 6) {
                Text(
                    "Showing first 6 color chips. Remaining players get auto-assigned colors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f)
                )
            }

            OutlinedTextField(
                value = names,
                onValueChange = { names = it },
                label = { Text("Player Names (comma-separated)") },
                placeholder = { Text("e.g. Alice, Bob, Charlie") },
                singleLine = true,
                supportingText = {
                    val parsed = names.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    Text("${parsed.size} of $playerCount names entered")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ScoreAnythingColors.SurfaceDark,
                    unfocusedContainerColor = ScoreAnythingColors.SurfaceDark,
                    focusedBorderColor = ScoreAnythingColors.Accent,
                    unfocusedBorderColor = ScoreAnythingColors.OnSurface.copy(alpha = 0.3f),
                    focusedLabelColor = ScoreAnythingColors.Accent,
                    unfocusedLabelColor = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f),
                    cursorColor = ScoreAnythingColors.Accent
                )
            )

            OutlinedTextField(
                value = globalStep.toString(),
                onValueChange = { globalStep = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                label = { Text("Points per full rotation") },
                placeholder = { Text("e.g. 5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ScoreAnythingColors.SurfaceDark,
                    unfocusedContainerColor = ScoreAnythingColors.SurfaceDark,
                    focusedBorderColor = ScoreAnythingColors.Accent,
                    unfocusedBorderColor = ScoreAnythingColors.OnSurface.copy(alpha = 0.3f),
                    focusedLabelColor = ScoreAnythingColors.Accent,
                    unfocusedLabelColor = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f),
                    cursorColor = ScoreAnythingColors.Accent
                )
            )

            Text("Timer (seconds, 0 = off)", style = MaterialTheme.typography.labelLarge, color = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f))
            Slider(
                value = timerSeconds.toFloat(),
                onValueChange = { timerSeconds = it.toInt() },
                valueRange = 0f..3600f,
                steps = if (timerSeconds > 0) 3599 else 0,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Off", style = MaterialTheme.typography.bodySmall, color = ScoreAnythingColors.OnSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.weight(1f))
                Text("${timerSeconds}s", style = MaterialTheme.typography.bodyMedium, color = ScoreAnythingColors.OnBackground, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = {
                    val parsedNames = names.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (parsedNames.size != playerCount) return@Button
                    onStart(playerCount, parsedNames, ScoreAnythingColors.PlayerColors.take(playerCount).toList(), globalStep, timerSeconds)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ScoreAnythingColors.Accent)
            ) {
                Text("Start Game", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(onClick = onViewHistory, modifier = Modifier.fillMaxWidth()) {
                Text("View Game History")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameBoardScreen(
    game: Game,
    viewModel: ScoreViewModel,
    onBack: () -> Unit
) {
    val step = game.step
    val circleSize = if (game.players.size > 6) 72.dp else 96.dp

    Scaffold(
        containerColor = ScoreAnythingColors.BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (game.winner() != null) "🏆 ${game.winner()!!.name} WINS!"
                        else "${game.players.size} Players",
                        fontWeight = if (game.winner() != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (game.winner() != null) ScoreAnythingColors.WinnerGold else ScoreAnythingColors.OnBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ScoreAnythingColors.OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScoreAnythingColors.BackgroundDark,
                    titleContentColor = ScoreAnythingColors.OnBackground,
                    navigationIconContentColor = ScoreAnythingColors.OnBackground
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(game.players.size) { idx ->
                val player = game.players[idx]
                RotaryDialTile(
                    player = player,
                    score = game.currentScore(player.id),
                    step = step,
                    isWinner = game.winner()?.id == player.id,
                    circleSize = circleSize,
                    onScoreAdd = { points -> viewModel.addScore(player.id, points) },
                    onClear = { viewModel.resetPlayerScore(player.id) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.resetAllScores() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ScoreAnythingColors.UndoDisabled)
                ) {
                    Text("Clear All Scores")
                }
            }
        }
    }
}

@Composable
fun RotaryDialTile(
    player: Player,
    score: Int,
    step: Int,
    isWinner: Boolean,
    circleSize: Dp,
    onScoreAdd: (Int) -> Unit,
    onClear: () -> Unit
) {
    val ctx = LocalContext.current
    val rot = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val appliedRotation = remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isWinner) ScoreAnythingColors.WinnerGold.copy(alpha = 0.15f) else Color.Transparent)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val sizePx = with(density) { (circleSize + 12.dp).toPx() }
            Box(
                modifier = Modifier
                    .size(circleSize + 12.dp)
                    .clip(CircleShape)
                    .background(if (isWinner) ScoreAnythingColors.WinnerGold.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
            )
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .offset(y = 6.dp)
                    .clip(CircleShape)
                    .background(if (isWinner) ScoreAnythingColors.WinnerGold else Color(player.color))
                    .rotate(if (isPressed) 10f else 0f)
                    .pointerInput(player.id) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val cx = sizePx / 2f
                                val cy = sizePx / 2f
                                val dx = offset.x - cx
                                val dy = offset.y - cy
                                val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                                appliedRotation.value = angle
                            },
                            onDrag = { change, _ ->
                                val cx = sizePx / 2f
                                val cy = sizePx / 2f
                                val dx = change.position.x - cx
                                val dy = change.position.y - cy
                                val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                                val prev = appliedRotation.value
                                var delta = angle - prev
                                if (delta > PI.toFloat()) delta -= 2f * PI.toFloat()
                                else if (delta < -PI.toFloat()) delta += 2f * PI.toFloat()
                                appliedRotation.value = angle
                                val degrees = delta * 180f / PI.toFloat()
                                if (degrees >= 360f) {
                                    val turns = (degrees / 360f).toInt()
                                    onScoreAdd(turns * step)
                                    appliedRotation.value = angle - (turns * 360f).toFloat()
                                } else if (degrees <= -360f) {
                                    val turns = (-degrees / 360f).toInt()
                                    onScoreAdd(-turns * step)
                                    appliedRotation.value = angle + (turns * 360f).toFloat()
                                }
                            },
                            onDragEnd = {
                                appliedRotation.value = 0f
                            },
                            onDragCancel = {
                                appliedRotation.value = 0f
                            }
                        )
                    }
                    .pointerInput(player.id) {
                        detectTapGestures(
                            onTap = {
                                vibrate(ctx)
                                onScoreAdd(step)
                            }
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ScoreAnythingColors.OnBackground.copy(alpha = 0.9f))
                    .offset(y = 10.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = circleSize + 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = player.name, style = MaterialTheme.typography.titleMedium,
                color = if (isWinner) ScoreAnythingColors.WinnerGold else ScoreAnythingColors.OnBackground,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "$score", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = if (score > 0) ScoreAnythingColors.OnBackground else ScoreAnythingColors.OnSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center)
            if (!isWinner) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = ScoreAnythingColors.UndoDisabled)
                ) {
                    Text("Reset Score", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun vibrate(ctx: Context) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(12)
        }
    } catch (_: Exception) { }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistoryScreen(
    history: List<Game>,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Game History") },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ScoreAnythingColors.BackgroundDark,
                titleContentColor = ScoreAnythingColors.OnBackground,
                navigationIconContentColor = ScoreAnythingColors.OnBackground
            )
        )
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No games yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history.size) { idx ->
                    val game = history[idx]
                    Text("${game.players.size} players — ${game.winner()?.name ?: "no winner"}", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
