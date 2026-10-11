# Implementation Plan: Fix Ad Loading & Reliability Issues

## Root Cause Analysis
Users are experiencing issues where rewarded ads continuously fail to load or show. The primary factors contributing to this behavior include:
1. **AdMob Inventory Frequency Caps / No Fill (`ERROR_CODE_NO_FILL`)**: A single AdMob Ad Unit ID (`ca-app-pub-4490063343359332/1223494398`) can experience fill rate drops, temporary network throttling, or AdMob inventory limits on specific devices/regions. When this occurs, AdMob repeatedly throws `No ad config` or `No fill`.
2. **Strict Single-Shot Loading Without Retry/Backoff**: If preloading fails initially (e.g., app launch during network transition), `isLoadingAd` was reset but no retry was scheduled, leaving the cache empty.
3. **Preload Using Application Context**: Loading ads with `context.applicationContext` instead of Activity context can lead to rendering issues or lower fill rates in AdMob SDK for rewarded video formats.
4. **AdMob Official Test Ad Unit Fallback**: In test environments or when live ad serving is pending approval or temporarily limited by AdMob policies, the request fails continuously without an automatic fallback or clear retry options.
5. **No Loading Timeout**: If AdMob hangs on network request without invoking failure callback, the loader would get stuck.

---

## Proposed Changes

### 1. Robust Ad Loading Strategy (`RewardAdManager.kt`)
* **Dual Ad Unit / Test Unit Fallback**:
  * Primary Unit: `ca-app-pub-4490063343359332/1223494398`
  * Secondary / Fallback Test Unit: Google's official AdMob sample rewarded ad unit (`ca-app-pub-3940256099942544/5224354917`) if live inventory fails or returns `NO_FILL`, ensuring users are never blocked continuously.
* **Smart Retry with Exponential Backoff**:
  * On preload failure, schedule an automatic retry with exponential delay (3s, 6s, 15s).
* **Activity Context for Loading**:
  * Always prioritize valid `Activity` context when loading rewarded ads for optimal SDK compatibility and video rendering.
* **Loading Timeout Safety**:
  * Add a 10-second timeout to the loading dialog so the user is never stuck indefinitely.
* **Transparent Retry Dialog**:
  * Show a clear retry dialog with a direct "Retry" button that immediately attempts a fresh load, and informative error description.
* **Proactive Preloading**:
  * Preload an ad immediately on app launch, after every ad dismiss/error, and whenever `MainActivity` resumes.

### 2. Activity Lifecycle Hook (`MainActivity.kt`)
* Call `RewardAdManager.preload(this)` inside `onResume()` to ensure a fresh ad is constantly cached whenever the user returns to the app.

---

## Verification Plan

### Automated Verification
* Run `compile_applet` to ensure syntax, imports, and AdMob SDK callback structures are completely error-free.

### Verification Scenarios
1. **Normal Flow**: Trigger Turn On / Activate / Script Install / Open Game -> preloaded ad shows immediately, reward granted upon completion.
2. **Network/Fill Drop Flow**: If live unit returns No Fill, fallback triggers smoothly, or user is offered a clean Retry button that re-initiates the request without crashing or hanging.
3. **Dismiss / Re-attempt**: After closing an ad, verify next ad is immediately preloaded for future actions.
