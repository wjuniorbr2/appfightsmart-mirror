package com.example.appfightsmart

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.appfightsmart.database.GameSessionRepository
import com.example.appfightsmart.database.Player
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfilesScreen(navController: NavHostController, repository: GameSessionRepository) {
    val scope = rememberCoroutineScope()
    var players by remember { mutableStateOf<List<Player>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var punchHeight by remember { mutableStateOf("") }
    var dominantHand by remember { mutableStateOf("Right") }
    var message by remember { mutableStateOf("") }

    fun reload() { scope.launch { players = repository.getAllPlayers() } }
    LaunchedEffect(Unit) { reload() }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Add players") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Image(painterResource(R.drawable.frame_fight), null, Modifier.fillMaxSize().blur(5.dp), contentScale = ContentScale.FillBounds)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.56f)))
            Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Player profile", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("Save height and natural punch height so the game can normalize scores more fairly.", color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                ProfilePanel {
                    ProfileTextField("Name", name) { name = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileTextField("Height cm", height, Modifier.weight(1f)) { height = it.filter { ch -> ch.isDigit() }.take(3) }
                        ProfileTextField("Punch height cm", punchHeight, Modifier.weight(1f)) { punchHeight = it.filter { ch -> ch.isDigit() }.take(3) }
                    }
                    Text("Dominant hand", color = Color.White.copy(alpha = 0.70f), fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallChoice("Right", dominantHand == "Right", Modifier.weight(1f)) { dominantHand = "Right" }
                        SmallChoice("Left", dominantHand == "Left", Modifier.weight(1f)) { dominantHand = "Left" }
                    }
                    MainActionButton("Save player") {
                        val cleanName = name.trim()
                        if (cleanName.isBlank()) {
                            message = "Type the player name."
                        } else {
                            scope.launch {
                                repository.savePlayerProfile(cleanName, height.toIntOrNull(), punchHeight.toIntOrNull(), dominantHand)
                                message = "Saved $cleanName"
                                name = ""
                                height = ""
                                punchHeight = ""
                                dominantHand = "Right"
                                reload()
                            }
                        }
                    }
                    if (message.isNotBlank()) Text(message, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                if (players.isNotEmpty()) {
                    Text("Saved players", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth())
                    players.forEach { p ->
                        ProfilePanel {
                            Text(p.playerName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text("Height: ${p.heightCm ?: "--"} cm | Punch height: ${p.naturalPunchHeightCm ?: "--"} cm | Hand: ${p.dominantHand ?: "--"}", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Text("Edit", color = Color(0xFFFF5A4E), fontWeight = FontWeight.Black, modifier = Modifier.clickable {
                                    name = p.playerName
                                    height = p.heightCm?.toString().orEmpty()
                                    punchHeight = p.naturalPunchHeightCm?.toString().orEmpty()
                                    dominantHand = p.dominantHand ?: "Right"
                                }.padding(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePanel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(Color(0xDD050607), Color(0xDD23272D), Color(0xDD0A0B0D)))).border(1.dp, Color.White.copy(alpha = 0.24f), shape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun ProfileTextField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color.White.copy(alpha = 0.85f), unfocusedLabelColor = Color.White.copy(alpha = 0.60f), cursorColor = Color.White, focusedBorderColor = Color.White.copy(alpha = 0.85f), unfocusedBorderColor = Color.White.copy(alpha = 0.38f), focusedContainerColor = Color.Black.copy(alpha = 0.28f), unfocusedContainerColor = Color.Black.copy(alpha = 0.22f)), modifier = modifier.fillMaxWidth())
}

@Composable
private fun SmallChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.height(42.dp).clip(shape).background(if (selected) Color(0xFFC62828) else Color.Black.copy(alpha = 0.40f)).border(1.dp, Color.White.copy(alpha = if (selected) 0.85f else 0.35f), shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontWeight = FontWeight.Black) }
}

@Composable
private fun MainActionButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().height(52.dp).clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF4A0808), Color(0xFFC62828), Color(0xFF4A0808)))).border(1.dp, Color(0xFFFF6A5E), shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(text.uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black) }
}
