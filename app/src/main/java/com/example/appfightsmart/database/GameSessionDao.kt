package com.example.appfightsmart.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface GameSessionDao {

        // Insert a new player
    @Insert
    suspend fun insertPlayer(player: Player): Long

    // Insert a new game session
    @Insert
    suspend fun insertGameSession(gameSession: GameSession): Long

    // Insert a player-game session relationship
    @Insert
    suspend fun insertPlayerGameSession(playerGameSession: PlayerGameSession)

    // Get all players
    @Query("SELECT * FROM players")
    suspend fun getAllPlayers(): List<Player>

    // Get all game sessions for a specific player
    @Transaction
    @Query("SELECT * FROM game_sessions WHERE sessionId IN (SELECT sessionId FROM player_game_session WHERE playerId = :playerId)")
    suspend fun getGameSessionsForPlayer(playerId: Long): List<GameSession>

    // Get all players for a specific game session
    @Transaction
    @Query("SELECT * FROM players WHERE playerId IN (SELECT playerId FROM player_game_session WHERE sessionId = :sessionId)")
    suspend fun getPlayersForGameSession(sessionId: Long): List<Player>

    // Optional: Check if a player-game session relationship already exists
    @Query("SELECT * FROM player_game_session WHERE playerId = :playerId AND sessionId = :sessionId LIMIT 1")
    suspend fun getPlayerGameSession(playerId: Long, sessionId: Long): PlayerGameSession?

    // Optional: Delete a player-game session relationship
    @Query("DELETE FROM player_game_session WHERE playerId = :playerId AND sessionId = :sessionId")
    suspend fun deletePlayerGameSession(playerId: Long, sessionId: Long)

    // Search names
    @Query("SELECT * FROM players WHERE playerName LIKE :query")
    suspend fun searchPlayers(query: String): List<Player>

    @Query("SELECT * FROM players WHERE playerName = :playerName LIMIT 1")
    suspend fun getPlayerByName(playerName: String): Player?
}