<!-- SPDX-License-Identifier: MIT -->

# `:benchmark` — Microbenchmarks

A self-instrumenting microbenchmark module for the hot paths of
the OTP overlay app. Powered by `androidx.benchmark:benchmark-junit4`.

## When to run

This module is **opt-in tooling for contributors**. It is NOT a CI
gate, and you are not required to run it before opening a PR. The
recommended workflow is the **before / after self-comparison**:

1. Pick a hot path you want to optimise (e.g. `OtpExtractor.extract`).
2. Without applying any changes yet, capture a baseline run on
   your device.
3. Apply your changes.
4. Capture a second run on the same device, in the same conditions.
5. Run `compare.ps1` (Windows) or `compare.sh` (Linux/macOS) to see
   per-benchmark deltas.

You are comparing **your phone to itself**, before and after the
patch. Absolute numbers don't matter — only the delta does.

## What's measured

| Class | Surface |
|---|---|
| `OtpExtractorBenchmark` | `OtpExtractor.extract()` for typical English/Russian OTP bodies, the no-keyword fast-reject, the empty-body no-op, and the trigger-keyword detector. |
| `RedactorBenchmark` | `LastNotification.redact` (single/multiple OTPs, phones, prose), `LogRedactor.redactSender`, `LogRedactor.redactDigits`. |
| `OverlayQueueBenchmark` | `OverlayQueue.offer` on empty / half-loaded / duplicate paths, `pollFirst`. |
| `RegexCacheBenchmark` | `OtpRegexCache.safeCompile` cache hit / cache miss / invalid-source fallback. |
| `DeduplicatorBenchmark` | `OtpDeduplicator.isDuplicate` / `markShown` on cold / warm cache. |

## Running the suite (the easy path)

Helper scripts live under `benchmark/scripts/`. They build the
APKs, install them, run the suite, save the raw output to a file
of your choice, and restore the system animation scales the
`androidx.benchmark` runner disables for stability.

### Windows (PowerShell)

```powershell
# 1. Capture baseline.
./benchmark/scripts/run.ps1 -Output before.txt

# 2. Apply your changes, rebuild not necessary — run.ps1 does it.

# 3. Capture the new run.
./benchmark/scripts/run.ps1 -Output after.txt

# 4. Compare.
./benchmark/scripts/compare.ps1 -Before before.txt -After after.txt
```

### Linux / macOS (bash)

```bash
chmod +x benchmark/scripts/*.sh

# 1. Capture baseline.
./benchmark/scripts/run.sh before.txt

# 2. Apply your changes.

# 3. Capture the new run.
./benchmark/scripts/run.sh after.txt

# 4. Compare.
./benchmark/scripts/compare.sh before.txt after.txt
```

The total runtime is roughly 3-5 minutes per pass on a mid-range
device.

## Running the suite (manual path)

If the helper scripts are inconvenient (e.g. you want to invoke
the runner from Android Studio's UI), the underlying commands are:

```bash
./gradlew :app:assembleDebug
./gradlew :benchmark:assembleDebugAndroidTest

adb install -r -t app/build/outputs/apk/debug/otp-overlay-*-debug.apk
adb install -r -t benchmark/build/outputs/apk/androidTest/debug/benchmark-debug-androidTest.apk

adb shell am instrument -w -r --no-window-animation \
  -e androidx.benchmark.suppressErrors UNLOCKED,EMULATOR,UNSUSTAINED-ACTIVITY-MISSING,UNSUSTAINED-CLOCKS,DEBUGGABLE,LOW-BATTERY,METHOD-TRACING-ENABLED,UNLOCKED-NO-ACTIVITY \
  com.midtano.otp.benchmark.test/androidx.benchmark.junit4.AndroidBenchmarkRunner
```

If you cancel the run mid-flight (USB disconnect, Ctrl+C), restore
the system animations:

```bash
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings put global animator_duration_scale 1.0
```

## Reading the output

Each benchmark reports four numbers per run: min, median, max
nanoseconds, and median allocation count. The median is the most
useful — it's resistant to one-off GC pauses and CPU contention
from background processes.

The `compare` script tags each row:

| Tag | Meaning |
|---|---|
| `[+]` | Improvement past the threshold (default 10% — anything within `+/-10%` reports as a wash). |
| `[-]` | Regression past the threshold. |
| `[~]` | Wash — within the threshold either way. |
| `[NEW]` | The benchmark exists in `after.txt` but not `before.txt`. |
| `[GONE]` | The benchmark existed in `before.txt` but not `after.txt`. |

Override the threshold if you care about smaller wins:

```powershell
./benchmark/scripts/compare.ps1 -Before before.txt -After after.txt -Threshold 0.05
```

```bash
./benchmark/scripts/compare.sh before.txt after.txt 5
```

## Why debuggable=true

`androidx.benchmark` 1.5.0-alpha06 with AGP 9.2 cannot auto-toggle
the `debuggable` flag for the benchmark APK at the moment, so the
recorded numbers are 5..10x slower than a release build's measured
behaviour. **This is fine for self-comparison** — both your before
and after runs share the same overhead, so a 20% improvement here
is a 20% improvement in production too.

## What the suite is NOT

- It is NOT a CI gate. GitHub Actions has no physical Android
  devices, and emulator numbers fluctuate too much to gate on.
- It is NOT a way to compare two devices. Different SoCs land
  wildly different absolute timings; the only valid comparison is
  one device vs itself across two commits.
- It is NOT a substitute for thinking. A 30% speed-up on a code
  path that's already sub-microsecond on production is meaningless.
  Read the absolute numbers, ask whether the path actually shows
  up in a real-user CPU profile, then decide if the optimisation is
  worth the complexity.
