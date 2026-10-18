# Free Fire Shizuku Package Suspension & Launch Protection

System-level lock down of Free Fire (`com.dts.freefireth`) using Shizuku Elevated Package Manager controls (`pm suspend`), ensuring the game cannot be launched manually from the home screen even if xcel2 is completely killed in the background, while seamlessly unsuspending upon tapping "Open Game" with a 5-minute post-play grace period.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were confirmed during clarification and will govern the implementation:

- **Confirmed Decision 1 (Suspension Enforcement)**: Use Shizuku `pm suspend --user 0 com.dts.freefireth` (or Android fallback `pm suspend`) so the game is suspended directly by the Android package manager. While suspended, Android prevents manual launch from the home screen/app drawer with a system-level notice, regardless of whether xcel2 is active in the background.
- **Confirmed Decision 2 (Post-Session Grace Period)**: When the user finishes playing Free Fire, xcel2 provides a **5-minute grace period** before re-suspending the game package, accommodating quick app-switching or brief interruptions.
- **Confirmed Decision 3 (Server Freeze Policy)**: Free Fire (`com.dts.freefireth`) retains full access to open through "Open Game" even when `app_settings/global/isFrozen` is true.

---

## 1. Overview & Core Concept

- **What It Does**: Replaces volatile in-memory foreground polling with persistent Android system package suspension via Shizuku. When idle, `com.dts.freefireth` is suspended. Tapping "Open Game" inside xcel2 temporarily unsuspends the app, authorizes the session, and boots the game. Once the player leaves the game, a 5-minute delayed timer / AlarmManager triggers automatic re-suspension.
- **Target Persona**: Users requiring absolute anti-ban safeguards who want to ensure they never accidentally launch Free Fire outside the protected environment.
- **Key Value**: 100% protection reliability that survives phone reboots, memory purges, and app task-swiping.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Initial Setup / Shizuku Authorization**:
   - If Shizuku is connected and authorized, xcel2 verifies whether `com.dts.freefireth` is installed.
   - Initial package state is synchronized: Free Fire is set to suspended state.
2. **Launching via xcel2 ("Open Game")**:
   - User taps **Open Game** -> Selects **Free Fire**.
   - xcel2 runs `pm unsuspend com.dts.freefireth` via Shizuku shell.
   - Writes `ffth_opened_via_app = true` and updates timestamp.
   - Cancels any pending re-suspension alarms.
   - Launches Free Fire smoothly with anti-ban protections applied.
3. **Attempted Manual Launch Outside xcel2**:
   - Since the package is suspended in the OS, clicking the icon from the phone's launcher or app drawer immediately triggers the system dialog: *"Free Fire is suspended"* (or prevents launch).
   - If user attempts launch via secondary intents, `GameMonitorService` also force-quits the package and brings user to xcel2.
4. **Exiting the Game & Grace Period**:
   - When the user exits Free Fire, xcel2 registers the exit and starts a **5-minute countdown**.
   - If the player re-opens Free Fire within 5 minutes (or re-enters via xcel2), the session continues without disruption.
   - After 5 minutes elapse, `AlarmReceiver` executes `pm suspend` to re-lock the package.

### Visual Polish & Feedback
- Glassmorphic Cyberpunk dialog styling remains completely untouched and preserved.
- When unlocking, a subtle toast indicates *"Free Fire unlocked for this session."*
- If manual opening is attempted, clear guidance points the user back to xcel2.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: `pm suspend` vs. `pm disable-user`**
  - *Chosen Approach*: `pm suspend` (package suspension).
  - *Why*: Disabling an app completely removes its icon from the launcher, which confuses users into thinking the game was uninstalled. Suspension keeps the icon present but visibly greyed out/locked, providing clear native OS feedback that the app cannot be launched directly.
  - *Fallback*: If `pm suspend` is unsupported on a specific OEM flavor, fallback to `am force-stop` + monitor service.
- **Decision 2: Delayed Locking via AlarmManager (`RTC_WAKEUP`)**
  - *Chosen Approach*: `AlarmManager` with `setExactAndAllowWhileIdle` for the 5-minute grace period.
  - *Why*: Standard in-app `Handler.postDelayed` fails if the user closes xcel2 from the recent apps list. `AlarmManager` guarantees that even if xcel2 is swiped away, the system wakes up `AlarmReceiver` at exactly 5 minutes and executes `pm suspend`.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                          xcel2 App                          │
│                                                             │
│  [ Open Game Button ]                                       │
│          │                                                  │
│          ▼                                                  │
│  [ Unsuspends Package via Shizuku: pm unsuspend ]           │
│          │                                                  │
│          ▼                                                  │
│  [ Cancels Pending Suspend Alarm ]                          │
│          │                                                  │
│          ▼                                                  │
│  [ Sets ffth_opened_via_app = true & Launches Game ]        │
└──────────┬──────────────────────────────────────────────────┘
           │
           │ (User exits game or switches away)
           ▼
┌─────────────────────────────────────────────────────────────┐
│                  Session Expiry & Grace Period              │
│                                                             │
│  [ GameMonitorService detects exit ]                        │
│          │                                                  │
│          ▼                                                  │
│  [ Schedules 5-Minute Alarm via AlarmReceiver ]             │
│          │                                                  │
│   (5 minutes elapse without re-entry)                       │
│          ▼                                                  │
│  [ AlarmReceiver triggers: pm suspend com.dts.freefireth ]  │
│  [ Package re-locked at OS level ]                          │
└─────────────────────────────────────────────────────────────┘
```

### Components to Update
1. **`RenameUtil.kt`**:
   - Add `suspendPackage(pkg: String): Boolean` (`pm suspend --user 0 <pkg>`).
   - Add `unsuspendPackage(pkg: String): Boolean` (`pm unsuspend --user 0 <pkg>`).
   - Add `isPackageSuspended(pkg: String): Boolean`.
2. **`AlarmReceiver.kt`**:
   - Add `ACTION_SUSPEND_FFTH` with `scheduleSuspendAlarm(context, delayMillis = 5 * 60 * 1000L)` and `cancelSuspendAlarm(context)`.
   - On alarm receipt: executes `RenameUtil.suspendPackage("com.dts.freefireth")`.
3. **`GameMonitorService.kt`**:
   - When foreground moves away from `com.dts.freefireth`, schedule the 5-minute re-suspension alarm.
   - When foreground returns to `com.dts.freefireth`, cancel any pending re-suspension alarm.
4. **`HomeFragment.kt`**:
   - When user taps Free Fire in the "Open Game" dialog:
     - Unsuspend package immediately.
     - Cancel any pending suspension alarms.
     - Launch game.

---

## Verification Plan

### Automated Verification
- Run `compile_applet` to confirm zero build errors or syntax issues.

### Manual / Integration Scenarios
1. **Launch Test**:
   - Tap "Open Game" -> Choose Free Fire -> Verify package is unsuspended and game boots cleanly.
2. **App Kill Test**:
   - Launch Free Fire -> Swipe away xcel2 from recents -> Verify game continues running.
3. **5-Minute Grace Period Test**:
   - Exit Free Fire -> Verify package remains open for 5 minutes.
   - After 5 minutes -> Verify `AlarmReceiver` re-suspends `com.dts.freefireth`.
4. **Manual Attempt Test**:
   - While suspended, tapping Free Fire directly from Android launcher shows OS suspension prompt and prevents play.
