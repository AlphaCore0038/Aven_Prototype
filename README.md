# Aven

A private-first Android AI assistant built with Jetpack Compose. Chat with
remote models (OpenCode Zen, OpenRouter) or fully on-device with a local
Llama 3.2 1B model compiled from llama.cpp — no accounts required, keys stored
in the Android Keystore, and a genuine "private" mode whose messages are never
listed, searched, or exported.

## Features

- **Dark-first Material 3 Compose UI** with a chat welcome view, streaming
  replies, a model picker, and a settings drawer.
- **Three generation backends** behind one `AiChatEngine` interface:
  - OpenCode Zen (`deepseek-v4-flash`) — fast, free.
  - OpenRouter (`deepseek-v4-flash`, `deepseek-v4-pro`) — same APIs, paid.
  - Local Llama 3.2 1B (Q4_K_M) via vendored llama.cpp — private, offline.
- **Honest failures**: no fake output and no silent provider fallback. A
  missing key or uninstalled model surfaces a clear, user-actionable error.
- **Streaming**: SSE parsing for remote providers, token-level streaming from
  the native bridge for the local model, cancellable mid-generation.
- **Room persistence**: conversations and messages survive restarts; a reply
  is persisted when generation completes or is stopped.
- **Security**: API keys live in an AES-GCM keystore (Android Keystore-backed);
  personal defaults are injected only into debug builds from gitignored
  `local.properties` and never appear in release builds or version control.

## Architecture

```
com.aven.ai
├── ui/                      Jetpack Compose screens + components
│   ├── chat/                ChatScreen, ChatController (state machine),
│   │                        ChatViewModel, WelcomeView, MessageRow
│   ├── settings/            Settings + API Keys screens, SettingsViewModel
│   ├── drawer/              Navigation drawer
│   ├── navigation/          NavHost + destinations
│   └── components/          Composer, logo, shared widgets
├── engine/
│   ├── AiChatEngine.kt      Generation contract (Flow<String> streaming)
│   ├── ModelRegistry.kt     Model catalogue + explicit engine selection
│   ├── remote/              OpenAI-compatible SSE engines (Zen, OpenRouter),
│   │                        SseParser, DeltaExtractor
│   └── local/               LlamaNative (JNI), LocalEngine (chat template),
│                            ModelManager (install/download/verify state
│                            machine), ModelDownloader
├── data/
│   ├── local/               Room database, entities, DAO
│   ├── security/            KeystoreCrypto (AES-GCM), KeyManager
│   ├── settings/            DataStore-backed settings/profile repositories
│   └── network/             OkHttp client factory
├── di/AppContainer.kt       Manual dependency injection (no DI framework)
├── AvenApplication.kt       Seeding of debug-only default keys
└── MainActivity.kt
app/src/main/cpp/
├── CMakeLists.txt           CMake entry point
├── aven_llama.cpp           JNI bridge to llama.cpp (greedy sampling,
│                            streaming, cancellation)
└── llama.cpp/               Vendored llama.cpp v0.1.2 (git submodule clone)
```

Design decisions:

- **No DI framework, no Retrofit, no Hilt/Koin.** Dependencies are built once in
  `AppContainer` and passed explicitly.
- **Room is the single source of truth** for messages; streaming text exists
  only while a generation is in flight.
- **No silent fallback.** `ModelRegistry.engineForSettings` returns the saved
  selection when its backend is usable, the first usable option otherwise, and
  `UnavailableEngine` only when nothing is configured.
- **Capability-driven UI.** The model picker shows the local model's live state
  (not installed / downloading / ready / failed) and installs on tap.

## Tech stack

- Kotlin 2.4, Jetpack Compose (BOM 2026.08), Material 3, Navigation Compose
- Room 2.8, DataStore 1.2, OkHttp 5.5, kotlinx.serialization/coroutines
- AGP 9.3 (built-in Kotlin), NDK 28.2, CMake 3.22, llama.cpp v0.1.2
- Tests: JUnit 4, Robolectric, kotlinx-coroutines-test, MockWebServer

## Build & run

Requirements: JDK 17+, Android SDK (platform 37), NDK 28.2.13676358 and
CMake 3.22.1 (AGP auto-installs them on first build when the SDK license is
accepted).

```bash
# First build (compiles llama.cpp for arm64-v8a; takes several minutes)
./gradlew assembleDebug

# Run unit tests (54 tests; Keystore tests are device-only and skipped)
./gradlew testDebugUnitTest

# Install on a connected arm64 device
adb install app/build/outputs/apk/debug/app-debug.apk
```

The APK targets `arm64-v8a` only (`app/build.gradle.kts` → `defaultConfig.ndk`).

### API keys (debug builds only)

Add your personal defaults to `local.properties` (gitignored):

```properties
AVEN_DEFAULT_ZEN_API_KEY=sk-...
AVEN_DEFAULT_OPENROUTER_API_KEY=sk-or-...
```

On first launch these are seeded into the Keystore-backed secure store. You can
also set or replace keys at runtime in **Settings → API Keys**. Release builds
carry empty defaults and require keys to be entered on-device.

### Local model

- Default behavior: the app downloads the ~780 MB GGUF
  (`Llama-3.2-1B-Instruct-Q4_K_M.gguf`) into app-private storage on first use
  via the model picker or Settings.
- To bundle the model into the APK instead, set `AVEN_BUNDLE_MODEL=true` in
  `local.properties` and place the file at
  `app/src/main/assets/models/Llama-3.2-1B-Instruct-Q4_K_M.gguf`.
  (Packaging it into `assets/` is a TODO — the flag is plumbed through
  `BuildConfig.BUNDLE_MODEL` but the asset unpack path is not wired yet.)

## Roadmap / status

| Phase | Scope | Status |
|-------|-------|--------|
| 1 | App scaffold, theme, navigation, all screens | Done |
| 2 | Room data layer, Keystore crypto, settings/profile repos | Done |
| 3 | Conversation engine: ChatController, streaming UI, Room as source of truth | Done |
| 4 | Remote AI: Zen + OpenRouter engines, SSE parsing, API Keys screen | Done |
| 5 | Local AI: llama.cpp build, JNI bridge, model manager, on-device engine | Done |

## Testing

Unit tests cover the data layer (Room DAO, settings, keystore-crypto helpers),
the conversation state machine, SSE parsing, the OpenAI-compatible engine
(MockWebServer), the model registry, the local-model manager state machine, and
the chat-template builder. Live network calls, real model downloads, and
Keystore behavior require a physical device and are not exercised by the JVM
test suite.

## License

See `app/src/main/cpp/llama.cpp/LICENSE` for llama.cpp's license (MIT) and the
GGML submodule license under `app/src/main/cpp/llama.cpp/ggml/LICENSE`.