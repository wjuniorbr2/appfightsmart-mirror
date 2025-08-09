package com.example.appfightsmart

import android.content.Intent
import android.view.View

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object GameSetup : Screen("game_setup")
    object Game : Screen("game?playerNames={playerNames}&gameMode={gameMode}&selectedMoveType={selectedMoveType}") {
        fun createRoute(playerNames: String, gameMode: String, selectedMoveType: String): String {
            return "game?playerNames=$playerNames&gameMode=$gameMode&selectedMoveType=$selectedMoveType"
        }
    }
    object Training : Screen("training")
    object Leaderboard : Screen("leaderboard")
    object Settings : Screen("settings")
}