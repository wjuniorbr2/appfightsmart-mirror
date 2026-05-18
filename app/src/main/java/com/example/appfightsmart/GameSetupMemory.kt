package com.example.appfightsmart

object GameSetupMemory {
    var numberOfPlayers: Int = 1
    var numberOfMoves: Int = 1
    var selectedMoveType: String = ""
    var playerNames: MutableList<String> = MutableList(6) { "" }

    fun ensureSize() {
        while (playerNames.size < 6) playerNames.add("")
        if (playerNames.size > 6) playerNames = playerNames.take(6).toMutableList()
    }

    fun activeNames(): List<String> {
        ensureSize()
        return playerNames.take(numberOfPlayers.coerceIn(1, 6))
    }
}
