package com.example.appfightsmart

import android.net.Uri

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Calibration : Screen("calibration")
    object PlayerProfiles : Screen("player_profiles")
    object GameSetup : Screen("game_setup")
    object Game : Screen("game?playerNames={playerNames}&gameMode={gameMode}&selectedMoveType={selectedMoveType}&playerHeights={playerHeights}") {
        fun createRoute(playerNames: String, gameMode: String, selectedMoveType: String, playerHeights: String = ""): String {
            val encodedPlayerNames = Uri.encode(playerNames)
            val encodedGameMode = Uri.encode(gameMode)
            val encodedMoveType = Uri.encode(selectedMoveType)
            val encodedPlayerHeights = Uri.encode(playerHeights)
            return "game?playerNames=$encodedPlayerNames&gameMode=$encodedGameMode&selectedMoveType=$encodedMoveType&playerHeights=$encodedPlayerHeights"
        }
    }
    object Training : Screen("training")
    object Leaderboard : Screen("leaderboard")
    object Settings : Screen("settings")
}
