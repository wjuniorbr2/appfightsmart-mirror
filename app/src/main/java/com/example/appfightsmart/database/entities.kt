package com.example.appfightsmart.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val playerId: Long = 0,
    val playerName: String
)

@Entity(tableName = "game_sessions")
data class GameSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val gameMode: String,    // e.g., "Single", "Best of 3", "Best of 5"
    val selectedMoveType: String, // e.g., "Punch", "Hook", "Front Kick"
    val date: Long // Store as a timestamp (System.currentTimeMillis())
)

@Entity(
    tableName = "player_game_session",
    primaryKeys = ["playerId", "sessionId"] // Define composite primary key
)
data class PlayerGameSession(
    val playerId: Long,
    val sessionId: Long
)