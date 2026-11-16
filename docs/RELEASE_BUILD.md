# Release build and signing

The fork builds version `1.1.0-rc.1`, version code `272`, package `com.renanespinar.homecar.auto`. The Android Auto flavor contains a dynamic `web` module; distribute a universal APK that includes that feature.

## Build pipeline

`.github/workflows/build-homecar-apk.yml` runs on feature branches and pull requests. It uses JDK 17 and the runner's Android API 36 / Build Tools 36 installation, executes unit tests and Android Lint, builds a release AAB, and uses checksum-verified Google Bundletool to create a universal intermediate APK. The web module's fusing flag is checked before packaging. Test/lint reports are uploaded for review.

The intermediate APK has a temporary transport signature. It is **not** the installation artifact. Release signing happens on the owner's workstation with `scripts/Sign-HomeCar.ps1`; no private signing material is sent to GitHub Actions.

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

Source checks and CI results are recorded with the release. Android KeyChain dialogs, mobile-data mTLS, Android Auto acceptance, font scaling, TalkBack, and head-unit behavior still require the actual target device. A successful JVM test, static lint run, or Lovable browser prototype does not establish those outcomes. See the [device acceptance matrix](CLIENT_CERTIFICATES.md#device-acceptance-checks).
