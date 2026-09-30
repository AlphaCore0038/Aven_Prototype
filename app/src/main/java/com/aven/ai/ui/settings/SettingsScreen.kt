package com.aven.ai.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aven.ai.data.local.ChatDao
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.ui.theme.rememberRefPalette
import kotlinx.coroutines.launch

/**
 * Aven settings — grouped into AI & Models, Appearance, Privacy, Data and
 * About. Rows for subsystems landing in later phases render as plain labels
 * until their wiring exists.
 */
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    chatDao: ChatDao,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenUserDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var confirmClearHistory by remember { mutableStateOf(false) }
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.AppSettings()
    )
    val themeLabel = when (settings.theme) {
        SettingsRepository.AppSettings.THEME_LIGHT -> "Light"
        SettingsRepository.AppSettings.THEME_SYSTEM -> "System"
        else -> "Dark"
    }
    val dark = settings.theme != SettingsRepository.AppSettings.THEME_LIGHT
    val ref = rememberRefPalette(dark)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ref.settingsBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Settings",
                fontSize = 21.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        SettingsGroup(dark = dark) {
            SettingsRow(
                icon = Icons.Filled.Palette,
                label = "Theme",
                subtext = themeLabel,
                dark = dark
            ) {
                ThemeSelector(settingsRepository)
            }
            SettingsRow(
                icon = Icons.Filled.Person,
                label = "User details",
                onClick = onOpenUserDetails,
                dark = dark
            )
            SettingsRow(
                icon = Icons.Filled.Cloud,
                label = "Online models",
                onClick = onOpenModels,
                showDivider = false,
                dark = dark
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsGroup(dark = dark) {
            SettingsRow(
                icon = Icons.Filled.DeleteOutline,
                label = "Clear Chat history",
                onClick = { confirmClearHistory = true },
                dark = dark
            )
            SettingsRow(
                icon = Icons.Filled.Info,
                label = "About Aven",
                onClick = onOpenAbout,
                showDivider = false,
                dark = dark
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear chat history?") },
            text = {
                Text("All conversations and their messages — private chats included — " +
                    "will be permanently deleted.")
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { chatDao.deleteAllConversations() }
                        confirmClearHistory = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable
private fun SettingsGroup(
    dark: Boolean,
    content: @Composable () -> Unit
) {
    val ref = rememberRefPalette(dark)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ref.settingsCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    subtext: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    dark: Boolean = true,
    content: (@Composable () -> Unit)? = null
) {
    val ref = rememberRefPalette(dark)
    val hasDetail = subtext != null || content != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = if (hasDetail) 14.dp else 16.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ref.settingsIcon,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtext != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtext,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Normal,
                            color = ref.settingsSubText
                        )
                    }
                }
            }
            if (content != null) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = ref.settingsDivider,
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun ThemeSelector(settingsRepository: SettingsRepository) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.AppSettings()
    )
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isLight = settings.theme == SettingsRepository.AppSettings.THEME_LIGHT

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isLight) "Light" else "Dark",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (expanded) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            ThemeOption(
                label = "Dark",
                selected = !isLight,
                onSelect = {
                    scope.launch {
                        settingsRepository.setTheme(SettingsRepository.AppSettings.THEME_DARK)
                    }
                    expanded = false
                }
            )
            ThemeOption(
                label = "Light",
                selected = isLight,
                onSelect = {
                    scope.launch {
                        settingsRepository.setTheme(SettingsRepository.AppSettings.THEME_LIGHT)
                    }
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}