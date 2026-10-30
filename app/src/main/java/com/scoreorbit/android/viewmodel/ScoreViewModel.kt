package com.scoreorbit.android.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.model.MAX_TIMER_SEC
import com.scoreorbit.android.model.MIN_TIMER_SEC
import com.scoreorbit.android.model.Player
import com.scoreorbit.android.model.Rotation
import com.scoreorbit.android.model.ScoreEntry
import com.scoreorbit.android.model.Team
import com.scoreorbit.android.model.WinMetric
import com.scoreorbit.android.ui.theme.ScoreOrbitColors
import com.scoreorbit.android.ui.buzz
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import com.scoreorbit.android.util.RotationUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
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

    // -- turn timer (live session state; see below for its accessors) --------
    private val _timerMs = mutableStateOf(120_000L)
    val timerMs: State<Long> = _timerMs

    /** The armed duration: what the countdown drains from and Reset restores. */
    private val _timerSetMs = mutableStateOf(120_000L)
    val timerSetMs: State<Long> = _timerSetMs

    private val _timerRunning = mutableStateOf(false)
    val timerRunning: State<Boolean> = _timerRunning

    /** Increments each time the timer reaches zero; the UI consumes this for haptics. */
    private val _timerDoneTick = mutableStateOf(0)
    val timerDoneTick: State<Int> = _timerDoneTick

    private var timerEndAt = 0L
    private var timerJob: Job? = null

    /** True while the expired-time alarm is ringing; consumes for UI pulse. */
    private val _timerRinging = mutableStateOf(false)
    val timerRinging: State<Boolean> = _timerRinging
    private var alarmJob: Job? = null

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
            _timerMs.value = saved.first.timerStartSec * 1000L
            _timerSetMs.value = saved.first.timerStartSec * 1000L
        } else {
            startNewGame(
                playerCount = 2,
                names = listOf("Player 1", "Player 2"),
                colors = ScoreOrbitColors.PlayerColors.take(2),
                rotationPoints = 10,
                tapPoints = 0,
                winMetric = WinMetric.HIGHEST,
                // First install: the welcome wizard gets to set the table.
                onboarded = false,
            )
            _timerMs.value = (_currentGame.value?.timerStartSec ?: 120) * 1000L
            _timerSetMs.value = _timerMs.value
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
        targetScore: Int? = null,
        showRankBadges: Boolean = true,
        keepLastVisible: Boolean = false,
        enlargeActiveDot: Boolean = false,
        showPlayerNames: Boolean = true,
        hapticsEnabled: Boolean = false,
        hapticStrength: Int = 65,
        teams: List<Team> = emptyList(),
        teamIds: List<String?> = emptyList(),
        // Explicit "New Game" actions come from inside the app, so their
        // boards skip the wizard; only a fresh install walks it (init
        // passes false).
        onboarded: Boolean = true,
    ) {
        val count = playerCount.coerceIn(1, 12)
        val players = (0 until count).map { i ->
            Player(
                id = UUID.randomUUID().toString(),
                name = names.getOrElse(i) { "Player ${i + 1}" }.ifBlank { "Player ${i + 1}" },
                color = colors.getOrElse(i) { ScoreOrbitColors.PlayerColors[i % ScoreOrbitColors.PlayerColors.size] },
                rotation = Rotation.NONE,
                teamId = teamIds.getOrNull(i)?.takeIf { tid -> teams.any { it.id == tid } },
            )
        }
        _currentGame.value = Game(
            name = boardName,
            players = players,
            teams = teams,
            rotationPoints = rotationPoints.coerceIn(1, 100),
            tapPoints = tapPoints.coerceIn(0, 100),
            winMetric = winMetric,
            targetScore = targetScore?.takeIf { it >= 1 },
            showRankBadges = showRankBadges,
            keepLastVisible = keepLastVisible,
            enlargeActiveDot = enlargeActiveDot,
            showPlayerNames = showPlayerNames,
            hapticsEnabled = hapticsEnabled,
            hapticStrength = hapticStrength,
            onboarded = onboarded,
        )
        undoStack.clear()
        redoStack.clear()
        // New board: timer shall go back to its fresh, default duration.
        stopAlarm()
        timerJob?.cancel()
        timerJob = null
        timerEndAt = 0L
        _timerRunning.value = false
        _timerMs.value = (_currentGame.value?.timerStartSec ?: 120) * 1000L
        _timerSetMs.value = _timerMs.value
        _screen.value = AppScreen.Board
        persist()
    }

    /** Fresh scores, same setup (teams included). */
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
            targetScore = game.targetScore,
            showRankBadges = game.showRankBadges,
            keepLastVisible = game.keepLastVisible,
            enlargeActiveDot = game.enlargeActiveDot,
            showPlayerNames = game.showPlayerNames,
            hapticsEnabled = game.hapticsEnabled,
            hapticStrength = game.hapticStrength,
            teams = game.teams,
            teamIds = game.players.map { it.teamId },
            onboarded = game.onboarded,
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

    /** Null (or anything below 1) means endless scoring. */
    fun setTargetScore(target: Int?) {
        _currentGame.value = _currentGame.value?.copy(targetScore = target?.takeIf { it >= 1 })
        persist()
    }

    // -- turn timer control ---------------------------------------------------
    // Driven off a wall-clock deadline so it stays accurate across
    // recompositions and process doze. `timerSetMs` is the duration the
    // user armed (settings default until they change it on the board):
    // the countdown drains from it, and Reset returns to it. Only the
    // default travels with the game.

    /**
     * Board timer +/-: moves between multiples of 5 seconds rather than a
     * fixed step, so a reading mid-slide always lands on a clean number.
     * From 1:12, + goes to 1:15, + again to 1:20; 1:12, − goes to 1:10.
     * [deltaSec] only picks the direction.
     */
    fun timerAdjust(deltaSec: Int) {
        val game = _currentGame.value ?: return
        // Next/previous multiple of 5 seconds, taking at least one step so
        // an already-clean value keeps moving.
        fun step(from: Long): Long {
            val base = from / 5L * 5L
            val moving = if (deltaSec > 0) base + 5L else if (base == from) base - 5L else base
            return moving.coerceIn(MIN_TIMER_SEC.toLong(), MAX_TIMER_SEC.toLong())
        }
        if (_timerRunning.value) {
            // Running: shifts the deadline only; the armed duration is
            // untouched, so Reset still restores what the user chose.
            val newSec = step(_timerMs.value / 1000L)
            _timerMs.value = newSec * 1000L
            timerEndAt = System.currentTimeMillis() + newSec * 1000L
        } else {
            // Stopped or paused: re-arming from whatever the clock reads,
            // so a mid-countdown pause keeps its context (1:12, + → 1:15).
            // This becomes the duration Start uses and Reset returns to.
            val newSec = step(_timerMs.value / 1000L)
            _timerSetMs.value = newSec * 1000L
            _timerMs.value = newSec * 1000L
        }
        _timerDoneTick.value = 0
    }

    fun timerStartPause() {
        stopAlarm()
        if (_timerRunning.value) {
            _timerRunning.value = false
            timerJob?.cancel()
            timerJob = null
            timerEndAt = 0L
            _timerDoneTick.value = 0
        } else {
            // Off the zero-done state, Start restarts from the armed
            // duration; Reset does the same for a stopped timer.
            if (_timerMs.value <= 0L) _timerMs.value = _timerSetMs.value
            timerEndAt = System.currentTimeMillis() + _timerMs.value
            _timerRunning.value = true
            timerJob = viewModelScope.launch {
                while (true) {
                    val left = timerEndAt - System.currentTimeMillis()
                    if (left <= 0L) {
                        _timerMs.value = 0L
                        _timerRunning.value = false
                        _timerDoneTick.value += 1
                        startAlarm()
                        break
                    }
                    _timerMs.value = left
                    delay(100)
                }
            }
        }
    }

    fun timerReset() {
        stopAlarm()
        timerJob?.cancel()
        timerJob = null
        timerEndAt = 0L
        _timerRunning.value = false
        _timerMs.value = _timerSetMs.value
        _timerDoneTick.value = 0
    }

    /** Settings: default duration the timer arms itself to (in seconds). */
    fun setTimerStartSec(sec: Int) {
        _currentGame.value = _currentGame.value?.copy(timerStartSec = sec.coerceIn(MIN_TIMER_SEC, MAX_TIMER_SEC))
        timerJob?.cancel()
        timerJob = null
        stopAlarm()
        timerEndAt = 0L
        _timerRunning.value = false
        _timerSetMs.value = sec * 1000L
        _timerMs.value = sec * 1000L
        _timerDoneTick.value = 0
        persist()
    }

    /** Settings: whether the timer pill is offered on the board's top bar. */
    fun setShowTimer(show: Boolean) {
        _currentGame.value = _currentGame.value?.copy(showTimer = show)
        if (!show) {
            timerReset()
        } else {
            stopAlarm()
        }
        _timerSetMs.value = (_currentGame.value?.timerStartSec ?: 120) * 1000L
        _timerMs.value = _timerSetMs.value
        persist()
    }

    /**
     * Time's up: rings like a proper alarm — a burst of three clean sine
     * pips (classic kitchen-timer sound, streamed on the alarm volume so
     * it respects the device's alarm level) plus a solid buzz, repeating
     * on a short cadence until the user acknowledges it.
     */
    private fun startAlarm() {
        stopAlarm()
        _timerRinging.value = true
        alarmJob = viewModelScope.launch(Dispatchers.IO) {
            val rate = 16_000
            val pipMs = 300
            val gapMs = 140
            val pips = 3
            // Full burst: pip, gap, pip, gap, pip (leading edge dampened to
            // avoid the click a raw square wave would make).
            val burst = ShortArray(rate * (pipMs * pips + gapMs * (pips - 1)) / 1000)
            var i = 0
            repeat(pips) { p ->
                val n = rate * pipMs / 1000
                val ramp = (rate * 0.014).toInt()
                for (k in 0 until n) {
                    val env = when {
                        k < ramp -> k / ramp.toFloat()
                        k > n - ramp -> (n - k) / ramp.toFloat()
                        else -> 1f
                    }
                    val s = kotlin.math.sin(2.0 * kotlin.math.PI * 1760.0 * k / rate)
                    burst[i + k] = (s.toFloat() * env * 0.5f * Short.MAX_VALUE).toInt().toShort()
                }
                i += n
                if (p < pips - 1) i += rate * gapMs / 1000
            }
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(rate)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(minBuf)
                    .build()
                alarmTrack = track
                track.play()
                delay(350)
                var off = 0
                while (isActive) {
                    // Stream the burst; writes park until the buffer drains.
                    while (off < burst.size && isActive) {
                        off += track.write(burst, off, burst.size - off)
                    }
                    // Firm double buzz rides along with the beep train.
                    buzz(getApplication(), 350L, _currentGame.value?.hapticStrength ?: 65)
                    if (!isActive) break
                    // Short cadence: back on the clocks in ~1s.
                    off = 0
                    delay(1000)
                }
            } catch (_: Exception) {
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
                alarmTrack = null
            }
        }
    }

    /** Silence the alarm; idempotent so every teardown path can call it. */
    fun stopAlarm() {
        if (alarmJob != null || _timerRinging.value) {
            alarmJob?.cancel()
            alarmJob = null
            _timerRinging.value = false
            // Cutting the track stops the in-flight write; its loops unwind.
            runCatching { alarmTrack?.pause() }
        }
    }
    private var alarmTrack: AudioTrack? = null

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

    fun setShowRankBadges(show: Boolean) {
        _currentGame.value = _currentGame.value?.copy(showRankBadges = show)
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
        val oldIds = game.players.map { it.id }.toSet()
        val players = (0 until target).map { i ->
            game.players.getOrElse(i) {
                Player(
                    name = "Player ${i + 1}",
                    color = nextFreeColor(game.players.map { it.color }, i),
                    rotation = Rotation.NONE,
                )
            }
        }
        // Fresh faces join the smallest team (random among ties) so the
        // split stays even without reshuffling everybody.
        val counts = players.groupingBy { it.teamId }.eachCount().toMutableMap()
        val filled = players.map { p ->
            if (p.id in oldIds || game.teams.isEmpty()) {
                p
            } else {
                val smallest = game.teams.map { it.id }.shuffled()
                    .minByOrNull { counts.getOrDefault(it, 0) }!!
                counts[smallest] = counts.getOrDefault(smallest, 0) + 1
                p.copy(teamId = smallest)
            }
        }
        val ids = filled.map { it.id }.toSet()
        _currentGame.value = game.copy(
            players = filled,
            entries = game.entries.filter { it.playerId in ids },
        ).pruned()
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
        ).pruned()
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

    /** Reorder the roster (seat order on the board). */
    fun movePlayer(playerId: String, toIndex: Int) {
        val game = _currentGame.value ?: return
        val from = game.players.indexOfFirst { it.id == playerId }
        val to = toIndex.coerceIn(0, game.players.size - 1)
        if (from < 0 || from == to) return
        val list = game.players.toMutableList()
        val p = list.removeAt(from)
        list.add(to, p)
        _currentGame.value = game.copy(players = list)
        persist()
    }

    /** Exchange two seats (board drag-and-drop). Entries reference ids, so scores follow. */
    fun swapPlayers(a: String, b: String) {
        val game = _currentGame.value ?: return
        val ia = game.players.indexOfFirst { it.id == a }
        val ib = game.players.indexOfFirst { it.id == b }
        if (ia < 0 || ib < 0 || ia == ib) return
        val list = game.players.toMutableList()
        val tmp = list[ia]
        list[ia] = list[ib]
        list[ib] = tmp
        _currentGame.value = game.copy(players = list)
        persist()
    }

    // -- teams ---------------------------------------------------------------
    // Four quadrant zones max, so four teams max. The board gives each
    // non-empty team (plus solos, if any) its own zone; anything beyond
    // that shares the last zone instead of breaking the layout.

    fun renameTeam(teamId: String, name: String) {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(
            teams = game.teams.map { if (it.id == teamId) it.copy(name = name.ifBlank { it.name }) else it }
        )
        persist()
    }

    fun recolorTeam(teamId: String, color: Int) {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(
            teams = game.teams.map { if (it.id == teamId) it.copy(color = color) else it }
        )
        persist()
    }

    /** Deleting a team turns its players solo; their scores stay with them. */
    fun deleteTeam(teamId: String) {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(
            teams = game.teams.filterNot { it.id == teamId },
            players = game.players.map { if (it.teamId == teamId) it.copy(teamId = null) else it },
        )
        persist()
    }

    /** First-run wizard finished: the board never shows it again. */
    fun completeOnboarding() {
        val game = _currentGame.value ?: return
        _currentGame.value = game.copy(onboarded = true)
        _showWizard.value = false
        persist()
    }

    /** Settings request: show the setup wizard again over the board. */
    fun replayWizard() {
        _screen.value = AppScreen.Board
        _showWizard.value = true
    }

    private val _showWizard = mutableStateOf(false)
    val showWizard: State<Boolean> = _showWizard

    fun assignPlayer(playerId: String, teamId: String?, keepEmpty: Boolean = false) {
        val game = _currentGame.value ?: return
        if (teamId != null && game.teams.none { it.id == teamId }) return
        _currentGame.value = game.copy(
            players = game.players.map { if (it.id == playerId) it.copy(teamId = teamId) else it },
        ).pruned(keepEmpty)
        persist()
    }

    /**
     * Teams left with no players go away on their own — unless [keepEmpty]
     * is true, which the setup wizard passes so handing players between the
     * team chips can never shrink the team count underneath the selector.
     */
    private fun Game.pruned(keepEmpty: Boolean = false): Game {
        if (keepEmpty) return this
        val used = players.mapNotNull { it.teamId }.toSet()
        return if (teams.any { it.id !in used }) copy(teams = teams.filter { it.id in used }) else this
    }

    /**
     * Team count stepper behind the Players settings. Growing adds named
     * teams, shrinking drops from the end — then everybody is dealt back
     * out randomly and as evenly as possible (round-robin over a shuffle),
     * so the count always means a fresh even split. Shrunk-away members
     * rejoin the deal with their scores intact.
     */
    fun setTeamCount(count: Int) {
        val game = _currentGame.value ?: return
        val target = count.coerceIn(0, 4)
        if (target == game.teams.size) return
        val teams = if (target > game.teams.size) {
            val used = (game.players.map { it.color } + game.teams.map { it.color }).toMutableList()
            game.teams + (game.teams.size until target).map { i ->
                Team(name = "Team ${i + 1}", color = nextFreeColor(used, i))
                    .also { used.add(it.color) }
            }
        } else {
            game.teams.take(target)
        }
        val teamIds = teams.map { it.id }
        val dealt = if (teamIds.isEmpty()) {
            game.players.map { it.copy(teamId = null) }
        } else {
            val order = game.players.map { it.id }.shuffled()
            val table = order.mapIndexed { i, pid -> pid to teamIds[i % teamIds.size] }.toMap()
            game.players.map { it.copy(teamId = table[it.id]) }
        }
        _currentGame.value = game.copy(teams = teams, players = dealt)
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
        .put("targetScore", game.targetScore ?: JSONObject.NULL)
        .put("keepLastVisible", game.keepLastVisible)
        .put("enlargeActiveDot", game.enlargeActiveDot)
        .put("showPlayerNames", game.showPlayerNames)
        .put("showRankBadges", game.showRankBadges)
        .put("hapticsEnabled", game.hapticsEnabled)
        .put("hapticStrength", game.hapticStrength)
        .put("showTimer", game.showTimer)
        .put("timerStartSec", game.timerStartSec)
        .put("onboarded", game.onboarded)
        .put("players", JSONArray(game.players.map { p ->
            JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("color", p.color)
                .put("rotation", p.rotation.name)
                .put("teamId", p.teamId)
        }))
        .put("teams", JSONArray(game.teams.map { t ->
            JSONObject()
                .put("id", t.id)
                .put("name", t.name)
                .put("color", t.color)
        }))
        .put("entries", JSONArray(game.entries.map { it.toJson() }))

    private fun gameFromJson(o: JSONObject): Game? {
        val teams = buildList {
            val arr = o.optJSONArray("teams") ?: return@buildList
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                add(Team(id = t.getString("id"), name = t.getString("name"), color = t.getInt("color")))
            }
        }
        val teamIds = teams.map { it.id }.toSet()
        val players = (0 until o.getJSONArray("players").length()).map { i ->
            val p = o.getJSONArray("players").getJSONObject(i)
            Player(
                id = p.getString("id"),
                name = p.getString("name"),
                color = p.getInt("color"),
                rotation = runCatching { Rotation.valueOf(p.getString("rotation")) }
                    .getOrDefault(Rotation.NONE),
                teamId = p.optString("teamId", null)?.takeIf { it in teamIds },
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
            teams = teams,
            rotationPoints = o.optInt("rotationPoints", 10),
            tapPoints = o.optInt("tapPoints", 0),
            winMetric = runCatching { WinMetric.valueOf(o.getString("winMetric")) }
                .getOrDefault(WinMetric.HIGHEST),
            targetScore = if (o.isNull("targetScore")) null else o.optInt("targetScore").takeIf { it >= 1 },
            keepLastVisible = o.optBoolean("keepLastVisible", false),
            enlargeActiveDot = o.optBoolean("enlargeActiveDot", false),
            showPlayerNames = o.optBoolean("showPlayerNames", true),
            showRankBadges = o.optBoolean("showRankBadges", true),
            hapticsEnabled = o.optBoolean("hapticsEnabled", false),
            hapticStrength = o.optInt("hapticStrength", 65),
            showTimer = o.optBoolean("showTimer", true),
            timerStartSec = o.optInt("timerStartSec", 120).coerceIn(MIN_TIMER_SEC, MAX_TIMER_SEC),
            // Games saved before the wizard existed count as walked through.
            onboarded = o.optBoolean("onboarded", true),
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
