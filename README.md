# Aven

**A native Android AI chat app built around choice, privacy, and on-device AI.**

Aven brings remote AI models and local language models together in one Android app. Choose a model, configure a remote provider or run inference directly on your device, and manage conversations in a clean native interface.

Built with Kotlin and Jetpack Compose, Aven combines configurable remote endpoints with on-device inference powered by `llama.cpp`.

## Features

- **Remote AI models** — configure compatible provider gateways, model IDs, and credentials.
- **On-device AI** — run Dolphin 3.0 1B RP Uncensored or Gemma 1B locally using GGUF and `llama.cpp`.
- **Streaming responses** — receive generated text as it arrives, with support for cancelling generation.
- **Conversation history** — chats and messages are stored locally using Room.
- **Private chat mode** — private conversations are excluded from the normal chat listing, search, and export flows.
- **Model selection** — choose a configured remote model or manage local model downloads and availability.
- **Credential protection** — stored API keys are protected using Android Keystore-backed encryption.
- **Personalization** — configure models, themes, and profile preferences.
- **Native Android UI** — built specifically for Android with Jetpack Compose and Material 3.
- **Explicit model selection** — Aven does not silently switch to a different model when the selected one is unavailable.

## Remote AI or On-Device AI?

Aven supports both approaches, so you can choose based on your needs.

| | Remote AI | On-device AI |
|---|---|---|
| Where inference happens | Remote provider | Your Android device |
| Internet for generation | Required | Not required after model download |
| Model selection | User-configured remote models | Dolphin 3.0 1B RP Uncensored or Gemma 1B |
| Provider API key | Usually required | Not required |
| Model management | Configure endpoint and credentials | Download, load, or remove models |

Remote requests are sent to the provider configured for the selected model. Local inference runs on your device and does not send prompts to a remote AI provider for generation. Other operations, such as downloading models or using remote services, still require connectivity.

## Tech Stack

| Area | Technologies |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose |
| Local database | Room |
| Preferences | DataStore |
| Networking | OkHttp |
| Asynchronous work | Kotlin Coroutines and Flow |
| Serialization | kotlinx.serialization |
| On-device inference | llama.cpp, JNI, C++, CMake, Android NDK |
| Testing | JUnit, Robolectric, MockWebServer, coroutine test utilities |

## Project Structure

The codebase separates the UI, inference engines, data storage, and security logic.

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
    └── native inference bridge
```

## Getting Started

### Requirements

- Android Studio
- JDK 17 or newer
- Android SDK compatible with the project configuration
- Android NDK `28.2.13676358`
- CMake `3.22.1`
- An ARM64 Android device for the configured native inference build

### Clone the repository

```bash
git clone https://github.com/AlphaCore0038/Aven_Prototype.git
cd Aven_Prototype
```

Open the project in Android Studio and allow Gradle to sync.

### Build the debug APK

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

### Install on a connected device

With Android Debug Bridge (`adb`) configured and USB debugging enabled:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Run unit tests

**Windows**

```bat
gradlew.bat testDebugUnitTest
```

**Linux or macOS**

```bash
./gradlew testDebugUnitTest
```

## Configure Remote API Keys

Remote model configurations and credentials can be managed in Aven's settings.

For optional development defaults, create a `.env` file in the repository root. The debug build reads these values; `.env` is excluded from version control.

```properties
AVEN_DEFAULT_ZEN_API_KEY=your_zen_api_key
OPENROUTER_API_KEY=your_openrouter_api_key
AVEN_DEFAULT_ZAI_API_KEY=your_zai_api_key
NVIDIA_NIM_API_KEY=your_nvidia_nim_api_key
```

Add only the values you use. The corresponding provider or model must still be configured in the app.

**Security notes**

- Never commit `.env`, API keys, or other credentials to GitHub.
- These defaults are for local debug builds, not for distributing a debug APK.
- The Gradle release configuration sets these API-key defaults to empty strings.
- API keys saved in the app are protected with Android Keystore-backed encryption.
- If a credential is exposed, revoke or rotate it with the provider.

## On-Device Models

Aven currently includes two local model options:

### Dolphin 3.0 1B RP Uncensored

The primary local model, distributed in GGUF format and run through `llama.cpp`. Aven uses a ChatML-style conversation format for this model.

### Gemma 1B

An alternative based on Gemma 3 1B Instruct, also using GGUF and `llama.cpp`, with a Gemma-specific conversation format.

Models are downloaded on demand to app-private storage. Aven manages download progress, loading, availability, and removal. The app is designed to keep only the selected local model resident in memory rather than loading both models at once.

Local inference performance depends on device memory, processing capability, and model configuration. Model downloads require an internet connection and sufficient storage. After a model is downloaded, generation can run offline.

The configured native build targets `arm64-v8a`.

## Privacy

Privacy is an important part of Aven's design.

- API keys saved by the app are protected using Android Keystore-backed encryption.
- Conversation data is stored locally using Room.
- Local inference does not send prompts to a remote AI provider for generation.
- Remote inference sends requests to the provider configured for the selected model.
- Private chat mode excludes conversations from the app's normal listing, search, and export flows.

These behaviors do not mean every app operation is offline or that all data is automatically end-to-end encrypted. Remote providers process requests according to their own services and policies.

## Testing

The JVM test suite covers areas including:

- Database and repository behavior
- Conversation state management
- Streaming response parsing
- Remote engine behavior using a mock HTTP server
- Model selection and local-model management
- Chat-template construction

Tests involving physical devices, live services, model downloads, or actual Android Keystore behavior require separate device-level verification.

## Project Status

Aven is an actively developed Android project. The repository contains the application source, native inference integration, and automated tests. Features and implementation details may evolve as development continues.

## Third-Party Software

Aven uses `llama.cpp` and other third-party libraries. Refer to the applicable license files distributed with those components for their licensing terms.

---

**Repository:** [AlphaCore0038/Aven_Prototype](https://github.com/AlphaCore0038/Aven_Prototype)
