package com.scoreorbit.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.model.Player
import com.scoreorbit.android.ui.theme.ScoreOrbitColors
import com.scoreorbit.android.viewmodel.ScoreViewModel
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val PAGE_WELCOME = 0
private const val PAGE_SETUP = 5
private const val PAGE_COUNT = 6

/**
 * First-run wizard. The welcome page offers two ways in: a quick start that
 * jumps straight to table setup, or a swipeable tour of the app's features
 * (dial, teams, winning, timer and history) that ends on the same setup page.
 * Setup is the only page that edits state — the player and team counts, live
 * on the game; everything else is words and pictures.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    game: Game,
    viewModel: ScoreViewModel,
    initialPage: Int = PAGE_WELCOME,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val theme = MaterialTheme.colorScheme
    val page = pagerState.currentPage
    // Quick start skips the tour, so back from setup returns to the welcome
    // page rather than walking through pages that were never shown.
    var quick by remember { mutableStateOf(false) }

    fun goTo(target: Int) {
        scope.launch { pagerState.animateScrollToPage(target) }
    }

    // Back steps through the pages; on page one it falls through to the
    // system default (leaving the app), so nothing traps anybody.
    BackHandler(enabled = page > PAGE_WELCOME) {
        goTo(if (quick && page == PAGE_SETUP) PAGE_WELCOME else page - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { p ->
            when (p) {
                0 -> WelcomePage()
                1 -> SpinPage(game)
                2 -> TeamsPage()
                3 -> WinningPage()
                4 -> ToolsPage()
                else -> TableSetupPage(game, viewModel)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Page dots.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(PAGE_COUNT) { i ->
                    val active = page == i
                    val size by animateDpAsState(if (active) 11.dp else 8.dp)
                    Spacer(modifier = Modifier.width(if (i > 0) 7.dp else 0.dp))
                    Box(
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(
                                if (active) theme.primary
                                else theme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (page) {
                    PAGE_WELCOME -> {
                        OutlinedButton(
                            onClick = {
                                quick = true
                                goTo(PAGE_SETUP)
                            },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        ) { Text("Quick start") }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { goTo(1) },
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        ) { Text("Take the tour") }
                    }
                    PAGE_SETUP -> {
                        TextButton(
                            onClick = { goTo(if (quick) PAGE_WELCOME else page - 1) },
                        ) { Text("Back") }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { viewModel.completeOnboarding() },
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                        ) { Text("Start playing") }
                    }
                    else -> {
                        // Skipping the rest of the tour still lands on setup.
                        TextButton(onClick = { goTo(PAGE_SETUP) }) { Text("Skip tour") }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { goTo(page + 1) },
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                        ) { Text("Next") }
                    }
                }
            }
        }
    }
}

/** Shared page chrome: heading centered above the page content. */
@Composable
private fun WizardPage(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(0.8f))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp),
        ) { content() }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Bullet row with a colored dot marker. When the text points at something
 * the user taps on the board, pass it as [icon] and the actual glyph shows
 * inline in the sentence instead of words like "the history icon".
 */
private const val INLINE_ICON_ID = "ic"

@Composable
private fun WizardBullet(
    color: Color,
    prefix: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    suffix: String = "",
) {
    val theme = MaterialTheme.colorScheme
    val inline = if (icon == null) emptyMap() else mapOf(
        INLINE_ICON_ID to InlineTextContent(
            Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            buildAnnotatedString {
                append(prefix)
                if (icon != null) appendInlineContent(INLINE_ICON_ID, icon.name)
                append(suffix)
            },
            inlineContent = inline,
            style = MaterialTheme.typography.bodyLarge,
            color = theme.onSurfaceVariant,
        )
    }
}

private fun wizardDots(): List<Color> =
    ScoreOrbitColors.PlayerColors.take(4).map { Color(it) }

/** Ring-of-dots art, echoing the real board. */
@Composable
private fun OrbitArt(modifier: Modifier = Modifier) {
    val theme = MaterialTheme.colorScheme
    val dots = wizardDots()
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val ringR = size.minDimension * 0.30f
        val strokeWidth = size.minDimension * 0.17f
        // The track.
        drawCircle(
            color = theme.onSurfaceVariant.copy(alpha = 0.22f),
            radius = ringR,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth),
        )
        // Player dots riding the track at the board's corner angles.
        val dotR = size.minDimension * 0.115f
        val angles = listOf(225f, 315f, 135f, 45f)
        val d = ringR + strokeWidth / 2f
        dots.forEachIndexed { i, color ->
            val a = Math.toRadians(angles[i].toDouble())
            drawCircle(
                color = color,
                radius = dotR,
                center = Offset(
                    cx + d * cos(a).toFloat(),
                    cy + d * sin(a).toFloat(),
                ),
            )
        }
        // One gold bead mid-orbit, like the README header.
        val beadA = Math.toRadians(90.0)
        drawCircle(
            color = ScoreOrbitColors.WinnerGold,
            radius = size.minDimension * 0.055f,
            center = Offset(
                cx + d * cos(beadA).toFloat(),
                cy + d * sin(beadA).toFloat(),
            ),
        )
    }
}

@Composable
private fun WelcomePage() {
    WizardPage(
        title = "Welcome to Score Orbit",
    ) {
        OrbitArt(modifier = Modifier.size(230.dp))
    }
}

/** Tour page chrome: a tinted icon badge above the title and bullets. */
@Composable
private fun TourPage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val theme = MaterialTheme.colorScheme
    WizardPage(title = title, subtitle = subtitle) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(theme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = theme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        content()
    }
}

@Composable
private fun SpinPage(game: Game) {
    val dots = wizardDots()
    WizardPage(
        title = "How scoring works",
        subtitle = "Everyone scores on the same dial.",
    ) {
        OrbitArt(modifier = Modifier.size(150.dp))
        Spacer(modifier = Modifier.height(12.dp))
        WizardBullet(
            dots[0],
            "Drag your dot around the ring. A full turn scores ",
            null,
            "${game.rotationPoints} points",
        )
        WizardBullet(dots[1], "Let go and it springs back. Your points count.")
        WizardBullet(dots[2], "Tap a score to turn which way it faces.")
        WizardBullet(dots[3], "Long-press a score and drop it on another seat to swap players.")
    }
}

@Composable
private fun TeamsPage() {
    val dots = wizardDots()
    TourPage(
        icon = Icons.Default.Groups,
        title = "Playing in teams",
        subtitle = "Play solo, or split into up to 4 teams.",
    ) {
        WizardBullet(dots[0], "Players are split evenly at random.")
        WizardBullet(dots[1], "Each player still scores alone. Team totals sit beside the dials.")
        WizardBullet(dots[2], "Drag a player onto another team's area to move them.")
    }
}

@Composable
private fun WinningPage() {
    val dots = wizardDots()
    TourPage(
        icon = Icons.Default.EmojiEvents,
        title = "Know who's winning",
    ) {
        WizardBullet(dots[0], "Trophies mark the top three scores.")
        WizardBullet(dots[1], "Tap ", Icons.Default.Leaderboard, " to see every score.")
        WizardBullet(dots[2], "Pick ", Icons.Default.Settings, " whether highest or lowest wins.")
        WizardBullet(dots[3], "Set a Score to Win and the game ends in confetti. Leave it blank to keep going.")
    }
}

@Composable
private fun ToolsPage() {
    TourPage(
        icon = Icons.Default.Timer,
        title = "Handy extras",
        subtitle = "All of these live in the top bar of the board.",
    ) {
        WizardBullet(wizardDots()[0], "Tap ", Icons.Default.History, " to undo or redo a score.")
        WizardBullet(wizardDots()[1], "Tap ", Icons.Default.Timer, " to set a countdown timer.")
        WizardBullet(wizardDots()[2], "Tap ", Icons.Default.Settings, " for teams, scoring rules, Score to Win and more.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableSetupPage(game: Game, viewModel: ScoreViewModel) {
    var colorFor by remember { mutableStateOf<Player?>(null) }
    colorFor?.let { player ->
        ColorPaletteDialog(
            selected = player.color,
            onPick = {
                viewModel.recolorPlayer(player.id, it)
                colorFor = null
            },
            onDismiss = { colorFor = null },
        )
    }
    WizardPage(
        title = "Set your table",
        subtitle = "Set up tonight's game. You can change any of this later in Settings.",
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    StepperCell(
                        value = "${game.players.size}",
                        caption = "Number of Players",
                        onMinus = { viewModel.setPlayerCount(game.players.size - 1) },
                        onPlus = { viewModel.setPlayerCount(game.players.size + 1) },
                        minusEnabled = game.players.size > 1,
                        plusEnabled = game.players.size < 12,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Teams",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val teamOptions = listOf(0, 2, 3, 4)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        teamOptions.forEachIndexed { i, count ->
                            SegmentedButton(
                                selected = game.teams.size == count,
                                onClick = { viewModel.setTeamCount(count) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = i,
                                    count = teamOptions.size,
                                ),
                            ) {
                                Text(if (count == 0) "Solo" else "$count")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Players are split evenly at random; tap a chip to move a player between teams.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (game.teams.isEmpty()) 280.dp else 240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        "Roster",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    game.players.forEachIndexed { index, player ->
                        RosterRow(index, player, game, viewModel,
                            onPickColor = { colorFor = player })
                    }
                }
            }
        }
    }
}

/** One wizard roster row: color dot (tap to pick), name field, and — when
 *  teams exist — a chip per team on a second line. */
@Composable
private fun RosterRow(
    index: Int,
    player: Player,
    game: Game,
    viewModel: ScoreViewModel,
    onPickColor: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color = player.color, onPick = onPickColor)
            Spacer(modifier = Modifier.width(8.dp))
            RosterNameField(
                key = player.id,
                initial = player.name,
                defaultName = "Player ${index + 1}",
                color = Color(player.color),
                onRename = { viewModel.renamePlayer(player.id, it) },
                modifier = Modifier.weight(1f),
            )
        }
        if (game.teams.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 2.dp),
            ) {
                TeamChipRow(
                    player = player,
                    teams = game.teams,
                    onAssign = {
                        // Wizard chips never let a team die: moving the
                        // last member out must keep the team count intact.
                        viewModel.assignPlayer(player.id, it, keepEmpty = true)
                    },
                )
            }
        }
    }
}
