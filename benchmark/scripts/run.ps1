# SPDX-License-Identifier: MIT
#
# Convenience runner for the microbenchmark suite. Builds the
# benchmark APK, installs it next to the debug app APK, runs the
# JUnit instrumentation, and tees the raw output to a file you
# pick. The output file is what `compare.ps1` consumes.
#
# Usage:
#   ./benchmark/scripts/run.ps1 -Output before.txt
#   # ... apply your changes ...
#   ./benchmark/scripts/run.ps1 -Output after.txt
#   ./benchmark/scripts/compare.ps1 -Before before.txt -After after.txt
#
# Prerequisites:
#   - A connected, unlocked, charging Android device (`adb devices`).
#   - The debug app APK is built (`./gradlew :app:assembleDebug`).
#   - The benchmark test APK is built (`./gradlew :benchmark:assembleDebugAndroidTest`).
#
# This script will trigger both Gradle assemble tasks if the APKs
# are missing, and re-install them on every run so the benchmarks
# match the source tree exactly.

param(
    [Parameter(Mandatory=$true)] [string]$Output
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

# ── 1. Confirm device ─────────────────────────────────────────────
$devices = (adb devices) -split "`n" | Where-Object { $_ -match "device$" }
if (-not $devices) {
    throw "No Android device connected. Run 'adb devices' to verify."
}

# ── 2. Build APKs (incremental — fast on a no-op rebuild) ────────
Write-Host "[1/4] Building app + benchmark APKs..."
& .\gradlew.bat :app:assembleDebug :benchmark:assembleDebugAndroidTest --console=plain | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }

# ── 3. Install ────────────────────────────────────────────────────
Write-Host "[2/4] Installing APKs on device..."
$appApk = Get-ChildItem app/build/outputs/apk/debug/*.apk | Select-Object -First 1
$testApk = Get-ChildItem benchmark/build/outputs/apk/androidTest/debug/*.apk | Select-Object -First 1
if (-not $appApk) { throw "Debug app APK not found under app/build/outputs/apk/debug." }
if (-not $testApk) { throw "Benchmark test APK not found under benchmark/build/outputs/apk/androidTest/debug." }

adb install -r -t $appApk.FullName | Out-Null
adb install -r -t $testApk.FullName | Out-Null

# ── 4. Run ────────────────────────────────────────────────────────
Write-Host "[3/4] Running benchmarks (3-5 min, do not touch the device)..."
$suppressErrors = "UNLOCKED,EMULATOR,UNSUSTAINED-ACTIVITY-MISSING,UNSUSTAINED-CLOCKS,DEBUGGABLE,LOW-BATTERY,METHOD-TRACING-ENABLED,UNLOCKED-NO-ACTIVITY"
$cmd = @(
    'shell', 'am', 'instrument', '-w', '-r', '--no-window-animation',
    '-e', 'androidx.benchmark.suppressErrors', $suppressErrors,
    'com.midtano.otp.benchmark.test/androidx.benchmark.junit4.AndroidBenchmarkRunner'
)

$resolvedOutput = if ([System.IO.Path]::IsPathRooted($Output)) {
    $Output
} else {
    Join-Path (Get-Location) $Output
}
$outDir = Split-Path -Parent $resolvedOutput
if ($outDir -and -not (Test-Path $outDir)) {
    $null = New-Item -ItemType Directory -Force -Path $outDir
}

& adb @cmd | Tee-Object -FilePath $resolvedOutput | ForEach-Object {
    if ($_ -match '^INSTRUMENTATION_STATUS: test=(.+)$') {
        Write-Host "  - $($Matches[1])" -ForegroundColor DarkGray
    } elseif ($_ -match '^Time: ([\d,.]+)') {
        Write-Host ("Total time: {0} s" -f $Matches[1]) -ForegroundColor Green
    } elseif ($_ -match 'OK \(\d+ tests\)') {
        Write-Host $_ -ForegroundColor Green
    } elseif ($_ -match '^FAILURES!!!') {
        Write-Host $_ -ForegroundColor Red
    }
}

# ── 5. Restore animations ────────────────────────────────────────
Write-Host "[4/4] Restoring system animation scales..."
adb shell settings put global window_animation_scale 1.0 | Out-Null
adb shell settings put global transition_animation_scale 1.0 | Out-Null
adb shell settings put global animator_duration_scale 1.0 | Out-Null

Write-Host ""
Write-Host ("Output saved -> {0}" -f $resolvedOutput) -ForegroundColor Cyan
Write-Host ""
Write-Host "Next step: run another pass after your changes, then compare:"
Write-Host "  ./benchmark/scripts/compare.ps1 -Before before.txt -After $Output"
