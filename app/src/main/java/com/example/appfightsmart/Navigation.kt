package com.example.appfightsmart

import android.net.Uri

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Calibration : Screen("calibration")
    object GameSetup : Screen("game_setup")
    object Game : Screen("game?playerNames={playerNames}&gameMode={gameMode}&selectedMoveType={selectedMoveType}") {
        fun createRoute(playerNames: String, gameMode: String, selectedMoveType: String): String {
            val encodedPlayerNames = Uri.encode(playerNames)
            val encodedGameMode = Uri.encode(gameMode)
            val encodedMoveType = Uri.encode(selectedMoveType)
            return "game?playerNames=$encodedPlayerNames&gameMode=$encodedGameMode&selectedMoveType=$encodedMoveType"
        }
    }
    object Training : Screen("training")
    object Leaderboard : Screen("leaderboard")
    object Settings : Screen("settings")
}
