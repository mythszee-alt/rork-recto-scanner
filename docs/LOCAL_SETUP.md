# Running Recto in Android Studio

## 1. Open the right folder

**Open `android/`, not the repository root.**

The repo holds two apps (see `rork.json`): the Kotlin app in `android/` and the
Supabase backend in `backend/`. The Gradle build lives in `android/`, so pointing
Android Studio at the repo root gives you "no Gradle project found".

```
File → Open… → select the android/ folder → Open
```

Let it finish "Gradle sync" the first time — it downloads the Android Gradle
Plugin, Compose, CameraX, ML Kit and RevenueCat, so expect several minutes.

If sync complains about the SDK, install via **Tools → SDK Manager**:
- SDK Platform **API 36** (`compileSdk`/`targetSdk`)
- Build-Tools and Platform-Tools (latest)

The project builds with **Java 17+** (Android Studio bundles a suitable JDK —
check **Settings → Build → Build Tools → Gradle → Gradle JDK** if it fails).

## 2. Supply the config values

Three values are compiled into `BuildConfig` (see `app/build.gradle.kts`). Without
them the app builds fine but reports *"Account service is still being configured"*
and the paywall stays disabled:

| Property | Where to get it |
|---|---|
| `EXPO_PUBLIC_SUPABASE_URL` | Supabase → Settings → API → **Project URL** |
| `EXPO_PUBLIC_SUPABASE_ANON_KEY` | Supabase → Settings → API → **anon / public** key |
| `EXPO_PUBLIC_REVENUECAT_ANDROID_API_KEY` | RevenueCat → Project → API keys → **Google Play** |

The build reads an environment variable first (that's how Rork and CI inject
them), then falls back to a **Gradle property**. Use the Gradle property locally,
because Android Studio is normally launched from a desktop icon and therefore
does *not* inherit `export`s from your shell — a very common source of
"I set the variable but it's still blank".

Put them in your **user-level** Gradle properties file, which lives outside this
repository and so can never be committed:

- macOS / Linux: `~/.gradle/gradle.properties`
- Windows: `C:\Users\<you>\.gradle\gradle.properties`

```properties
EXPO_PUBLIC_SUPABASE_URL=https://YOUR-PROJECT.supabase.co
EXPO_PUBLIC_SUPABASE_ANON_KEY=YOUR-ANON-KEY
EXPO_PUBLIC_REVENUECAT_ANDROID_API_KEY=YOUR-REVENUECAT-KEY
```

Then **File → Sync Project with Gradle Files**.

> Do **not** put these in the repo's `android/gradle.properties` — that file is
> tracked by git and you would publish your keys. The anon key is designed to be
> public-ish and is protected by RLS, but the habit is what matters, and the
> RevenueCat key should not be in git either.

Skipping this step is fine if you only want to test scanning: the app has a
**"SCAN WITHOUT AN ACCOUNT"** button, and capture, OCR, barcode scanning, import
and export all work fully offline.

## 3. Run it

Pick a target, then Run (`Ctrl+R` / `Shift+F10`):

- **Physical device** — best choice. The camera, ProofCheck metering and barcode
  scanning all need a real camera. Enable Developer Options → USB debugging.
- **Emulator** — works, but the emulated camera is a synthetic scene, so capture
  quality metering and barcode detection are close to useless. Fine for testing
  navigation, auth, export and the UI.

## 4. What to actually test

Guest path (no config needed):

1. Onboarding → **SCAN WITHOUT AN ACCOUNT** → you should land in the library
2. FAB → capture a page → **REVIEW** → name it → **SAVE ON DEVICE**
3. Open the document → **EXTRACT TEXT (OCR)** → text appears, Copy/Share work
4. Home → **SCAN CODE** → point at a QR code → type label + Open for links
5. Home → **IMPORT** → pick photos → lands in the review screen
6. Document → export as PDF and as searchable PDF → **SHARE** → opens chooser

Account path (needs step 2 **and** the SQL migrations in `backend/migrations/`
applied to your Supabase project):

7. Create an account → confirm a `profiles` row appears in Supabase
8. Save a document → confirm a `documents` row plus an object in the
   `encrypted-documents` storage bucket
9. Sign out, sign back in → the document should be restored from the cloud

## 5. Reading errors

- **Build errors** — the **Build** tool window (`Cmd/Ctrl+F9` to rebuild). Kotlin
  compile errors show file and line; click to jump.
- **Runtime crashes / logs** — **Logcat**, filtered to the app. Filter by
  `package:mine`, or `tag:` for a specific area.
- **Gradle sync failures** — the sync panel; usually a missing SDK component or
  an unresolvable dependency version.

## 6. Known-unverified areas

The OCR, barcode, import and guest-mode code was written without being compiled
(the environment it was authored in cannot reach Google's Maven repo). The most
likely failure points, if the build breaks:

- `mlkitBarcode = "17.3.0"` in `gradle/libs.versions.toml` — bump if unresolvable
- `Icons.Rounded.QrCodeScanner`, `Icons.Rounded.PhotoLibrary`,
  `Icons.Outlined.TextFields` — all expected in `material-icons-extended`
- `androidx.camera.core.ExperimentalGetImage` opt-in in `BarcodeScannerScreen.kt`

Paste any compile error verbatim and it can be fixed quickly.

## 7. Do not commit

`android/.gitignore` already covers `build/`, `.gradle/`, `.idea/`,
`local.properties`, `*.apk` and `*.aab`. If Android Studio offers to add
`.idea/` files to git, decline.
