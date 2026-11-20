# NexVora Smart AI Browser

**Browse • Search • Ask AI • Explore More.** · v1.0.0 · `com.nexvora.smartbrowser` · minSdk 26 / targetSdk 34
Developed by Prince AR Abdur Rahman · Published by NexVora Lab's Ofc · © 2026 NexVora Lab's Ofc. All Rights Reserved.

A native Android browser (Kotlin, Jetpack Compose, Android WebView) with tabs, private browsing, a tracker blocker, downloads,
bookmarks, history, reader mode, a QR scanner/generator and an optional **on-device** AI assistant.

---
## Completion report (read this first)

| Status | Items |
|---|---|
| **IMPLEMENTED** (written, not yet compiled) | Everything in "Features" below |
| **BUILD VERIFIED** | **Nothing.** No Gradle build has ever been run on this code. The authoring environment had no JDK compiler, Kotlin compiler, Android SDK, Gradle or internet. |
| **UNIT TESTED** | **Nothing has been run.** 4 test classes (~45 tests) are written in `app/src/test`, but no test has been executed. |
| **DEVICE TESTED** | **Nothing.** The app has never been installed or launched. |
| **NOT IMPLEMENTED** | Tab groups / manual tab organizing · toolbar customization · animation toggle · download pause/resume and speed display (Android's DownloadManager doesn't offer them) · model checksum verification (only the file extension is checked) · automatic blocklist updates · search-engine suggestions (skipped for privacy) · web notifications (WebView can't) · Compose UI and instrumented tests |
| **BLOCKED** | Gradle wrapper jar (cannot be created without network; see below) · any APK · verification of the MediaPipe API and of any model · lint results |

Because nothing was compiled, **expect compile errors and some runtime bugs on the first build.** The code was reviewed by reading only.

## Getting your first build (3 options)

1. **GitHub Actions (no local setup).** Create a GitHub repository, push this folder, open the *Actions* tab → *Build NexVora* → *Run workflow*.
   It installs JDK 17, the Android SDK and Gradle 8.7, runs unit tests and lint, builds the debug APK and uploads it (plus test/lint reports, and logs on failure) as artifacts.
   ```bash
   git init && git add . && git commit -m "NexVora Smart AI Browser 1.0.0"
   git branch -M main && git remote add origin https://github.com/<you>/<repo>.git && git push -u origin main
   ```
   Tick *generate_wrapper* when running manually to also receive `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`; commit those to the repo.
2. **Locally.** Install JDK 17, Android Studio (or the command-line SDK with platform 34) and Gradle 8.7, then:
   ```bash
   gradle wrapper --gradle-version 8.7     # creates gradlew + wrapper jar (once)
   ./gradlew :app:testDebugUnitTest        # unit tests
   ./gradlew :app:lintDebug                # lint
   ./gradlew :app:assembleDebug            # APK: app/build/outputs/apk/debug/app-debug.apk
   ```
3. **Android Studio**: open the folder, let it sync, press Run.

If the build fails, the first compile error is the thing to fix; paste it to your AI assistant together with the file.

## Release signing
A release APK must be signed to be installable. Create a keystore once (`keytool -genkeypair -v -keystore nexvora.jks -alias nexvora -keyalg RSA -keysize 4096 -validity 10000`) and **keep it out of git**.
- Locally: export `NEXVORA_KEYSTORE=/path/nexvora.jks`, `NEXVORA_KEYSTORE_PASSWORD`, `NEXVORA_KEY_ALIAS`, `NEXVORA_KEY_PASSWORD`, then `./gradlew :app:assembleRelease`.
- In GitHub: add repository secrets `NEXVORA_KEYSTORE_BASE64` (`base64 -w0 nexvora.jks`), `NEXVORA_KEYSTORE_PASSWORD`, `NEXVORA_KEY_ALIAS`, `NEXVORA_KEY_PASSWORD`. The workflow then also builds a signed release APK. Without them only the debug APK is built.
- Release builds use R8 shrinking; `proguard-rules.pro` keeps serialization and MediaPipe classes. Test the release APK, not only debug.

## Features
**Browser**: WebView tabs (lazily created, destroyed on close), address bar with URL/search detection, back/forward/reload/stop, progress, favicons, find in page, desktop site, text zoom, HTTPS indicator + certificate details, HTTPS-only mode, offline/error page, SSL errors blocked (never bypassed), external-app links, file upload, fullscreen video, JS dialogs, camera/mic/location prompts, share/copy, QR code of a page, **QR scanner** (CameraX + ZXing), saved-tab restore at launch (normal tabs only), reading list (a Bookmarks folder).
**Tabs**: grid with thumbnails (best effort), search, normal/private views, close/close all, reopen closed tab.
**Home**: shortcuts (add/remove/reorder), engine picker (Google default, DuckDuckGo, Bing, Brave, Ecosia, custom), voice search, local suggestions, quick actions, recent browsing, bookmarks, wallpaper.
**Bookmarks / History / Downloads / Reader mode / Privacy Center / Settings / Onboarding / About**: as named; see the app.
**AI**: chat UI (markdown, code copy, stop, retry, history), page summary/explain/translate/notes/key facts, selected-text actions, page text sent only to the on-device model and fenced as untrusted data, model import/download/load/unload/delete with honest status.

## Architecture
`NexVoraApp` (manual DI container) → `Settings` (SharedPreferences exposed as StateFlow), `Repository` (JSON-file stores), `TabManager` (tabs + WebView lifecycle + WebView clients), `AdBlocker`/`HostMatcher`, `Downloads` (DownloadManager), `AiEngine` + `AiController`, `SitePermissions`. UI is Compose under `ui/`, with one theme (`Theme.kt`) and a tiny stack navigator (`Nav.kt`). Pure logic (`UrlUtils`, `HostMatcher`, `JsonStore`) has no Android dependencies so it is unit-testable.

## Security decisions
- `usesCleartextTraffic="true"` is kept because a general browser must open `http://` sites the user chooses. Mitigations: typed addresses default to `https://`, mixed content is `NEVER_ALLOW`, and *HTTPS-only mode* upgrades links.
- No JavaScript interfaces are exposed. File and content access are off; `file:`, `content:`, `javascript:` navigations are refused. WebView debugging is enabled only in debug builds. Safe Browsing is on where supported. Certificate errors always cancel the load.
- Camera/mic/location need your choice per site (remembered; reset in Privacy Center) **and** the Android permission.
- Private tabs: separate WebView profile (own cookies/storage/cache, deleted when the last private tab closes) where the device WebView supports `MULTI_PROFILE`; otherwise they only skip history/cache/local storage and still share cookies, and the Privacy Center says so. Private tabs are never saved or restored.
- Blocker: hostname-based on sub-resources only. It cannot remove first-party ads or hide elements. Imported lists are validated and rejected if invalid; there is no automatic list download.

## AI model requirements
NexVora uses **Google MediaPipe Tasks GenAI (`com.google.mediapipe:tasks-genai:0.10.24`)**. That version and the API calls in `ai/AiEngine.kt` (`LlmInference`, `LlmInferenceSession`, `addQueryChunk`, `generateResponseAsync`, `cancelGenerateResponseAsync`) were written from memory and **have not been checked against the official docs or compiled**. No model is bundled and **no model has been tested**, so no model is advertised as compatible. To try one: obtain a MediaPipe-format LLM file (`.task`/`.bin`, e.g. a Gemma variant, subject to its license) → Settings → AI features → *Import model file* (or paste an https link) → *Load model*. Choose the prompt format (Gemma/Plain) to match the model. Large models need a lot of RAM; if loading fails, try a smaller one. If MediaPipe proves unsuitable, `AiEngine` is the only file to replace.

## Testing
`./gradlew :app:testDebugUnitTest` runs `UrlUtilsTest`, `HostMatcherTest`, `JsonStoreTest` (bookmark/history-style persistence), `DownloadItemTest`. Settings persistence, tab state and AI state transitions have no tests yet (they need Android or an Android test runner).

## Licenses
NexVora's code: © 2026 NexVora Lab's Ofc (add a LICENSE file of your choice). Dependencies are Apache 2.0; see `THIRD_PARTY_NOTICES.md`. The earlier claim that the app is based on/inspired by OpenBrowse was removed: no OpenBrowse code is used, so no OpenBrowse license applies.
