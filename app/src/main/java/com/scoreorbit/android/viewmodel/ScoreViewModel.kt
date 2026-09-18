package com.scoreorbit.android.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.model.Player
import com.scoreorbit.android.model.Rotation
import com.scoreorbit.android.model.ScoreEntry
import com.scoreorbit.android.model.WinMetric
import com.scoreorbit.android.ui.theme.ScoreOrbitColors
import com.scoreorbit.android.util.RotationUtils
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

sealed interface AppScreen {
    data object Board : AppScreen
    data object Settings : AppScreen
    data object PlayerSetup : AppScreen
    data object ScoreHistory : AppScreen
    data object Leaderboard : AppScreen
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class ScoreViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("score_orbit", Context.MODE_PRIVATE)

    private val _themeMode = mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: State<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    private val _currentGame = mutableStateOf<Game?>(null)
    val currentGame: State<Game?> = _currentGame

    private val _screen = mutableStateOf<AppScreen>(AppScreen.Board)
    val screen: State<AppScreen> = _screen

    val undoStack = mutableStateListOf<ScoreEntry>()
    val redoStack = mutableStateListOf<ScoreEntry>()

    init {
        // Restore the saved game so scores survive the process being
        // killed in the background; fresh install starts a new board.
        val saved = restoreGame()
        if (saved != null) {
            _currentGame.value = saved.first
            undoStack.addAll(saved.second)
            redoStack.addAll(saved.third)
        } else {
            startNewGame(
                playerCount = 2,
                names = listOf("Player 1", "Player 2"),
                colors = ScoreOrbitColors.PlayerColors.take(2),
                rotationPoints = 10,
                tapPoints = 0,
                winMetric = WinMetric.HIGHEST,
            )
        }
    }

    fun go(screen: AppScreen) {
        _screen.value = screen
    }

    // -- game lifecycle -----------------------------------------------------

    fun startNewGame(
        playerCount: Int,
        names: List<String>,
        colors: List<Int>,
        rotationPoints: Int,
        tapPoints: Int,
        boardName: String = "",
        winMetric: WinMetric = WinMetric.HIGHEST,
        keepLastVisible: Boolean = false,
        enlargeActiveDot: Boolean = false,
        showPlayerNames: Boolean = true,
        hapticsEnabled: Boolean = false,
        hapticStrength: Int = 65,
    ) {
        val count = playerCount.coerceIn(1, 12)
        val players = (0 until count).map { i ->
            Player(
                id = UUID.randomUUID().toString(),
                name = names.getOrElse(i) { "Player ${i + 1}" }.ifBlank { "Player ${i + 1}" },
                color = colors.getOrElse(i) { ScoreOrbitColors.PlayerColors[i % ScoreOrbitColors.PlayerColors.size] },
                rotation = Rotation.NONE,
            )
        }
        _currentGame.value = Game(
            name = boardName,
            players = players,
            rotationPoints = rotationPoints.coerceIn(1, 100),
            tapPoints = tapPoints.coerceIn(0, 100),
            winMetric = winMetric,
            keepLastVisible = keepLastVisible,
            enlargeActiveDot = enlargeActiveDot,
            showPlayerNames = showPlayerNames,
            hapticsEnabled = hapticsEnabled,
            hapticStrength = hapticStrength,
        )
        undoStack.clear()
        redoStack.clear()
        _screen.value = AppScreen.Board
        persist()
    }

    /** Fresh scores, same setup. */
    fun restartWithSameSetup() {
        val game = _currentGame.value ?: return
        startNewGame(
            playerCount = game.players.size,
            names = game.players.map { it.name },
            colors = game.players.map { it.color },
            rotationPoints = game.rotationPoints,
            tapPoints = game.tapPoints,
            boardName = game.name,
            winMetric = game.winMetric,
            keepLastVisible = game.keepLastVisible,
            enlargeActiveDot = game.enlargeActiveDot,
            showPlayerNames = game.showPlayerNames,
            hapticsEnabled = game.hapticsEnabled,
            hapticStrength = game.hapticStrength,
        )
    }

    // -- scoring ------------------------------------------------------------

    fun addScore(playerId: String, delta: Int) {
        val game = _currentGame.value ?: return
        if (delta == 0) return
        val entry = ScoreEntry(playerId = playerId, delta = delta, timestamp = System.currentTimeMillis())
        _currentGame.value = game.copy(entries = game.entries + entry)
        undoStack.add(entry)
        redoStack.clear()
        persist()
    }

    fun undo() {
        val game = _currentGame.value ?: return
        val last = undoStack.removeLastOrNull() ?: return
        _currentGame.value = game.copy(entries = game.entries.filterNot { it.id == last.id })
        redoStack.add(last)
        persist()
    }

    fun redo() {
        val game = _currentGame.value ?: return
        val entry = redoStack.removeLastOrNull() ?: return
        _currentGame.value = game.copy(entries = game.entries + entry)
        undoStack.add(entry)
        persist()
    }

    fun rotatePlayer(playerId: String) {
        val game = _currentGame.value ?: return
        val updated = game.players.map { p -> if (p.id == playerId) p.copy(rotation = p.rotation.next()) else p }
        _currentGame.value = game.copy(players = updated)
        persist()
    }

    fun resetAllScores() {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(entries = emptyList())
        undoStack.clear()
        redoStack.clear()
        persist()
    }

    // -- setup edits (live on the current game) ------------------------------

    fun setBoardName(name: String) {
        _currentGame.value = _currentGame.value?.copy(name = name)
        persist()
    }

    fun setRotationPoints(points: Int) {
        _currentGame.value = _currentGame.value?.copy(rotationPoints = points.coerceIn(1, 100))
        persist()
    }

    fun setTapPoints(points: Int) {
        _currentGame.value = _currentGame.value?.copy(tapPoints = points.coerceIn(0, 100))
        persist()
    }

    fun setWinMetric(metric: WinMetric) {
        _currentGame.value = _currentGame.value?.copy(winMetric = metric)
        persist()
    }

    fun setKeepLastVisible(keep: Boolean) {
        _currentGame.value = _currentGame.value?.copy(keepLastVisible = keep)
        persist()
    }

    fun setEnlargeActiveDot(enlarge: Boolean) {
        _currentGame.value = _currentGame.value?.copy(enlargeActiveDot = enlarge)
        persist()
    }

    fun setShowPlayerNames(show: Boolean) {
        _currentGame.value = _currentGame.value?.copy(showPlayerNames = show)
        persist()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _currentGame.value = _currentGame.value?.copy(hapticsEnabled = enabled)
        persist()
    }

    fun setHapticStrength(level: Int) {
        _currentGame.value = _currentGame.value?.copy(hapticStrength = level.coerceIn(0, 100))
        persist()
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
                    rotation = Rotation.NONE,
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
        persist()
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
        persist()
    }

    fun renamePlayer(playerId: String, name: String) {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(
            players = game.players.map { if (it.id == playerId) it.copy(name = name.ifBlank { it.name }) else it }
        )
        persist()
    }

    fun recolorPlayer(playerId: String, color: Int) {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(
            players = game.players.map { if (it.id == playerId) it.copy(color = color) else it }
        )
        persist()
    }

    private fun nextFreeColor(used: List<Int>, index: Int): Int {
        val palette = ScoreOrbitColors.PlayerColors
        return palette.firstOrNull { it !in used } ?: palette[index % palette.size]
    }

    // -- background persistence ------------------------------------------------
    // The whole game (setup + ledger + undo/redo) is snapshotted to prefs on
    // every mutation and restored on launch, so scores survive the process
    // being killed while the app sits in the background. Plain org.json:
    // no new dependency for a few KB of state.

    private fun ScoreEntry.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("playerId", playerId)
        .put("delta", delta)
        .put("ts", timestamp)

    private fun JSONObject.toScoreEntry(): ScoreEntry = ScoreEntry(
        id = getString("id"),
        playerId = getString("playerId"),
        delta = getInt("delta"),
        timestamp = optLong("ts", System.currentTimeMillis()),
    )

    private fun gameToJson(game: Game): JSONObject = JSONObject()
        .put("name", game.name)
        .put("rotationPoints", game.rotationPoints)
        .put("tapPoints", game.tapPoints)
        .put("winMetric", game.winMetric.name)
        .put("keepLastVisible", game.keepLastVisible)
        .put("enlargeActiveDot", game.enlargeActiveDot)
        .put("showPlayerNames", game.showPlayerNames)
        .put("hapticsEnabled", game.hapticsEnabled)
        .put("hapticStrength", game.hapticStrength)
        .put("players", JSONArray(game.players.map { p ->
            JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("color", p.color)
                .put("rotation", p.rotation.name)
        }))
        .put("entries", JSONArray(game.entries.map { it.toJson() }))

    private fun gameFromJson(o: JSONObject): Game? {
        val players = (0 until o.getJSONArray("players").length()).map { i ->
            val p = o.getJSONArray("players").getJSONObject(i)
            Player(
                id = p.getString("id"),
                name = p.getString("name"),
                color = p.getInt("color"),
                rotation = runCatching { Rotation.valueOf(p.getString("rotation")) }
                    .getOrDefault(Rotation.NONE),
            )
        }
        if (players.isEmpty()) return null
        val entries = (0 until o.getJSONArray("entries").length()).map { i ->
            o.getJSONArray("entries").getJSONObject(i).toScoreEntry()
        }
        val ids = players.map { it.id }.toSet()
        return Game(
            name = o.optString("name", ""),
            players = players,
            rotationPoints = o.optInt("rotationPoints", 10),
            tapPoints = o.optInt("tapPoints", 0),
            winMetric = runCatching { WinMetric.valueOf(o.getString("winMetric")) }
                .getOrDefault(WinMetric.HIGHEST),
            keepLastVisible = o.optBoolean("keepLastVisible", false),
            enlargeActiveDot = o.optBoolean("enlargeActiveDot", false),
            showPlayerNames = o.optBoolean("showPlayerNames", true),
            hapticsEnabled = o.optBoolean("hapticsEnabled", false),
            hapticStrength = o.optInt("hapticStrength", 65),
            entries = entries.filter { it.playerId in ids },
        )
    }

    private fun persist() {
        val game = _currentGame.value ?: return
        runCatching {
            prefs.edit()
                .putString("saved_game", gameToJson(game).toString())
                .putString("saved_undo", JSONArray(undoStack.map { it.toJson() }).toString())
                .putString("saved_redo", JSONArray(redoStack.map { it.toJson() }).toString())
                .apply()
        }
    }

    private fun restoreGame(): Triple<Game, List<ScoreEntry>, List<ScoreEntry>>? = runCatching {
        val raw = prefs.getString("saved_game", null) ?: return null
        val game = gameFromJson(JSONObject(raw)) ?: return null
        fun stack(key: String, inLedger: Boolean): List<ScoreEntry> = runCatching {
            val arr = JSONArray(prefs.getString(key, null) ?: return emptyList())
            val playerIds = game.players.map { it.id }.toSet()
            val ledgerIds = game.entries.map { it.id }.toSet()
            (0 until arr.length()).map { arr.getJSONObject(it).toScoreEntry() }
                .filter { it.playerId in playerIds && (it.id in ledgerIds) == inLedger }
        }.getOrDefault(emptyList())
        // Undo entries are live ledger rows; redo entries are rows already
        // taken back out — each side only keeps entries that still belong.
        Triple(game, stack("saved_undo", inLedger = true), stack("saved_redo", inLedger = false))
    }.getOrNull()
}
