package com.example.appfightsmart.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appfightsmart.database.GameSessionRepository
import com.example.appfightsmart.database.Player
import kotlinx.coroutines.launch

class GameSetupViewModel(private val repository: GameSessionRepository) : ViewModel() {

    suspend fun insertPlayerIfNotExists(playerName: String): Long {
        return repository.insertPlayerIfNotExists(playerName)
    }

    suspend fun savePlayerProfile(playerName: String, heightCm: Int?, naturalPunchHeightCm: Int?, dominantHand: String?): Long {
        return repository.savePlayerProfile(playerName, heightCm, naturalPunchHeightCm, dominantHand)
    }

    suspend fun getPlayerByName(playerName: String): Player? {
        return repository.getPlayerByName(playerName)
    }

    suspend fun getPlayersByNames(playerNames: List<String>): List<Player?> {
        return repository.getPlayersByNames(playerNames)
    }

    fun insertGameSession(playerIds: List<Long>, gameMode: String, selectedMoveType: String) = viewModelScope.launch {
        repository.insertGameSession(playerIds, gameMode, selectedMoveType)
    }

    private val _searchResults = mutableStateListOf<Player>()
    val searchResults: List<Player> get() = _searchResults

    fun searchPlayers(query: String) = viewModelScope.launch {
        searchPlayersNow(query)
    }

    suspend fun searchPlayersNow(query: String): List<Player> {
        _searchResults.clear()
        val results = repository.searchPlayers(query)
        _searchResults.addAll(results)
        return results
    }
}
