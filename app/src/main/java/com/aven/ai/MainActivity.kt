package com.aven.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
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
            AvenTheme(darkTheme = settings.theme != SettingsRepository.AppSettings.THEME_LIGHT) {
                AvenApp()
            }
        }
    }
}