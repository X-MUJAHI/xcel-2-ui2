# Implementation Plan - Rewarded Ads Integration for Normal Users

Integrate Google Mobile Ads SDK rewarded video ads on key action buttons (**Turn On**, **ACTIVATE FIRST TIME**, **+Install New script**, and **OPEN GAME**) strictly for normal users, while allowing admin users to bypass all ads.

## User Review Required

> [!IMPORTANT]
> **Ad Behavior Highlights based on Clarifications:**
> 1. **Admin Bypass**: Users with the `ADMIN` role bypass all ads immediately.
> 2. **Reward Completion**: Normal users must watch the rewarded ad to completion to unlock the button action.
> 3. **Load Failure / Offline Handling**: If an ad fails to load or the device is offline, a cyberpunk alert dialog will instruct the user to check their connection and retry.
> 4. **AdMob Identifiers**:
>    - App ID: `ca-app-pub-4490063343359332~3485921577`
>    - Rewarded Ad Unit: `ca-app-pub-4490063343359332/1223494398`

---

## Proposed Changes

### Dependency & Manifest Configuration

#### `app/build.gradle.kts`
- Uncomment `implementation(libs.play.services.ads)` so Google Mobile Ads SDK (`play-services-ads:23.6.0`) is included in compilation.

#### `app/src/main/res/values/strings.xml`
- Update/add `admob_rewarded_ad_unit_id` with `ca-app-pub-4490063343359332/1223494398`.

---

### Ad Management Architecture

#### `app/src/main/java/com/example/utils/RewardAdManager.kt`
Create a centralized, lifecycle-aware ad manager:
- **Initialization**: Initialize `MobileAds` on application start.
- **Admin Check**: Read `KEY_USER_TYPE` from `SharedPreferences`. If `userType == "ADMIN"`, bypass ads and run the target action immediately.
- **Preloading & Caching**: Maintain a preloaded `RewardedAd` instance so ads display quickly without freezing the UI.
- **Loading Overlay**: Show a themed cyber loading dialog while loading an ad on-demand if not yet cached.
- **Failure Dialog**: If ad loading fails, display a themed retry alert dialog: *"Ad unavailable. Please check your internet connection and try again."*
- **Reward Verification**: Track `OnUserEarnedRewardListener`. Only invoke the action callback if the reward was fully earned; if dismissed early, notify the user.

---

### Action Buttons Integration

#### `app/src/main/java/com/example/fragments/HomeFragment.kt`
Gate the following actions through `RewardAdManager`:
1. **ACTIVATE FIRST TIME** (`btnActivate`): Show rewarded ad before opening script activation selection.
2. **TURN ON** (`btnTogglePower` when mod is OFF): Show rewarded ad before opening script selection / turning on mod.
3. **OPEN GAME** (`btnOpenGame`): Show rewarded ad before launching the game sequence for normal users.

#### `app/src/main/java/com/example/fragments/SettingsFragment.kt`
Gate:
4. **+ INSTALL NEW SCRIPT** (`btn_new_script`): Show rewarded ad before opening the script installation catalog for normal users.

---

## Verification Plan

### Automated Build Verification
- Execute `compile_applet` to confirm the Google Mobile Ads SDK resolves, imports cleanly, and compiles without errors.

### Manual Verification Scenarios
1. **Normal User Flow**:
   - Tap *Turn On*, *ACTIVATE FIRST TIME*, *+Install New script*, and *Open Game*.
   - Verify loading spinner appears, rewarded ad plays, and action triggers only upon completion.
   - Test early dismissal: Verify action does NOT execute and alert appears.
2. **Admin User Flow**:
   - Log in with `ADMIN` account (`mujahi@admin`).
   - Tap all 4 action buttons and verify instant execution with 0 ads.
3. **Offline / Load Failure Flow**:
   - Simulate offline/error state.
   - Verify error dialog prompts the user to retry rather than silently executing or crashing.
