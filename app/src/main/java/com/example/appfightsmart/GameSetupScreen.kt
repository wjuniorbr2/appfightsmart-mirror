package com.example.appfightsmart

import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    GameSetupMemory.ensureSize()
    var numberOfPlayers by remember { mutableIntStateOf(GameSetupMemory.numberOfPlayers.coerceIn(1, 6)) }
    val playerNames = remember { mutableStateListOf<String>().also { it.addAll(GameSetupMemory.playerNames.take(6)) } }
    var selectedGameMode by remember { mutableIntStateOf(GameSetupMemory.numberOfMoves.coerceIn(1, 20)) }
    var selectedCategory by remember { mutableStateOf(MoveCategory.Punch) }
    var selectedMoveType by remember { mutableStateOf(GameSetupMemory.selectedMoveType) }

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

    var missingProfiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var profileIndex by remember { mutableIntStateOf(0) }
    var profileName by remember { mutableStateOf("") }
    var profileHeight by remember { mutableStateOf("") }
    var profilePunchHeight by remember { mutableStateOf("") }
    var profileHand by remember { mutableStateOf("Right") }

    fun hideSuggestionsAndKeyboard() {
        focusedPlayerIndex = null
        searchResults.clear()
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    fun saveSetupMemory() {
        GameSetupMemory.numberOfPlayers = numberOfPlayers.coerceIn(1, 6)
        GameSetupMemory.numberOfMoves = selectedGameMode.coerceIn(1, 20)
        GameSetupMemory.selectedMoveType = selectedMoveType
        GameSetupMemory.ensureSize()
        while (playerNames.size < 6) playerNames.add("")
        GameSetupMemory.playerNames = playerNames.take(6).toMutableList()
    }

    BackHandler(enabled = focusedPlayerIndex != null) { hideSuggestionsAndKeyboard() }
    LaunchedEffect(numberOfPlayers, selectedGameMode, selectedMoveType, playerNames.toList()) { saveSetupMemory() }
    LaunchedEffect(scrollState.isScrollInProgress) { if (scrollState.isScrollInProgress) hideSuggestionsAndKeyboard() }
    LaunchedEffect(showErrorMessage) { if (showErrorMessage) { delay(4000); showErrorMessage = false; errorMessage = null } }

    fun getPlayerName(index: Int): String = playerNames.getOrElse(index) { "" }
    fun setPlayerName(index: Int, value: String) {
        while (playerNames.size <= index) playerNames.add("")
        playerNames[index] = value
        saveSetupMemory()
    }
    fun setPlayerCount(newCount: Int) {
        val count = newCount.coerceIn(1, 6)
        numberOfPlayers = count
        while (playerNames.size < 6) playerNames.add("")
        if ((focusedPlayerIndex ?: -1) >= count) hideSuggestionsAndKeyboard()
        saveSetupMemory()
    }
    fun hasDuplicateNames(names: List<String>) = names.distinct().size != names.size
    fun loadPlayerSuggestions(index: Int, query: String) { coroutineScope.launch { searchResults[index] = viewModel.searchPlayersNow(query) } }

    fun startMissingProfileFlow(names: List<String>) {
        missingProfiles = names
        profileIndex = 0
        profileName = names.firstOrNull().orEmpty()
        profileHeight = ""
        profilePunchHeight = "120"
        profileHand = "Right"
    }

    fun continueOrStartGame(names: List<String>) {
        saveSetupMemory()
        coroutineScope.launch {
            try {
                val players = viewModel.getPlayersByNames(names)
                val missing = names.filterIndexed { index, _ -> players.getOrNull(index)?.hasCompleteProfile() != true }
                if (missing.isNotEmpty()) {
                    startMissingProfileFlow(missing)
                } else {
                    val playerIds = names.map { viewModel.insertPlayerIfNotExists(it) }
                    viewModel.insertGameSession(playerIds, selectedGameMode.toString(), selectedMoveType)
                    val heights = players.map { it?.naturalPunchHeightCm ?: 120 }.joinToString(",")
                    navController.navigate(Screen.Game.createRoute(names.joinToString(","), selectedGameMode.toString(), selectedMoveType, heights))
                }
            } catch (e: Exception) {
                Log.e("GameSetupScreen", "Failed to start game", e)
                errorMessage = "Could not start game."
                showErrorMessage = true
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        FightSmartSetupBackground()
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 5.dp).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SensorStatusRow(connected = sensorConnected, rssi = signalRssi, batteryPercent = batteryPercent, modifier = Modifier.padding(bottom = 3.dp, end = 4.dp))
            Text(stringResource(R.string.prepare_match).uppercase(), color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(bottom = 4.dp))

            SetupMetalPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(if (numberOfPlayers == 1) 8.dp else 12.dp)) {
                    SectionTitle(stringResource(R.string.players))
                    for (i in 0 until numberOfPlayers) {
                        Column(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = getPlayerName(i),
                                onValueChange = { setPlayerName(i, it); focusedPlayerIndex = i; loadPlayerSuggestions(i, it) },
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
                                    if (state.isFocused) { focusedPlayerIndex = i; keyboardController?.show(); loadPlayerSuggestions(i, getPlayerName(i)) }
                                }
                            )
                            val currentSearchResults = searchResults[i] ?: emptyList()
                            if (focusedPlayerIndex == i && currentSearchResults.isNotEmpty()) {
                                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 196.dp).background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(8.dp))) {
                                    items(currentSearchResults) { player ->
                                        Text(player.playerName, color = Color.White, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().height(46.dp).clickable { setPlayerName(i, player.playerName); hideSuggestionsAndKeyboard() }.padding(horizontal = 12.dp, vertical = 12.dp))
                                    }
                                }
                            }
                        }
                    }

                    CounterSection("${stringResource(R.string.number_of_players_plain)} (1 - 6)", numberOfPlayers, { setPlayerCount(numberOfPlayers - 1) }, { setPlayerCount(numberOfPlayers + 1) })
                    Slider(value = numberOfPlayers.toFloat(), onValueChange = { setPlayerCount(it.roundToInt()) }, valueRange = 1f..6f, steps = 4, modifier = Modifier.padding(top = 0.dp))
                    CounterSection("${stringResource(R.string.number_of_moves_plain)} (1 - 20)", selectedGameMode, { selectedGameMode = (selectedGameMode - 1).coerceIn(1, 20); saveSetupMemory() }, { selectedGameMode = (selectedGameMode + 1).coerceIn(1, 20); saveSetupMemory() })
                    Slider(value = selectedGameMode.toFloat(), onValueChange = { selectedGameMode = it.roundToInt().coerceIn(1, 20); saveSetupMemory() }, valueRange = 1f..20f, steps = 18, modifier = Modifier.padding(top = 0.dp))

                    SectionTitle(stringResource(R.string.move_types))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoveCategoryCard(stringResource(R.string.punch), selectedCategory == MoveCategory.Punch, Modifier.weight(1f)) { selectedCategory = MoveCategory.Punch; selectedMoveType = punchOptions.first().label; saveSetupMemory() }
                        MoveCategoryCard(stringResource(R.string.kick), selectedCategory == MoveCategory.Kick, Modifier.weight(1f)) { selectedCategory = MoveCategory.Kick; selectedMoveType = kickOptions.first().label; saveSetupMemory() }
                    }
                    val visibleOptions = if (selectedCategory == MoveCategory.Punch) punchOptions else kickOptions
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        visibleOptions.chunked(2).forEach { rowOptions ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowOptions.forEach { option -> MoveOptionChip(option.label, selectedMoveType == option.label, Modifier.weight(1f)) { selectedMoveType = option.label; saveSetupMemory() } }
                                if (rowOptions.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            val activePlayerNames = (0 until numberOfPlayers).map { getPlayerName(it).trim() }
            val isFormValid = activePlayerNames.none { it.isBlank() } && selectedMoveType.isNotBlank()
            val context = LocalContext.current
            SetupSummaryCard(numberOfPlayers, selectedGameMode, selectedMoveType, Modifier.fillMaxWidth().padding(top = 7.dp))
            StartGameButton(enabled = isFormValid, modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 8.dp)) {
                if (hasDuplicateNames(activePlayerNames)) { errorMessage = context.getString(R.string.error_duplicate_names); showErrorMessage = true }
                else continueOrStartGame(activePlayerNames)
            }
        }

        if (showErrorMessage) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).padding(16.dp), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White) { Text(errorMessage ?: "", color = Color.Red, modifier = Modifier.padding(16.dp), fontSize = 18.sp, textAlign = TextAlign.Center) }
            }
        }

        if (missingProfiles.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Player information") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add data for $profileName. This is saved for future games.")
                        OutlinedTextField(value = profileHeight, onValueChange = { profileHeight = it.filter { ch -> ch.isDigit() }.take(3) }, label = { Text("Player height cm") }, singleLine = true)
                        OutlinedTextField(value = profilePunchHeight, onValueChange = { profilePunchHeight = it.filter { ch -> ch.isDigit() }.take(3) }, label = { Text("Natural punch height on bag cm") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { profileHand = "Right" }) { Text(if (profileHand == "Right") "✓ Right" else "Right") }
                            TextButton(onClick = { profileHand = "Left" }) { Text(if (profileHand == "Left") "✓ Left" else "Left") }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { coroutineScope.launch { viewModel.savePlayerProfile(profileName, profileHeight.toIntOrNull(), profilePunchHeight.toIntOrNull(), profileHand); if (profileIndex >= missingProfiles.lastIndex) { val allNames = (0 until numberOfPlayers).map { getPlayerName(it).trim() }; missingProfiles = emptyList(); continueOrStartGame(allNames) } else { profileIndex++; profileName = missingProfiles[profileIndex]; profileHeight = ""; profilePunchHeight = "120"; profileHand = "Right" } } }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { missingProfiles = emptyList() }) { Text("Cancel") } }
            )
        }
    }
}

@Composable private fun FightSmartSetupBackground() { Box(Modifier.fillMaxSize()) { Image(painterResource(R.drawable.frame_fight), stringResource(R.string.frame_image), Modifier.fillMaxSize().blur(5.dp), contentScale = ContentScale.FillBounds); Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f))); Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color(0x990B0D10), Color.Black.copy(alpha = 0.48f))))) } }
@Composable private fun SectionTitle(text: String) { Text(text.uppercase(), color = Color.White.copy(alpha = 0.76f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }
@Composable private fun CounterSection(title: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) { Column(Modifier.fillMaxWidth()) { SectionTitle(title); Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { SmallMetalButton("−", onMinus); Text(value.toString(), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp)); SmallMetalButton("+", onPlus) } } }
@Composable private fun SmallMetalButton(text: String, onClick: () -> Unit) { val shape = RoundedCornerShape(12.dp); Box(Modifier.width(62.dp).height(36.dp).clip(shape).background(Brush.linearGradient(listOf(Color(0xFF2A2D31), Color(0xFF60646A), Color(0xFF1A1C20)))).border(1.dp, Color.White.copy(alpha = 0.30f), shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun MoveCategoryCard(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) { val shape = RoundedCornerShape(14.dp); Card(modifier.clip(shape).clickable(onClick = onClick), shape = shape, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) { Box(Modifier.fillMaxWidth().aspectRatio(3.35f)) { Image(painterResource(R.drawable.button), null, Modifier.matchParentSize().graphicsLayer(scaleX = 1.04f, scaleY = 1.18f), contentScale = ContentScale.FillBounds); if (!selected) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.30f))); Box(Modifier.matchParentSize().border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color.LightGray.copy(alpha = 0.55f), shape), contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, textAlign = TextAlign.Center) } } } }
@Composable private fun MoveOptionChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) { val shape = RoundedCornerShape(18.dp); val colors = if (selected) listOf(Color(0xFF454545), Color(0xFF8B8B8B), Color(0xFFD8D8D8), Color(0xFF777777), Color(0xFF2F2F2F)) else listOf(Color(0xFF202225), Color(0xFF515151), Color(0xFF25272B)); Box(modifier.clip(shape).background(Brush.linearGradient(colors, Offset.Zero, Offset.Infinite), shape).border(if (selected) 2.dp else 1.dp, if (selected) Color.White.copy(alpha = 0.90f) else Color.LightGray.copy(alpha = 0.45f), shape).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
@Composable private fun SetupSummaryCard(players: Int, moves: Int, moveName: String, modifier: Modifier = Modifier) { SetupMetalPanel(modifier) { Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { SummaryCell(stringResource(R.string.players), players.toString()); SummaryDivider(); SummaryCell(stringResource(R.string.moves), moves.toString()); SummaryDivider(); SummaryCell(stringResource(R.string.types), moveName.uppercase()) } } }
@Composable private fun SummaryCell(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1) } }
@Composable private fun SummaryDivider() { Box(Modifier.height(34.dp).width(1.dp).background(Color.White.copy(alpha = 0.18f))) }
@Composable private fun StartGameButton(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) { val shape = RoundedCornerShape(18.dp); Box(modifier.height(50.dp).clip(shape).background(Brush.horizontalGradient(if (enabled) listOf(Color(0xFF4A0808), Color(0xFFC62828), Color(0xFF4A0808)) else listOf(Color(0xFF252525), Color(0xFF3A3A3A), Color(0xFF252525)))).border(1.dp, if (enabled) Color(0xFFFF6A5E) else Color.White.copy(alpha = 0.22f), shape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { Text(stringResource(R.string.start_game).uppercase(), color = Color.White.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp) } }
@Composable private fun SetupMetalPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) { val shape = RoundedCornerShape(22.dp); Box(modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xDD050607), Color(0xDD23272D), Color(0xDD0A0B0D)))).border(1.dp, Color.White.copy(alpha = 0.24f), shape)) { content() } }