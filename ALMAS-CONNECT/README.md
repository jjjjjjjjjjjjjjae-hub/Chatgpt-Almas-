# ALMAS CONNECT · v0.3 (Android APK + Windows EXE)

A Kazakh-language Flutter MVP for transferring files, gallery/camera photos and videos, and text between an Android phone and a Windows laptop **without internet**, while both are connected to the same local Wi-Fi or phone hotspot.

## v0.3 QR pairing (offline)

- On Windows, click **QR көрсету** to show a QR for the current LAN IP and the six-digit pairing code. Android can show its QR too.
- On the phone, click **QR сканерлеу**, allow camera access, scan the laptop QR, inspect the destination IP, then press **Қосылу**. No manual IP or code typing required.
- Scanning works **without internet** because Android uses bundled ML Kit in `mobile_scanner`. This adds several MB to Android build size.
- QR data includes only the local IP, app port and temporary visible code; it does not contain the session authentication token.
- Both devices must be on the same Wi-Fi network, or the laptop can connect to the phone hotspot with mobile data disabled. Windows Firewall must allow the private network.
- If Windows has multiple adapters, choose the actual Wi-Fi IP in the drop-down before displaying the QR. QR does not solve Wi-Fi client isolation, blocked ports or VLAN separation.
- QR pairs using the current LAN HTTP code flow, which is **not encrypted**. Do not use on public/untrusted networks.

## What works in v0.3

- Android and Windows device screens from the same Flutter codebase.
- Manual IP + six-digit pairing code; server uses randomly generated tokens and rate-limits bad pairing attempts.
- Bidirectional file transfer and text-to-clipboard; received files saved in `Downloads/ALMAS CONNECT` on Windows and in the app's private documents folder on Android.
- Guided flow: destination `Downloads`/`Clipboard` → photo/video → Gallery/Camera (camera on Android).
- On Windows, incoming photos using `Clipboard` are placed in the Windows image clipboard using STA PowerShell + Windows Forms. Images appearing in Win+V depend on Windows clipboard history settings and limits (typically 4 MB per item).
- Incoming videos using `Clipboard` are saved and copied as a *file drop list*, so `Ctrl+V` in File Explorer can paste them. They do not appear as video thumbnails in Win+V.

## v0.2: low-bandwidth updates

- Each launch checks the **latest published GitHub Release**, then rechecks every 12 hours while running. This is a small HTTPS JSON request, not an APK download.
- New versions appear with a **Download / Resume** button. If the connection breaks, a `.part` file stays in app support storage and the next attempt requests the remaining bytes using HTTP Range. If the server doesn't support Range, a safe full retry is used.
- The **Automatic download on Wi-Fi** toggle is **off by default** and saved locally. Even a Windows Wi-Fi connection may actually be a metered phone hotspot, so don't turn it on if you are conserving mobile data.
- Every update must come from this repository's GitHub Release and match the release asset's SHA-256 digest. Invalid or oversized files are rejected.
- The app never silently installs. **Android:** opens the system APK installer and asks for user approval (unknown-app permission may be needed). **Windows:** shows explicit install confirmation, then exits, replaces files using an external PowerShell updater and relaunches. Updates to protected folders can fail without write permission; see `%TEMP%/ALMAS-CONNECT-update.log` for errors.
- GitHub **Actions artifacts are not a public update feed**. The updater only sees releases published under semantic version tags (`v0.2.1`, etc.) with assets exactly named `ALMAS-CONNECT-Android.apk` and `ALMAS-CONNECT-Windows.zip`. No Release means no update appears.
- This uses full APK/ZIP downloads, **not binary delta patches**. Resume helps unstable internet; the total data volume of a new release is unchanged.

### Important: stable APK signatures

Previously distributed Android APK test builds used transient signing from the GitHub Actions runner. **They cannot be upgraded in place** by a new APK signed with a different key; Android will reject a signature mismatch. Preserve/export any needed app data and reinstall once when transitioning to the permanent release signing key.

Public update releases are **blocked** by CI until you configure four GitHub repository Actions Secrets, using a keystore you safely retain:
`ALMAS_SIGNING_KEYSTORE_B64`, `ALMAS_SIGNING_STORE_PASSWORD`, `ALMAS_SIGNING_KEY_ALIAS`, `ALMAS_SIGNING_KEY_PASSWORD`.
The keystore must **never** be committed to source or shared with strangers. Keep it backed up: losing it prevents future in-place APK updates. The tagged-release job unpacks the secret and uses its permanent key to sign the APK. The tag builds must succeed before GitHub publishes files. When publishing, ensure `lib/update_service.dart`'s `almasVersion`, `pubspec.yaml` version and Git tag are in sync.

Until a signed release is published, the UI can check for updates but does not have a valid public release to download. In-app updating is implemented, but the physical APK installation and Windows replacement still require device tests.

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
