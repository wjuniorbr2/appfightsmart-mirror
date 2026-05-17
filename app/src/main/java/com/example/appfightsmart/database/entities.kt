package com.example.appfightsmart.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val playerId: Long = 0,
    val playerName: String,
    val heightCm: Int? = null,
    val naturalPunchHeightCm: Int? = null,
    val dominantHand: String? = null
) {
    fun hasCompleteProfile(): Boolean = heightCm != null && naturalPunchHeightCm != null
}

@Entity(tableName = "game_sessions")
data class GameSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val gameMode: String,
    val selectedMoveType: String,
    val date: Long
)

@Entity(
    tableName = "player_game_session",
    primaryKeys = ["playerId", "sessionId"]
)
data class PlayerGameSession(
    val playerId: Long,
    val sessionId: Long
)
