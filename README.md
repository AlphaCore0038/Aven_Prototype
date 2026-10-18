# Aven

**Your AI chat app. Choose how you run AI.**

Aven is an Android AI chat application that brings **remote AI providers** and **on-device language models** into one native app. Choose a configured model, chat with streaming responses, and manage your conversations from a clean Kotlin and Jetpack Compose interface.

## What Aven offers

- **Two ways to chat** — use a remote provider or run a supported model directly on your phone.
- **Local AI** — download and run supported GGUF models with `llama.cpp`.
- **Streaming replies** — see responses as they are generated, with support for stopping generation.
- **Chat history** — conversations are stored locally on your device.
- **Private chats** — keep selected conversations out of the normal chat list, search, and export flows.
- **Model controls** — configure remote models and manage local model downloads.
- **Personal settings** — adjust the app's available appearance and profile preferences.
- **Credential protection** — API keys saved in the app are protected with Android Keystore-backed encryption.

## Choose your AI mode

| | Remote AI | On-device AI |
|---|---|---|
| Runs on | A provider's servers | Your Android phone |
| Internet | Required for requests | Not required after the model is downloaded |
| API key | Usually required | Not required |
| Setup | Configure a compatible endpoint, model, and credentials | Download a supported model in Aven |

With remote AI, prompts are sent to the provider configured for the selected model. With on-device AI, generation happens on your phone and prompts are not sent to a remote AI provider for inference. Downloading a local model still requires an internet connection.

## Supported local models

Aven currently offers these local model options:

- **Dolphin 3.0 1B RP Uncensored** — a compact GGUF model for local chat.
- **Gemma 3 1B Instruct** — an alternative compact model in GGUF format.

Local models are downloaded on demand and stored in app-private storage. Performance depends on your phone's memory and processing capabilities, and model files need additional storage space.

Remote models are configured by the user in the app. Compatibility depends on the provider's endpoint and API format.

## Get started

### Try Aven

**[Download Aven 1.2.8 APK](https://raw.githubusercontent.com/AlphaCore0038/Aven_Prototype/main/app-release.apk)**

If you received an APK directly, install it on your Android device. Android may ask you to allow installation from that source. Only install APKs from a source you trust.

After installing:
1. Open Aven.
2. Choose a local model and download it, or configure a remote model in settings.
3. Start a conversation.

Local model downloads can be large, so make sure your device has enough free storage.

### Build from source

**Requirements**
- Android Studio
- JDK 17
- Android SDK
- Android NDK `28.2.13676358`
- CMake `3.22.1`
- An ARM64 Android device for on-device inference

Clone the repository:

```bash
git clone https://github.com/AlphaCore0038/Aven_Prototype.git
cd Aven_Prototype
```

Open the project in Android Studio and allow Gradle to sync.

Build a debug APK:

**Windows**
```bat
gradlew.bat assembleDebug
```

**Linux / macOS**
```bash
./gradlew assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run the unit tests:

**Windows**
```bat
gradlew.bat testDebugUnitTest
```

**Linux / macOS**
```bash
./gradlew testDebugUnitTest
```

## Built with

- **Kotlin** and **Jetpack Compose** — Android app and UI
- **Material 3** — UI components
- **Room** and **DataStore** — local persistence and preferences
- **OkHttp** and Kotlin Coroutines — networking and asynchronous work
- **llama.cpp**, JNI, C++, CMake, and Android NDK — local inference
- **JUnit**, Robolectric, and MockWebServer — automated tests

## Privacy notes

- Conversations are stored locally by the app.
- Local inference does not send prompts to a remote AI provider for generation.
- Remote AI requests are sent to the provider you configured; that provider's own policies apply.
- API keys saved in Aven are protected with Android Keystore-backed encryption.
- Optional development API-key defaults belong in a local, gitignored `.env` file. **Never commit API keys or distribute a debug APK containing personal credentials.** The release build configuration leaves these defaults empty.

Private chat mode changes how conversations appear in Aven; it is not a promise of end-to-end encryption or invisibility to someone with access to the device.

## Project status

Aven is an actively developed project. Features, supported providers, and model compatibility may change as development continues.

## Third-party software

Aven uses `llama.cpp` and other third-party libraries. Check the relevant project and model licenses for their terms.

---

**Source code:** [AlphaCore0038/Aven_Prototype](https://github.com/AlphaCore0038/Aven_Prototype)
