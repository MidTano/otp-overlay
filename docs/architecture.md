<!-- SPDX-License-Identifier: MIT -->

# Architecture

A one-page map of the project. If you just opened the repo and
do not know where to start, read this first.

## What the app actually does

An OTP code arrives on the phone — either as an SMS or as a
notification from some other app. From there:

1. This app intercepts the event.
2. Extracts the code (4-9 digits) from the body text.
3. Surfaces a card on top of the foreground app with the code.
4. On tap, copies it to the clipboard.
5. Optionally, auto-pastes it into the focused field via the
   Accessibility Service.

Everything happens on-device. No network. No analytics. No
third-party SDKs.

## Entry points

You cannot "run main" in an Android app — the system calls into
your code at several different places. We have **four**:

| Entry point | Class | Triggered when |
|---|---|---|
| Launcher tap | `SplashActivity` → `OnboardingActivity` / `MainActivity` | The user taps the app icon |
| Incoming SMS | `SmsReceiver` (BroadcastReceiver) | The platform broadcasts `SMS_RECEIVED` |
| Incoming push | `NotificationListener` (NotificationListenerService) | Any notification appears in the shade |
| Time to auto-paste | `OtpAccessibilityService` | The user focuses an editable field |

`OverlayService` is **not an entry point** — it is a
foreground service that the other entry points ask (via Intent)
to surface the overlay.

## Data flow

The most common scenario: a push notification arrives, it
contains an OTP, the user sees the overlay and taps "copy".

```
   ┌─────────────────────────────┐
   │ android push notification   │  (any app: bank, messenger, …)
   └──────────────┬──────────────┘
                  │ platform delivers StatusBarNotification
                  ▼
   ┌─────────────────────────────┐
   │ NotificationListener        │  service/NotificationListener.kt
   │  prefilterPackage           │   ← allowlist filter, ignore prefix
   │  prefilterNotification      │   ← skip foreground / group-summary
   │  readExtras / readBody      │   ← decode bundle (title/text/big)
   └──────────────┬──────────────┘
                  │ String body
                  ▼
   ┌─────────────────────────────┐
   │ OtpExtractor.extract        │  extractor/OtpExtractor.kt
   │  ↓ snapshot prefs           │   ← trigger, regex, stop-words …
   │  └─→ OtpExtractorCore       │  extractor/OtpExtractorCore.kt
   │       1. truncate           │   ← max 8 KB on input
   │       2. normalise digits   │   ← Persian / Arabic → ASCII
   │       3. ignore phrases     │   ← "vscode", "barcode", …
   │       4. cleanup            │   ← strip "example.com", "<#>"
   │       5. trigger keyword    │   ← must contain "code"/"код"/…
   │       6. stop word check    │   ← user-defined blocklist
   │       7. regex (ReDoS-safe) │   ← RegexTimeout 250 ms
   │       8. currency adjacency │   ← skip "1234 USD"
   └──────────────┬──────────────┘
                  │ String? otp ("482915")
                  ▼
   ┌─────────────────────────────┐
   │ OtpDeduplicator.isDuplicate │  extractor/OtpDeduplicator.kt
   │  ← LRU 60 s, 32 codes       │   ← same code via SMS+push wins once
   └──────────────┬──────────────┘
                  │ if not a duplicate —
                  ▼
   ┌─────────────────────────────┐
   │ Intent SHOW_OTP             │  → ContextCompat.startForegroundService
   └──────────────┬──────────────┘
                  ▼
   ┌─────────────────────────────┐
   │ OverlayService.routeIntent  │  service/OverlayService.kt
   │  enqueueShow                │   ← OverlayQueue (cap 200)
   │  showOverlayInternal        │
   │   ↓ tryAutoPaste            │   ← if enabled: AccessibilityService.pasteNow
   │   └─→ attachCard            │
   └──────────────┬──────────────┘
                  ▼
   ┌─────────────────────────────┐
   │ OverlayCardPresenter        │  service/overlay/OverlayCardPresenter.kt
   │  inflate overlay_otp.xml    │
   │  WindowManager.addView      │   ← TYPE_APPLICATION_OVERLAY
   │  startAutoCopyAndWatchdog   │   ← 10 s auto-copy, 13 s watchdog
   └──────────────┬──────────────┘
                  ▼
   ┌─────────────────────────────┐
   │ OtpRevealLayout             │  overlay/OtpRevealLayout.kt
   │  REVEAL → IDLE → COPY →     │   ← 4 phases, ValueAnimator + SpringAnimation
   │  DISMISS                    │   ← + Lottie on copy + countdown stroke
   └─────────────────────────────┘
```

## Layers (packages)

```
com.midtano.otp/
├── core/         — Application, BaseActivity, locale, CrashLogger install
├── data/         — Prefs facade
│   └── prefs/    — typed wrappers around SharedPreferences
│                   (PrefsCore — flags, PrefsFx — animations,
│                   PrefsFxLevel, PrefsLocale, PhraseListStore)
├── extractor/    — ALL OTP extraction logic. The pure-function
│                   part lives in *Core.kt — testable without
│                   Android.
│                   - OtpExtractor / OtpExtractorCore
│                   - OtpCleanup, OtpRegexCache, OtpDeduplicator
│                   - OtpDigits, OtpTriggers, OtpStats, OtpDiagnoser
│                   - InterruptibleCharSequence + RegexTimeout (ReDoS)
├── locale/       — runtime locale switching without recreating activities
├── overlay/      — card rendering (OtpRevealLayout draws gradients,
│                   glow, blur, countdown, Lottie)
├── permissions/  — single point for permission checks / requests
├── service/      — Service classes (platform entry points)
│   ├── SmsReceiver
│   ├── NotificationListener
│   ├── OtpAccessibilityService
│   ├── OverlayService
│   └── overlay/  — OverlayService components
│                   (Presenter, QueueUiController, Toast,
│                   ScreenOff bridge, ForegroundNotifier,
│                   Shade notifier)
├── system/       — shared system infra
│                   - CrashLogger (uncaught handler + ring buffer)
│                   - LogRedactor / LastNotification (PII redaction)
│                   - IoScope (background coroutine scope)
│                   - ScreenState (locked/unlocked check)
├── ui/           — Activities (about, debug, main, onboarding,
│                   settings, splash, stats)
├── util/         — small helpers without dependencies
└── widget/       — custom Views (SpringSwitch, BarChartView, …)
```

## Core / context-aware split

Where the logic is non-trivial and the platform is not strictly
required, there are **two classes** with the same prefix:

- `*Core` — pure function. Takes data + a settings snapshot,
  returns a result. No `Context`, no `SharedPreferences`, no
  `Resources`. Tested in pure-JVM unit tests in milliseconds.
- The non-`Core` class — wrapper around `Core`. Reads `Prefs.*`,
  builds the snapshot, forwards to `Core`. This layer is tested
  via Robolectric, if it is tested at all.

Examples:
- `OtpExtractor` ↔ `OtpExtractorCore`
- `OtpCleanup` ↔ `OtpCleanupCore`

When you add new functionality to the extractor, follow this
template. Do not bolt logic directly onto `OtpExtractor.kt`.

## Host interfaces in `OverlayService`

`OverlayService` historically did too much. To pull pieces out
without breaking shared state, we use callback interfaces:

- `OverlayCardHost` — what `OverlayCardPresenter` needs from the
  service.
- `QueueUiHost` — what `QueueUiController` needs.
- `AutoPasteToastHost` — what `AutoPasteToastController` needs.

`OverlayService` implements all three interfaces. The
controllers see only the slice of the API they actually need —
and can be unit-tested against a stub host.

When adding a new overlay component, follow this template. Do
not give the controller a direct reference to `OverlayService`.

## Hard rules

- **Never log an OTP value without redacting first.** Anything
  going into `CrashLogger.log` or `LastNotification.save` must
  pass through `LogRedactor.redactSender` /
  `redactDigits` first, otherwise the digits land in a file the
  user can export.
- **No network calls.** The manifest does not declare
  `INTERNET` — that is a contract.
- **No `GlobalScope`, `runBlocking` (outside tests),
  `Thread.sleep`, `printStackTrace`.** Long-running work goes
  through `IoScope.scope`. Logging goes through `CrashLogger`.
- **No direct `SharedPreferences` access outside `data/prefs/`.**
  The entry point for everything else is `Prefs.*`. This makes
  it possible to swap the storage backend later without a
  cross-repo grep.

## Where to look if you want to...

| If you want | Look at |
|---|---|
| Understand how a code is extracted | `extractor/OtpExtractorCore.kt` |
| Change the default regex | `extractor/OtpExtractor.kt` (`DEFAULT_REGEX`) |
| Add a trigger word | `data/prefs/PrefsFilter.kt` (`DEFAULT_TRIGGER_WORDS`) |
| Tweak the reveal animation | `overlay/OtpRevealLayout.kt` + `overlay/RevealTimings.kt` |
| Tweak something in the card | `service/overlay/OverlayCardPresenter.kt` + `res/layout/overlay_otp.xml` |
| Change the queue logic | `service/overlay/OverlayQueue.kt` + `OverlayService.enqueueShow` |
| Touch auto-paste | `service/OtpAccessibilityService.kt` + `service/OtpFieldFinder.kt` |
| Add a new `Prefs.*` option | `data/prefs/PrefsCore.kt` (or the matching PrefsFx/...) + `Prefs.kt` (facade) |
| Change CI | `.github/workflows/android.yml` |
| Change R8 / ProGuard rules | `app/proguard-rules.pro` |

## Tests

See [`CONTRIBUTING.md`](../CONTRIBUTING.md#test-pyramid) for the
full pyramid breakdown and the commands.
