package com.example.appfightsmart

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import java.util.Locale

@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }

    var language by remember { mutableStateOf(sharedPreferences.getString("language", "en") ?: "en") }
    var isDarkMode by remember { mutableStateOf(sharedPreferences.getBoolean("dark_mode", false)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(id = R.string.theme), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(text = "Normal Mode", modifier = Modifier.padding(end = 16.dp))
            Switch(
                checked = isDarkMode,
                onCheckedChange = {
                    isDarkMode = it
                    sharedPreferences.edit().putBoolean("dark_mode", it).apply()
                    setAppTheme(context, it)
                }
            )
            Text(text = "Dark Mode", modifier = Modifier.padding(start = 16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.language), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        Spacer(modifier = Modifier.height(8.dp))
        val languages = listOf("English" to "en", "Português" to "pt")
        languages.forEach { (langName, langCode) ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = (langCode == language),
                    onClick = {
                        language = langCode
                        sharedPreferences.edit().putString("language", langCode).apply()
                        setAppLanguage(context, langCode)
                        restartActivity(context)
                    }
                ).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (langCode == language),
                    onClick = {
                        language = langCode
                        sharedPreferences.edit().putString("language", langCode).apply()
                        setAppLanguage(context, langCode)
                        restartActivity(context)
                    }
                )
                Text(text = langName, modifier = Modifier.padding(start = 8.dp))
            }
        }

        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

        Button(onClick = { navController.navigate(Screen.Calibration.route) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.calibrate_sensor))
        }
    }
}

private fun setAppLanguage(context: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val config = Configuration()
    config.setLocale(locale)
    return context.createConfigurationContext(config)
}

private fun setAppTheme(context: Context, isDarkMode: Boolean) {
    AppCompatDelegate.setDefaultNightMode(if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
    restartActivity(context)
}

private fun restartActivity(context: Context) {
    val intent = Intent(context, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    (context as? Activity)?.finish()
}
