# Release build and signing

The fork builds version `1.1.0-rc.1`, version code `272`, package `com.renanespinar.homecar.auto`. The Android Auto flavor contains a dynamic `web` module; distribute a universal APK that includes that feature.

## Build pipeline

`.github/workflows/build-homecar-apk.yml` runs on feature branches and pull requests. It uses JDK 17 and the runner's Android API 36 / Build Tools 36 installation, executes unit tests and Android Lint, builds a release AAB, and uses checksum-verified Google Bundletool to create a universal intermediate APK. The web module's fusing flag is checked before packaging. Test/lint reports are uploaded for review.

The intermediate APK has a temporary transport signature. It is **not** the installation artifact. Release signing happens on the owner's workstation with `scripts/Sign-HomeCar.ps1`; no private signing material is sent to GitHub Actions.

A dependent emulator job installs that release-mode universal APK on a disposable Android API 36 emulator. It opens settings in light, dark, and 150% font modes, checks that actions remain reachable, captures screenshots, and fails on app crashes. This tests native layout inflation and packaging. It does not install the app on the owner's phone or exercise a real client certificate.

## Private signing identity

The helper stores the encrypted PKCS12 keystore outside the repository under `%LOCALAPPDATA%\HomeCarBuild\signing`. Its random password is protected by Windows DPAPI for the current user. The directory permits only that user and SYSTEM. The helper refuses to overwrite an existing identity or output APK.

Public signing certificate SHA-256:

```text
14b26e8b73e1586c96d1257af4145202955465762379716bfdd3239eaa768108
```

The signing helper verifies the APK signature, package, version code, and non-debuggable release mode. APK SHA-256 checksums accompany release assets. Keep a secure recoverable backup of the signing key and password: DPAPI protection alone is tied to the Windows user/environment, and future in-place APK updates require the same signing identity.

Example, from PowerShell 7.5+ with Java 17 and verified Android Build Tools available:

```powershell
.\scripts\Sign-HomeCar.ps1 -Action Sign -InputApk C:\build\HomeCar-intermediate.apk -OutputApk C:\build\HomeCar-1.1.0-rc.1.apk
```

Do not run Initialize again for normal releases. Do not commit keystores, passwords, client certificates, or private Home Assistant configuration. The upstream deterministic signing-key generator has been removed.

## Validation boundary

The inherited `utils` library uses the annotation-based `WrongThread` lint check in place of lint 32.2.1's interprocedural `ThreadConstraint` inference. A CI thread dump showed the inference engine consuming almost an entire CPU core in recursive `Type.Application`/`Type.Union` hashing and fixed-point analysis after more than ten minutes; heap usage was only about 533 MiB. Increasing memory did not resolve it. The app and web modules retain the default inference check. This is a scoped tooling workaround, not a claim that every inferred threading condition in the inherited library has been verified. Revisit it after a verified lint fix. The separately registered checks and ID-specific configuration were verified in Google's [lint checks](https://dl.google.com/dl/android/maven2/com/android/tools/lint/lint-checks/32.2.1/lint-checks-32.2.1-sources.jar) and [lint API](https://dl.google.com/dl/android/maven2/com/android/tools/lint/lint-api/32.2.1/lint-api-32.2.1-sources.jar) sources.

The lint baseline records 66 findings verified against the original upstream source: 44 missing translations, 12 localized string-format findings, 9 Android TV restricted-API uses, and one inherited file/content intent-filter host finding. These remain technical debt; the baseline does not mark them fixed. It contains no new HomeCar settings or security findings and no warnings. The new settings are explicitly English-only until translations are available. Scoped fixes address the malformed Khmer About placeholders and the API 28 string-format compatibility issue.

Source checks and CI results are recorded with the release. Android KeyChain dialogs, mobile-data mTLS, Android Auto acceptance, font scaling, TalkBack, and head-unit behavior still require the actual target device. A successful JVM test, static lint run, or Lovable browser prototype does not establish those outcomes. See the [device acceptance matrix](CLIENT_CERTIFICATES.md#device-acceptance-checks).
