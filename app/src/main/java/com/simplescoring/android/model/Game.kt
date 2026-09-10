package com.simplescoring.android.model

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
    val step: Int = 1,
    val entries: List<ScoreEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val winnerId: String? = null
) {
    fun currentScore(playerId: String): Int =
        entries.filter { it.playerId == playerId }.fold(0) { acc, e -> acc + e.delta }

    fun scoresMap(): Map<String, Int> =
        players.associate { it.id to currentScore(it.id) }

    fun winner(): Player? {
        if (players.isEmpty()) return null
        val scores = players.map { p -> p to currentScore(p.id) }
        val max = scores.maxOfOrNull { it.second } ?: return null
        return scores.firstOrNull { it.second == max }?.first
    }
}
