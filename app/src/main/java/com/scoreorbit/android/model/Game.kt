package com.scoreorbit.android.model

data class Player(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Player",
    val color: Int = 0xFF5B9BD5.toInt(),
    val rotation: Rotation = Rotation.NONE,
    /** Team this player belongs to, or null for a solo player. */
    val teamId: String? = null
) {
    fun isSolo(): Boolean = teamId == null
}

data class Team(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Team",
    val color: Int = 0xFF9C27B0.toInt()
)

enum class Rotation {
    NONE, ROTATED_90, ROTATED_180, ROTATED_270;

    fun next(): Rotation = entries[(ordinal + 1) % entries.size]
}

enum class WinMetric {
    HIGHEST, LOWEST;
}

data class ScoreEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val playerId: String,
    val delta: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class Game(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Untitled Game",
    val players: List<Player> = emptyList(),
    val teams: List<Team> = emptyList(),
    val rotationPoints: Int = 10,
    val tapPoints: Int = 0,
    val winMetric: WinMetric = WinMetric.HIGHEST,
    /** First-to-target score that ends the game, or null for endless scoring. */
    val targetScore: Int? = null,
    val keepLastVisible: Boolean = false,
    val enlargeActiveDot: Boolean = false,
    val showPlayerNames: Boolean = true,
    val hapticsEnabled: Boolean = false,
    val hapticStrength: Int = 65,
    val entries: List<ScoreEntry> = emptyList(),
) {
    fun currentScore(playerId: String): Int {
        var total = 0
        entries.forEach { if (it.playerId == playerId) total += it.delta }
        return total
    }

    /** Single pass over the ledger instead of one filter+fold per player. */
    fun scoresMap(): Map<String, Int> {
        val totals = HashMap<String, Int>(players.size * 2 + 1)
        players.forEach { totals[it.id] = 0 }
        entries.forEach { e -> totals[e.playerId] = (totals[e.playerId] ?: 0) + e.delta }
        return totals
    }

    /** Teams that actually have players, in zone order. */
    fun activeTeams(): List<Team> = teams.filter { t -> players.any { it.teamId == t.id } }

    fun teamScore(teamId: String): Int {
        val ids = players.filter { it.teamId == teamId }.map { it.id }.toSet()
        var total = 0
        entries.forEach { if (it.playerId in ids) total += it.delta }
        return total
    }

    fun teamScoresMap(): Map<String, Int> = activeTeams().associate { it.id to teamScore(it.id) }
}
