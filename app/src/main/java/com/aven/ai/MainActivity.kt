package com.aven.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.ui.AvenApp
import com.aven.ai.ui.theme.AvenTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as AvenApplication).container
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = SettingsRepository.AppSettings()
            )
            val darkTheme = settings.theme != SettingsRepository.AppSettings.THEME_LIGHT

            // System bars follow the app theme, not the OS setting, so light
            // mode gets dark icons immediately when the theme switches.
            DisposableEffect(darkTheme) {
                val clearScrim = Color.Transparent.toArgb()
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(clearScrim)
                    } else {
                        SystemBarStyle.light(clearScrim, clearScrim)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(clearScrim)
                    } else {
                        SystemBarStyle.light(clearScrim, clearScrim)
                    }
                )
                onDispose { }
            }

            AvenTheme(darkTheme = darkTheme) {
                // One themed canvas behind every screen: screens that do not
                // paint their own background (chat, welcome, sub-screens)
                // would otherwise show the hardcoded dark window background.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AvenApp()
                }
            }
        }
    }
}