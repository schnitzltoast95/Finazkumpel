package de.quartalsmappe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.AppearanceSettings
import de.quartalsmappe.app.reminder.ReminderScheduler
import de.quartalsmappe.app.time.SystemAppClock
import de.quartalsmappe.app.ui.QuartalsmappeApp
import de.quartalsmappe.app.ui.theme.QuartalsmappeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var store by remember { mutableStateOf<AppStore?>(null) }
            LaunchedEffect(Unit) {
                val loaded = withContext(Dispatchers.IO) { AppStore(applicationContext) }
                ReminderScheduler.scheduleAll(applicationContext, loaded.reminderSettings)
                store = loaded
            }
            val appearance = store?.appearanceSettings ?: AppearanceSettings()
            val darkTheme = appearance.themeMode.isDarkTheme(isSystemInDarkTheme())
            SideEffect {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(transparent, transparent) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(transparent, transparent) { darkTheme },
                )
            }
            QuartalsmappeTheme(palette = appearance.palette, darkTheme = darkTheme) {
                val ready = store
                if (ready == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = "Daten werden geladen" })
                    }
                } else {
                    QuartalsmappeApp(ready, SystemAppClock)
                }
            }
        }
    }
}

