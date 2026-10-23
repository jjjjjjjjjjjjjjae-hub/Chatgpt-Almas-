# ONAY UI Demo (Android)

Unofficial, non-payment visual prototype made from the user's HTML. It is **not** the official ONAY! app. No payments, tickets, fare deduction, account synchronization, or transit QR validation are available.

## Build

Requires JDK 17, Android SDK platform 35, Gradle 8.10.2.

```sh
gradle -p onay-android assembleDebug
```

Output: `onay-android/app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the same debug APK as an artifact at `Actions → Build ONAY Demo APK → latest run → Artifacts`.

The built-in WebView loads `app/src/main/assets/index.html` from the app package with a virtual HTTPS origin so camera permission can be requested. Network access is used by fonts and libraries currently referenced by the HTML.

**Demo disclaimer is deliberately visible** to avoid mistaken identification as an official payment application.
