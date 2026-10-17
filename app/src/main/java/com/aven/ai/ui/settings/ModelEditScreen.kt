package com.aven.ai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aven.ai.data.security.KeyManager
import com.aven.ai.data.settings.RemoteModelRepository
import com.aven.ai.engine.AiChatException
import com.aven.ai.engine.GenerateRequest
import com.aven.ai.engine.RemoteModelConfig
import com.aven.ai.engine.remote.OpenAiCompatibleEngine
import com.aven.ai.engine.validationError
import com.aven.ai.ui.theme.AvenSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * Add/edit one user-configured online model: provider name, base gateway URL,
 * model ID, and API key. The key is written to the Keystore-backed store and
 * is never displayed in plaintext or included in errors.
 */
@Composable
fun ModelEditScreen(
    remoteModelRepository: RemoteModelRepository,
    keyManager: KeyManager,
    httpClient: OkHttpClient,
    configId: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNew = configId.isBlank()
    var providerName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var apiKeyDraft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(!isNew) }
    var error by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(configId) {
        if (isNew) {
            loading = false
            return@LaunchedEffect
        }
        val config = remoteModelRepository.models.first().firstOrNull { it.id == configId }
        if (config != null) {
            providerName = config.providerName
            baseUrl = config.baseUrl
            modelId = config.modelId
        }
        loading = false
    }

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
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (isNew) "Add Model" else "Edit Model",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        } else {
            OutlinedTextField(
                value = providerName,
                onValueChange = { providerName = it },
                label = { Text("Provider Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base Gateway URL") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
            OutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text("Model ID") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
            OutlinedTextField(
                value = apiKeyDraft,
                onValueChange = { apiKeyDraft = it },
                label = { Text(if (isNew || keyManager.readKey(configId) == null) "API Key" else "API Key (leave blank to keep)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            testResult?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AvenSuccess,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                TextButton(
                    onClick = {
                        testResult = null
                        val validation = remoteModelRepository.validate(providerName, baseUrl, modelId)
                        if (validation != null) {
                            error = validation
                            return@TextButton
                        }
                        val key = apiKeyDraft.trim().ifBlank { keyManager.readKey(configId) }
                        if (key.isNullOrBlank()) {
                            error = "Enter an API key."
                            return@TextButton
                        }
                        scope.launch {
                            testing = true
                            error = null
                            val failure = testConnection(providerName, baseUrl, modelId, key, httpClient)
                            if (failure == null) {
                                testResult = "Connection successful."
                            } else {
                                error = failure
                            }
                            testing = false
                        }
                    },
                    enabled = !testing
                ) {
                    Text(if (testing) "Testing…" else "Test Connection")
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(
                    onClick = {
                        val validation = remoteModelRepository.validate(providerName, baseUrl, modelId)
                        if (validation != null) {
                            error = validation
                            return@TextButton
                        }
                        scope.launch {
                            if (isNew) {
                                val id = java.util.UUID.randomUUID().toString()
                                val added = remoteModelRepository.add(
                                    RemoteModelConfig(
                                        id = id,
                                        providerName = providerName.trim(),
                                        baseUrl = baseUrl.trim(),
                                        modelId = modelId.trim()
                                    )
                                )
                                if (!added) {
                                    error = "You can add up to ${RemoteModelConfig.MAX_MODELS} models."
                                    return@launch
                                }
                                if (apiKeyDraft.isNotBlank()) {
                                    keyManager.writeKey(id, apiKeyDraft.trim())
                                }
                            } else {
                                remoteModelRepository.update(
                                    RemoteModelConfig(
                                        id = configId,
                                        providerName = providerName.trim(),
                                        baseUrl = baseUrl.trim(),
                                        modelId = modelId.trim()
                                    )
                                )
                                if (apiKeyDraft.isNotBlank()) {
                                    keyManager.writeKey(configId, apiKeyDraft.trim())
                                }
                            }
                            onDone()
                        }
                    }
                ) {
                    Text("Save")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete model?") },
            text = { Text("This removes the model and its stored API key from secure storage.") },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            remoteModelRepository.delete(configId)
                            keyManager.clearKey(configId)
                            onDone()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

/**
 * One short live request through the same engine chat uses. Returns null on
 * success, or the engine's mapped user-facing error.
 *
 * Success means the stream completed without error — even with no visible
 * text, since this validates connectivity/authentication, not generation.
 * Uses chat's default retry policy; only the overall timeout is test-specific.
 */private suspend fun testConnection(
    providerName: String,
    baseUrl: String,
    modelId: String,
    apiKey: String,
    client: OkHttpClient
): String? = try {
    val engine = OpenAiCompatibleEngine(
        config = RemoteModelConfig(
            id = "test",
            providerName = providerName.trim(),
            baseUrl = baseUrl.trim(),
            modelId = modelId.trim()
        ),
        apiKey = { apiKey },
        client = client
    )
    val completed = withTimeoutOrNull(TEST_TIMEOUT_MS) {
        engine.generate(
            GenerateRequest(conversationId = 0L, prompt = "Hi", history = emptyList(), isPrivate = true)
        ).collect { }
        true
    } ?: false
    if (completed) {
        null
    } else {
        "The request timed out. The model gateway took too long to respond."
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: AiChatException) {
    e.message
} catch (_: Exception) {
    "Connection failed. Aven couldn't reach the model gateway."
}

private const val TEST_TIMEOUT_MS = 20_000L