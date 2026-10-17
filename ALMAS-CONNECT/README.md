# ALMAS CONNECT · v0.1 (Android APK + Windows EXE)

A Kazakh-language Flutter MVP for transferring files, gallery/camera photos and videos, and text between an Android phone and a Windows laptop **without internet**, while both are connected to the same local Wi-Fi or phone hotspot.

## What works in v0.1

- Android and Windows device screens from the same Flutter codebase.
- Manual IP + six-digit pairing code; server uses randomly generated tokens and rate-limits bad pairing attempts.
- Bidirectional file transfer and text-to-clipboard; received files saved in `Downloads/ALMAS CONNECT` on Windows and in the app's private documents folder on Android.
- Guided flow: destination `Downloads`/`Clipboard` → photo/video → Gallery/Camera (camera on Android).
- On Windows, incoming photos using `Clipboard` are placed in the Windows image clipboard using STA PowerShell + Windows Forms. Images appearing in Win+V depend on Windows clipboard history settings and limits (typically 4 MB per item).
- Incoming videos using `Clipboard` are saved and copied as a *file drop list*, so `Ctrl+V` in File Explorer can paste them. They do not appear as video thumbnails in Win+V.

## Limitations / security

This is an **early LAN-only prototype**, not the complete ALMAS CONNECT design. It is not yet encrypted with TLS: do **not** use on untrusted networks, or over the public internet. It doesn't yet include online relay, screen streaming/control, Bluetooth bridging, Smart Mouse, camera webcam streaming, QR pairing, resume-on-interruption, multi-device session management, or full Android public Downloads storage. These are planned features, **not** included in this build. Keep the app open during transfer; avoid heavy files for initial testing. Pair only with devices you trust.

The Android receiver writes to its app-private documents directory due to modern Android scoped storage. We will add a system file-save/share UI in a later release.

## Build automatically using GitHub Actions

The repository workflow `.github/workflows/almas-connect.yml` (with source copy in `ALMAS-CONNECT/.github/workflows/build.yml`) runs a stable Flutter setup on Ubuntu/Windows, generates native Android/Windows boilerplate, runs `flutter analyze lib`, builds an APK and a Windows EXE, and attaches them as downloadable workflow **artifacts**. The Windows EXE must be kept together with its `.dll` and `data/` directory (the Windows artifact is zipped for this reason).

Open **Actions → ALMAS CONNECT APK + EXE → latest successful run → Artifacts** to download:
- `ALMAS-CONNECT-Android-APK`: contains `app-release.apk`.
- `ALMAS-CONNECT-Windows-EXE-ZIP`: contains `ALMAS-CONNECT-Windows.zip` with `almas_connect.exe` and required files.

CI uses Flutter's default testing signing configuration, **not** a Google Play publishing key.

## Local development

Requires a current Flutter stable SDK and Android/Windows build tools.

```bash
flutter create --platforms=android,windows --project-name almas_connect /tmp/almas_scaffold
# Copy android/ and windows/ from the scaffold to this project.
# On Android only: add INTERNET permission and cleartext LAN transport setting.
flutter pub get
flutter run -d windows # or Flutter Android device ID
```

For Android's `AndroidManifest.xml`, ensure the INTERNET permission is included and the application's `usesCleartextTraffic` flag is true for LAN HTTP. The CI handles both automatically.

## Pairing

1. Connect phone and laptop to the same Wi-Fi or phone hotspot.
2. Start the application on both. On Windows allow it through the **private network** firewall prompt.
3. Read the IP address and six-digit code shown on device B.
4. On device A enter device B's IP and code and choose **Құрылғыларды қосу**.
5. Both sides will show the paired device. Choose send file or photo/video; or enter text to sync clipboard.

Only the IP address should be entered (e.g. `192.168.43.15`), not the port. Both apps listen on port `8765`.

## Roadmap (separate milestones)

1. Secure authenticated transport using TLS + persistent QR pairing, more robust LAN discovery.
2. Resume transfers with per-chunk integrity checks, better Android storage export.
3. Online relay / NAT traversal with end-to-end encryption.
4. Android screen preview + control (user-approved MediaProjection / root optional).
5. Windows screen streaming/control and live phone camera webcam.
6. Smart Audio and Smart Mouse across device boundaries; drag and drop.

No remote control will start without the receiving device's explicit authorization.
