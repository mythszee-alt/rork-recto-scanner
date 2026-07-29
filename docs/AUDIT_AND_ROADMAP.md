# Recto — Audit, Roadmap & Copilot Task Prompts

Working document for taking Recto from its current state to a publishable,
CamScanner-comparable document scanner.

Every finding below was verified against the code at the commit this file was
added on. File and line references are real — start there rather than
re-deriving. Each task ends with a **Prompt** block you can paste straight into
Copilot / Claude / any coding agent.

**Read this first:** items are ordered P0 → P5. P0 blocks Play Store publish
outright. P1 items are shipped-but-broken or advertised-but-missing, which is
both a user trust problem and a policy risk. Do not start P3 feature work
before P0/P1 are closed.

---

## 0. Repo map & current state

```
android/                        Kotlin + Jetpack Compose app (single module)
  app/build.gradle.kts          Build config, BuildConfig fields from env vars
  app/src/main/java/com/rork/recto/
    MainActivity.kt             Entry, handles recto://auth deep link
    RectoApplication.kt         RevenueCat init
    data/
      AuthRepository.kt         Supabase auth (REST via Ktor), Keystore session store
      BillingService.kt         RevenueCat wrapper, "pro" entitlement
      CloudSyncService.kt       Encrypted backup upload/download/delete-sync
      EncryptionKeyRepository.kt Account-level AES key resolution
      ExportService.kt          PDF / searchable PDF / JPEG / PNG / WebP / ZIP
      ImportService.kt          System photo-picker import
      OcrService.kt             ML Kit on-device text recognition
    ui/navigation/AppNavigation.kt   Gate (onboarding/auth/paywall/library) + NavHost
    ui/screens/                 Compose screens + AppViewModel / RectoViewModel
    ui/theme/Theme.kt           Colors
backend/
  functions/delete-account/     Public HTML page for Play Store deletion URL
  migrations/                   SQL (must be run manually — see §4.1)
  types.ts                      Generated Supabase types
rork.json                       Rork platform manifest (android + supabase apps)
```

**Working today:** onboarding, email/password auth, guest mode, RevenueCat
paywall, CameraX capture with live blur/glare metering, page reorder/rotate,
export in 6 formats, OCR text extraction, barcode/QR scanning, gallery import,
encrypted cloud backup with cross-device restore, account-deletion request flow.

**Not working / not built:** everything in §1–§5 below.

---

## P0 — Blocks Play Store publish

### 0.1 Release build is signed with the debug key

`android/app/build.gradle.kts:31` —
```kotlin
release { signingConfig = signingConfigs.getByName("debug") }
```

Google Play rejects debug-signed uploads. You also need an **App Bundle (.aab)**,
not an APK, and the upload key must be kept safe forever (or enrolled in Play
App Signing) — losing it means you can never update the app.

> **Prompt**
> In `android/app/build.gradle.kts`, add a proper release signing config that
> reads keystore credentials from environment variables
> (`RECTO_KEYSTORE_PATH`, `RECTO_KEYSTORE_PASSWORD`, `RECTO_KEY_ALIAS`,
> `RECTO_KEY_PASSWORD`), falling back to unsigned (not debug-signed) when they
> are absent so local debug builds still work. Wire `release` to use it, and
> document the `./gradlew bundleRelease` command plus the required env vars in
> `docs/RELEASE.md`. Never commit a keystore or its passwords.

### 0.2 No code shrinking or obfuscation

`android/app/build.gradle.kts:30` — `isMinifyEnabled = false`.

Larger download, and all class/method names ship readable.

> **Prompt**
> Enable R8 for the release build in `android/app/build.gradle.kts`
> (`isMinifyEnabled = true`, `isShrinkResources = true`). Add the keep rules
> needed for kotlinx.serialization `@Serializable` classes, Ktor, ML Kit and
> RevenueCat to `android/app/proguard-rules.pro`. Then build a release bundle
> and verify auth, capture, export, OCR, barcode and cloud sync all still work
> — serialization and reflection-based libraries are what typically break.

### 0.3 Version is hardcoded at 1 / "1.0"

`android/app/build.gradle.kts:14-15`. Every Play upload needs a unique,
increasing `versionCode`.

> **Prompt**
> Move `versionCode`/`versionName` in `android/app/build.gradle.kts` into
> `gradle.properties` (or read from an env var so CI can set it), and document
> the bump process in `docs/RELEASE.md`.

### 0.4 Play Data Safety declarations must match reality

The app collects: email (account), document images (cloud backup, encrypted),
purchase state (RevenueCat), and camera access. The Data Safety form must say
so, and the deletion URL must be live.

> **Prompt**
> Read `backend/functions/delete-account/index.ts`, `AuthRepository.kt`,
> `CloudSyncService.kt` and `BillingService.kt`, then write
> `docs/PLAY_DATA_SAFETY.md` listing exactly what data is collected, whether
> it's shared, whether it's encrypted in transit and at rest, whether deletion
> is offered, and which code path handles each. Flag any mismatch between that
> and the claims in `AccountScreen.kt`'s privacy dialog and the onboarding copy.

### 0.5 16 KB page size compatibility (Android 15+)

ML Kit and CameraX ship native libraries. Google Play requires 16 KB page size
support for new/updated apps targeting recent SDKs.

> **Prompt**
> Verify that every native `.so` in the release bundle is 16 KB page aligned
> (use `zipalign -c -P 16 -v` or the Android Studio APK Analyzer). If ML Kit,
> CameraX or any transitive dependency ships non-compliant libs, bump to the
> versions that do and document the minimum required versions.

---

## P1 — Shipped but broken, or advertised but missing

### 1.1 "Verified Redaction" does not exist — but is advertised

`ui/screens/GateScreens.kt:72` (onboarding page 3) promises:

> "Recto flattens redactions into page pixels and issues a verification receipt
> only after the hidden content is checked."

**There is no redaction tool anywhere in the app.** There is no way to draw a
redaction box, nothing flattens anything, and nothing verifies anything.

Worse, `ui/screens/ReceiptScreen.kt` — the "verification receipt" — is entirely
hardcoded: `"RC-7F3A-0192"`, `"100.0%"`, `"98 / 100"`, `"NONE DETECTED"` are
string literals, not measurements of any real document. The screen is also
**unreachable**: the `receipt` route exists in `AppNavigation.kt` but nothing
navigates to it.

This is the single biggest integrity problem in the app. Advertising a security
feature that does not exist is a Play Store policy risk and, if a user relies on
it for a real document, a genuine harm.

> **Prompt**
> Either (A) build real redaction, or (B) remove the claim. Do not leave it as
> is.
>
> For (A): add a redaction editor reachable from `DocumentScreen`. Let the user
> draw rectangles over a page; on apply, **destructively** paint those regions
> onto the page bitmap and re-encode the file, so the original pixels are gone
> from the stored image (not an overlay). Then re-run ML Kit OCR over the
> redacted image and assert no text is detected inside the redacted rectangles.
> Rewrite `ReceiptScreen` to take a real result object — document id, page
> count, redacted-region count, measured pixel coverage, OCR-verified
> "no text detected in redacted areas" boolean, and a receipt id derived from a
> hash of the output file — and navigate to it after a successful redaction.
> Remove every hardcoded value from that screen.
>
> For (B): delete `ReceiptScreen.kt` and its route, and rewrite onboarding page
> 3 in `GateScreens.kt` to describe a capability the app actually has.

### 1.2 Session never refreshes — cloud sync dies after ~1 hour

`data/AuthRepository.kt` stores `refresh_token` (line 33) but **never uses it**.
There is no `grant_type=refresh_token` call anywhere. Supabase access tokens
expire after ~1 hour by default, so every authenticated request
(`CloudSyncService`, `EncryptionKeyRepository`, account deletion) starts
returning 401 and silently fails once the token ages out. The user sees backups
stop working with no explanation and no way to fix it short of signing out.

> **Prompt**
> Add token refresh to `data/AuthRepository.kt`: a `refreshSession()` that POSTs
> to `/auth/v1/token?grant_type=refresh_token` with the stored refresh token,
> persists the new session, and returns it. Track expiry (`expires_in` is
> already in `RectoSession`) by storing an absolute expiry timestamp. Add a
> `validSession()` accessor that refreshes when the token is expired or within
> ~60s of expiring, and route every authenticated caller
> (`CloudSyncService`, `EncryptionKeyRepository`, `requestAccountDeletion`)
> through it instead of `restoredSession()`. On refresh failure, clear the
> session and surface a "please sign in again" state rather than failing
> silently.

### 1.3 "Export my account data" button does nothing

`ui/screens/AccountScreen.kt:85` — the `ListItem` has no `onClick` / `clickable`
modifier at all. It renders, it looks tappable, nothing happens. GDPR data
portability is also a Play Store data-safety expectation.

> **Prompt**
> Implement account data export. Add an `AccountExportService` that builds a ZIP
> containing: a JSON of profile + subscription + document metadata, and the
> local document page images. Write it to cache and hand it to the system share
> sheet via the existing `FileProvider` (authority `${applicationId}.files`,
> already configured). Wire it to the "Export my account data" item in
> `AccountScreen.kt` with progress and error states.

### 1.4 Cloud restore can't be triggered manually

`RectoAction.RestoreFromCloud` is defined (`RectoViewModel.kt:61`) and handled
(line 105), but **no UI dispatches it**. Restore only runs automatically in
`init`. If that first attempt fails (offline, expired token), the user has no
way to retry.

> **Prompt**
> Add a "Restore from cloud" action to `AccountScreen` (and/or a retry button in
> the `SyncBanner` on `HomeScreen` when `isSyncError` is true) that dispatches
> `RectoAction.RestoreFromCloud`. Show syncing/success/failure state. Hide or
> disable it for guests, who have no account to restore from.

### 1.5 ProofCheck score is theatre

`RectoViewModel.kt` — `proofScore` defaults to `94` and is set to a hardcoded
`98` on `AddPage`. The home screen presents it as a measured "quality gate is
calibrated" number with a `BLUR · GLARE · SKEW · DPI` caption.

`CaptureScreen.kt` *does* compute real `sharpness` and `glare` from the camera
frame — but those values are thrown away when the page is captured, and never
reach the saved document. `RectoDocument.quality` and `isVerified` are likewise
decorative (`isVerified` is never set to `true` anywhere).

> **Prompt**
> Make ProofCheck real. Pass the measured `sharpness`/`glare` values from
> `CaptureScreen` through `RectoAction.AddPage` into per-page quality data.
> Compute a document score from the real per-page measurements (and add a skew
> and effective-DPI estimate if you want to keep the `BLUR · GLARE · SKEW · DPI`
> caption honest — otherwise change the caption to list only what is actually
> measured). Set `RectoDocument.quality` from that. Either implement a real
> definition of `isVerified` or remove the field and the "VERIFIED" filter.

### 1.6 "RECENT" filter does nothing

`HomeScreen.kt` filter chips are `ALL / VERIFIED / RECENT`, but the filter
predicate only special-cases `VERIFIED`. Selecting `RECENT` shows everything.
There is also no timestamp on `RectoDocument` to sort by — `detail` is a
display string like `"JUST NOW · ON DEVICE"`.

> **Prompt**
> Add a real `createdAt: Long` (epoch millis) to `RectoDocument`, populate it on
> save/import/restore, and migrate existing persisted documents (the JSON in
> SharedPreferences `recto_documents`) with a sensible default. Make `RECENT`
> sort/filter by it, and derive the `detail` display string from it instead of
> storing prose.

### 1.7 Deleted documents are unreachable

`RectoAction.Delete` sets `isDeleted = true` and `Restore` clears it, but
`HomeScreen` filters `!document.isDeleted` and there is no trash view — so
"restore" can never be triggered and deleted documents are invisible forever
while still consuming storage.

> **Prompt**
> Add a Trash screen listing `isDeleted` documents with Restore and Delete
> Forever actions. Delete Forever must remove the local page files, the cloud
> object and the `documents` row. Consider auto-purging trash after 30 days to
> match the account-deletion language already used in the app.

---

## P2 — Security & data integrity

### 2.1 The "encrypted backup" key is stored in plaintext on the server

`backend/migrations/20260729_encryption_keys_and_sync_policies.sql` creates
`encryption_keys.wrapped_key`, and `EncryptionKeyRepository.kt` stores the raw
AES key base64-encoded in it. **The column name says "wrapped" but nothing wraps
it.**

RLS prevents other *users* from reading it, and it is a real improvement over
the previous device-locked key (which made restore impossible at all). But
anyone with the service-role key or database access — including a compromised
backup or a rogue admin — can read every user's key and decrypt every document.
The app's privacy dialog and paywall say "encrypted backup", and users will
reasonably read that as *we can't read your documents*. Right now, that is not
true.

> **Prompt**
> Make the backup key genuinely non-recoverable by the server. Derive a
> key-encryption key from the user's password with Argon2id or PBKDF2
> (high iteration count, per-user random salt stored alongside), use it to
> AES-GCM-wrap the document key, and store only the wrapped blob + salt in
> `encryption_keys`. Rename the column honestly if the scheme changes shape.
> Handle the consequences explicitly: password reset must either re-wrap the key
> (requires the old password) or destroy access to existing backups — decide
> which, and tell the user *before* they confirm. Google-OAuth users have no
> password, so design a passphrase or recovery-code flow for them. Until this
> lands, soften the "encrypted backup" wording in `GateScreens.kt` and
> `AccountScreen.kt` to state accurately what protection exists.

### 2.2 Row Level Security has never been audited end to end

Migrations add policies for `encryption_keys` and `documents`, but nothing has
verified `profiles`, `account_deletion_requests`, or the
`encrypted-documents` storage bucket. The storage read policy is still a
commented-out suggestion in the migration file.

> **Prompt**
> Write `backend/migrations/<date>_rls_audit.sql` that asserts and (where
> missing) creates least-privilege RLS on every table: `profiles`,
> `documents`, `encryption_keys`, `account_deletion_requests` — select/insert/
> update/delete each scoped to `auth.uid()`. Add the `encrypted-documents`
> storage policies for select/insert/update/delete restricted to objects whose
> first path segment equals the caller's uid. Then write
> `docs/RLS_VERIFICATION.md` with copy-pasteable SQL that proves user A cannot
> read user B's rows or objects.

### 2.3 Scheduled account purge may never run

`purge_due_recto_accounts()` exists in the schema, and both the app and the
deletion web page write `scheduled_for` / `deletion_due_at`. But nothing found
in the repo schedules it. If it isn't wired to `pg_cron`, the 30-day promise in
the deletion dialog and on the deletion page is not kept.

> **Prompt**
> Verify whether `purge_due_recto_accounts` is scheduled. If not, add a
> migration enabling `pg_cron` and scheduling it daily, and make sure it also
> deletes the user's objects from the `encrypted-documents` storage bucket and
> their `encryption_keys` row — not just table rows. Document how to confirm it
> ran.

### 2.4 Deletion flow gaps

`AuthRepository.requestAccountDeletion` upserts a request row and patches
`profiles`, then signs out — but access is **not** actually disabled, contrary
to what both the dialog (`AccountScreen.kt`) and the web page claim
("Access is disabled immediately"). The user can sign straight back in, and
nothing cancels the pending deletion when they do.

> **Prompt**
> Make the deletion flow match its own copy. Either genuinely disable access on
> request (server-side, e.g. a flag checked by RLS or an edge function that
> bans the user) or change the wording everywhere to describe what really
> happens. Add a visible "deletion scheduled — cancel?" banner on next sign-in
> using `profiles.deletion_due_at`, wired to clear the request
> (`cancelled_at`), since the schema already supports cancellation but no code
> uses it.

### 2.5 Unused schema columns signal missing sync safety

`documents.sync_version` and `documents.encrypted_metadata` exist and are never
written or read. `sync_version` in particular suggests intended conflict
resolution that does not exist — two devices editing the same document will
silently clobber each other (last upload wins).

> **Prompt**
> Either implement optimistic concurrency using `sync_version` (increment on
> write, reject/merge on mismatch, surface a conflict to the user) or drop the
> unused columns in a migration so the schema stops implying a guarantee the
> code doesn't provide.

---

## P3 — Scanner feature parity (CamScanner / iScanner)

Ordered by how much users notice the absence.

### 3.1 Automatic edge detection, perspective correction and crop — **highest impact**

This is *the* defining feature of a document scanner and Recto has none of it.
Pages are saved as raw camera photos: skewed, with the desk visible around them.
Every competitor auto-detects the page quadrilateral, lets you drag the corners,
and warps it to a flat rectangle.

> **Prompt**
> Add automatic document edge detection and perspective correction. Evaluate
> Google's ML Kit Document Scanner (`com.google.android.gms:play-services-mlkit-document-scanner`),
> which provides capture + edge detection + crop + filters in one flow and would
> replace much of `CaptureScreen` — compare it against implementing detection
> manually with OpenCV before choosing. Whichever you pick, the result must be:
> detected quadrilateral shown live over the preview, draggable corner handles
> on a post-capture crop screen, and a perspective-warped flat output image
> saved as the page. Keep the existing blur/glare metering if it still adds value.

### 3.2 Scan enhancement filters

Export-time `ExportColorMode` (COLOR / GRAYSCALE / BLACK_WHITE) exists, but
there is no per-page enhancement at scan time and no preview. Competitors offer
Magic Color, Enhance, B&W with adaptive thresholding, and shadow removal.

> **Prompt**
> Add per-page enhancement applied at capture/review time with live thumbnails
> of each option: Original, Auto-Enhance (contrast/white balance normalisation),
> Magic Color, Grayscale, and B&W using adaptive thresholding (not the current
> fixed `ColorMatrix` in `ExportService.applyColorMode`, which crushes detail on
> unevenly lit pages). Persist the chosen filter per page so export and cloud
> backup use the enhanced image.

### 3.3 More export formats

Currently PDF, searchable PDF, JPEG, PNG, WebP, ZIP. Missing versus competitors:

| Format | Notes |
|---|---|
| **TXT** | OCR output as a text file — `OcrService` already produces it, just needs a writer |
| **Password-protected PDF** | Common paid-tier feature; `PdfDocument` can't do it — needs a PDF library |
| **Long image** | All pages stitched vertically into one image |
| **DOCX** | Frequently requested; significant work |

> **Prompt**
> Extend `ExportFormat` in `data/ExportService.kt` with `TXT` (write
> `OcrService` output), `LONG_IMAGE` (vertically concatenate pages) and
> `PASSWORD_PDF`. `android.graphics.pdf.PdfDocument` cannot encrypt, so evaluate
> a permissively licensed PDF library (check the licence before adding — avoid
> AGPL unless you intend to comply). Add the password prompt to `DocumentScreen`
> and gate password-PDF behind Pro if that's the intended business model.

### 3.4 Page size and orientation options

`ExportService.createPdf` hardcodes `1240 x 1754` (A4 at ~150 DPI) and always
portrait. No Letter, no Legal, no landscape, no fit-to-content.

> **Prompt**
> Add a page-size selector (A4, US Letter, Legal, Fit to content) and
> orientation (auto from image aspect, portrait, landscape) to the export
> options in `DocumentScreen`, and honour them in `ExportService.createPdf`.

### 3.5 Document organisation

No folders, no tags, no sort options. Search matches only `title`
(`HomeScreen.kt`) — the OCR text that `OcrService` can already extract is not
indexed, so users can't find a document by its contents.

> **Prompt**
> Add folders (or tags) and persist them alongside documents. Store OCR text per
> document when extracted and include it in the `HomeScreen` search predicate so
> users can search document *contents*, not just titles. Add sort options (date,
> name, size). Consider migrating persistence from the SharedPreferences JSON
> blob in `RectoViewModel` to Room — the current approach rewrites the entire
> document list on every mutation and will not scale.

### 3.6 ID card mode

Standard feature: capture front and back of an ID and place both on one page.

> **Prompt**
> Add an ID Card capture mode that guides the user to capture front then back,
> and composes both onto a single page at correct relative scale.

### 3.7 Signature and watermark

> **Prompt**
> Add a signature tool (draw once, save, then place/resize on any page) and an
> optional text watermark with adjustable opacity, size and rotation, applied at
> export time.

### 3.8 Merge, split and reorder existing documents

`ReviewScreen` reorders pages only *before* first save. After that, a saved
document's pages are fixed — no add page, no delete page, no merge two
documents, no split.

> **Prompt**
> Let saved documents be edited: append pages (camera or import), delete pages,
> reorder, merge two documents into one, and split one into two. Reuse
> `ReviewScreen` where possible. Re-upload the backup and update `page_count`
> after any change.

### 3.9 PDF import

Import is images-only (`ImportService`). Users expect to be able to open an
existing PDF, and to append scans to it.

> **Prompt**
> Support importing PDFs via `ACTION_OPEN_DOCUMENT`, rendering pages with
> `android.graphics.pdf.PdfRenderer` into page images so they enter the existing
> pipeline.

---

## P4 — Frontend, onboarding & accessibility

### 4.1 Onboarding promises things the app doesn't do

Covered in §1.1 (redaction), but audit all three onboarding pages in
`GateScreens.kt:70-73` against actual behaviour. Page 1 claims ProofCheck checks
"framing, blur, glare, skew and effective resolution" — only blur and glare are
measured (`CaptureScreen.kt`). Framing, skew and DPI are not.

> **Prompt**
> Rewrite the onboarding copy in `GateScreens.kt` so every claim maps to
> shipped behaviour, or implement the missing measurements. Do not ship copy
> that describes unimplemented features.

### 4.2 No empty state on the home screen

A new user reaching an empty library sees the search field, an ambiguous
ProofCheck card and nothing else — no explanation, no call to action beyond an
unlabelled FAB.

> **Prompt**
> Add an empty state to `HomeScreen` shown when there are no documents:
> a short explanation and prominent Scan / Import actions.

### 4.3 Errors are inconsistent and often invisible

Three different mechanisms are in play: `AppUiState.message`,
`RectoUiState.message` + `isSyncError`, and per-screen local `message` state
(`DocumentScreen`, `BarcodeScannerScreen`). Several failures are swallowed
entirely — `RectoViewModel.restoreFromCloud`'s per-document `download` failures
are ignored, and `ImportService` silently drops images it can't read.

> **Prompt**
> Standardise on one user-feedback mechanism (a `Snackbar` host at the
> `Scaffold` level, or a shared event flow). Route all user-facing errors
> through it. Make silently-swallowed failures visible: report how many pages
> failed to import, and how many cloud documents failed to restore.

### 4.4 Accessibility gaps

Several icon-only controls lack content descriptions — `CaptureScreen`'s
shutter `Surface` has none, and decorative icons correctly pass `null` but
interactive ones must not. Colour alone conveys capture readiness (cyan vs
magenta), which fails for colour-blind users. No `testTag`s anywhere, which also
blocks UI testing.

> **Prompt**
> Do an accessibility pass: content descriptions on every interactive control,
> minimum 48dp touch targets, a non-colour indicator (icon or text) alongside
> the cyan/magenta capture-ready state, and verify contrast ratios against the
> dark theme in `ui/theme/Theme.kt`. Add `testTag`s to key elements. Test with
> TalkBack.

### 4.5 No configuration-change or process-death handling

State lives in `ViewModel`s (survives rotation) but nothing uses
`SavedStateHandle`, so in-progress capture state is lost if the process is
killed while the camera is open — a realistic scenario on low-memory devices.

> **Prompt**
> Persist in-progress capture (`capturedPages`) across process death using
> `SavedStateHandle` or by writing to disk immediately, so a user who gets a
> phone call mid-scan doesn't lose their pages.

---

## P5 — GitHub process, CI & testing

**There is currently no `.github/` directory, no CI, and not a single test.**
Every change so far has gone to `main` unverified. For an app that will handle
people's documents, that's the gap most likely to cause a bad release.

### 5.1 No CI

> **Prompt**
> Add `.github/workflows/android.yml` running on pull requests and pushes to
> `main`: set up JDK 17, cache Gradle, then run `./gradlew lintDebug`,
> `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`. Fail the build on
> lint errors. Add a separate workflow that builds a signed release bundle on
> tags, taking the keystore from GitHub Actions secrets.

### 5.2 No tests

> **Prompt**
> Add unit tests for the logic that is testable without a device:
> `OcrResult.fullText` assembly, `ExportService` filename sanitisation,
> `CloudSyncService` archive/unarchive round-trip, encrypt/decrypt round-trip,
> and `AuthRepository.errorMessage` mapping. Then add instrumented tests for the
> critical paths: guest mode reaches the library, capture → review → save
> persists a document, and export produces a non-empty valid PDF.

### 5.3 No static analysis

> **Prompt**
> Add ktlint (formatting) and detekt (static analysis) with a config tuned to
> this codebase, wire both into CI, and fix or explicitly baseline existing
> violations.

### 5.4 No dependency or secret hygiene

> **Prompt**
> Add `.github/dependabot.yml` for Gradle and GitHub Actions updates. Enable
> secret scanning and push protection on the repository. Add a `.github/`
> pull-request template with a testing checklist, and enable branch protection
> on `main` requiring the CI workflow to pass.

### 5.5 Full-repo review sweep

> **Prompt**
> Review every file under `android/app/src/main/java/com/rork/recto/` for:
> main-thread I/O, unclosed resources (`Bitmap.recycle`, ML Kit `close()`, Ktor
> `HttpClient.close()`), coroutine scope leaks, swallowed exceptions
> (`runCatching { }.getOrNull()` that hides real errors from the user), hardcoded
> strings that should be in `strings.xml` for localisation, and
> `BuildConfig` values used without a blank check. Report findings with
> file:line and fix them in small, reviewable commits — not one large one.

---

## Environment configuration (currently blocking)

The app reads three build-time env vars
(`android/app/build.gradle.kts:24-26`). None are set in the Rork project, which
is why the running app reports *"Account service is still being configured"*:

| Variable | Where to get it | Effect when missing |
|---|---|---|
| `EXPO_PUBLIC_SUPABASE_URL` | Supabase → Settings → API → Project URL | Auth and all sync disabled |
| `EXPO_PUBLIC_SUPABASE_ANON_KEY` | Supabase → Settings → API → anon/public key | Auth and all sync disabled |
| `EXPO_PUBLIC_REVENUECAT_ANDROID_API_KEY` | RevenueCat → Project → API keys → Google Play | Paywall shows "still being configured" |

Guest mode (added recently) works around this for previewing, but accounts and
billing stay dead until these are set in the Rork project settings.

**Also still pending:** the two SQL migrations in `backend/migrations/` have not
been run against the Supabase project. Until they are, cloud restore and account
deletion cannot work regardless of the client code.

---

## Suggested order of work

1. **Env vars + run migrations** — nothing account-related is testable until this is done.
2. **P0** — release signing, R8, versioning. Without these you cannot publish at all.
3. **§1.1 redaction** and **§1.2 token refresh** — the two most serious correctness problems.
4. **§5.1 CI + §5.2 tests** — so everything after this is verified before it ships.
5. **§3.1 edge detection** — the biggest single gap versus every competitor.
6. Remaining P1/P2, then P3 features by user impact.
