# SPDX-License-Identifier: MIT
#
# Compare two microbenchmark runs from the same device and print a
# diff report. Designed for the "before / after" workflow:
#
#   1. Run the benchmark suite once on `main`         -> before.txt
#   2. Apply your changes
#   3. Run the benchmark suite again on the same dev  -> after.txt
#   4. ./compare.ps1 -Before before.txt -After after.txt
#
# The script parses the `am instrument` raw output (as produced by
# `run.ps1` or by piping the adb command into `Tee-Object`) and
# emits one line per benchmark with the delta vs the BEFORE run.
#
# Threshold:
#   • -Threshold 0.10  (default 10%) — anything within +/-10% is a
#     wash and reports as `[~]`. Anything past that reports `[+]`
#     for an improvement (faster) or `[-]` for a regression
#     (slower).
#
# Exits 0 always — this is an informational tool, not a gate.

param(
    [Parameter(Mandatory=$true)] [string]$Before,
    [Parameter(Mandatory=$true)] [string]$After,
    [double]$Threshold = 0.10
)

$ErrorActionPreference = 'Stop'

function Read-Run([string]$path) {
    if (-not (Test-Path $path)) { throw "Run file not found: $path" }
    $records = @()
    $current = $null
    foreach ($line in Get-Content -Path $path -Encoding utf8) {
        if ($line -match '^INSTRUMENTATION_STATUS: class=(.+)$') {
            if ($current) { $records += $current }
            $current = [ordered]@{
                class = $Matches[1].Trim(); test = $null
                time_min_ns = $null; time_median_ns = $null; time_max_ns = $null
                allocs_median = $null
            }
        } elseif ($line -match '^INSTRUMENTATION_STATUS: test=(.+)$') {
            if ($current) { $current.test = $Matches[1].Trim() }
        } elseif ($line -match '^INSTRUMENTATION_STATUS: DEBUGGABLE_time_nanos_min=([\d.E+-]+)$') {
            if ($current) { $current.time_min_ns = [math]::Round([double]$Matches[1]) }
        } elseif ($line -match '^INSTRUMENTATION_STATUS: DEBUGGABLE_time_nanos_median=([\d.E+-]+)$') {
            if ($current) { $current.time_median_ns = [math]::Round([double]$Matches[1]) }
        } elseif ($line -match '^INSTRUMENTATION_STATUS: DEBUGGABLE_time_nanos_max=([\d.E+-]+)$') {
            if ($current) { $current.time_max_ns = [math]::Round([double]$Matches[1]) }
        } elseif ($line -match '^INSTRUMENTATION_STATUS: DEBUGGABLE_allocation_count_median=([\d.E+-]+)$') {
            if ($current) { $current.allocs_median = [math]::Round([double]$Matches[1]) }
        }
    }
    if ($current) { $records += $current }
    $records = $records | Where-Object { $_.test -and $_.time_median_ns -ne $null }
    $byKey = @{}
    foreach ($r in $records) { $byKey["$($r.class)::$($r.test)"] = $r }
    return $byKey
}

$beforeMap = Read-Run $Before
$afterMap = Read-Run $After

if ($beforeMap.Count -eq 0) { throw "No benchmark records parsed from $Before" }
if ($afterMap.Count -eq 0) { throw "No benchmark records parsed from $After" }

$keys = New-Object System.Collections.Generic.HashSet[string]
foreach ($k in $beforeMap.Keys) { $null = $keys.Add($k) }
foreach ($k in $afterMap.Keys) { $null = $keys.Add($k) }

$improved = 0
$regressed = 0
$unchanged = 0
$onlyBefore = 0
$onlyAfter = 0
$rows = New-Object System.Collections.ArrayList

foreach ($key in ($keys | Sort-Object)) {
    $b = $beforeMap[$key]
    $a = $afterMap[$key]
    $shortName = $key -replace '^com\.midtano\.otp\.benchmark\.', ''
    if (-not $b) {
        $onlyAfter++
        $null = $rows.Add([pscustomobject]@{
            tag = '[NEW]'
            name = $shortName
            before = '-'
            after = "{0:N0} ns" -f $a.time_median_ns
            delta = 'new'
        })
        continue
    }
    if (-not $a) {
        $onlyBefore++
        $null = $rows.Add([pscustomobject]@{
            tag = '[GONE]'
            name = $shortName
            before = "{0:N0} ns" -f $b.time_median_ns
            after = '-'
            delta = 'gone'
        })
        continue
    }
    $deltaPct = ($a.time_median_ns - $b.time_median_ns) / [double]$b.time_median_ns
    $deltaPctRounded = [math]::Round($deltaPct * 100, 1)
    $sign = if ($deltaPctRounded -ge 0) { '+' } else { '' }
    if ([math]::Abs($deltaPct) -le $Threshold) {
        $tag = '[~]'
        $unchanged++
    } elseif ($deltaPct -lt 0) {
        $tag = '[+]'
        $improved++
    } else {
        $tag = '[-]'
        $regressed++
    }
    $null = $rows.Add([pscustomobject]@{
        tag = $tag
        name = $shortName
        before = "{0:N0} ns" -f $b.time_median_ns
        after = "{0:N0} ns" -f $a.time_median_ns
        delta = "$sign$deltaPctRounded%"
    })
}

# ── Print table ──────────────────────────────────────────────────
Write-Host ""
Write-Host ("Comparing benchmark medians (threshold +/-{0}%)" -f ([math]::Round($Threshold * 100)))
Write-Host ("  before: {0}" -f (Resolve-Path $Before))
Write-Host ("  after:  {0}" -f (Resolve-Path $After))
Write-Host ""

foreach ($row in $rows) {
    $color = switch ($row.tag) {
        '[+]'    { 'Green' }
        '[-]'    { 'Red' }
        '[~]'    { 'DarkGray' }
        '[NEW]'  { 'Yellow' }
        '[GONE]' { 'Yellow' }
        default  { 'Gray' }
    }
    $line = "{0,-7} {1,-65}  before {2,15}  after {3,15}  delta {4,8}" -f `
        $row.tag, $row.name, $row.before, $row.after, $row.delta
    Write-Host $line -ForegroundColor $color
}

Write-Host ""
Write-Host ("Summary: {0} improved, {1} regressed, {2} unchanged (within +/-{3}%), {4} new, {5} gone" -f `
    $improved, $regressed, $unchanged, ([math]::Round($Threshold * 100)), $onlyAfter, $onlyBefore)
Write-Host ""
Write-Host "Notes:"
Write-Host "  - These are debuggable-build numbers (see benchmark/README.md). The"
Write-Host "    *delta* between before and after is what matters - both runs share"
Write-Host "    the same overhead so a 20% improvement here is a 20% improvement"
Write-Host "    in production too."
Write-Host "  - Run on the same physical device, with the same charging state,"
Write-Host "    and with the screen on but idle. Different conditions invalidate"
Write-Host "    the comparison."
