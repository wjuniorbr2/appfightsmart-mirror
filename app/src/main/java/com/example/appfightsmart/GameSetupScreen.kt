package com.example.appfightsmart

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.appfightsmart.database.Player
import com.example.appfightsmart.viewmodel.GameSetupViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class MoveTypeOption(
    val label: String,
    val enabled: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun GameSetupScreen(navController: NavHostController, viewModel: GameSetupViewModel) {
    var numberOfPlayers by rememberSaveable { mutableIntStateOf(1) }
    val playerNames = remember { mutableStateListOf("") }
    var selectedGameMode by rememberSaveable { mutableIntStateOf(1) }

    val moveTypes = listOf(
        MoveTypeOption(stringResource(R.string.punch_with_examples), true),
        MoveTypeOption(stringResource(R.string.hook), false),
        MoveTypeOption(stringResource(R.string.front_kick), false),
        MoveTypeOption(stringResource(R.string.leg_kick), false),
        MoveTypeOption(stringResource(R.string.rib_kick), false),
        MoveTypeOption(stringResource(R.string.head_kick), false)
    )
    var selectedMoveType by rememberSaveable { mutableStateOf("") }
    val punchLabel = stringResource(R.string.punch_with_examples)

    LaunchedEffect(punchLabel) {
        selectedMoveType = punchLabel
    }

    LaunchedEffect(numberOfPlayers) {
        while (playerNames.size < numberOfPlayers) {
            playerNames.add("")
        }
        while (playerNames.size > numberOfPlayers) {
            playerNames.removeAt(playerNames.lastIndex)
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showErrorMessage by remember { mutableStateOf(false) }

    val searchQueries = remember { mutableStateMapOf<Int, String>() }
    val searchResults = remember { mutableStateMapOf<Int, List<Player>>() }
    var focusedPlayerIndex by remember { mutableStateOf<Int?>(null) }

    val coroutineScope = rememberCoroutineScope()

    fun hasDuplicateNames(names: List<String>): Boolean {
        return names.distinct().size != names.size
    }

    fun loadPlayerSuggestions(index: Int, query: String) {
        searchQueries[index] = query
        coroutineScope.launch {
            searchResults[index] = viewModel.searchPlayersNow(query)
        }
    }

    LaunchedEffect(key1 = showErrorMessage) {
        if (showErrorMessage) {
            delay(4000)
            showErrorMessage = false
            errorMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.game_setup)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(text = stringResource(R.string.number_of_players, numberOfPlayers))
                Slider(
                    value = numberOfPlayers.toFloat(),
                    onValueChange = { numberOfPlayers = it.toInt() },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                for (i in 0 until numberOfPlayers) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stringResource(R.string.player_number_short, i + 1),
                            modifier = Modifier
                                .width(76.dp)
                                .padding(top = 24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = playerNames[i],
                                onValueChange = { newValue ->
                                    playerNames[i] = newValue
                                    focusedPlayerIndex = i
                                    loadPlayerSuggestions(i, newValue)
                                },
                                label = { Text(stringResource(R.string.name)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            focusedPlayerIndex = i
                                            keyboardController?.show()
                                            searchQueries.keys.forEach { key ->
                                                if (key != i) {
                                                    searchQueries[key] = ""
                                                    searchResults[key] = emptyList()
                                                }
                                            }
                                            loadPlayerSuggestions(i, playerNames[i])
                                        }
                                    }
                            )

                            val currentSearchResults = searchResults[i] ?: emptyList()
                            if (focusedPlayerIndex == i && currentSearchResults.isNotEmpty()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 120.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    items(currentSearchResults) { player ->
                                        Text(
                                            text = player.playerName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    playerNames[i] = player.playerName
                                                    searchQueries[i] = ""
                                                    searchResults[i] = emptyList()
                                                    focusedPlayerIndex = null
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                }
                                                .padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(text = stringResource(R.string.game_mode), modifier = Modifier.padding(top = 8.dp))
                Text(text = stringResource(R.string.number_of_moves, selectedGameMode))
                Slider(
                    value = selectedGameMode.toFloat(),
                    onValueChange = { selectedGameMode = it.toInt() },
                    valueRange = 1f..20f,
                    steps = 18,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Text(text = stringResource(R.string.move_types), modifier = Modifier.padding(top = 8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(moveTypes) { moveType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp)
                                .clickable(
                                    enabled = moveType.enabled,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedMoveType = moveType.label
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            RadioButton(
                                selected = selectedMoveType == moveType.label,
                                enabled = moveType.enabled,
                                onClick = { selectedMoveType = moveType.label }
                            )
                            Text(
                                text = moveType.label,
                                color = if (moveType.enabled) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
                                },
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                val activePlayerNames = playerNames.take(numberOfPlayers)
                val isFormValid = activePlayerNames.none { it.isBlank() } && selectedMoveType.isNotBlank()
                val context = LocalContext.current

                Button(
                    onClick = {
                        if (hasDuplicateNames(activePlayerNames)) {
                            errorMessage = context.getString(R.string.error_duplicate_names)
                            showErrorMessage = true
                        } else {
                            coroutineScope.launch {
                                try {
                                    val playerIds = mutableListOf<Long>()
                                    activePlayerNames.forEach { name ->
                                        val playerId = viewModel.insertPlayerIfNotExists(name.trim())
                                        playerIds.add(playerId)
                                    }
                                    viewModel.insertGameSession(playerIds, selectedGameMode.toString(), selectedMoveType)

                                    val route = Screen.Game.createRoute(
                                        playerNames = activePlayerNames.joinToString(",") { it.trim() },
                                        gameMode = selectedGameMode.toString(),
                                        selectedMoveType = selectedMoveType
                                    )
                                    navController.navigate(route)
                                } catch (e: Exception) {
                                    Log.e("GameSetupScreen", "Failed to start game", e)
                                    errorMessage = context.getString(R.string.error_generic)
                                    showErrorMessage = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                    enabled = isFormValid
                ) {
                    Text(stringResource(R.string.start_game))
                }
            }

            if (showErrorMessage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color.Black)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}