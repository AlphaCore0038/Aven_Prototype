package com.aven.ai.ui.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aven.ai.data.security.KeyManager
import com.aven.ai.data.settings.RemoteModelRepository
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.data.settings.UserProfileRepository
import com.aven.ai.ui.chat.LuckyRookieFamily
import com.aven.ai.ui.components.AvenMonogram
import com.aven.ai.ui.theme.rememberRefPalette
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Reference accent; the app's Noir palette has no purple, used only for glow/focus. */
internal val OnboardingPurple = Color(0xFF8038F7)

/** Where a launch should go: resolving, first-launch setup, or the normal app. */
internal enum class StartGate { Loading, Onboarding, App }

/**
 * A fresh install shows onboarding; anything else (already completed, or an
 * existing install detected via PackageManager install/update times) goes
 * straight to the app — also when the saved name is blank.
 */
internal fun startGate(completed: Boolean, isExistingInstall: Boolean): StartGate =
    if (completed || isExistingInstall) StartGate.App else StartGate.Onboarding

/** Step 0 = welcome (no name yet), step 1 = provider setup (name already saved). */
internal fun onboardingInitialStep(name: String): Int = if (name.isBlank()) 0 else 1

/**
 * True when this package has ever been updated in place, i.e. the current data
 * belongs to a pre-onboarding install. Equal timestamps mean a fresh install.
 */
internal fun isExistingInstall(context: Context): Boolean {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    return info.lastUpdateTime != info.firstInstallTime
}

/** The one-shot decision the root reads once both stores have emitted. */
internal data class GateState(val gate: StartGate, val name: String, val dark: Boolean)

/**
 * First-launch onboarding, rendered instead of the normal app root. Owns both
 * steps and their back behavior; the drawer and app navigation are not
 * composed while this is active. The step is derived from the persisted name,
 * so a process kill mid-setup resumes at the right screen.
 */
@Composable
internal fun OnboardingFlow(
    settingsRepository: SettingsRepository,
    userProfileRepository: UserProfileRepository,
    remoteModelRepository: RemoteModelRepository,
    keyManager: KeyManager,
    httpClient: OkHttpClient,
    initialName: String,
    dark: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as ComponentActivity
    var stepOverride by rememberSaveable { mutableStateOf<Int?>(null) }
    val step = stepOverride ?: onboardingInitialStep(initialName)

    BackHandler(enabled = step == 1) { stepOverride = 0 }
    BackHandler(enabled = step == 0) { activity.finish() }

    if (step == 1) {
        OnboardingProviderScreen(
            remoteModelRepository = remoteModelRepository,
            keyManager = keyManager,
            httpClient = httpClient,
            settingsRepository = settingsRepository,
            dark = dark,
            onBack = { stepOverride = 0 },
            onFinished = onFinished,
            modifier = modifier
        )
    } else {
        WelcomeStep(
            userProfileRepository = userProfileRepository,
            dark = dark,
            onDone = { stepOverride = 1 },
            modifier = modifier
        )
    }
}

@Composable
private fun WelcomeStep(
    userProfileRepository: UserProfileRepository,
    dark: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val ref = rememberRefPalette(dark)
    var name by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val trimmed = name.trim()

    fun submit() {
        if (trimmed.isEmpty() || saving) return
        scope.launch {
            saving = true
            error = null
            runCatching { userProfileRepository.setName(trimmed) }
                .onSuccess { onDone() }
                .onFailure {
                    error = "Couldn't save your name. Try again."
                    saving = false
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ref.settingsBg)
            .drawBehind {
                if (dark) {
                    val center = Offset(size.width / 2f, size.height * 0.28f)
                    val radius = size.width * 0.9f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(OnboardingPurple.copy(alpha = 0.16f), Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp)
            .padding(bottom = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AvenMonogram(size = 96.dp)

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Holaaa...",
                fontFamily = LuckyRookieFamily,
                fontSize = 58.sp,
                lineHeight = 66.sp,
                color = ref.textStrong,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(56.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp)
            ) {
                Text(
                    text = "WHAT'S YOUR NAME?",
                    fontFamily = FontFamily.Default,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    color = ref.settingsSubText,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    enabled = !saving,
                    placeholder = {
                        Text(
                            text = "Your name...",
                            fontFamily = FontFamily.Default,
                            fontSize = 14.sp,
                            color = ref.labelGray
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = ref.labelGray,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        color = ref.textStrong
                    ),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ref.rowBg,
                        unfocusedContainerColor = ref.rowBg,
                        disabledContainerColor = ref.rowBg,
                        focusedBorderColor = OnboardingPurple,
                        unfocusedBorderColor = ref.rowDivider,
                        disabledBorderColor = ref.rowDivider,
                        cursorColor = OnboardingPurple
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
                error?.let {
                    Text(
                        text = it,
                        fontFamily = FontFamily.Default,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp, start = 6.dp)
                    )
                }
            }
        }

        Button(
            onClick = { submit() },
            enabled = trimmed.isNotEmpty() && !saving,
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = if (saving) "Saving…" else "Continue",
                fontFamily = FontFamily.Default,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (!saving) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
