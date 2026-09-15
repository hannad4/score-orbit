package com.scoreorbit.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.model.Player
import com.scoreorbit.android.model.WinMetric
import com.scoreorbit.android.ui.theme.ScoreOrbitColors
import com.scoreorbit.android.viewmodel.AppScreen
import com.scoreorbit.android.viewmodel.ScoreViewModel
import com.scoreorbit.android.viewmodel.ThemeMode
import kotlin.math.cos
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin


/** Game settings: scoreboard identity, setup, and scoring rules. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(game: Game?, viewModel: ScoreViewModel) {
    var showRestartConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MediumTopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (game != null) {
                SettingsGroup(label = "Game setup") {
                    OutlinedTextField(
                        value = game.name,
                        onValueChange = { viewModel.setBoardName(it) },
                        label = { Text("Scoreboard Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Number of Players") },
                        leadingContent = {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            StepperControl(
                                value = "${game.players.size}",
                                onMinus = { viewModel.setPlayerCount(game.players.size - 1) },
                                onPlus = { viewModel.setPlayerCount(game.players.size + 1) },
                                minusEnabled = game.players.size > 1,
                                plusEnabled = game.players.size < 12,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Player Setup") },
                        supportingContent = { Text("${game.players.size} players") },
                        leadingContent = {
                            Icon(
                                Icons.Default.ManageAccounts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { viewModel.go(AppScreen.PlayerSetup) },
                    )
                }

                Button(
                    onClick = { showRestartConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("Start a New Game", style = MaterialTheme.typography.titleMedium)
                }

                SettingsGroup(label = "Scoring") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        StepperCell(
                            value = "${game.rotationPoints}",
                            caption = "Points per revolution",
                            onMinus = { viewModel.setRotationPoints(game.rotationPoints - 1) },
                            onPlus = { viewModel.setRotationPoints(game.rotationPoints + 1) },
                            minusEnabled = game.rotationPoints > 1,
                            plusEnabled = game.rotationPoints < 100,
                            modifier = Modifier.weight(1f),
                        )
                        StepperCell(
                            value = "${game.tapPoints}",
                            caption = "Points per tap",
                            onMinus = { viewModel.setTapPoints(game.tapPoints - 1) },
                            onPlus = { viewModel.setTapPoints(game.tapPoints + 1) },
                            minusEnabled = game.tapPoints > 0,
                            plusEnabled = game.tapPoints < 100,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "Winner",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val metrics = listOf(WinMetric.LOWEST, WinMetric.HIGHEST)
                            metrics.forEachIndexed { i, metric ->
                                SegmentedButton(
                                    selected = game.winMetric == metric,
                                    onClick = { viewModel.setWinMetric(metric) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = i,
                                        count = metrics.size,
                                    ),
                                ) {
                                    Text(if (metric == WinMetric.HIGHEST) "Highest" else "Lowest")
                                }
                            }
                        }
                    }
                }

                SettingsGroup(label = "Appearance") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "Theme",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val mode = viewModel.themeMode.value
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { i, entry ->
                                SegmentedButton(
                                    selected = mode == entry,
                                    onClick = { viewModel.setThemeMode(entry) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = i,
                                        count = ThemeMode.entries.size,
                                    ),
                                ) {
                                    Text(
                                        when (entry) {
                                            ThemeMode.SYSTEM -> "System"
                                            ThemeMode.LIGHT -> "Light"
                                            ThemeMode.DARK -> "Dark"
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Show Player Names") },
                        supportingContent = { Text("Display names beside scores") },
                        leadingContent = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = game.showPlayerNames,
                                onCheckedChange = { viewModel.setShowPlayerNames(it) },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Enlarge Active Dot") },
                        supportingContent = { Text("Temporarily enlarge the dot for the currently scoring player") },
                        leadingContent = {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = game.enlargeActiveDot,
                                onCheckedChange = { viewModel.setEnlargeActiveDot(it) },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Haptics") },
                        supportingContent = { Text("Buzz on every full turn") },
                        leadingContent = {
                            Icon(
                                Icons.Default.Vibration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = game.hapticsEnabled,
                                onCheckedChange = { viewModel.setHapticsEnabled(it) },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    AnimatedVisibility(
                        visible = game.hapticsEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Haptic Strength",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${game.hapticStrength}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Slider(
                                value = game.hapticStrength.toFloat(),
                                onValueChange = { viewModel.setHapticStrength(it.toInt()) },
                                valueRange = 0f..100f,
                                steps = 99,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Show Last Score") },
                        supportingContent = { Text("Keep the last score visible until the next player scores") },
                        leadingContent = {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = game.keepLastVisible,
                                onCheckedChange = { viewModel.setKeepLastVisible(it) },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            } else {
                // No active game (just finished): offer a fresh one.
                Button(
                    onClick = {
                        viewModel.startNewGame(
                            2,
                            listOf("Player 1", "Player 2"),
                            ScoreOrbitColors.PlayerColors.take(2),
                            rotationPoints = 10,
                            tapPoints = 0,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start a New Game")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            title = { Text("Start a new game?") },
            text = { Text("Current scores will be cleared. The player setup is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    viewModel.restartWithSameSetup()
                }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            content()
        }
    }
}

@Composable
private fun StepperControl(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onMinus, enabled = minusEnabled, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        FilledTonalIconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}

/** Compact value-forward stepper: big number, steppers, one-line caption. */
@Composable
private fun StepperCell(
    value: String,
    caption: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onMinus, enabled = minusEnabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalIconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Player setup
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSetupScreen(game: Game, viewModel: ScoreViewModel) {
    var paletteFor by remember { mutableStateOf<Player?>(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Player Setup") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.go(AppScreen.Settings) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (game.players.size < 12) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { viewModel.addPlayer() },
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    text = { Text("Add Player") },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Mini ring preview.
            RingPreview(players = game.players, modifier = Modifier.align(Alignment.CenterHorizontally))

            game.players.forEachIndexed { index, player ->
                val defaultName = "Player ${index + 1}"
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { paletteFor = player },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(player.color))
                                    .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            )
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = "Change color",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(21.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 5.dp, y = 5.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        // Hint-style editing: untouched defaults live as an empty
                        // box with the default as the hint, so there is never
                        // any text to fight over — tapping just types. Custom
                        // names still get select-all on tap (re-applied after
                        // the tap lands, since the tap re-places the cursor
                        // after focus and would clobber an immediate selection).
                        // Blank always saves back to the default, so model,
                        // box and hint can never disagree.
                    var nameField by remember(player.id) {
                        mutableStateOf(TextFieldValue(player.name))
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
                            label = { Text(defaultName) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
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
                                            nameField = TextFieldValue(defaultName)
                                        }
                                    }
                                },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color(player.color),
                                unfocusedTextColor = Color(player.color),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        if (game.players.size > 1) {
                            IconButton(onClick = { viewModel.removePlayer(player.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove player", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (game.players.size >= 12) {
                Text(
                    "Maximum 12 players",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(88.dp))
        }
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

/** Small ring with the players' dots, previewing the tabletop board. */
@Composable
private fun RingPreview(players: List<Player>, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(150.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large)
            .padding(8.dp)
            .size(134.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * 0.32f
        drawCircle(
            color = Color.Gray.copy(alpha = 0.35f),
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

/** 24-color palette in a Material dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPaletteDialog(selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Palette, contentDescription = null) },
        title = { Text("Choose color") },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScoreOrbitColors.PlayerColors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (c == selected) MaterialTheme.colorScheme.primary
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
