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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
private data class MoveOption(val label: String)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameSetupScreen(
    navController: NavHostController,
    viewModel: GameSetupViewModel,
    sensorConnected: Boolean = false,
    batteryPercent: Int? = null,
    signalRssi: Int? = null
) {
    var numberOfPlayers by rememberSaveable { mutableIntStateOf(1) }
    val playerNames = remember { mutableStateListOf("") }
    var selectedGameMode by rememberSaveable { mutableIntStateOf(1) }
    var selectedCategory by rememberSaveable { mutableStateOf(MoveCategory.Punch) }
    var selectedMoveType by rememberSaveable { mutableStateOf("") }

    val punchOptions = listOf(MoveOption(stringResource(R.string.jab)), MoveOption(stringResource(R.string.cross)), MoveOption(stringResource(R.string.hook)))
    val kickOptions = listOf(MoveOption(stringResource(R.string.leg_kick)), MoveOption(stringResource(R.string.rib_kick)), MoveOption(stringResource(R.string.head_kick)), MoveOption(stringResource(R.string.front_kick)))
    LaunchedEffect(punchOptions.first().label) { if (selectedMoveType.isBlank()) selectedMoveType = punchOptions.first().label }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showErrorMessage by remember { mutableStateOf(false) }
    val searchResults = remember { mutableStateMapOf<Int, List<Player>>() }
    var focusedPlayerIndex by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun getPlayerName(index: Int): String = playerNames.getOrElse(index) { "" }
    fun setPlayerName(index: Int, value: String) {
        while (playerNames.size <= index) playerNames.add("")
        playerNames[index] = value
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(showErrorMessage) {
        if (showErrorMessage) {
            delay(4000)
            showErrorMessage = false
            errorMessage = null
        }
    }

    fun setPlayerCount(newCount: Int) {
        val count = newCount.coerceIn(1, 6)
        numberOfPlayers = count
        while (playerNames.size < count) playerNames.add("")
        while (playerNames.size > count) playerNames.removeAt(playerNames.lastIndex)
        if ((focusedPlayerIndex ?: -1) >= count) {
            focusedPlayerIndex = null
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
    fun hasDuplicateNames(names: List<String>) = names.distinct().size != names.size
    fun loadPlayerSuggestions(index: Int, query: String) {
        coroutineScope.launch { searchResults[index] = viewModel.searchPlayersNow(query) }
    }

    Box(Modifier.fillMaxSize()) {
        FightSmartSetupBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SensorStatusRow(
                connected = sensorConnected,
                rssi = signalRssi,
                batteryPercent = batteryPercent,
                modifier = Modifier.padding(bottom = 8.dp, end = 4.dp)
            )
            Text(
                text = stringResource(R.string.prepare_match).uppercase(),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            SetupMetalPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle(stringResource(R.string.players))
                    for (i in 0 until numberOfPlayers) {
                        Column(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = getPlayerName(i),
                                onValueChange = {
                                    setPlayerName(i, it)
                                    focusedPlayerIndex = i
                                    loadPlayerSuggestions(i, it)
                                },
                                label = { Text(stringResource(R.string.player_number_short, i + 1)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedLabelColor = Color.White.copy(alpha = 0.85f),
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.60f),
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color.White.copy(alpha = 0.85f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.38f),
                                    focusedContainerColor = Color.Black.copy(alpha = 0.28f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                                    if (state.isFocused) {
                                        focusedPlayerIndex = i
                                        keyboardController?.show()
                                        loadPlayerSuggestions(i, getPlayerName(i))
                                    }
                                }
                            )
                            val currentSearchResults = searchResults[i] ?: emptyList()
                            if (focusedPlayerIndex == i && currentSearchResults.isNotEmpty()) {
                                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 120.dp).background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))) {
                                    items(currentSearchResults) { player ->
                                        Text(player.playerName, color = Color.White, modifier = Modifier.fillMaxWidth().clickable {
                                            setPlayerName(i, player.playerName)
                                            searchResults[i] = emptyList()
                                            focusedPlayerIndex = null
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        }.padding(12.dp))
                                    }
                                }
                            }
                        }
                    }

                    CounterSection(stringResource(R.string.number_of_players_plain), numberOfPlayers, "1 - 6", { setPlayerCount(numberOfPlayers - 1) }, { setPlayerCount(numberOfPlayers + 1) })
                    Slider(value = numberOfPlayers.toFloat(), onValueChange = { setPlayerCount(it.roundToInt()) }, valueRange = 1f..6f, steps = 4)
                    CounterSection(stringResource(R.string.number_of_moves_plain), selectedGameMode, "1 - 20", { selectedGameMode = (selectedGameMode - 1).coerceIn(1, 20) }, { selectedGameMode = (selectedGameMode + 1).coerceIn(1, 20) })
                    Slider(value = selectedGameMode.toFloat(), onValueChange = { selectedGameMode = it.roundToInt().coerceIn(1, 20) }, valueRange = 1f..20f, steps = 18)

                    SectionTitle(stringResource(R.string.move_types))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoveCategoryCard(stringResource(R.string.punch), selectedCategory == MoveCategory.Punch, Modifier.weight(1f)) {
                            selectedCategory = MoveCategory.Punch
                            selectedMoveType = punchOptions.first().label
                        }
                        MoveCategoryCard(stringResource(R.string.kick), selectedCategory == MoveCategory.Kick, Modifier.weight(1f)) {
                            selectedCategory = MoveCategory.Kick
                            selectedMoveType = kickOptions.first().label
                        }
                    }
                    val visibleOptions = if (selectedCategory == MoveCategory.Punch) punchOptions else kickOptions
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        visibleOptions.chunked(2).forEach { rowOptions ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowOptions.forEach { option -> MoveOptionChip(option.label, selectedMoveType == option.label, Modifier.weight(1f)) { selectedMoveType = option.label } }
                                if (rowOptions.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            val activePlayerNames = (0 until numberOfPlayers).map { getPlayerName(it) }
            val isFormValid = activePlayerNames.none { it.isBlank() } && selectedMoveType.isNotBlank()
            val context = LocalContext.current
            SetupSummaryCard(numberOfPlayers, selectedGameMode, selectedMoveType, Modifier.fillMaxWidth().padding(top = 12.dp))
            StartGameButton(enabled = isFormValid, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp)) {
                if (hasDuplicateNames(activePlayerNames.map { it.trim() })) {
                    errorMessage = context.getString(R.string.error_duplicate_names)
                    showErrorMessage = true
                } else {
                    coroutineScope.launch {
                        try {
                            val playerIds = mutableListOf<Long>()
                            activePlayerNames.forEach { playerIds.add(viewModel.insertPlayerIfNotExists(it.trim())) }
                            viewModel.insertGameSession(playerIds, selectedGameMode.toString(), selectedMoveType)
                            navController.navigate(Screen.Game.createRoute(activePlayerNames.joinToString(",") { it.trim() }, selectedGameMode.toString(), selectedMoveType))
                        } catch (e: Exception) {
                            Log.e("GameSetupScreen", "Failed to start game", e)
                            errorMessage = context.getString(R.string.error_generic)
                            showErrorMessage = true
                        }
                    }
                }
            }
        }

        if (showErrorMessage) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).padding(16.dp), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White) {
                    Text(errorMessage ?: "", color = Color.Red, modifier = Modifier.padding(16.dp), fontSize = 18.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun FightSmartSetupBackground() {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.frame_fight), stringResource(R.string.frame_image), Modifier.fillMaxSize().blur(5.dp), contentScale = ContentScale.FillBounds)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color(0x990B0D10), Color.Black.copy(alpha = 0.48f)))))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), color = Color.White.copy(alpha = 0.76f), fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
}

@Composable
private fun CounterSection(title: String, value: Int, rangeText: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            SmallMetalButton("−", onMinus)
            Text(value.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.width(80.dp))
            SmallMetalButton("+", onPlus)
        }
        Text(rangeText, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SmallMetalButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.width(72.dp).height(42.dp).clip(shape).background(Brush.linearGradient(listOf(Color(0xFF2A2D31), Color(0xFF60646A), Color(0xFF1A1C20)))).border(1.dp, Color.White.copy(alpha = 0.30f), shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MoveCategoryCard(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Card(modifier.clip(shape).clickable(onClick = onClick), shape = shape, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f)) {
            Image(painterResource(R.drawable.button), null, Modifier.matchParentSize().graphicsLayer(scaleX = 1.04f, scaleY = 1.18f), contentScale = ContentScale.FillBounds)
            if (!selected) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.30f)))
            Box(Modifier.matchParentSize().border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color.LightGray.copy(alpha = 0.55f), shape), contentAlignment = Alignment.Center) {
                Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun MoveOptionChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val colors = if (selected) listOf(Color(0xFF454545), Color(0xFF8B8B8B), Color(0xFFD8D8D8), Color(0xFF777777), Color(0xFF2F2F2F)) else listOf(Color(0xFF202225), Color(0xFF515151), Color(0xFF25272B))
    Box(modifier.clip(shape).background(Brush.linearGradient(colors, Offset.Zero, Offset.Infinite), shape).border(if (selected) 2.dp else 1.dp, if (selected) Color.White.copy(alpha = 0.90f) else Color.LightGray.copy(alpha = 0.45f), shape).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SetupSummaryCard(players: Int, moves: Int, moveName: String, modifier: Modifier = Modifier) {
    SetupMetalPanel(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            SummaryCell(stringResource(R.string.players), players.toString())
            SummaryDivider()
            SummaryCell(stringResource(R.string.moves), moves.toString())
            SummaryDivider()
            SummaryCell(stringResource(R.string.types), moveName.uppercase())
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun SummaryDivider() { Box(Modifier.height(42.dp).width(1.dp).background(Color.White.copy(alpha = 0.18f))) }

@Composable
private fun StartGameButton(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.height(58.dp).clip(shape).background(Brush.horizontalGradient(if (enabled) listOf(Color(0xFF4A0808), Color(0xFFC62828), Color(0xFF4A0808)) else listOf(Color(0xFF252525), Color(0xFF3A3A3A), Color(0xFF252525)))).border(1.dp, if (enabled) Color(0xFFFF6A5E) else Color.White.copy(alpha = 0.22f), shape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.start_game).uppercase(), color = Color.White.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}

@Composable
private fun SetupMetalPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xDD050607), Color(0xDD23272D), Color(0xDD0A0B0D)))).border(1.dp, Color.White.copy(alpha = 0.24f), shape)) { content() }
}
