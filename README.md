<div align="center">

# HomeCar

**Home Assistant dashboard for Android Auto**

[![Release](https://img.shields.io/github/v/release/Riftsan/HomeCar?include_prereleases&label=version)](https://github.com/Riftsan/HomeCar/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

[Download APK](https://github.com/Riftsan/HomeCar/releases) · [Certificate setup](docs/CLIENT_CERTIFICATES.md) · [Android Auto installation](docs/INSTALACION_ANDROID_AUTO.md)

</div>

![HomeCar running on Android Auto](https://github.com/user-attachments/assets/27b401d4-e12e-415f-b5be-7581de3537c3)

HomeCar displays a configurable Home Assistant dashboard in a dedicated Android Auto interface. It is a community project based on the open-source Fermata Media Player codebase.

> **This fork: v1.1.0-rc.1 (prerelease).** Adds explicit Android client-certificate selection and native Material 3 phone settings. Actual mobile-network and head-unit validation remains required. Fork builds use a private persistent signing identity. A same-package upstream build signed with another key must be uninstalled before installing this fork; record your server address first. Upstream debug builds with the `.auto.debug` package can coexist with the `.auto` release package.

This is a fork of [RenanEspinar/HomeCar](https://github.com/RenanEspinar/HomeCar). See [certificate behavior and device checks](docs/CLIENT_CERTIFICATES.md), [design decisions and consultation](docs/DESIGN.md), and [release build/signing](docs/RELEASE_BUILD.md).

## What HomeCar provides

- A dedicated Home Assistant WebView with configurable server URL (HTTPS domain or local HTTP address).
- Persistent Home Assistant cookies, application preferences and navigation state, subject to Android WebView and OS lifecycle limits.
- Reuse of the existing WebView while switching between Android Auto apps, reducing avoidable dashboard reloads.
- Standard Android WebView HTTP cache, rather than forcing a network reload on every return.
- Explicit Android KeyChain certificate selection, expiry/status, replacement and forgetting, scoped to each HTTPS host and port.
- Native Material 3 day/night settings, validated server addresses, larger controls, and certificate retry without clearing Home Assistant cookies.
- Removal of inherited photo/video/media storage permissions from the Android Auto flavor.
- Manual checks for this fork's **published GitHub Releases** from phone settings. Background checks stay quiet. The app **does not auto-download or auto-install** APKs.
- A universal APK bundling the dynamic Web module, built with Android SDK 36 and Bundletool.

## Download and install

**Get the signed APK from this fork's [GitHub Releases page](https://github.com/Riftsan/HomeCar/releases).** Open the **v1.1.0-rc.1** prerelease and download `HomeCar-1.1.0-rc.1.apk` from **Assets**. CI's intermediate APK is a build input, not the distribution download.

**Use the `.apk` asset**, not Source code ZIP and not `.apks` (a Bundletool container).

1. Install the APK on the Android phone. If HomeCar is absent from Android Auto, a device-specific installation method may be necessary.
2. Open HomeCar on the **phone**, tap Settings and enter your Home Assistant URL, e.g. `https://home.example.com/` or `http://192.168.1.100:8123/`.
3. If your HTTPS endpoint requires a client certificate, choose the already-installed certificate in HomeCar settings. Save & open, then log into Home Assistant and verify access on Wi-Fi and mobile data. Configure Android Auto's launcher while parked.
4. If HomeCar is not listed in the Android Auto launcher, follow the [KingInstaller + Shizuku guide](docs/INSTALACION_ANDROID_AUTO.md).

**KingInstaller does not guarantee Android Auto compatibility.** Android Auto may reject the inherited projection service at the system validator, even when APK installation succeeds; see [issue #9](https://github.com/RenanEspinar/HomeCar/issues/9).

### Installer resources

- [KingInstaller (fcaronte, official GitHub)](https://github.com/fcaronte/KingInstaller/releases)
- [Shizuku (RikkaApps, official GitHub)](https://github.com/RikkaApps/Shizuku/releases)
- [Step-by-step installation, debugging, and recovery guide](docs/INSTALACION_ANDROID_AUTO.md)

## Upstream RC2 validation history

The following table records upstream results, not validation of this fork. Current fork checks and limitations are recorded in [release build/signing](docs/RELEASE_BUILD.md).

| Check | Status |
| --- | --- |
| Static checks of RC2 patches | Passed locally |
| `:fermata:bundleAutoDebug` | Built successfully locally |
| Bundletool universal debug APK | Generated and installed locally |
| Home Assistant dashboard in Android Auto | Working in one real-world setup reported by the maintainer |
| Return to dashboard after switching applications in DHU | Observed to preserve the view and return quickly |
| Android Lint | **Not completed** (previous run was interrupted) |
| BMW/Mercedes rotary-controller compatibility | **Not validated**; [PR #8](https://github.com/RenanEspinar/HomeCar/pull/8) under review |
| Compatibility across devices/Android Auto releases | **Not guaranteed**; several open issues |
| Security audit and release signing | **Not complete** |

See [RC2 release notes](CHANGELOG.md) and [test matrix](docs/PRUEBAS_RC2.md).

## How to build

Requirements: JDK shipped with Android Studio, Android SDK/API 36, Android NDK/CMake as required by Gradle, Git, and [Google Bundletool](https://github.com/google/bundletool/releases).

```powershell
git clone https://github.com/Riftsan/HomeCar.git
cd HomeCar
.\gradlew.bat :fermata:bundleAutoDebug
```

The Android Auto flavor has a dynamic `web` feature. Convert the built AAB to a **universal** APK:

```powershell
java -jar bundletool.jar build-apks --bundle="fermata/build/outputs/bundle/autoDebug/homecar-1.1.0-rc.1-auto-debug.aab" --output=HomeCar.apks --mode=universal
```

Open `HomeCar.apks` as a ZIP archive and extract `universal.apk`. Debug APKs are not production distribution artifacts. For a stable release, use a secure, backed-up signing key and publish a consistently signed APK.

## Architecture

```text
Android Auto projection -> HomeCar CarService -> HomeCar UI -> Android WebView -> Home Assistant
                                                  |
                                                  +-> Android WebView cookies/cache
Phone settings -> Local preference store           +-> Github Releases update check
```

The project retains Fermata-derived Android Auto projection infrastructure. A **Car App Library IoT** template-based interface is a possible future compatibility path, but it cannot embed an arbitrary Lovelace WebView.

## Reporting problems

Use this fork's [GitHub Issues](https://github.com/Riftsan/HomeCar/issues). Please include your phone model, Android version, Android Auto version, head unit/vehicle, APK version, installer, and precise reproduction steps. Remove Home Assistant URLs, session tokens, credentials, personal details and logs containing private data before posting.

Known investigations: [#1 Security](https://github.com/RenanEspinar/HomeCar/issues/1), [#6 WebView reload](https://github.com/RenanEspinar/HomeCar/issues/6), [#7 Rotary controls](https://github.com/RenanEspinar/HomeCar/issues/7), [#9 Launcher visibility](https://github.com/RenanEspinar/HomeCar/issues/9), [#12 mTLS](https://github.com/RenanEspinar/HomeCar/issues/12), [#14 Fermata coexistence](https://github.com/RenanEspinar/HomeCar/issues/14), [#15 Back button](https://github.com/RenanEspinar/HomeCar/issues/15).

## Credits, safety and license

Upstream HomeCar is maintained by **Renan Espinar**. HomeCar is derived from [Fermata Media Player by Andrey Pavlenko](https://github.com/AndreyPavlenko/Fermata). All inherited copyrights and author attributions remain applicable. Distributed under the [GNU General Public License v3.0](LICENSE). No affiliation or endorsement from Google, Android Auto, Home Assistant or Nabu Casa is implied.

**Do not operate doors, gates, cameras, alarms or other potentially distracting controls while driving.** Configure and test while parked. HomeCar is not a certified automotive safety system.
