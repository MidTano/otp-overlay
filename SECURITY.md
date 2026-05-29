<!-- SPDX-License-Identifier: MIT -->

# Security Policy

This is a hobby project, but it handles one-time codes — the most
sensitive strings the average user receives. A bug in this
codebase can leak those codes into the clipboard banner, the
system log, or — worst of all — the diagnostic file the user
might accidentally share in a bug report. So security reports
get serious attention, even when "feature" issues might sit
untouched for weeks.

## Supported versions

Single-track release model: only the latest tag on `main`
receives security fixes. Older tags are not patched.

| Version | Supported |
|---------|-----------|
| Latest tag on `main` | yes |
| Earlier tags | no |

## Reporting a vulnerability

**Please do NOT open a public GitHub issue for security
vulnerabilities.**

Use one of the private channels below so the bug can be patched
before disclosure:

1. **GitHub Security Advisories** (preferred):
   https://github.com/MidTano/otp-overlay/security/advisories/new
2. **Email**: open a private channel through the maintainer's
   GitHub profile (https://github.com/MidTano).

Include in your report:
- A short description of the vulnerability and the impact you
  see.
- A minimal reproduction — sample SMS body, push notification
  payload, or sequence of UI actions that triggers the bug.
- Affected version (`adb shell dumpsys package com.midtano.otp |
  grep versionName`).
- Device + Android version (`adb shell getprop ro.build.version.release`).
- Optional: a suggested patch.

**Do not include real OTP values, real phone numbers, or real
sender labels** in the report. Reduce them to placeholders
(`123456`, `+0 000 000-00-00`, `Bank`) or, if the value is
essential to the reproduction, redact via
`LogRedactor.redactSender` before attaching.

## What counts as a security issue

Anything that breaks the privacy contract documented in
`README.md`:

| Class | Examples |
|---|---|
| OTP value leak | OTP digits surfacing in the rolling diagnostic, the clipboard banner, the foreground notification, an unredacted log file, an Intent extra leaving the process. |
| Sender / PII leak | Phone numbers or sender labels appearing unredacted in any persisted store, log file, or notification. |
| Privilege escalation | A third-party app gaining access to the overlay token, accessibility-paste path, or the SharedPreferences store. |
| Network egress | Any code path that opens a socket or makes a DNS query — the manifest declares no `INTERNET` permission, and the app must keep that contract. |
| Tamper of redaction | A regex / pattern change that lets a digit run pass through the redactor verbatim. |
| Auto-paste hijack | A path that lets a non-OTP field receive the auto-paste, or a path that pastes into an unintended app. |

## What is NOT a security issue

These are bugs, not vulnerabilities — please open a **public**
issue for them:

- The overlay does not appear because of a missing permission
  (this is by design — the user has not granted access).
- The auto-paste lands in the wrong field on a specific OEM ROM
  (a heuristics bug, not a privilege bug).
- The OTP is extracted incorrectly because of a regex / trigger
  word edge case (open an issue with the body text).
- Performance regressions, animation glitches, layout overlaps.

## Disclosure timeline

Once a report is acknowledged:

1. Acknowledgement within **3 business days** — possibly later
   given that this work happens in spare time.
2. Triage and severity assignment within **7 business days**.
3. Patch + private fix branch within **30 days** for high /
   critical, **60 days** for medium / low. If a particular month
   is heavy on the day-job side and I cannot meet the deadline,
   I will say so explicitly.
4. Coordinated disclosure once a fix is shipped to `main` and a
   release tag is published.

If the issue is critical and a patch is not feasible inside the
window, the report stays under embargo until a workaround is
documented in the README.

## Scope

In scope: every Kotlin file under `app/src/main`, every Gradle
build script, every CI workflow under `.github/workflows`, every
runtime resource (manifest, drawables, strings, layouts).

Out of scope: third-party dependencies (report upstream), the
Android platform itself, vendor-specific OEM behaviour, and the
Telegram emoji pack referenced in `CreditsDialog`.

## Hall of fame

Security reporters who agree to be acknowledged will be listed
here once the fix ships. Anonymity is fine — say so in the
report and your handle will not appear.

_(no entries yet — first report opens this list)_
