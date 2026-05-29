<!-- SPDX-License-Identifier: MIT -->

## Summary

<!-- One-paragraph description of what this PR changes and why.
     Refer to an issue with `Closes #N` if applicable. -->

## Type of change

<!-- Pick one. Conventional Commit prefix in the title should match. -->

- [ ] feat — new user-facing feature
- [ ] fix — bug fix
- [ ] refactor — code change without user-visible behaviour change
- [ ] perf — measurable performance improvement
- [ ] test — adding or improving tests
- [ ] docs — documentation only
- [ ] ci — CI / workflow only
- [ ] chore — tooling, deps, build scripts

## Privacy and security checklist

<!-- Mandatory for any change touching the SMS / notification /
     accessibility / clipboard / log / overlay path. Tick every
     applicable box; explain the unticked ones in "Notes" below. -->

- [ ] No new data is persisted that did not previously land on disk.
- [ ] Every new logging site routes through `LogRedactor` /
      `LastNotification.redact` before reaching `CrashLogger`.
- [ ] No new permission is declared in `AndroidManifest.xml`.
- [ ] No new dependency is added (or one is added and justified
      under "Notes" below).
- [ ] No clipboard write omits `OverlayClipboard.markSensitive`.
- [ ] No `printStackTrace`, `GlobalScope`, `runBlocking` (outside
      tests), or `Thread.sleep` introduced.
- [ ] If the change touches the redaction primitives — a new
      regression test in `LogRedactorTest` /
      `LastNotificationRedactTest` is included.

## Test plan

<!-- How did you verify the change? Mandatory for `feat` and `fix`. -->

- [ ] `./gradlew :app:testDebugUnitTest` passes locally.
- [ ] `./gradlew :app:detekt` passes locally with no new findings.
- [ ] `./gradlew :app:lintDebug :app:lintRelease` passes locally.
- [ ] Tested on a physical device — Android version: ___.

If the change touches the overlay rendering, attach a short `.gif`
or screenshot of the new behaviour. If it touches the SMS / push
extraction, paste the body the new heuristic now matches.

## Notes

<!-- Anything reviewers should know up-front: deferred work,
     dependencies, follow-up PRs, OEM-specific caveats. -->
