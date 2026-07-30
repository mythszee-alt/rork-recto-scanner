# Implementation Plan - Bypass Authentication for Testing

The goal is to allow the user to test the app's core functionality (scanning, camera, gallery, etc.) directly after installation without being forced to sign in or create an account.

## User Review Required

> [!IMPORTANT]
> This change makes "Guest Mode" the default entry point. Users will land on the Home screen immediately after onboarding. They can still sign in later via the Account screen if they wish to test Google/Email login.

## Proposed Changes

### [Component] Authentication Gating

#### [MODIFY] [AppViewModel.kt](file:///C:/Users/zeedr/StudioProjects/rork-recto-scanner/android/app/src/main/java/com/rork/recto/ui/screens/AppViewModel.kt)
- Update `AppUiState` initialization to default to `AppGate.LIBRARY` if onboarding is complete.
- Update `completeOnboarding()` to navigate directly to `AppGate.LIBRARY`.
- Ensure `isGuest` and `isFreeMode` are correctly set when entering the app without a session.
- (Optional) Skip the `PAYWALL` gate during testing to allow full access to export formats.

#### [MODIFY] [GateScreens.kt](file:///C:/Users/zeedr/StudioProjects/rork-recto-scanner/android/app/src/main/java/com/rork/recto/ui/screens/GateScreens.kt)
- Set `GOOGLE_SIGN_IN_ENABLED = true` to allow testing social login.

## Verification Plan

### Manual Verification
1. Install the APK.
2. Complete the onboarding flow.
3. Verify that the app opens the **Home Screen (Library)** immediately.
4. Verify that **Camera Capture** and **Gallery Import** work without a login.
5. Go to the **Account Screen** and verify that "SIGN IN TO ENABLE CLOUD BACKUP" is available for testing social login.
