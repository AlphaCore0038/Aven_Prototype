# Aven

**A native Android AI chat app built around choice, privacy, and on-device AI.**

Aven brings remote AI providers and local language models into one Android application. Choose how you want to chat, select your model, and keep your conversations organized in a clean, native interface.

Built with Kotlin and Jetpack Compose, Aven combines remote AI integrations with on-device inference powered by `llama.cpp`.

## What Aven Offers

- **Multiple AI providers** — connect to OpenCode Zen and OpenRouter.
- **On-device AI** — run Llama 3.2 1B Instruct locally using GGUF and `llama.cpp`.
- **Streaming responses** — watch responses appear as they're generated, with the ability to cancel generation.
- **Conversation history** — keep chats and messages stored locally using Room.
- **Private chat mode** — keep private conversations out of the normal chat listing, search, and export flows.
- **Model selection** — choose a remote model or manage the availability of the local model.
- **Secure credential storage** — protect stored API keys with Android Keystore-backed encryption.
- **Personalized settings** — configure providers, models, themes, and profile preferences.
- **Native Android experience** — a Jetpack Compose interface designed specifically for Android.

## How It Works

Aven supports two approaches to AI inference.

| | Remote AI | On-device AI |
|---|---|---|
| Where inference happens | Remote provider | Your Android device |
| Internet connection | Required | Not required for inference after the model is available |
| Model choice | Supported provider models | Llama 3.2 1B Instruct |
| API key | Depends on provider | Not required for local inference |

**Your choice matters.** Remote chats send requests to the selected provider. Local inference processes prompts on your device rather than sending them to a remote AI provider for generation.

Some app operations, such as downloading a model or using a remote provider, still require an internet connection.

## Built With

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose |
| Local database | Room |
| Preferences | DataStore |
| Networking | OkHttp |
| Asynchronous work | Kotlin Coroutines and Flow |
| Serialization | kotlinx.serialization |
| Local inference | llama.cpp, JNI, CMake, Android NDK |
| Testing | JUnit, Robolectric, MockWebServer |

## Project Structure

The codebase separates the user interface, AI engines, data storage, and security logic.

```text
app/src/main/
├── java/com/aven/ai/
│   ├── ui/
│   │   ├── chat/
│   │   ├── settings/
│   │   ├── drawer/
│   │   ├── navigation/
│   │   └── components/
│   ├── engine/
│   │   ├── remote/
│   │   └── local/
│   ├── data/
│   │   ├── local/
│   │   ├── network/
│   │   ├── security/
│   │   └── settings/
│   ├── di/
│   └── MainActivity.kt
└── cpp/
    ├── CMakeLists.txt
    └── native inference code
```

## Getting Started

### Prerequisites

- Android Studio
- JDK 17 or newer
- Android SDK compatible with the project configuration
- Android NDK `28.2.13676358`
- CMake `3.22.1`
- An ARM64 Android device for the configured native inference build

### Clone the Repository

```bash
git clone https://github.com/AlphaCore0038/Aven_Prototype.git
cd Aven_Prototype
```

Open the project in Android Studio and allow Gradle to sync.

### Build the Debug APK

**Windows — PowerShell or Command Prompt**

```bat
gradlew.bat assembleDebug
```

**Linux or macOS**

```bash
./gradlew assembleDebug
```

The generated APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install on a Connected Device

With Android Debug Bridge (`adb`) configured and USB debugging enabled:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Run Unit Tests

**Windows:**

```bat
gradlew.bat testDebugUnitTest
```

**Linux or macOS:**

```bash
./gradlew testDebugUnitTest
```

## Configure API Keys

Aven lets you configure provider credentials through the app's settings.

For optional development defaults, create a `.env` file in the repository root. The file is read by the debug build configuration and is excluded from version control.

```properties
AVEN_DEFAULT_ZEN_API_KEY=your_zen_api_key
OPENROUTER_API_KEY=your_openrouter_api_key
AVEN_DEFAULT_ZAI_API_KEY=your_zai_api_key
NVIDIA_NIM_API_KEY=your_nvidia_nim_api_key
```

Only add the credentials you actually use.

**Security notes:**

- Never commit `.env`, real API keys, or other credentials to GitHub.
- Debug-build defaults are intended for local development, not distribution.
- Release builds do not include these configured API-key defaults.
- Stored credentials are protected using Android Keystore-backed encryption.

If a credential is accidentally published, revoke or rotate it with the provider.

## Local Model

Aven currently supports **Llama 3.2 1B Instruct** in GGUF format through `llama.cpp`.

The default model configuration uses `Q4_K_M` quantization. The model is downloaded to app-private storage when needed, and the app manages its availability.

Performance depends on the device's available memory, processor, and model configuration. Model downloads can require significant storage and network data.

The configured native build targets `arm64-v8a`.

## Testing

The JVM test suite covers areas including:

- Database and repository behavior
- Conversation state management
- Streaming response parsing
- Remote engine behavior using a mock HTTP server
- Model selection and local-model management
- Chat-template construction

Tests involving physical hardware, live services, model downloads, and actual Android Keystore behavior require separate device-level verification.

## Privacy

Privacy is an important part of Aven's design.

- API keys are stored using Android Keystore-backed encryption.
- Conversation data is stored locally using Room.
- Local inference does not require sending prompts to a remote AI provider.
- Remote inference sends requests to the provider you select.
- Private chat mode excludes conversations from the app's normal listing, search, and export flows.

These protections describe the app's implemented behavior; they do not mean every operation is offline or that all data is automatically end-to-end encrypted.

## Project Status

Aven is an actively developed Android project. Its repository contains the application source, native inference integration, and automated tests.

Features, supported providers, and implementation details may evolve as development continues.

## Third-Party Software

Aven uses `llama.cpp` and other third-party libraries. Refer to the applicable license files distributed with those components for their licensing terms.

---

**Repository:** [AlphaCore0038/Aven_Prototype](https://github.com/AlphaCore0038/Aven_Prototype)
