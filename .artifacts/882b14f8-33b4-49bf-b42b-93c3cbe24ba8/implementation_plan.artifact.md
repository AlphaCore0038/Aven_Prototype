# Fix App Errors and Improve UX

The app currently experiences "NetworkOnMainThreadException" when using remote AI providers because network calls are executed synchronously on the main thread. Additionally, several UX and logic issues were identified, including incorrect cursor blinking, brittle API key seeding, and incomplete persistence.

## User Review Required

> [!IMPORTANT]
> The network logic will be moved to use non-blocking calls or `withContext(Dispatchers.IO)`. This will fix the "Something went wrong" errors seen when sending messages.

> [!NOTE]
> I will update the API key seeding logic to be more robust. If you've already launched the app and it shows "UNAVAILABLE", these changes will allow the app to re-attempt seeding if the keys are now present in `local.properties`.

## Proposed Changes

### AI Engine Layer

#### [MODIFY] [OpenAiCompatibleEngine.kt](file:///C:/Users/chait/OneDrive/Desktop/New folder/projects/OC/app/src/main/java/com/aven/ai/engine/remote/OpenAiCompatibleEngine.kt)
- Switch `client.newCall(httpRequest).execute()` to a coroutine-friendly approach using `withContext(Dispatchers.IO)`.

### Data & Security Layer

#### [MODIFY] [KeyManager.kt](file:///C:/Users/chait/OneDrive/Desktop/New folder/projects/OC/app/src/main/java/com/aven/ai/data/security/KeyManager.kt)
- Update `seedDefaultKeysIfNeeded` to re-attempt seeding if keys are missing but available in `BuildConfig`, even if previously "seeded".

#### [MODIFY] [ChatController.kt](file:///C:/Users/chait/OneDrive/Desktop/New folder/projects/OC/app/src/main/java/com/aven/ai/ui/chat/ChatController.kt)
- Fix the conversation update logic to stop clearing `providerId` and `modelId` (set to `null`) on every message.

### UI & UX Improvements

#### [MODIFY] [MessageRow.kt](file:///C:/Users/chait/OneDrive/Desktop/New folder/projects/OC/app/src/main/java/com/aven/ai/ui/chat/MessageRow.kt)
- Fix `StreamingRow` cursor blinking: ensure only the cursor character (▍) blinks, not the entire message text.

#### [MODIFY] [ModelRegistry.kt](file:///C:/Users/chait/OneDrive/Desktop/New folder/projects/OC/app/src/main/java/com/aven/ai/engine/ModelRegistry.kt)
- Remove the unused `currentOption` function.
- Add the missing trailing comma in `AiModelOption` data class.

#### [MODIFY] [ChatScreen.kt](file:///C:/Users/chait/OneDrive/Desktop/New folder/projects/OC/app/src/main/java/com/aven/ai/ui/chat/ChatScreen.kt)
- Improve `currentModelLabel` to use a helper that considers both the saved selection and its availability.

## Verification Plan

### Automated Tests
- Run `./gradlew testDebugUnitTest` to ensure no regressions in data layer or engine logic.

### Manual Verification
- Deploy the app to a device/emulator.
- Verify that sending a message with "OpenRouter" or "Zen" no longer results in "Something went wrong".
- Verify that the cursor blinks correctly during streaming.
- Verify that the "UNAVAILABLE" badge correctly reflects key/model state.
