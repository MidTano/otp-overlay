<!-- SPDX-License-Identifier: MIT -->

# Contributing

Thanks for considering a contribution. A few important things up
front so nobody is disappointed later:

- This is a **personal pet-project**. I work on it in my spare
  time, when the mood strikes. PRs and issues get attention when
  I have the bandwidth — **do not expect a fast response**.
- Nothing personal: "slow to respond" ≠ "ignoring you". The
  cadence is just slow.

The rest of this document is the practical side for people who
still want to send a PR.

If you are looking at the repository for the first time, start
with [`docs/architecture.md`](docs/architecture.md) — it has the
package map, the four platform entry points, and a data flow
diagram.

## Environment

| Tool | Version |
|---|---|
| JDK | Temurin 21 (LTS) |
| Android SDK | API 36 (`compileSdk` / `targetSdk`) |
| Gradle | 9.5.1 (auto-provisioned via the wrapper) |
| Kotlin | 2.3.20 (K2 compiler) |
| AGP | 9.2.1 |

`./gradlew` provisions the rest. The only thing you need
manually is the platform-tools you already use for `adb`.

## Tech stack

The runtime / build / test stack the app pulls in. Pinned
versions live in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

| Layer | Libraries |
|---|---|
| Language / build | Kotlin 2.3.20 (K2), AGP 9.2.1, Gradle 9.5.1, JDK 21 |
| AndroidX | AppCompat, Core-KTX, ConstraintLayout, RecyclerView, DynamicAnimation, Palette |
| Animation | Lottie 6.7.1 |
| Async | Coroutines 1.11.0 |
| Static analysis | Detekt 1.23.8, Android Lint, CodeQL (java-kotlin, security-extended) |
| Coverage | JaCoCo 0.8.13 |
| Testing — JVM | JUnit 4, Robolectric 4.14.1, Mockito |
| Testing — device | Espresso 3.7.0, UiAutomator 2.3.0, AndroidX Benchmark |

## Build

Local debug build:

```bash
./gradlew :app:assembleDebug
```

Signed release. Set `RELEASE_KEYSTORE_PATH`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD` (env var, gradle property, or
`keystore.properties` at the repo root) and run:

```bash
./gradlew :app:assembleRelease
```

The build is reproducible across the same commit — `BUILD_TIME`
is honoured from `SOURCE_DATE_EPOCH` (CI sets it to the tag
commit's timestamp; local builds default to the current hour).

## Test pyramid

| Tier | Where | Purpose |
|---|---|---|
| Pure-JVM unit | `app/src/test/kotlin/**` (no `Robolectric`) | Pure functions, regex helpers, redactors, queue invariants. |
| Robolectric integration | `app/src/test/kotlin/**` (annotated `@RunWith(AndroidJUnit4::class)`, see `LastNotificationStorageTest.kt` for the canonical shape) | Tests that need a Context, SharedPreferences, NotificationChannel or Clipboard, but not a real screen. |
| Instrumented (real device / emulator) | `app/src/androidTest/kotlin/**` | WindowManager attach, Espresso click → Prefs round-trip, OverlayService lifecycle. |
| Microbenchmark (real device) | `benchmark/src/androidTest/kotlin/**` | Per-call latency / allocation counts on the extractor / redactor / queue hot paths. Opt-in tooling, not a gate. |

When adding a new module, **start at the bottom of the pyramid**.
Every behaviour that can be expressed as a pure function is
cheaper to test that way; promote upward only when the platform
actually matters. Microbenchmarks are reserved for code on the
request / listener hot path — adding one for a one-off helper is
overkill.

## Running tests

```bash
# Unit tests (pure-JVM + Robolectric)
./gradlew :app:testDebugUnitTest

# Static analysis
./gradlew :app:detekt

# Lint (debug + release)
./gradlew :app:lintDebug :app:lintRelease

# Coverage HTML report
./gradlew :app:jacocoTestReport
# → app/build/reports/jacoco/jacocoTestReport/html/index.html

# Instrumented (requires a connected device / emulator)
./gradlew :app:connectedDebugAndroidTest

# Microbenchmark suite (optional, opt-in tooling — see benchmark/README.md)
# 1. Capture a baseline run on the unmodified codebase:
./benchmark/scripts/run.ps1 -Output before.txt    # Windows
./benchmark/scripts/run.sh before.txt              # Linux / macOS
# 2. Apply your changes, run again with a different output name:
./benchmark/scripts/run.ps1 -Output after.txt
./benchmark/scripts/run.sh after.txt
# 3. Compare the two runs to see which benchmarks moved:
./benchmark/scripts/compare.ps1 -Before before.txt -After after.txt
./benchmark/scripts/compare.sh before.txt after.txt
```

If `connectedAndroidTest` cannot reach the test platform mirror
(`io.grpc:grpc-services` is occasionally flaky on Maven Central),
run them through `am instrument` directly:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/debug/otp-overlay-*-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell appops set com.midtano.otp.debug SYSTEM_ALERT_WINDOW allow
adb shell am instrument -w -r --no-window-animation \
  com.midtano.otp.debug.test/androidx.test.runner.AndroidJUnitRunner
```

## Coding standards

- **Kotlin style** — official conventions, 4-space indent, line
  width 120 (see `.editorconfig` and `config/detekt/detekt.yml` —
  the two values must stay in sync; if you raise one, raise the
  other).
- **KDoc on every public symbol.** If a symbol is `internal` to
  the module, prefer `internal` over a public class with implicit
  package privacy.
- **No magic numbers in animation code.** Promote them to a named
  const in the relevant `*Config.kt` (see `OverlayServiceConfig.kt`).
- **No PII in logs.** All user input passes through
  `LogRedactor.redactSender` / `LogRedactor.redactDigits` before
  reaching `CrashLogger.log`.
- **No hard-coded user-facing strings in Kotlin.** Every visible
  string lives in `res/values/strings.xml` with a Russian
  counterpart in `res/values-ru/strings.xml`. Strings flagged
  `translatable="false"` are reserved for URLs, brand wordmarks,
  and format strings.
- **No `GlobalScope`, `runBlocking` (outside tests),
  `Thread.sleep`, `printStackTrace`.** All long-lived background
  work goes through `IoScope`. Tests can use `runBlocking` to
  drain it.
- **No new dependencies without discussion.** Every transitive
  dep is a supply-chain risk; the bar is "we genuinely cannot do
  this without it."

## Permissions in the manifest

The full list of permissions the app declares and why each one
is needed. Keep this in sync with `app/src/main/AndroidManifest.xml`.

| Permission | Reason |
|---|---|
| `RECEIVE_SMS` | Watch for incoming SMS in `SmsReceiver` (broadcast-only; we never query `content://sms`). |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read posted notifications to surface push-delivered OTPs (`NotificationListener`). |
| `BIND_ACCESSIBILITY_SERVICE` | Auto-paste into the focused OTP field (`OtpAccessibilityService`); user opt-in. |
| `SYSTEM_ALERT_WINDOW` | Draw the OTP card on top of the active app (`OverlayService`). |
| `FOREGROUND_SERVICE` + `_DATA_SYNC` + `_SPECIAL_USE` | Keep `OverlayService` alive long enough to surface the overlay; type chosen at runtime by SDK. |
| `POST_NOTIFICATIONS` | Push the silent shade-only mirror (`NotificationMirror`) plus the foreground-service placeholder. |
| `VIBRATE` | Single 60 ms confirmation buzz when the overlay surfaces. |

`READ_SMS` is **NOT** declared — the receiver path uses the SMS
broadcast PDUs directly.

## Static analysis and security

- **Detekt**: `./gradlew :app:detekt`. Pre-existing findings live
  in `config/detekt/detekt-baseline.xml`; new findings fail the
  build. Regenerate the baseline ONLY after an intentional
  formatting pass with `./gradlew :app:detektBaseline`. A PR
  that quietly bumps the baseline to hide a new finding will be
  rejected.
- **Lint**: `./gradlew :app:lintDebug :app:lintRelease`. Debug
  and release variants run separately so R8-aware checks do not
  get skipped. Hard-fails on any error; `SetTextI18n` and
  `UnusedResources` are promoted from warning to error so a
  regression cannot slip in.
- **CodeQL** (java-kotlin, security-extended): runs on every
  push, PR, and weekly on a schedule.

## Reproducible release pipeline

`.github/workflows/release.yml` triggers on `v*` tags:

1. Decode the keystore from the `RELEASE_KEYSTORE_BASE64` secret
   into the runner workspace.
2. Pin `SOURCE_DATE_EPOCH` to the tag commit's timestamp so
   `BUILD_TIME` in BuildConfig is byte-identical across rebuilds.
3. Run `lintRelease` (hard-fail) → `assembleRelease` → upload
   signed APK + R8 mapping → publish a draft GitHub Release.
4. Wipe the decoded keystore in `if: always()`.

All third-party actions are pinned to a full commit SHA with the
matching tag in the trailing comment — see
`.github/workflows/android.yml` for the rationale.

## CI gates

Every PR must pass, in order:

1. `:app:testDebugUnitTest` — unit tests (pure-JVM + Robolectric)
2. `:app:detekt`
3. `:app:lintDebug` and `:app:lintRelease`
4. `:app:assembleDebug` and `:app:assembleDebugAndroidTest`
5. `:app:assembleRelease` (R8 + shrinkResources)
6. `:app:connectedDebugAndroidTest` on the emulator job
7. CodeQL `java-kotlin` security-extended analysis

A green local `./gradlew :app:check :app:detekt` is a strong
predictor of a green CI run.

## PR layout

- **One concern per PR.** Don't bundle unrelated cleanups.
- **Conventional commit prefix in the title** (`feat:`, `fix:`,
  `refactor:`, `test:`, `docs:`, `ci:`).
- **Body**: what changed and why. If the change touches the
  privacy contract, the PR description must explicitly call out
  what data flows where.
- **Screenshots** when the change affects the overlay rendering.
  A short `.gif` of the new behaviour is worth a thousand words.

## Reporting bugs

Open a regular issue. It helps to include:
- The device + Android version (`adb shell getprop ro.build.version.release`)
- A short description of the expected vs actual behaviour
- The diagnostic from Settings → Logs (already redacted)

Issues with PII in them will be edited or closed; please use the
Logs export, not raw screenshots of notification bodies.

For privacy or security vulnerabilities — anything that leaks an
OTP value, a phone number, a sender label, or any persisted user
data — do **NOT** open a public issue. Use the GitHub Security
Advisories flow described in [SECURITY.md](SECURITY.md).

## License

By submitting a contribution, you agree that your code is
licensed under the same terms as the rest of the repository
(MIT, see [LICENSE](LICENSE)).
