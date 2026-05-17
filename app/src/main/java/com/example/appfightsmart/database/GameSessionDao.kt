package com.example.appfightsmart.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface GameSessionDao {

    @Insert
    suspend fun insertPlayer(player: Player): Long

    @Update
    suspend fun updatePlayer(player: Player)

    @Insert
    suspend fun insertGameSession(gameSession: GameSession): Long

    @Insert
    suspend fun insertPlayerGameSession(playerGameSession: PlayerGameSession)

    @Query("SELECT * FROM players ORDER BY playerName COLLATE NOCASE ASC")
    suspend fun getAllPlayers(): List<Player>

    @Transaction
    @Query("SELECT * FROM game_sessions WHERE sessionId IN (SELECT sessionId FROM player_game_session WHERE playerId = :playerId)")
    suspend fun getGameSessionsForPlayer(playerId: Long): List<GameSession>

    @Transaction
    @Query("SELECT * FROM players WHERE playerId IN (SELECT playerId FROM player_game_session WHERE sessionId = :sessionId)")
    suspend fun getPlayersForGameSession(sessionId: Long): List<Player>

    @Query("SELECT * FROM player_game_session WHERE playerId = :playerId AND sessionId = :sessionId LIMIT 1")
    suspend fun getPlayerGameSession(playerId: Long, sessionId: Long): PlayerGameSession?

    @Query("DELETE FROM player_game_session WHERE playerId = :playerId AND sessionId = :sessionId")
    suspend fun deletePlayerGameSession(playerId: Long, sessionId: Long)

    @Query("SELECT * FROM players WHERE playerName LIKE :query ORDER BY playerName COLLATE NOCASE ASC")
    suspend fun searchPlayers(query: String): List<Player>

    @Query("SELECT * FROM players WHERE playerName = :playerName LIMIT 1")
    suspend fun getPlayerByName(playerName: String): Player?
}
