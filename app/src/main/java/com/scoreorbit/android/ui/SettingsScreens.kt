package com.scoreorbit.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.model.Player
import com.scoreorbit.android.model.Team
import com.scoreorbit.android.model.WinMetric
import com.scoreorbit.android.ui.theme.ScoreOrbitColors
import com.scoreorbit.android.viewmodel.AppScreen
import com.scoreorbit.android.viewmodel.ScoreViewModel
import com.scoreorbit.android.viewmodel.ThemeMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin


private val winMetrics = listOf(WinMetric.LOWEST, WinMetric.HIGHEST)

// Discrete haptic strength levels (no drag physics to fight, each tap
// previews itself immediately). Top-level so every recomposition doesn't
// reallocate the list.
private val hapticLevels = listOf(
    "Low" to 35,
    "Med" to 65,
    "High" to 95,
)

/** Game settings: scoreboard identity, setup, and scoring rules. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(game: Game?, viewModel: ScoreViewModel) {
    var showRestartConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current

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
                actions = {
                    FilledTonalButton(
                        onClick = { showRestartConfirm = true },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text("New Game", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
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
                val teamMode = game.teams.isNotEmpty()
                SettingsGroup(label = "Game Setup") {
                    // Local text state: pushing every keystroke into the
                    // viewmodel would rebuild this whole screen per character.
                    // Commits on Done, focus loss, or leaving the screen.
                    var boardNameField by remember(game.id) { mutableStateOf(game.name) }
                    DisposableEffect(game.id) {
                        onDispose {
                            if (boardNameField != viewModel.currentGame.value?.name) {
                                viewModel.setBoardName(boardNameField)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = boardNameField,
                        onValueChange = { boardNameField = it },
                        label = { Text("Scoreboard Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (boardNameField != game.name) viewModel.setBoardName(boardNameField)
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .onFocusChanged { focus ->
                                if (!focus.isFocused && boardNameField != game.name) {
                                    viewModel.setBoardName(boardNameField)
                                }
                            },
                    )
                }

                SettingsGroup(label = "Players") {
                    // Twin steppers, mirroring the Scoring cells: players on
                    // the left, teams on the right. Teams default to 0,
                    // players to 2.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        StepperCell(
                            value = "${game.players.size}",
                            caption = "Number of Players",
                            onMinus = { viewModel.setPlayerCount(game.players.size - 1) },
                            onPlus = { viewModel.setPlayerCount(game.players.size + 1) },
                            minusEnabled = game.players.size > 1,
                            plusEnabled = game.players.size < 12,
                            modifier = Modifier.weight(1f),
                        )
                        StepperCell(
                            value = "${game.teams.size}",
                            caption = "Number of Teams",
                            onMinus = { viewModel.setTeamCount(game.teams.size - 1) },
                            onPlus = { viewModel.setTeamCount(game.teams.size + 1) },
                            minusEnabled = game.teams.size > 0,
                            plusEnabled = game.teams.size < 4,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Roster Setup") },
                        supportingContent = {
                            Text(
                                if (teamMode) "${game.players.size} players \u2022 ${game.teams.size} teams"
                                else "${game.players.size} players"
                            )
                        },
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
                            winMetrics.forEachIndexed { i, metric ->
                                SegmentedButton(
                                    selected = game.winMetric == metric,
                                    onClick = { viewModel.setWinMetric(metric) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = i,
                                        count = winMetrics.size,
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
                        // Discrete strength levels like the Theme control:
                        // no drag physics to fight, and each tap previews
                        // itself immediately.
                        val selected =
                            hapticLevels.minByOrNull { (_, value) -> abs(value - game.hapticStrength) }?.first
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "Haptic Strength",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                hapticLevels.forEachIndexed { i, (label, value) ->
                                    SegmentedButton(
                                        selected = selected == label,
                                        onClick = {
                                            viewModel.setHapticStrength(value)
                                            buzz(ctx, 40, value)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = i,
                                            count = hapticLevels.size,
                                        ),
                                    ) {
                                        Text(label)
                                    }
                                }
                            }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerSetupScreen(game: Game, viewModel: ScoreViewModel) {
    val ctx = LocalContext.current
    var latestGame by remember { mutableStateOf(game) }
    latestGame = game
    var paletteFor by remember { mutableStateOf<Player?>(null) }
    var teamPaletteFor by remember { mutableStateOf<Team?>(null) }
    var assignFor by remember { mutableStateOf<Player?>(null) }
    var showNewTeam by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Long-press-drag reorder state. The dragged card follows the finger via
    // graphicsLayer (placement untouched); neighbors swap live underneath.
    var draggedId by remember(game.id) { mutableStateOf<String?>(null) }
    var dragOffsetY by remember(game.id) { mutableFloatStateOf(0f) }
    var cardHeightPx by remember(game.id) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val stepPx = cardHeightPx + with(density) { 10.dp.toPx() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    // Team mode shows the Teams section and per-player team chips;
    // Individual mode is a pure player list.
    val teamMode = game.teams.isNotEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SharedTopAppBar(
                title = "Roster Setup",
                viewModel = viewModel,
                scrollBehavior = scrollBehavior,
                backDestination = AppScreen.Settings,
                actions = {
                    // Explicit confirmation path: everything already saves
                    // as you type, so Done just steps back to Settings.
                    FilledTonalButton(
                        onClick = { viewModel.go(AppScreen.Settings) },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text("Done", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                },
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Changes apply instantly",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (teamMode) "Drag the handle to reorder \u2022 tap Solo to join a team"
                    else "Drag the handle to reorder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Mini ring preview.
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    RingPreview(players = game.players)
                }
            }
            // Teams live above the roster: color, name, members, delete.
            // Hidden entirely in Individual mode.
            if (teamMode) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Teams",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (game.teams.size < 4) {
                            TextButton(onClick = { showNewTeam = true }) {
                                Text("New team")
                            }
                        }
                    }
                }
                items(game.teams, key = { it.id }) { team ->
                    val members = game.players.filter { p -> p.teamId == team.id }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorDot(color = team.color, onPick = { teamPaletteFor = team })
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                RosterNameField(
                                    key = team.id,
                                    initial = team.name,
                                    defaultName = "",
                                    color = Color(team.color),
                                    onRename = { viewModel.renameTeam(team.id, it) },
                                )
                                Text(
                                    if (members.isEmpty()) "No players yet"
                                    else members.joinToString { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteTeam(team.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete team",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            items(game.players, key = { it.id }) { player ->
                val team = game.teams.firstOrNull { t -> t.id == player.teamId }
                val isDragged = draggedId == player.id
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier
                        .onSizeChanged { if (cardHeightPx == 0) cardHeightPx = it.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            if (isDragged) {
                                translationY = dragOffsetY
                                scaleX = 1.03f
                                scaleY = 1.03f
                                shadowElevation = 24f
                            }
                        }
                        .animateItemPlacement(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .pointerInput(player.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = player.id
                                            dragOffsetY = 0f
                                            if (latestGame.hapticsEnabled) {
                                                buzz(ctx, 25, latestGame.hapticStrength)
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffsetY += amount.y
                                            if (stepPx > 0f) {
                                                val shift = (dragOffsetY / stepPx).roundToInt()
                                                if (shift != 0) {
                                                    val g = latestGame
                                                    val idx = g.players.indexOfFirst { p -> p.id == player.id }
                                                    if (idx >= 0) {
                                                        viewModel.movePlayer(player.id, idx + shift)
                                                        dragOffsetY -= shift * stepPx
                                                        if (latestGame.hapticsEnabled) {
                                                            buzz(ctx, 12, latestGame.hapticStrength)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggedId = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedId = null
                                            dragOffsetY = 0f
                                        },
                                    )
                                },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        ColorDot(color = player.color, onPick = { paletteFor = player })
                        Spacer(modifier = Modifier.width(4.dp))
                        RosterNameField(
                            key = player.id,
                            initial = player.name,
                            defaultName = "Player ${game.players.indexOfFirst { p -> p.id == player.id } + 1}",
                            color = Color(player.color),
                            onRename = { viewModel.renamePlayer(player.id, it) },
                            modifier = Modifier.weight(1f),
                        )
                        if (teamMode) {
                            AssistChip(
                                onClick = { assignFor = player },
                                label = { Text(team?.name ?: "Solo") },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Color(
                                                    team?.color
                                                        ?: ScoreOrbitColors.PlayerColors[0]
                                                )
                                            ),
                                    )
                                },
                            )
                        }
                        if (game.players.size > 1) {
                            IconButton(
                                onClick = { viewModel.removePlayer(player.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove player",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
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
    teamPaletteFor?.let { team ->
        ColorPaletteDialog(
            selected = team.color,
            onPick = {
                viewModel.recolorTeam(team.id, it)
                teamPaletteFor = null
            },
            onDismiss = { teamPaletteFor = null },
        )
    }
    assignFor?.let { player ->
        AssignTeamDialog(
            playerName = player.name,
            currentTeamId = player.teamId,
            teams = game.teams,
            onConfirm = { pick, newName ->
                if (pick == NEW_TEAM_ID) {
                    val id = viewModel.createTeam(newName)
                    if (id != null) viewModel.assignPlayer(player.id, id)
                } else {
                    viewModel.assignPlayer(player.id, pick)
                }
                assignFor = null
            },
            onDismiss = { assignFor = null },
        )
    }
    if (showNewTeam) {
        NewTeamDialog(
            onConfirm = {
                viewModel.createTeam(it)
                showNewTeam = false
            },
            onDismiss = { showNewTeam = false },
        )
    }
}

/** Tint shown for solos in the team chip when they belong to no team. */
private const val NEW_TEAM_ID = "__new__"

/** Color dot that opens the palette dialog. Shared by player and team rows. */
@Composable
private fun ColorDot(color: Int, onPick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onPick),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(color))
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
}

/**
 * Hint-style name box: untouched defaults live as an empty box with the
 * default as the hint, so there is never any text to fight over — tapping
 * just types. Custom names still get select-all on tap (re-applied after
 * the tap lands, since the tap re-places the cursor after focus and would
 * clobber an immediate selection). Blank always saves back to the default,
 * so model, box and hint can never disagree. The keyboard treats it as a
 * person's name, so autocapitalization kicks in.
 */
@Composable
private fun RosterNameField(
    key: String,
    initial: String,
    defaultName: String,
    color: Color,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nameField by remember(key) { mutableStateOf(TextFieldValue(initial)) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var selectJob by remember(key) { mutableStateOf<Job?>(null) }
    fun selectAll() {
        if (nameField.text.isNotEmpty()) {
            nameField = nameField.copy(selection = TextRange(0, nameField.text.length))
        }
    }
    OutlinedTextField(
        value = nameField,
        onValueChange = {
            nameField = it
            onRename(it.text.ifBlank { defaultName })
        },
        singleLine = true,
        label = { Text(defaultName) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        ),
        modifier = modifier
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
            focusedTextColor = color,
            unfocusedTextColor = color,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Pick Solo, an existing team, or name a new one. */
@Composable
private fun AssignTeamDialog(
    playerName: String,
    currentTeamId: String?,
    teams: List<Team>,
    onConfirm: (pick: String?, newName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pick by remember(playerName) { mutableStateOf<String?>(currentTeamId) }
    var newName by remember(playerName) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(playerName) },
        text = {
            Column {
                TeamPickRow(selected = pick == null, onPick = { pick = null }, label = "Solo") {
                    Text("Solo")
                }
                teams.forEach { team ->
                    TeamPickRow(
                        selected = pick == team.id,
                        onPick = { pick = team.id },
                        label = team.name,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(team.color)),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(team.name)
                        }
                    }
                }
                TeamPickRow(
                    selected = pick == NEW_TEAM_ID,
                    onPick = { pick = NEW_TEAM_ID },
                    label = "New team",
                ) {
                    Text("New team")
                }
                if (pick == NEW_TEAM_ID) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("Team name") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pick, newName) }) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TeamPickRow(
    selected: Boolean,
    onPick: () -> Unit,
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onPick,
                role = Role.RadioButton,
            )
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.semantics { contentDescription = label }) {
            content()
        }
    }
}

/** Name-only dialog behind the Teams "New team" button. */
@Composable
private fun NewTeamDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New team") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Team name") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
