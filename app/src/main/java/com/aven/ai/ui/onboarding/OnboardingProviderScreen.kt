package com.aven.ai.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aven.ai.data.security.KeyManager
import com.aven.ai.data.settings.RemoteModelRepository
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.engine.RemoteModelConfig
import com.aven.ai.ui.settings.testConnection
import com.aven.ai.ui.theme.AvenSuccess
import com.aven.ai.ui.theme.RefPalette
import com.aven.ai.ui.theme.rememberRefPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID

/**
 * Id of an identical already-saved provider, so retrying after a partial
 * failure reuses it instead of adding a duplicate.
 */
internal fun matchingProviderId(
    models: List<RemoteModelConfig>,
    providerName: String,
    baseUrl: String,
    modelId: String
): String? = models.firstOrNull {
    it.providerName == providerName && it.baseUrl == baseUrl && it.modelId == modelId
}?.id

/**
 * Optional provider setup step. Reuses the existing validation, secure key
 * storage and connection test; only the layout is onboarding-specific.
 * Skip (bottom-left) completes onboarding without touching any provider.
 */
@Composable
internal fun OnboardingProviderScreen(
    remoteModelRepository: RemoteModelRepository,
    keyManager: KeyManager,
    httpClient: OkHttpClient,
    settingsRepository: SettingsRepository,
    dark: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val ref = rememberRefPalette(dark)
    var providerName by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var modelId by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val busy = testing || saving

    suspend fun markComplete(): Boolean =
        runCatching { settingsRepository.setOnboardingCompleted() }.isSuccess

    fun test() {
        testResult = null
        val validation = remoteModelRepository.validate(providerName, baseUrl, modelId)
        if (validation != null) {
            error = validation
            return
        }
        val key = apiKey.trim()
        if (key.isBlank()) {
            error = "Enter an API key."
            return
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
    }

    fun save() {
        testResult = null
        val validation = remoteModelRepository.validate(providerName, baseUrl, modelId)
        if (validation != null) {
            error = validation
            return
        }
        val key = apiKey.trim()
        if (key.isBlank()) {
            error = "Enter an API key."
            return
        }
        scope.launch {
            saving = true
            error = null
            val name = providerName.trim()
            val url = baseUrl.trim()
            val model = modelId.trim()
            val models = runCatching { remoteModelRepository.models.first() }.getOrNull()
            if (models == null) {
                error = "Couldn't save the model. Try again."
                saving = false
                return@launch
            }
            val existingId = matchingProviderId(models, name, url, model)
            val id = existingId ?: UUID.randomUUID().toString()
            if (existingId == null) {
                val added = runCatching {
                    remoteModelRepository.add(RemoteModelConfig(id, name, url, model))
                }
                if (added.isFailure) {
                    error = "Couldn't save the model. Try again."
                    saving = false
                    return@launch
                }
                if (!added.getOrDefault(false)) {
                    error = "You can add up to ${RemoteModelConfig.MAX_MODELS} models."
                    saving = false
                    return@launch
                }
            }
            if (runCatching { keyManager.writeKey(id, key) }.isFailure) {
                error = "Couldn't store the API key securely. Try again."
                saving = false
                return@launch
            }
            if (markComplete()) {
                onFinished()
            } else {
                error = "Couldn't complete setup. Try again."
                saving = false
            }
        }
    }

    fun skip() {
        if (busy) return
        scope.launch {
            saving = true
            error = null
            if (markComplete()) {
                onFinished()
            } else {
                error = "Couldn't complete setup. Try again."
                saving = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ref.settingsBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                enabled = !busy,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ref.rowBg)
                    .border(1.dp, ref.rowDivider, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ref.settingsSubText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Configure Model Provider",
                fontFamily = FontFamily.Default,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                color = ref.textStrong
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Set up connection parameters to route inference requests " +
                    "directly through your custom AI model provider gateway.",
                fontFamily = FontFamily.Default,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = ref.descGray
            )
            Spacer(modifier = Modifier.height(20.dp))

            ProviderField(
                label = "PROVIDER NAME",
                value = providerName,
                onValueChange = { providerName = it },
                ref = ref,
                enabled = !busy
            )
            ProviderField(
                label = "BASE GATEWAY URL",
                value = baseUrl,
                onValueChange = { baseUrl = it },
                ref = ref,
                enabled = !busy,
                monospace = true,
                keyboardType = KeyboardType.Uri
            )
            ProviderField(
                label = "MODEL ID",
                value = modelId,
                onValueChange = { modelId = it },
                ref = ref,
                enabled = !busy,
                monospace = true
            )
            ProviderField(
                label = "API KEY",
                value = apiKey,
                onValueChange = { apiKey = it },
                ref = ref,
                enabled = !busy,
                monospace = true,
                password = true,
                imeAction = ImeAction.Done
            )

            error?.let {
                Text(
                    text = it,
                    fontFamily = FontFamily.Default,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 6.dp)
                )
            }
            testResult?.let {
                Text(
                    text = it,
                    fontFamily = FontFamily.Default,
                    fontSize = 13.sp,
                    color = AvenSuccess,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { skip() },
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = ref.settingsSubText)
            ) {
                Text(
                    text = "Skip",
                    fontFamily = FontFamily.Default,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { test() }, enabled = !busy) {
                Text(
                    text = if (testing) "Testing…" else "Test Connection",
                    fontFamily = FontFamily.Default,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = { save() },
                enabled = !busy,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = if (saving) "Saving…" else "Save",
                    fontFamily = FontFamily.Default,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (!saving) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    ref: RefPalette,
    enabled: Boolean,
    monospace: Boolean = false,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Default,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = ref.settingsSubText,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            textStyle = TextStyle(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                fontSize = if (monospace) 13.sp else 14.sp,
                color = ref.textStrong
            ),
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ref.rowBg,
                unfocusedContainerColor = ref.rowBg,
                disabledContainerColor = ref.rowBg,
                focusedBorderColor = OnboardingPurple,
                unfocusedBorderColor = ref.rowDivider,
                disabledBorderColor = ref.rowDivider,
                cursorColor = OnboardingPurple,
                disabledTextColor = ref.textStrong
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
