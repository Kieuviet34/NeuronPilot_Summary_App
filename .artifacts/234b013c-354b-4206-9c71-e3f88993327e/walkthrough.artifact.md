# Walkthrough: WebView Meeting Transcription Integration

Successfully integrated the WebView-based meeting transcription interface from the AiBox project into the NeuroPilotJniDemo. Implemented a "Switch Mode" toggle to transition between native Chat and WebView modes.

## Changes Made

### 1. Web Assets Migration
- Migrated all JS, CSS, and font assets from `AiBox` to `app/src/main/assets/web/assets/`.
- Ensured `index.html`, `config.js`, and `worklet.js` are in the root `assets/web/` directory.

### 2. Logic Migration (Kotlin)
- Created the `com.mediatek.neuropilot.jnidemo.aibox` package.
- Migrated and adapted the following components:
    - `AudioCapture.kt`: Handles microphone input and VAD.
    - `AndroidBridge.kt` & `WebViewManager.kt`: Bridges communication between Native and WebView.
    - `STTEngine.kt` & `WhisperServerSTTEngine.kt`: Orchestrates speech-to-text via local server.
    - `TranslateEngine.kt` & `NeuroPilotLlmBridge.kt`: Handles LLM-based translation and summarization.
    - `TranscriptHistory.kt` & `TranscriptSanitizer.kt`: Manages session data and cleans up noise.
- **AiBoxBridge.kt**: New bridge class to manage the WebView lifecycle and Kotlin coroutines, making it accessible from the Java `MainActivity`.

### 3. Build & Dependency Updates
- Upgraded the project to **AndroidX**.
- Updated `app/build.gradle`:
    - Added Kotlin plugin (`kotlin-android`).
    - Added dependencies: `GSON`, `OkHttp`, `Coroutines`.
    - Set `jvmTarget = '1.8'`.
    - Enabled `buildConfig`.
- Updated `gradle.properties`:
    - Enabled `android.useAndroidX` and `android.enableJetifier`.
- Updated `build.gradle` (root):
    - Added `kotlin-gradle-plugin` classpath.

### 4. UI Enhancements (UI Header Separation)
- Modified `activity_main.xml`:
    - Added the `WebView` component (taking full screen when active).
    - Mapped legacy Support Library tags to **AndroidX** (ConstraintLayout, RecyclerView).
    - Moved `layout_avatar_container` outside of `layout_header` to make it a top-level floating component with high elevation.
- Updated `MainActivity.java`:
    - Integrated `AiBoxBridge`.
    - Implemented `toggleAppMode()` triggered by the `btn_switch_mode` button.
    - **Header Separation**: In WebView mode, the system hides the `layout_header` (title, status, summarize/settings buttons) and the AI avatar image, leaving only the floating "Switch Mode" button visible to maximize WebView space.
    - Added resource management (Pause/Resume) logic when switching between modes.

### 5. Native Integration
- Updated `nn_sample.cpp`:
    - Added JNI exports for the new `NeuroPilotLlmBridge` package to allow the Kotlin logic to use the native LLM engine.

## Verification Results
- **Build**: Successfully compiled `app:assembleDebug`.
- **Logic**: All references are resolved, and mode-switching logic correctly manages visibility and resources.
