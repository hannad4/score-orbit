package com.simplescoring.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.simplescoring.android.model.Game
import com.simplescoring.android.model.Player
import com.simplescoring.android.ui.theme.ScoreAnythingColors
import com.simplescoring.android.viewmodel.AppScreen
import com.simplescoring.android.viewmodel.ScoreViewModel
import kotlin.math.cos
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

private val SheetBg = Color(0xFF1C1C1E)
private val CardBg = Color(0xFF2C2C2E)
private val iOSBlue = Color(0xFF0A84FF)

/** iOS-style Settings sheet (see App Store screenshot 4). */
@Composable
fun SettingsScreen(game: Game?, viewModel: ScoreViewModel) {
    var showRestartConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Title + close.
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(
                onClick = { viewModel.go(AppScreen.Board) },
                modifier = Modifier.align(Alignment.CenterEnd).size(32.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
            }
        }

        if (game != null) {
            SectionLabel("SCOREBOARD")
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    OutlinedTextField(
                        value = game.name,
                        onValueChange = { viewModel.setBoardName(it) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = iOSBlue,
                        ),
                    )
                    MiniScorePreview(game)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.go(AppScreen.GameHistory) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Game History", color = Color.White, modifier = Modifier.weight(1f))
                        Text("›", color = Color.White.copy(alpha = 0.4f), fontSize = 20.sp)
                    }
                }
            }

            Button(
                onClick = { showRestartConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CardBg, contentColor = iOSBlue),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Start a New Game", fontWeight = FontWeight.Medium)
            }

            SectionLabel("GAME SETUP")
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    StepperRow(
                        label = "Number of Players",
                        value = "${game.players.size}",
                        onMinus = { viewModel.setPlayerCount(game.players.size - 1) },
                        onPlus = { viewModel.setPlayerCount(game.players.size + 1) },
                        minusEnabled = game.players.size > 1,
                        plusEnabled = game.players.size < 12,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.go(AppScreen.PlayerSetup) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Player Setup", color = Color.White, modifier = Modifier.weight(1f))
                        Text("›", color = Color.White.copy(alpha = 0.4f), fontSize = 20.sp)
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    StepperRow(
                        label = "Score Step",
                        value = "${game.step}",
                        onMinus = { viewModel.setStep(game.step - 1) },
                        onPlus = { viewModel.setStep(game.step + 1) },
                        minusEnabled = game.step > 1,
                        plusEnabled = game.step < 100,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    ToggleRow(
                        label = "Keep Last Score Visible",
                        checked = game.keepLastVisible,
                        onChecked = { viewModel.setKeepLastVisible(it) },
                    )
                }
            }
        } else {
            // No active game (just finished): offer a fresh one.
            Button(
                onClick = {
                    viewModel.startNewGame(2, listOf("Player 1", "Player 2"), ScoreAnythingColors.PlayerColors.take(2), 1)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = iOSBlue),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Start a New Game")
            }
            TextButton(onClick = { viewModel.go(AppScreen.GameHistory) }, modifier = Modifier.fillMaxWidth()) {
                Text("Game History", color = iOSBlue)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("Start a new game?") },
            text = { Text("Current scores will be cleared. The player setup is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    viewModel.restartWithSameSetup()
                }) { Text("Start", color = iOSBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text("Cancel", color = iOSBlue) }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 14.dp, top = 6.dp),
    )
}

/** "17 - 21 - 35" mini preview in player colors. */
@Composable
private fun MiniScorePreview(game: Game) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        game.players.forEachIndexed { i, p ->
            if (i > 0) Text(" - ", color = Color.White.copy(alpha = 0.4f))
            Text("${game.currentScore(p.id)}", color = Color(p.color), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus, enabled = minusEnabled, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = iOSBlue)
        }
        Text(
            value,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        IconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Increase", tint = iOSBlue)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF30D158)),
        )
    }
}

// ---------------------------------------------------------------------------
// Player setup (iOS screenshot 3)
// ---------------------------------------------------------------------------

@Composable
fun PlayerSetupScreen(game: Game, viewModel: ScoreViewModel) {
    var paletteFor by remember { mutableStateOf<Player?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Player Setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(
                onClick = { viewModel.go(AppScreen.Settings) },
                modifier = Modifier.align(Alignment.CenterEnd).size(32.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
            }
        }

        // Mini ring preview.
        RingPreview(players = game.players, modifier = Modifier.align(Alignment.CenterHorizontally))

        game.players.forEachIndexed { index, player ->
            // Untouched players carry the default "Player N" name. Tapping
            // the box auto-deletes it so typing starts clean; tapping a
            // customized name just selects all text instead. (Select-all
            // alone isn't reliable: the tap that focuses the field re-places
            // the cursor after focus, clobbering the selection on device.)
            val defaultName = "Player ${index + 1}"
            Card(                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Hint-style editing: untouched defaults live as an empty
                    // box with the default as the hint, so there is never
                    // any text to fight over — tapping just types. Custom
                    // names still get select-all on tap (re-applied after
                    // the tap lands, since the tap re-places the cursor
                    // after focus and would clobber an immediate selection).
                    // Blank always saves back to the default, so model,
                    // box and hint can never disagree.
                    var nameField by remember(player.id) {
                        mutableStateOf(
                            TextFieldValue(player.name.takeUnless { it == defaultName } ?: "")
                        )
                    }
                    val focusManager = LocalFocusManager.current
                    val scope = rememberCoroutineScope()
                    var selectJob by remember(player.id) { mutableStateOf<Job?>(null) }
                    fun selectAll() {
                        if (nameField.text.isNotEmpty()) {
                            nameField = nameField.copy(
                                selection = TextRange(0, nameField.text.length)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = nameField,
                        onValueChange = {
                            nameField = it
                            viewModel.renamePlayer(player.id, it.text.ifBlank { defaultName })
                        },
                        singleLine = true,
                        placeholder = { Text(defaultName) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focus ->
                                if (focus.isFocused) {
                                    if (nameField.text.isNotEmpty()) {
                                        // Select now AND re-select once the
                                        // tap has landed (see above).
                                        selectAll()
                                        selectJob?.cancel()
                                        selectJob = scope.launch {
                                            delay(150)
                                            selectAll()
                                        }
                                    }
                                } else {
                                    selectJob?.cancel()
                                    if (nameField.text.isBlank()) {
                                        nameField = TextFieldValue("")
                                    }
                                }
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(player.color),
                            unfocusedTextColor = Color(player.color),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = iOSBlue,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(player.color))
                            .clickable { paletteFor = player },
                    )
                    if (game.players.size > 1) {
                        IconButton(onClick = { viewModel.removePlayer(player.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove player", tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        if (game.players.size < 12) {
            Button(
                onClick = { viewModel.addPlayer() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CardBg, contentColor = iOSBlue),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Player")
            }
        } else {
            Text(
                "Maximum 12 players",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    paletteFor?.let { player ->
        ColorPaletteDialog(
            selected = player.color,
            onPick = {
                viewModel.recolorPlayer(player.id, it)
                paletteFor = null
            },
            onDismiss = { paletteFor = null },
        )
    }
}

/** Small ring with the players' dots, like the iOS setup preview. */
@Composable
private fun RingPreview(players: List<Player>, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(150.dp)
            .background(Color.Black, shape = RoundedCornerShape(16.dp))
            .padding(8.dp)
            .size(134.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * 0.32f
        drawCircle(
            color = Color(0xFF262626),
            radius = r,
            center = Offset(cx, cy),
        )
        players.forEachIndexed { i, p ->
            val a = (-90.0 + i * 360.0 / players.size) * Math.PI / 180.0
            drawCircle(
                color = Color(p.color),
                radius = minOf(size.width, size.height) * 0.11f,
                center = Offset(
                    cx + cos(a).toFloat() * r,
                    cy + sin(a).toFloat() * r,
                ),
            )
        }
    }
}

/** 24-color palette popup (iOS screenshot 3). */
@Composable
private fun ColorPaletteDialog(selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3C)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScoreAnythingColors.PlayerColors.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (c == selected) Color.White
                                        else Color.Transparent
                                    )
                                    .clickable { onPick(c) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (c == selected) 32.dp else 34.dp)
                                        .clip(CircleShape)
                                        .background(Color(c)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
