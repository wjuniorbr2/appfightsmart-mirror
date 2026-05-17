package com.example.appfightsmart.database

class GameSessionRepository(private val gameSessionDao: GameSessionDao) {

    suspend fun insertPlayer(playerName: String): Long {
        return gameSessionDao.insertPlayer(Player(playerName = playerName.trim()))
    }

    suspend fun savePlayerProfile(
        playerName: String,
        heightCm: Int?,
        naturalPunchHeightCm: Int?,
        dominantHand: String?
    ): Long {
        val cleanName = playerName.trim()
        val existing = gameSessionDao.getPlayerByName(cleanName)
        return if (existing == null) {
            gameSessionDao.insertPlayer(
                Player(
                    playerName = cleanName,
                    heightCm = heightCm,
                    naturalPunchHeightCm = naturalPunchHeightCm,
                    dominantHand = dominantHand
                )
            )
        } else {
            gameSessionDao.updatePlayer(
                existing.copy(
                    heightCm = heightCm,
                    naturalPunchHeightCm = naturalPunchHeightCm,
                    dominantHand = dominantHand
                )
            )
            existing.playerId
        }
    }

    suspend fun getPlayerByName(playerName: String): Player? {
        return gameSessionDao.getPlayerByName(playerName.trim())
    }

    suspend fun getPlayersByNames(playerNames: List<String>): List<Player?> {
        return playerNames.map { gameSessionDao.getPlayerByName(it.trim()) }
    }

    suspend fun insertGameSession(playerIds: List<Long>, gameMode: String, selectedMoveType: String) {
        val gameSession = GameSession(gameMode = gameMode, selectedMoveType = selectedMoveType, date = System.currentTimeMillis())
        val sessionId = gameSessionDao.insertGameSession(gameSession)
        playerIds.forEach { playerId ->
            if (gameSessionDao.getPlayerGameSession(playerId, sessionId) == null) {
                gameSessionDao.insertPlayerGameSession(PlayerGameSession(playerId = playerId, sessionId = sessionId))
            }
        }
    }

    suspend fun searchPlayers(query: String): List<Player> {
        return gameSessionDao.searchPlayers("%$query%")
    }

    suspend fun getAllPlayers(): List<Player> {
        return gameSessionDao.getAllPlayers()
    }

    suspend fun getGameSessionsForPlayer(playerId: Long): List<GameSession> {
        return gameSessionDao.getGameSessionsForPlayer(playerId)
    }

    suspend fun getPlayersForGameSession(sessionId: Long): List<Player> {
        return gameSessionDao.getPlayersForGameSession(sessionId)
    }

    suspend fun insertPlayerIfNotExists(playerName: String): Long {
        val cleanName = playerName.trim()
        val existingPlayer = gameSessionDao.getPlayerByName(cleanName)
        return existingPlayer?.playerId ?: gameSessionDao.insertPlayer(Player(playerName = cleanName))
    }
}
