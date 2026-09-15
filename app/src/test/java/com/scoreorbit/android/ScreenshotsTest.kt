package com.scoreorbit.android

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_5
import app.cash.paparazzi.Paparazzi
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.model.Player
import com.scoreorbit.android.model.Rotation
import com.scoreorbit.android.model.ScoreEntry
import com.scoreorbit.android.model.WinMetric
import com.scoreorbit.android.ui.PlayerSetupScreen
import com.scoreorbit.android.ui.ScoreHistoryScreen
import com.scoreorbit.android.ui.ScoreboardScreen
import com.scoreorbit.android.ui.SettingsScreen
import com.scoreorbit.android.ui.theme.ScoreOrbitColors
import com.scoreorbit.android.ui.theme.ScoreOrbitTheme
import com.scoreorbit.android.viewmodel.ScoreViewModel
import com.scoreorbit.android.viewmodel.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

/** Headless screenshots for the README (`recordPaparazziDebug`). */
class ScreenshotsTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = PIXEL_5)

    private fun demoGame(): Game {
        val colors = ScoreOrbitColors.PlayerColors
        val players = listOf(
            Player(id = "p1", name = "Joey", color = colors[4], rotation = Rotation.ROTATED_270),
            Player(id = "p2", name = "Stu", color = colors[2], rotation = Rotation.NONE),
            Player(id = "p3", name = "Tomas", color = colors[0], rotation = Rotation.ROTATED_270),
            Player(id = "p4", name = "Sofia", color = colors[1], rotation = Rotation.NONE),
        )
        val entries = listOf(
            ScoreEntry(id = "e1", playerId = "p1", delta = 30),
            ScoreEntry(id = "e2", playerId = "p2", delta = 45),
            ScoreEntry(id = "e3", playerId = "p3", delta = 37),
            ScoreEntry(id = "e4", playerId = "p4", delta = 18),
            ScoreEntry(id = "e5", playerId = "p1", delta = 12),
            ScoreEntry(id = "e6", playerId = "p3", delta = -5),
        )
        return Game(
            name = "Game Night",
            players = players,
            rotationPoints = 10,
            tapPoints = 1,
            winMetric = WinMetric.HIGHEST,
            entries = entries,
        )
    }

    private fun stubViewModel(vararg undo: ScoreEntry): ScoreViewModel {
        val vm = Mockito.mock(ScoreViewModel::class.java)
        Mockito.`when`(vm.undoStack).thenReturn(mutableStateListOf(*undo))
        Mockito.`when`(vm.redoStack).thenReturn(mutableStateListOf())
        Mockito.`when`(vm.themeMode).thenReturn(mutableStateOf(ThemeMode.SYSTEM))
        return vm
    }

    @Test
    fun board() {
        val game = demoGame()
        paparazzi.snapshot {
            ScoreOrbitTheme(dynamicColor = false) {
                ScoreboardScreen(game = game, viewModel = stubViewModel())
            }
        }
    }

    @Test
    fun settings() {
        val game = demoGame()
        paparazzi.snapshot {
            ScoreOrbitTheme(dynamicColor = false) {
                SettingsScreen(game = game, viewModel = stubViewModel())
            }
        }
    }

    @Test
    fun playerSetup() {
        val game = demoGame()
        paparazzi.snapshot {
            ScoreOrbitTheme(dynamicColor = false) {
                PlayerSetupScreen(game = game, viewModel = stubViewModel())
            }
        }
    }

    @Test
    fun scoreHistory() {
        val game = demoGame()
        paparazzi.snapshot {
            ScoreOrbitTheme(dynamicColor = false) {
                ScoreHistoryScreen(game = game, viewModel = stubViewModel(game.entries.last()))
            }
        }
    }

}
