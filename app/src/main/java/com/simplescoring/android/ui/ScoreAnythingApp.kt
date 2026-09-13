package com.simplescoring.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.simplescoring.android.ui.theme.ExpressiveMotion
import com.simplescoring.android.viewmodel.AppScreen
import com.simplescoring.android.viewmodel.ScoreViewModel

@Composable
fun ScoreAnythingApp(viewModel: ScoreViewModel) {
    val screen by viewModel.screen
    val game by viewModel.currentGame
    val history = viewModel.history

    BackHandler(enabled = screen != AppScreen.Board) {
        when (screen) {
            AppScreen.PlayerSetup -> viewModel.go(AppScreen.Settings)
            AppScreen.ScoreHistory -> viewModel.go(AppScreen.Board)
            AppScreen.Settings, AppScreen.GameHistory ->
                if (game != null) viewModel.go(AppScreen.Board)
            AppScreen.Board -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(animationSpec = ExpressiveMotion.contentTween(350)) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = ExpressiveMotion.contentTween(350),
                    )) togetherWith
                    fadeOut(animationSpec = ExpressiveMotion.contentTween(200))
            },
            label = "screen",
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
        ) { target ->
            when (target) {
                AppScreen.Board -> {
                    val g = game
                    if (g != null) ScoreboardScreen(game = g, viewModel = viewModel)
                    else GameHistoryScreen(history = history, currentGame = null, viewModel = viewModel)
                }
                AppScreen.Settings -> SettingsScreen(game = game, viewModel = viewModel)
                AppScreen.PlayerSetup -> {
                    val g = game
                    if (g != null) PlayerSetupScreen(game = g, viewModel = viewModel)
                    else SettingsScreen(game = null, viewModel = viewModel)
                }
                AppScreen.ScoreHistory -> {
                    val g = game
                    if (g != null) ScoreHistoryScreen(game = g, viewModel = viewModel)
                    else GameHistoryScreen(history = history, currentGame = null, viewModel = viewModel)
                }
                AppScreen.GameHistory -> GameHistoryScreen(history = history, currentGame = game, viewModel = viewModel)
            }
        }
    }
}
