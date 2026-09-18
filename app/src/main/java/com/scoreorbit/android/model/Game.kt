package com.scoreorbit.android.model

data class Player(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Player",
    val color: Int = 0xFF5B9BD5.toInt(),
    val rotation: Rotation = Rotation.NONE
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
    val rotationPoints: Int = 10,
    val tapPoints: Int = 0,
    val winMetric: WinMetric = WinMetric.HIGHEST,
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

    fun winner(): Player? {
        if (players.isEmpty() || entries.isEmpty()) return null
        val scores = players.map { p -> p to currentScore(p.id) }
        return when (winMetric) {
            WinMetric.HIGHEST -> {
                val max = scores.maxOf { it.second }
                scores.firstOrNull { it.second == max }?.first
            }
            WinMetric.LOWEST -> {
                val min = scores.minOf { it.second }
                scores.firstOrNull { it.second == min }?.first
            }
        }
    }
}
