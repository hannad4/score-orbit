package com.simplescoring.android.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.simplescoring.android.model.Game
import com.simplescoring.android.model.Player
import com.simplescoring.android.model.Rotation
import com.simplescoring.android.model.ScoreEntry
import com.simplescoring.android.repository.GameRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class ScoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository

    private val _currentGame = mutableStateOf<Game?>(null)
    val currentGame: State<Game?> = _currentGame

    private val _showHistory = mutableStateOf(false)
    val showHistory: State<Boolean> = _showHistory

    val history = mutableStateListOf<Game>()

    val undoStack = mutableStateListOf<ScoreEntry>()

    private val _elapsedSeconds = mutableLongStateOf(0L)
    val elapsedSeconds: State<Long> = _elapsedSeconds

    private var timerJob: Job? = null

    init {
        loadHistory()
    }

    private fun loadHistory() {
        history.clear()
        history.addAll(repository.loadHistory())
    }

    fun startNewGame(playerCount: Int, names: List<String>, colors: List<Int>, step: Int, timerSeconds: Int) {
        val players = (0 until playerCount).map { i ->
            Player(
                id = UUID.randomUUID().toString(),
                name = names.getOrElse(i) { "Player ${i + 1}" },
                color = colors.getOrElse(i) { 0xFF5B9BD5.toInt() },
                rotation = Rotation.NONE
            )
        }
        _currentGame.value = Game(players = players, step = step, createdAt = System.currentTimeMillis())
        _elapsedSeconds.value = 0L
        undoStack.clear()
        timerJob?.cancel()
        if (timerSeconds > 0) {
            timerJob = MainScope().launch {
                var remaining = timerSeconds
                while (remaining > 0 && _currentGame.value != null) {
                    delay(1000)
                    _elapsedSeconds.value = (_elapsedSeconds.value ?: 0L) + 1
                    remaining--
                    if (remaining <= 0) finishGame()
                }
            }
        }
    }

    fun addScore(playerId: String, delta: Int) {
        val game = _currentGame.value ?: return
        val entry = ScoreEntry(playerId = playerId, delta = delta, timestamp = System.currentTimeMillis())
        _currentGame.value = game.copy(entries = game.entries + entry)
        undoStack.add(entry)
    }

    fun undo() {
        val game = _currentGame.value ?: return
        val last = undoStack.removeLastOrNull() ?: return
        _currentGame.value = game.copy(entries = game.entries.filterNot { it.id == last.id })
    }

    fun rotatePlayer(playerId: String) {
        val game = _currentGame.value ?: return
        val updated = game.players.map { p -> if (p.id == playerId) p.copy(rotation = p.rotation.next()) else p }
        _currentGame.value = game.copy(players = updated)
    }

    fun flipAllOrientation() {
        val game = _currentGame.value ?: return
        val updated = game.players.map { it.copy(rotation = Rotation.NONE) }
        _currentGame.value = game.copy(players = updated)
    }

    fun finishGame() {
        val game = _currentGame.value ?: return
        timerJob?.cancel()
        timerJob = null
        val winner = game.winner()
        _currentGame.value = game.copy(finishedAt = System.currentTimeMillis(), winnerId = winner?.id)
        repository.appendGame(_currentGame.value!!)
        _currentGame.value = null
        _showHistory.value = true
        _elapsedSeconds.value = 0L
        loadHistory()
    }

    fun showHistoryScreen() { _showHistory.value = true }
    fun dismissHistory() { _showHistory.value = false }

    fun resumeGame(game: Game) {
        _currentGame.value = game.copy(finishedAt = null, winnerId = null)
        _showHistory.value = false
        _elapsedSeconds.value = 0L
        undoStack.clear()
        timerJob?.cancel()
        timerJob = null
    }

    fun deleteGame(game: Game) { GameRepository.deleteGame(game.id); loadHistory() }
    fun clearHistory() { GameRepository.clearHistory(); loadHistory() }

    fun resetPlayerScore(playerId: String) {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(entries = game.entries.filter { it.playerId != playerId })
    }

    fun resetAllScores() {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(entries = emptyList())
    }

    companion object {
        fun formatElapsed(totalSeconds: Long): String {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        }
    }
}
