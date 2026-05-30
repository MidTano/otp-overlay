<!-- SPDX-License-Identifier: MIT -->

# OTP Overlay

> A small Android overlay that surfaces one-time codes from SMS
> and push notifications on top of the foreground app, copies
> them on tap, and (optionally) auto-pastes into the OTP field
> via the Accessibility Service. Everything runs **on-device** —
> no network, no analytics, no third-party SDKs.

<p align="center">
  <img alt="OTP Overlay demo" src="https://github.com/MidTano/otp-overlay/releases/download/media/demo.webp" width="320">
</p>

<p align="center">
  <a href="https://github.com/MidTano/otp-overlay/actions/workflows/android.yml"><img alt="Android CI" src="https://img.shields.io/github/actions/workflow/status/MidTano/otp-overlay/android.yml?branch=main&label=Android%20CI&logo=githubactions&logoColor=white&style=for-the-badge"></a>
  <a href="https://github.com/MidTano/otp-overlay/actions/workflows/codeql.yml"><img alt="CodeQL" src="https://img.shields.io/github/actions/workflow/status/MidTano/otp-overlay/codeql.yml?branch=main&label=CodeQL&logo=github&logoColor=white&style=for-the-badge"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-2ea44f?style=for-the-badge&logo=opensourceinitiative&logoColor=white"></a>
</p>

<p align="center">
  <a href="app/build.gradle.kts"><img alt="min-sdk" src="https://img.shields.io/badge/min--sdk-31-3DDC84?style=flat-square&logo=android&logoColor=white"></a>
  <a href="app/build.gradle.kts"><img alt="target-sdk" src="https://img.shields.io/badge/target--sdk-36-3DDC84?style=flat-square&logo=android&logoColor=white"></a>
  <a href="gradle/libs.versions.toml"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white"></a>
  <a href="gradle/libs.versions.toml"><img alt="AGP" src="https://img.shields.io/badge/AGP-9.2.1-1F6FEB?style=flat-square&logo=gradle&logoColor=white"></a>
  <a href="gradle/wrapper/gradle-wrapper.properties"><img alt="Gradle" src="https://img.shields.io/badge/Gradle-9.5.1-02303A?style=flat-square&logo=gradle&logoColor=white"></a>
  <a href="gradle.properties"><img alt="JDK" src="https://img.shields.io/badge/JDK-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white"></a>
</p>

<p align="center">
  <a href="gradle/libs.versions.toml"><img alt="Lottie" src="https://img.shields.io/badge/Lottie-6.7.1-00DDB3?style=flat-square&logo=airbnb&logoColor=white"></a>
  <a href="gradle/libs.versions.toml"><img alt="Coroutines" src="https://img.shields.io/badge/Coroutines-1.11.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white"></a>
  <a href="config/detekt/detekt.yml"><img alt="Detekt" src="https://img.shields.io/badge/Detekt-1.23.8-4279F7?style=flat-square"></a>
  <a href="app/build.gradle.kts"><img alt="JaCoCo" src="https://img.shields.io/badge/JaCoCo-0.8.13-D32F2F?style=flat-square"></a>
  <a href="gradle/libs.versions.toml"><img alt="Robolectric" src="https://img.shields.io/badge/Robolectric-4.16.1-0A8754?style=flat-square"></a>
  <a href="gradle/libs.versions.toml"><img alt="Espresso" src="https://img.shields.io/badge/Espresso-3.7.0-3DDC84?style=flat-square&logo=android&logoColor=white"></a>
  <a href="gradle/libs.versions.toml"><img alt="Mockito" src="https://img.shields.io/badge/Mockito-5.23.0-25A162?style=flat-square"></a>
</p>

<p align="center">
  <strong>Built with the help of an awesome animated emoji pack —
  please give the original author some love.</strong>
</p>

<p align="center">
  <a href="https://t.me/addemoji/KawaiiEmoji">
    <img alt="Install KawaiiEmoji on Telegram" src="https://img.shields.io/badge/💖%20Install%20KawaiiEmoji%20pack%20on%20Telegram-26A5E4?style=for-the-badge&logo=telegram&logoColor=white&labelColor=0088CC">
  </a>
</p>

> **Status**: personal pet-project, published as-is. I update it
> when I have the time and the mood.
> If you need something urgently, fork it; the
> MIT licence allows that.

## Why

OTP codes still arrive as plain SMS or push notifications, and
the bank / marketplace / messenger expects you to memorise six
digits in three seconds before the banner rolls off. This app
flips the order:

- The overlay **stays on top of the active app** (banking,
  login, checkout) for the full window the user needs.
- One tap to copy, one accessibility paste to drop the code
  straight into the input field — no manual typing, no shade
  pull-down.
- The original heads-up notification can be silenced or replaced
  with a quiet shade-only mirror so the screen flash does not
  break flow.

## Privacy by construction

| Concern | What we do |
|---|---|
| OTP value | Never persisted. Logged as `***N digits` in the diagnostic; redacted on disk. |
| Sender / phone number | Long digit runs masked as `***N-digit-phone`; sender labels redacted in the rolling diagnostic. |
| Notification body | Stored in the `Last notification` diagnostic with all OTP-shaped digit runs masked before the body crosses a thread boundary. |
| Backups | `allowBackup="false"` — user-tuned regex / trigger words / stats never leave the device on a Google Drive backup or a phone-transfer. |
| Network | None. The app declares no `INTERNET` permission. |
| Logs | Stored under `filesDir` (sandboxed). The user can export through Settings → Logs for bug reports; nothing leaves the device automatically. |

## Permissions

| Permission | What it lets the app do |
|---|---|
| Display over other apps | Draw the OTP card on top of the foreground app. |
| Receive SMS | Catch incoming SMS via the platform broadcast. `READ_SMS` is **not** declared — the app never queries `content://sms`. |
| Notification access | Read posted notifications to extract OTPs delivered via push. |
| Accessibility (optional) | Auto-paste the code into the focused field. The user opts in. |

## Built with

Kotlin · AndroidX · Lottie · Coroutines · Detekt · JaCoCo ·
Robolectric · Espresso · UiAutomator · AndroidX Benchmark.

Pinned versions live in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Install / build

```bash
./gradlew :app:assembleDebug
```

The signed-release pipeline, reproducible build setup, R8
config, JaCoCo, microbenchmark suite and CI gates are documented
in [`CONTRIBUTING.md`](CONTRIBUTING.md). The package map and the
data flow diagram are in
[`docs/architecture.md`](docs/architecture.md).

## Contributing

PRs are welcome, but no SLA on review time — see
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## Security

Bugs that leak OTP values, sender labels, or any other PII fall
under the security policy. Report them through GitHub Security
Advisories — see [`SECURITY.md`](SECURITY.md). Do **not** open a
public issue.

## License

MIT — see [LICENSE](LICENSE) and [NOTICE](NOTICE) (NOTICE is
kept for third-party attribution; the MIT licence does not
require it).

## Credits

Some pieces of this app stand on the work of others. Big thanks
to:

- **[KawaiiEmoji](https://t.me/addemoji/KawaiiEmoji)** —
  every animated emoji in this app is a Lottie export of
  stickers from this Telegram pack. The original author is
  unknown to me; if that is you and you would prefer the
  animations not to be used here, please open an issue and the
  assets will be replaced. Install the original sticker pack on
  Telegram with one tap using the button at the top of this
  README.
- **[otphelper](https://github.com/jd1378/otphelper)** by
  [@jd1378](https://github.com/jd1378) — referenced while
  building the OTP-detection pipeline. A couple of the cleanup /
  ignore phrase lists in `data/prefs/PrefsFilter.kt` are
  inspired by their constants.
