# Implementation Plan - Inactivity Auto-Turn-Off, Game Activity Tracking & Background Updates

## User Review Required

> [!IMPORTANT]
> **Technical Constraint - "Seamless Silent Update":**
> Android **strictly prohibits** entirely silent, automated app updates (`InstallPackage` permission) for third-party apps not installed via the Google Play Store. The system enforces user interaction for APK installation to protect user security.
>
> **Proposed Approach:** The update will be **seamlessly downloaded in the background**. Once finished, the app will trigger the Android system's package installer, which requires the user to click "Install" to confirm the update. This is the closest possible implementation to a "seamless" experience within Android security policies.

---

## Proposed Changes

### 1. Inactivity Auto-Turn-Off & Activity Tracking
*   **`GameMonitorService.kt`**:
    *   Track the game's foreground/background state using `UsageStatsManager`.
    *   Accumulate total foreground time in `SharedPreferences` while the mod is ON.
    *   Implement a 20-minute inactivity timer. If the game remains backgrounded beyond this threshold, trigger `MainActivity.executeTurnOffGlobal()`.
*   **`HomeFragment.kt`**:
    *   Read the accumulated foreground time from `SharedPreferences` and display it in the Home screen UI.

### 2. Seamless Background Update
*   **`UpdateManager.kt` (New)**:
    *   Uses `WorkManager` for background APK download.
    *   Monitors download status via `LiveData`.
    *   Upon completion, triggers `Intent(Intent.ACTION_VIEW)` to launch the system package installer for the downloaded APK.
*   **`MainActivity.kt`**:
    *   Triggers the `UpdateManager` workflow when a remote version update is detected (via Firestore).

---

## Verification Plan

### Automated Build Verification
- Execute `compile_applet` to ensure new `WorkManager` dependencies (if needed) and logic integrate correctly.

### Manual Verification Scenarios
1. **Activity Tracking**:
   - Start the mod, open Free Fire MAX, and verify time starts accumulating on the Home UI.
   - Switch apps, verify time stops accumulating.
2. **Auto-Turn-Off**:
   - Start the mod, open Free Fire MAX, then background it for 21 minutes.
   - Verify the mod state automatically flips to "OFF" and logs the action.
3. **Background Update**:
   - Trigger a remote version mismatch in Firebase.
   - Verify the app begins the background download process.
   - Verify the system install prompt appears once the download finishes.
