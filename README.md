# Aven

**A private-first AI assistant for Android.**

Aven brings remote AI providers and on-device language models into one native Android app. It is built with Kotlin and Jetpack Compose, with an emphasis on privacy, explicit model selection, and a clean chat experience.

## Features

- **Native Android UI** — Jetpack Compose, Material 3, dark-first design, and a navigation drawer.
- **Multiple AI providers** — connect to OpenCode Zen and OpenRouter.
- **On-device AI** — run Llama 3.2 1B locally using GGUF quantization and llama.cpp.
- **Streaming responses** — see generated text as it arrives, with support for cancelling generation.
- **Persistent conversations** — chats and messages are stored locally using Room.
- **Private chat mode** — private conversations are excluded from normal chat listing, search, and export.
- **Secure API-key storage** — API keys are protected using Android Keystore-backed encryption.
- **Model management** — select remote models or manage the installation state of the local model.
- **Settings and personalization** — configure providers, models, themes, and profile preferences.
- **Clear error handling** — unavailable providers and missing configuration produce actionable errors rather than silently switching models.

## Tech stack

| Area | Technologies |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose |
| Local database | Room |
| Settings | DataStore |
| Networking | OkHttp |
| Asynchronous operations | Kotlin Coroutines and Flow |
| Serialization | kotlinx.serialization |
| On-device inference | llama.cpp, JNI, CMake, Android NDK |
| Testing | JUnit, Robolectric, MockWebServer, coroutine test utilities |

## Architecture

Aven separates its UI, conversation engine, provider integrations, and data storage.

```text
com.aven.ai/
├── ui/
│   ├── chat/          Chat UI and conversation state
│   ├── settings/      Settings and API-key management
│   ├── drawer/        Navigation drawer
│   ├── navigation/    Navigation destinations
│   └── components/    Shared UI components
├── engine/
│   ├── AiChatEngine   Common generation interface
│   ├── remote/        Remote provider integrations
│   └── local/         Local inference and model management
├── data/
│   ├── local/         Room database and DAOs
│   ├── security/      API-key encryption and key management
│   ├── settings/      Settings and profile repositories
│   └── network/       HTTP client configuration
├── di/                Application dependency container
└── MainActivity.kt
```

Native inference code is located under `app/src/main/cpp/`, including the JNI bridge and llama.cpp source.

## Privacy and security

- API keys are stored using Android Keystore-backed encryption.
- Personal default API keys can be supplied through the local `local.properties` file for debug builds.
- API keys and local model files are excluded from version control.
- Remote providers require network access; local inference can run without sending prompts to a remote AI provider.
- Private chat mode keeps conversations out of the app's normal listing, search, and export flows.

**Note:** Using a local model does not automatically make every app operation offline. Remote providers, model downloads, and other network-dependent operations still require connectivity.

## Getting started

### Requirements

- Android Studio with a compatible Android SDK
- JDK 17 or newer
- Android NDK 28.2.13676358
- CMake 3.22.1
- An ARM64 Android device for the configured native inference build

### Build

Clone the repository:

```bash
git clone https://github.com/AlphaCore0038/Aven_Prototype.git
cd Aven_Prototype
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

Run the unit tests:

```bash
./gradlew testDebugUnitTest
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

To install the generated APK on a connected device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

The current native build targets `arm64-v8a`.

## Configure API keys

You can configure API keys in the app's settings. For optional debug-build defaults, add your own keys to the root `local.properties` file:

```properties
AVEN_DEFAULT_ZEN_API_KEY=your_zen_api_key
AVEN_DEFAULT_OPENROUTER_API_KEY=your_openrouter_api_key
```

Never commit real API keys or credentials to GitHub. Release builds should use keys entered by the user rather than personal development defaults.

## Local AI model

Aven supports Llama 3.2 1B Instruct in GGUF format through llama.cpp.

The default model configuration uses the `Q4_K_M` quantization. The model is downloaded to app-private storage when needed, and its availability is managed by the app.

Local inference depends on device memory, processing capability, and the model's storage requirements. Download size and generation speed vary with the model build and device.

## Testing

The JVM test suite covers areas including:

- Database and repository behavior
- Conversation state management
- Remote response-stream parsing
- Remote engine behavior using a mock HTTP server
- Model selection and local-model management
- Chat-template construction

Tests requiring a physical device, live network access, real model downloads, or actual Android Keystore behavior need separate device-level verification.

## Project status

Aven's core application features and primary UI are implemented. The repository includes the Android application source, native inference integration, and automated tests.

## Third-party licenses

Aven includes llama.cpp and related native components. Refer to the license files distributed with those components for their applicable terms.