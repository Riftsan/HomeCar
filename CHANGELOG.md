# Changelog

## [1.1.0-rc.1] — Fork prerelease (2026-11-16)

- Added explicit Android KeyChain certificate selection, status/expiry, replacement and forgetting. Choices are bound to the exact HTTPS hostname and port; private keys stay under Android KeyChain control.
- Added asynchronous credential checks, client-auth constraints, stale callback protection, and retry after changing certificate choices. Server TLS validation remains enforced.
- Replaced the phone's basic settings dialog with native Material 3 controls, system light/dark appearance, a scrollable form, accessible touch targets, and validated server URLs.
- Opened native setup on an unconfigured phone instead of loading a placeholder webpage.
- Preserved dashboard paths and query strings during URL normalization. Home Assistant cookies and retained WebView behavior remain in place.
- Pointed release checks to this fork, with English Material dialogs for manual checks and quiet background checks.
- Removed deterministic signing-key generation. Release APKs use a private persistent signing identity outside the repository; device-bound certificate preferences are excluded from backups.
- Added 23 JVM security tests and a release build pipeline with unit tests and Android Lint.

The release remains a candidate until actual phone/mobile-data and head-unit checks pass. See [certificate setup](docs/CLIENT_CERTIFICATES.md), [design notes](docs/DESIGN.md), and [build/signing](docs/RELEASE_BUILD.md). Upstream history follows.

## [1.0.0-rc.2] — Pre-release candidate (2026-10-31)

**Source/build:** RC2 modifications and local validation, including successful `:fermata:bundleAutoDebug` and Bundletool-generated universal APK. Signed with the developer machine's **debug key**, not a long-term release key.

### Changes from 0.1.1-beta

- Home Assistant WebView lifecycle retention and navigation-state handling to limit reloads between Android Auto apps.
- Cookie and browser session handling improvements; standard WebView cache usage and fewer redundant preference writes.
- Removed inherited photo/video and broad media storage permissions from the Android Auto variant.
- Phone-side GitHub Releases version checks, no more than once per day, and manual check in HomeCar server settings; no auto-install.
- HTTP(S), WebView, permission and TLS security changes, with optional Android client-certificate (mTLS) selection.
- Floating navigation/back improvements prepared for testing.
- Updated README, KingInstaller/Shizuku setup and documented troubleshooting.

### Validation evidence

- Seven initial static checks and twelve RC2 structural checks passed.
- Compiled with Android SDK 36 and generated a universal debug APK.
- APK installed successfully on the maintainer's phone.
- Maintainer reported RC2 working in the car using KingInstaller + Shizuku; Desktop Head Unit test preserved the dashboard when switching away and back.

### Outstanding work (do not close issues based on this release)

- Android Lint not completed in the prior run.
- Android Auto launcher/Google Play Services compatibility on other phones remains inconsistent ([#9](https://github.com/RenanEspinar/HomeCar/issues/9)).
- Rotary knob focus and selection across OEMs not verified ([#7](https://github.com/RenanEspinar/HomeCar/issues/7), [#11](https://github.com/RenanEspinar/HomeCar/issues/11), [PR #8](https://github.com/RenanEspinar/HomeCar/pull/8)).
- Continued mTLS, security, app-back-button and Fermata co-installation testing.
- Production signing, update migration and long-term compatibility remain pending.

### Installing

Download **`HomeCar-1.0.0-RC2.apk`** from this release's assets and follow [docs/INSTALACION_ANDROID_AUTO.md](docs/INSTALACION_ANDROID_AUTO.md). A debug-signed build is meant for testing, and updating to a different signing key can require reinstalling the app.

## [0.1.1-beta]

Previous beta build of HomeCar, retaining Fermata-derived Android Auto projection infrastructure with a customizable Home Assistant dashboard.
