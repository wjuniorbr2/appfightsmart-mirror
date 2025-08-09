package com.example.appfightsmart

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun GameSetupScreen(navController: NavHostController, viewModel: GameSetupViewModel) {
    // State declarations
    var numberOfPlayers by remember { mutableIntStateOf(2) }
    var playerNames = remember { mutableStateListOf<String>() }
    var selectedGameMode by remember { mutableIntStateOf(1) } // Changed to Int, default to 1 move
    val moveTypes = listOf(
        stringResource(R.string.punch),
        stringResource(R.string.hook),
        stringResource(R.string.front_kick),
        stringResource(R.string.leg_kick),
        stringResource(R.string.rib_kick),
        stringResource(R.string.head_kick)
    )
    var selectedMoveType by remember { mutableStateOf(moveTypes[0]) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // State to show errors or success messages
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showErrorMessage by remember { mutableStateOf(false) }

    // Track search state per field
    val searchQueries = remember { mutableStateMapOf<Int, String>() }
    val searchResults = remember { mutableStateMapOf<Int, List<Player>>() }

    // Create a coroutine scope for launching suspendable operations
    val coroutineScope = rememberCoroutineScope()

    // Function to check for duplicate names
    fun hasDuplicateNames(names: List<String>): Boolean {
        return names.distinct().size != names.size
    }

    // LaunchedEffect to handle error message timeout
    LaunchedEffect(key1 = showErrorMessage) {
        if (showErrorMessage) {
            delay(4000) // 4 seconds
            showErrorMessage = false
            errorMessage = null // Clear the error message
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
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Number of Players Section
                Text(text = stringResource(R.string.number_of_players, numberOfPlayers))
                Slider(
                    value = numberOfPlayers.toFloat(),
                    onValueChange = { numberOfPlayers = it.toInt() },
                    valueRange = 2f..10f,
                    steps = 8,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                focusManager.clearFocus()
                            }
                        }
                )

                // Player Names Section
                for (i in 0 until numberOfPlayers) {
                    if (playerNames.size <= i) {
                        playerNames.add("")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.player_number, i + 1, playerNames[i]), // Fixed line
                            modifier = Modifier.width(80.dp)
                        )
                        Column {
                            OutlinedTextField(
                                value = playerNames[i],
                                onValueChange = { newValue ->
                                    playerNames[i] = newValue
                                    searchQueries[i] = newValue // Update search query for this field
                                    coroutineScope.launch {
                                        viewModel.searchPlayers(newValue) // Perform the search
                                        searchResults[i] = viewModel.searchResults // Update search results for this field
                                    }
                                },
                                label = { Text(stringResource(R.string.name)) },
                                modifier = Modifier.padding(8.dp)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            keyboardController?.show()
                                            // Clear search results for all other fields
                                            searchQueries.keys.forEach { key ->
                                                if (key != i) {
                                                    searchQueries[key] = ""
                                                    searchResults[key] = emptyList()
                                                }
                                            }
                                        }
                                    }
                            )

                            // Display search results for this field
                            val currentSearchResults = searchResults[i] ?: emptyList()
                            if (searchQueries[i]?.isNotEmpty() == true) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 150.dp) // Limit the height of the dropdown
                                ) {
                                    items(currentSearchResults) { player ->
                                        Text(
                                            text = player.playerName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                                .clickable {
                                                    playerNames[i] = player.playerName // Autocomplete the correct field
                                                    searchQueries[i] = "" // Clear the search query for this field
                                                    searchResults[i] = emptyList() // Clear the search results for this field
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Game Mode Section
                Text(text = stringResource(R.string.game_mode), modifier = Modifier.padding(top = 16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Replace RadioButtons with Slider
                    Text(text = stringResource(R.string.number_of_moves, selectedGameMode))
                    Slider(
                        value = selectedGameMode.toFloat(),
                        onValueChange = { selectedGameMode = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 19,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Move Types Section
                Text(text = stringResource(R.string.move_types), modifier = Modifier.padding(top = 16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    moveTypes.forEach { moveType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 60.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            RadioButton(
                                selected = selectedMoveType == moveType,
                                onClick = { selectedMoveType = moveType }
                            )
                            Text(text = moveType)
                        }
                    }
                }

                // Start Game Button
                val isFormValid = playerNames.none { it.isBlank() } && selectedMoveType.isNotBlank()

                val context = LocalContext.current // Retrieve context for string resources

                Button(
                    onClick = {
                        if (hasDuplicateNames(playerNames)) {
                            errorMessage = context.getString(R.string.error_duplicate_names) // Use context instead of stringResource
                            showErrorMessage = true
                        } else {
                            coroutineScope.launch {
                                try {
                                    val playerIds = mutableListOf<Long>()
                                    playerNames.forEach { name ->
                                        val playerId = viewModel.insertPlayerIfNotExists(name)
                                        playerIds.add(playerId)
                                    }
                                    viewModel.insertGameSession(playerIds, selectedGameMode.toString(), selectedMoveType)

                                    val route = Screen.Game.createRoute(
                                        playerNames = playerNames.joinToString(","),
                                        gameMode = selectedGameMode.toString(),
                                        selectedMoveType = selectedMoveType
                                    )
                                    navController.navigate(route)
                                } catch (e: Exception) {
                                    errorMessage = context.getString(R.string.error_generic) // Use context instead
                                    showErrorMessage = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 32.dp),
                    enabled = isFormValid
                ) {
                    Text(stringResource(R.string.start_game)) // This is fine because it's inside a @Composable
                }
            }

            // Show error message if any (outside the Column but inside the Box)
            if (showErrorMessage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)) // Semi-transparent background overlay
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White, // White background
                        border = BorderStroke(1.dp, Color.Black) // Black border line
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red, // Red text color
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