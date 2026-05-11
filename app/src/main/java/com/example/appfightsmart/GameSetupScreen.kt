package com.example.appfightsmart

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.appfightsmart.database.Player
import com.example.appfightsmart.viewmodel.GameSetupViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class MoveCategory { Punch, Kick }

private data class MoveOption(
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun GameSetupScreen(navController: NavHostController, viewModel: GameSetupViewModel) {
    var numberOfPlayers by rememberSaveable { mutableIntStateOf(1) }
    val playerNames = remember { mutableStateListOf("") }
    var selectedGameMode by rememberSaveable { mutableIntStateOf(1) }
    var selectedCategory by rememberSaveable { mutableStateOf<MoveCategory?>(null) }
    var selectedMoveType by rememberSaveable { mutableStateOf("") }

    val punchOptions = listOf(
        MoveOption(stringResource(R.string.jab)),
        MoveOption(stringResource(R.string.cross)),
        MoveOption(stringResource(R.string.hook))
    )
    val kickOptions = listOf(
        MoveOption(stringResource(R.string.leg_kick)),
        MoveOption(stringResource(R.string.rib_kick)),
        MoveOption(stringResource(R.string.head_kick)),
        MoveOption(stringResource(R.string.front_kick))
    )

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

    fun setPlayerCount(newCount: Int) {
        val clampedCount = newCount.coerceIn(1, 10)
        numberOfPlayers = clampedCount
        while (playerNames.size < clampedCount) {
            playerNames.add("")
        }
        while (playerNames.size > clampedCount) {
            playerNames.removeAt(playerNames.lastIndex)
        }
        if ((focusedPlayerIndex ?: -1) >= clampedCount) {
            focusedPlayerIndex = null
            focusManager.clearFocus()
            keyboardController?.hide()
        }
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
                    onValueChange = { setPlayerCount(it.roundToInt()) },
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
                    onValueChange = { selectedGameMode = it.roundToInt().coerceIn(1, 20) },
                    valueRange = 1f..20f,
                    steps = 18,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Text(text = stringResource(R.string.move_types), modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoveCategoryCard(
                        text = stringResource(R.string.punch),
                        selected = selectedCategory == MoveCategory.Punch,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedCategory = MoveCategory.Punch
                            selectedMoveType = ""
                        }
                    )
                    MoveCategoryCard(
                        text = stringResource(R.string.kick),
                        selected = selectedCategory == MoveCategory.Kick,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedCategory = MoveCategory.Kick
                            selectedMoveType = ""
                        }
                    )
                }

                val visibleOptions = when (selectedCategory) {
                    MoveCategory.Punch -> punchOptions
                    MoveCategory.Kick -> kickOptions
                    null -> emptyList()
                }

                if (visibleOptions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleOptions.chunked(2).forEach { rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowOptions.forEach { option ->
                                    FilterChip(
                                        selected = selectedMoveType == option.label,
                                        onClick = { selectedMoveType = option.label },
                                        label = {
                                            Text(
                                                text = option.label,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowOptions.size == 1) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                val activePlayerNames = playerNames.take(numberOfPlayers)
                val isFormValid = activePlayerNames.none { it.isBlank() } && selectedMoveType.isNotBlank()
                val context = LocalContext.current

                Button(
                    onClick = {
                        if (hasDuplicateNames(activePlayerNames.map { it.trim() })) {
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
                        color = Color.White
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

@Composable
private fun MoveCategoryCard(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Card(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.button),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )

            if (!selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.18f))
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Color.White else Color.LightGray.copy(alpha = 0.70f),
                        shape = shape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
