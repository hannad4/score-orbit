package com.simplescoring.android.repository

import android.content.Context
import com.simplescoring.android.model.Game
import com.simplescoring.android.model.Player
import com.simplescoring.android.model.Rotation
import com.simplescoring.android.model.ScoreEntry
import com.simplescoring.android.model.WinMetric
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Simple JSON-based persistence for game history using Android's built-in org.json. */
object GameRepository {

    private lateinit var context: Context
    private val historyFile: File by lazy { File(context.filesDir, "score_history.json") }

    fun init(appContext: Context) {
        context = appContext
        if (!historyFile.exists()) {
            historyFile.writeText("[]")
        }
    }

    fun loadHistory(): List<Game> {
        if (!::context.isInitialized) throw IllegalStateException("GameRepository not initialized")
        return try {
            val raw = historyFile.readText().trim().ifEmpty { "[]" }
            val arr = JSONArray(raw)
            val result = mutableListOf<Game>()
            for (i in 0 until arr.length()) {
                result.add(gameFromJson(arr.getJSONObject(i)))
            }
            result.sortedByDescending { it.finishedAt ?: it.createdAt }
        } catch (_: Exception) {
            // Corrupt history file: back it up once and start fresh rather than crashing.
            try {
                historyFile.renameTo(File(context.filesDir, "score_history.corrupt.json"))
                historyFile.writeText("[]")
            } catch (_: Exception) { }
            emptyList()
        }
    }

    fun appendGame(game: Game) {
        if (!::context.isInitialized) throw IllegalStateException("GameRepository not initialized")
        val current = loadHistory()
        val arr = JSONArray()
        (current + game).forEach { arr.put(gameToJson(it)) }
        historyFile.writeText(arr.toString())
    }

    fun deleteGame(id: String) {
        if (!::context.isInitialized) throw IllegalStateException("GameRepository not initialized")
        val current = loadHistory()
        val filtered = current.filterNot { it.id == id }
        val arr = JSONArray()
        filtered.forEach { arr.put(gameToJson(it)) }
        historyFile.writeText(arr.toString())
        loadHistory()
    }

    fun clearHistory() {
        if (!::context.isInitialized) throw IllegalStateException("GameRepository not initialized")
        historyFile.writeText("[]")
        loadHistory()
    }

    private fun gameToJson(game: Game): JSONObject = JSONObject().apply {
        put("id", game.id)
        put("name", game.name)
        put("createdAt", game.createdAt)
        put("finishedAt", game.finishedAt ?: JSONObject.NULL)
        put("winnerId", game.winnerId ?: JSONObject.NULL)
        put("step", game.step)
        put("winMetric", game.winMetric.ordinal)
        put("players", JSONArray().also { arr ->
            game.players.forEach { arr.put(playerToJson(it)) }
        })
        put("entries", JSONArray().also { arr ->
            game.entries.forEach { arr.put(entryToJson(it)) }
        })
    }

    private fun playerToJson(player: Player): JSONObject = JSONObject().apply {
        put("id", player.id)
        put("name", player.name)
        put("color", player.color)
        put("rotation", player.rotation.ordinal)
    }

    private fun entryToJson(entry: ScoreEntry): JSONObject = JSONObject().apply {
        put("id", entry.id)
        put("playerId", entry.playerId)
        put("delta", entry.delta)
        put("timestamp", entry.timestamp)
    }

    private fun gameFromJson(obj: JSONObject): Game {
        val playersArr = obj.getJSONArray("players")
        val players = mutableListOf<Player>()
        for (i in 0 until playersArr.length()) {
            val p = playersArr.getJSONObject(i)
            players.add(Player(
                id = p.optString("id", java.util.UUID.randomUUID().toString()),
                name = p.optString("name", "Player"),
                color = p.optInt("color", 0xFF5B9BD5.toInt()),
                rotation = Rotation.entries.getOrElse(p.optInt("rotation", 0)) { Rotation.NONE }
            ))
        }
        val entriesArr = obj.getJSONArray("entries")
        val entries = mutableListOf<ScoreEntry>()
        for (i in 0 until entriesArr.length()) {
            val e = entriesArr.getJSONObject(i)
            entries.add(ScoreEntry(
                id = e.optString("id", java.util.UUID.randomUUID().toString()),
                playerId = e.optString("playerId", ""),
                delta = e.optInt("delta", 0),
                timestamp = e.optLong("timestamp", System.currentTimeMillis())
            ))
        }
        return Game(
            id = obj.optString("id", java.util.UUID.randomUUID().toString()).ifEmpty { java.util.UUID.randomUUID().toString() },
            name = obj.optString("name", "Untitled Game"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            finishedAt = if (obj.isNull("finishedAt")) null else obj.getLong("finishedAt"),
            winnerId = if (obj.isNull("winnerId")) null else obj.getString("winnerId"),
            step = obj.optInt("step", 1).coerceAtLeast(1),
            winMetric = WinMetric.entries.getOrElse(obj.optInt("winMetric", 0)) { WinMetric.HIGHEST },
            players = players,
            entries = entries
        )
    }
}
