package com.simplescoring.android.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.simplescoring.android.model.Game
import com.simplescoring.android.model.Player
import com.simplescoring.android.model.Rotation
import com.simplescoring.android.model.ScoreEntry
import com.simplescoring.android.model.WinMetric
import com.simplescoring.android.repository.GameRepository
import com.simplescoring.android.ui.theme.ScoreAnythingColors
import com.simplescoring.android.util.RotationUtils
import java.util.UUID

sealed interface AppScreen {
    data object Board : AppScreen
    data object Settings : AppScreen
    data object PlayerSetup : AppScreen
    data object ScoreHistory : AppScreen
    data object GameHistory : AppScreen
}

class ScoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository

    private val _currentGame = mutableStateOf<Game?>(null)
    val currentGame: State<Game?> = _currentGame

    private val _screen = mutableStateOf<AppScreen>(AppScreen.Board)
    val screen: State<AppScreen> = _screen

    val history = mutableStateListOf<Game>()
    val undoStack = mutableStateListOf<ScoreEntry>()
    val redoStack = mutableStateListOf<ScoreEntry>()

    init {
        loadHistory()
        // Launch straight into a scoreboard like the iOS app does.
        if (_currentGame.value == null) {
            startNewGame(
                playerCount = 2,
                names = listOf("Player 1", "Player 2"),
                colors = ScoreAnythingColors.PlayerColors.take(2),
                step = 1,
                boardName = "My Game",
                winMetric = WinMetric.HIGHEST,
            )
        }
    }

    fun go(screen: AppScreen) {
        _screen.value = screen
    }

    private fun loadHistory() {
        history.clear()
        history.addAll(repository.loadHistory())
    }

    // -- game lifecycle -----------------------------------------------------

    fun startNewGame(
        playerCount: Int,
        names: List<String>,
        colors: List<Int>,
        step: Int,
        boardName: String = "My Game",
        winMetric: WinMetric = WinMetric.HIGHEST,
        allowNegative: Boolean = false,
        keepLastVisible: Boolean = true,
    ) {
        val count = playerCount.coerceIn(1, 12)
        val players = (0 until count).map { i ->
            Player(
                id = UUID.randomUUID().toString(),
                name = names.getOrElse(i) { "Player ${i + 1}" }.ifBlank { "Player ${i + 1}" },
                color = colors.getOrElse(i) { ScoreAnythingColors.PlayerColors[i % ScoreAnythingColors.PlayerColors.size] },
                rotation = RotationUtils.defaultForPosition(i, count),
            )
        }
        _currentGame.value = Game(
            name = boardName.ifBlank { "My Game" },
            players = players,
            step = step.coerceIn(1, 100),
            winMetric = winMetric,
            allowNegative = allowNegative,
            keepLastVisible = keepLastVisible,
            createdAt = System.currentTimeMillis(),
        )
        undoStack.clear()
        redoStack.clear()
        _screen.value = AppScreen.Board
    }

    /** Fresh scores, same setup (iOS "Start a New Game"). */
    fun restartWithSameSetup() {
        val game = _currentGame.value ?: return
        startNewGame(
            playerCount = game.players.size,
            names = game.players.map { it.name },
            colors = game.players.map { it.color },
            step = game.step,
            boardName = game.name,
            winMetric = game.winMetric,
            allowNegative = game.allowNegative,
            keepLastVisible = game.keepLastVisible,
        )
    }

    fun finishGame() {
        val game = _currentGame.value ?: return
        val winner = game.winner()
        val finished = game.copy(finishedAt = System.currentTimeMillis(), winnerId = winner?.id)
        repository.appendGame(finished)
        _currentGame.value = null
        undoStack.clear()
        redoStack.clear()
        loadHistory()
        _screen.value = AppScreen.GameHistory
    }

    fun resumeGame(game: Game) {
        _currentGame.value = game.copy(finishedAt = null, winnerId = null)
        undoStack.clear()
        redoStack.clear()
        _screen.value = AppScreen.Board
    }

    fun deleteGame(game: Game) {
        repository.deleteGame(game.id)
        loadHistory()
    }

    fun clearHistory() {
        repository.clearHistory()
        loadHistory()
    }

    // -- scoring ------------------------------------------------------------

    fun addScore(playerId: String, delta: Int) {
        val game = _currentGame.value ?: return
        if (delta == 0) return
        var fixed = delta
        if (!game.allowNegative) {
            val total = game.currentScore(playerId)
            if (total + fixed < 0) fixed = -total
            if (fixed == 0) return
        }
        val entry = ScoreEntry(playerId = playerId, delta = fixed, timestamp = System.currentTimeMillis())
        _currentGame.value = game.copy(entries = game.entries + entry)
        undoStack.add(entry)
        redoStack.clear()
    }

    fun undo() {
        val game = _currentGame.value ?: return
        val last = undoStack.removeLastOrNull() ?: return
        _currentGame.value = game.copy(entries = game.entries.filterNot { it.id == last.id })
        redoStack.add(last)
    }

    fun redo() {
        val game = _currentGame.value ?: return
        val entry = redoStack.removeLastOrNull() ?: return
        _currentGame.value = game.copy(entries = game.entries + entry)
        undoStack.add(entry)
    }

    fun rotatePlayer(playerId: String) {
        val game = _currentGame.value ?: return
        val updated = game.players.map { p -> if (p.id == playerId) p.copy(rotation = p.rotation.next()) else p }
        _currentGame.value = game.copy(players = updated)
    }

    fun resetAllScores() {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(entries = emptyList())
        undoStack.clear()
        redoStack.clear()
    }

    // -- setup edits (live on the current game) ------------------------------

    fun setBoardName(name: String) {
        _currentGame.value = _currentGame.value?.copy(name = name)
    }

    fun setStep(step: Int) {
        _currentGame.value = _currentGame.value?.copy(step = step.coerceIn(1, 100))
    }

    fun setAllowNegative(allow: Boolean) {
        _currentGame.value = _currentGame.value?.copy(allowNegative = allow)
    }

    fun setKeepLastVisible(keep: Boolean) {
        _currentGame.value = _currentGame.value?.copy(keepLastVisible = keep)
    }

    fun setPlayerCount(count: Int) {
        val game = _currentGame.value ?: return
        val target = count.coerceIn(1, 12)
        if (target == game.players.size) return
        val players = (0 until target).map { i ->
            game.players.getOrElse(i) {
                Player(
                    name = "Player ${i + 1}",
                    color = nextFreeColor(game.players.map { it.color }, i),
                    rotation = RotationUtils.defaultForPosition(i, target),
                )
            }
        }
        val ids = players.map { it.id }.toSet()
        _currentGame.value = game.copy(
            players = players,
            entries = game.entries.filter { it.playerId in ids },
        )
        undoStack.clear()
        redoStack.clear()
    }

    fun addPlayer() {
        val game = _currentGame.value ?: return
        if (game.players.size >= 12) return
        setPlayerCount(game.players.size + 1)
    }

    fun removePlayer(playerId: String) {
        val game = _currentGame.value ?: return
        if (game.players.size <= 1) return
        _currentGame.value = game.copy(
            players = game.players.filterNot { it.id == playerId },
            entries = game.entries.filterNot { it.playerId == playerId },
        )
        undoStack.clear()
        redoStack.clear()
    }

    fun renamePlayer(playerId: String, name: String) {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(
            players = game.players.map { if (it.id == playerId) it.copy(name = name.ifBlank { it.name }) else it }
        )
    }

    fun recolorPlayer(playerId: String, color: Int) {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(
            players = game.players.map { if (it.id == playerId) it.copy(color = color) else it }
        )
    }

    private fun nextFreeColor(used: List<Int>, index: Int): Int {
        val palette = ScoreAnythingColors.PlayerColors
        return palette.firstOrNull { it !in used } ?: palette[index % palette.size]
    }
}
