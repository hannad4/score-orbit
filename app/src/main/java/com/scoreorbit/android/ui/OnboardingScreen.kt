package com.scoreorbit.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.ui.theme.ScoreOrbitColors
import com.scoreorbit.android.viewmodel.ScoreViewModel
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * First-run setup wizard: four swipeable pages that set the table and walk
 * through how to use the board, ending back on it. Page three is the only
 * thing that edits state — the player and team counts, live on the game;
 * everything else is words and pictures.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    game: Game,
    viewModel: ScoreViewModel,
    initialPage: Int = 0,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val theme = MaterialTheme.colorScheme

    // Back steps through the pages; on page one it falls through to the
    // system default (leaving the app), so nothing traps anybody.
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> WelcomePage(game)
                1 -> HowToScorePage(game)
                2 -> TableSetupPage(game, viewModel)
                else -> AllSetPage(game)
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
                repeat(4) { i ->
                    val active = pagerState.currentPage == i
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
                TextButton(onClick = { viewModel.completeOnboarding() }) {
                    Text("Skip")
                }
                Spacer(modifier = Modifier.weight(1f))
                val last = pagerState.currentPage == 3
                Button(
                    onClick = {
                        if (last) {
                            viewModel.completeOnboarding()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                ) {
                    Text(if (last) "Start playing" else "Next")
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

/** Bullet row with a colored dot marker, matching the README banners. */
@Composable
private fun WizardBullet(color: Color, text: String) {
    val theme = MaterialTheme.colorScheme
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
            text,
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
private fun WelcomePage(game: Game) {
    WizardPage(
        title = "Welcome to Score Orbit",
        subtitle = "The scorekeeper that lives on the table \u2014 every player " +
            "around one shared dial. Three quick steps and you're playing.",
    ) {
        OrbitArt(modifier = Modifier.size(230.dp))
    }
}

@Composable
private fun HowToScorePage(game: Game) {
    val dots = wizardDots()
    WizardPage(
        title = "Spin to score",
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WizardBullet(
                dots[0],
                "Drag your dot around the ring \u2014 a full turn scores " +
                    "${game.rotationPoints} points",
            )
            WizardBullet(
                dots[1],
                "Let go and the dial springs back, points banked",
            )
            WizardBullet(
                dots[2],
                "Tap a dot to turn its score to face that seat",
            )
            WizardBullet(
                dots[3],
                "Long-press a score and drop it on another seat to swap",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableSetupPage(game: Game, viewModel: ScoreViewModel) {
    WizardPage(
        title = "Your table",
        subtitle = "Match the board to tonight's game \u2014 anything can still be " +
            "changed later in Settings.",
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
                        "Number of Teams",
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
                        "Players are dealt out evenly at random across the teams.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Names and colors are edited in Roster Setup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AllSetPage(game: Game) {
    val theme = MaterialTheme.colorScheme
    WizardPage(
        title = "You're all set",
    ) {
        val setup = buildString {
            append("${game.players.size} players")
            append(if (game.teams.isEmpty()) " \u00b7 solo table" else " \u00b7 ${game.teams.size} teams")
            append(" \u00b7 ${game.rotationPoints} points per lap")
        }
        Text(
            setup,
            style = MaterialTheme.typography.titleMedium,
            color = theme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Column {
            WizardIconRow(Icons.Default.History, "The clock opens score history with undo and redo")
            WizardIconRow(Icons.Default.Leaderboard, "The bar chart opens the leaderboard")
            WizardIconRow(Icons.Default.Timer, "The timer pill runs your turn countdown \u2014 it rings until you stop it")
            WizardIconRow(Icons.Default.Settings, "The gear keeps teams, scoring rules, Score to Win and more")
        }
    }
}

@Composable
private fun WizardIconRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = theme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = theme.onSurfaceVariant,
        )
    }
}
