package com.example.appfightsmart.database

import android.util.Log

class GameSessionRepository(private val gameSessionDao: GameSessionDao) {

    // Insert a new player
    suspend fun insertPlayer(playerName: String): Long {
        Log.d("GameSessionRepository", "Inserting player: $playerName")
        val player = Player(playerName = playerName)
        return gameSessionDao.insertPlayer(player)
    }

    // Insert a new game session
    suspend fun insertGameSession(playerIds: List<Long>, gameMode: String, selectedMoveType: String) {
        Log.d("GameSessionRepository", "Inserting game session with player IDs: $playerIds")
        try {
            val gameSession = GameSession(gameMode = gameMode, selectedMoveType = selectedMoveType, date = System.currentTimeMillis())
            val sessionId = gameSessionDao.insertGameSession(gameSession)
            Log.d("GameSessionRepository", "Game session inserted with ID: $sessionId")

            playerIds.forEach { playerId ->
                // Check if the player is already linked to the session
                val existingLink = gameSessionDao.getPlayerGameSession(playerId, sessionId)
                if (existingLink == null) {
                    gameSessionDao.insertPlayerGameSession(PlayerGameSession(playerId = playerId, sessionId = sessionId))
                    Log.d("GameSessionRepository", "Linked player $playerId to game session $sessionId")
                } else {
                    Log.d("GameSessionRepository", "Player $playerId is already linked to game session $sessionId")
                }
            }
        } catch (e: Exception) {
            Log.e("GameSessionRepository", "Error inserting game session: ${e.message}", e)
            throw e
        }
    }


    // Search for players by name
    suspend fun searchPlayers(query: String): List<Player> {
        Log.d("GameSessionRepository", "Searching for players with query: $query")
        return gameSessionDao.searchPlayers("%$query%")
    }

    // Get all players
    suspend fun getAllPlayers(): List<Player> {
        Log.d("GameSessionRepository", "Fetching all players")
        return gameSessionDao.getAllPlayers()
    }

    // Get all game sessions for a specific player
    suspend fun getGameSessionsForPlayer(playerId: Long): List<GameSession> {
        Log.d("GameSessionRepository", "Fetching game sessions for player ID: $playerId")
        return gameSessionDao.getGameSessionsForPlayer(playerId)
    }

    // Get all players for a specific game session
    suspend fun getPlayersForGameSession(sessionId: Long): List<Player> {
        Log.d("GameSessionRepository", "Fetching players for game session ID: $sessionId")
        return gameSessionDao.getPlayersForGameSession(sessionId)
    }

    suspend fun insertPlayerIfNotExists(playerName: String): Long {
        val existingPlayer = gameSessionDao.getPlayerByName(playerName)
        return if (existingPlayer != null) {
            existingPlayer.playerId // Return the existing player's ID
        } else {
            val player = Player(playerName = playerName)
            gameSessionDao.insertPlayer(player) // Insert new player and return their ID
        }
    }
}