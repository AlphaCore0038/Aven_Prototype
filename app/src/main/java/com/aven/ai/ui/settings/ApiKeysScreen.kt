package com.aven.ai.ui.settings

import android.content.ClipData
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aven.ai.data.security.KeyManager
import kotlinx.coroutines.launch

/**
 * Manage the API keys stored in the Keystore-backed secure store. Values are
 * shown masked; they can be replaced or cleared. Nothing leaves the device.
 */
@Composable
fun ApiKeysScreen(
    keyManager: KeyManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zenKey by remember { mutableStateOf(keyManager.readKey(KeyManager.Provider.ZEN)) }
    var openRouterKey by remember { mutableStateOf(keyManager.readKey(KeyManager.Provider.OPENROUTER)) }
    var zaiKey by remember { mutableStateOf(keyManager.readKey(KeyManager.Provider.ZAI)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
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
                text = "API Keys",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = "Keys are encrypted with your device's secure hardware and never leave the device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        KeyProviderRow(
            providerLabel = "OpenCode Zen",
            key = zenKey,
            onKeyChanged = { newKey ->
                keyManager.writeKey(KeyManager.Provider.ZEN, newKey)
                zenKey = newKey
            },
            onClear = {
                keyManager.clearKey(KeyManager.Provider.ZEN)
                zenKey = null
            }
        )

        KeyProviderRow(
            providerLabel = "OpenRouter",
            key = openRouterKey,
            onKeyChanged = { newKey ->
                keyManager.writeKey(KeyManager.Provider.OPENROUTER, newKey)
                openRouterKey = newKey
            },
            onClear = {
                keyManager.clearKey(KeyManager.Provider.OPENROUTER)
                openRouterKey = null
            }
        )

        KeyProviderRow(
            providerLabel = "Z.AI",
            key = zaiKey,
            onKeyChanged = { newKey ->
                keyManager.writeKey(KeyManager.Provider.ZAI, newKey)
                zaiKey = newKey
            },
            onClear = {
                keyManager.clearKey(KeyManager.Provider.ZAI)
                zaiKey = null
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun KeyProviderRow(
    providerLabel: String,
    key: String?,
    onKeyChanged: (String) -> Unit,
    onClear: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = providerLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (key == null) {
                Text(
                    text = "No key set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mask(key),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("Aven API key", key))
                                    )
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = "Copy $providerLabel key",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                TextButton(onClick = {
                    draft = key ?: ""
                    editing = true
                }) {
                    Text(if (key == null) "Set key" else "Replace key")
                }
                if (key != null) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(providerLabel) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onKeyChanged(draft.trim())
                        editing = false
                    },
                    enabled = draft.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear key?") },
            text = { Text("The $providerLabel key will be removed from secure storage.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

private fun mask(key: String): String {
    if (key.length <= 8) return "••••"
    val head = key.take(4)
    val tail = key.takeLast(4)
    return "$head••••••••$tail"
}