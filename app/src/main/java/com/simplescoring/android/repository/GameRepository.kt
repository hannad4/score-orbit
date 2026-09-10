package com.simplescoring.android.repository

import android.content.Context
import com.simplescoring.android.model.Game
import com.simplescoring.android.model.Player
import com.simplescoring.android.model.Rotation
import com.simplescoring.android.model.ScoreEntry
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
        val raw = historyFile.readText().trim().ifEmpty { "[]" }
        val arr = JSONArray(raw)
        val result = mutableListOf<Game>()
        for (i in 0 until arr.length()) {
            result.add(gameFromJson(arr.getJSONObject(i)))
        }
        return result
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
        put("name", game.name)
        put("createdAt", game.createdAt)
        put("finishedAt", game.finishedAt ?: JSONObject.NULL)
        put("winnerId", game.winnerId ?: JSONObject.NULL)
        put("step", game.step)
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
                id = p.getString("id"),
                name = p.getString("name"),
                color = p.getInt("color"),
                rotation = Rotation.entries[p.getInt("rotation")]
            ))
        }
        val entriesArr = obj.getJSONArray("entries")
        val entries = mutableListOf<ScoreEntry>()
        for (i in 0 until entriesArr.length()) {
            val e = entriesArr.getJSONObject(i)
            entries.add(ScoreEntry(
                id = e.getString("id"),
                playerId = e.getString("playerId"),
                delta = e.getInt("delta"),
                timestamp = e.getLong("timestamp")
            ))
        }
        return Game(
            name = obj.getString("name"),
            createdAt = obj.getLong("createdAt"),
            finishedAt = if (obj.isNull("finishedAt")) null else obj.getLong("finishedAt"),
            winnerId = if (obj.isNull("winnerId")) null else obj.getString("winnerId"),
            step = obj.getInt("step"),
            players = players,
            entries = entries
        )
    }
}
