<div align="center">
  <img src="https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.ico" width="200" alt="YTMNT Logo">
  <h1>YTMNT (YouTube Music Ninja Tools)</h1>
</div>

YTMNT has two separate ways to run the same playback helpers:

1. a standalone userscript for a compatible browser;
2. a personal sideloaded Android app built with GeckoView.

Both live on the `main` branch. The Android app is experimental and is not a Play Store release.

## Standalone userscript

[Install YTMNT v5.10](https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.user.js) with Violentmonkey or Tampermonkey, then open [YouTube Music](https://music.youtube.com).

The script provides:

- Audio, Low, and HD quality modes;
- a draggable touch-friendly control badge;
- background-play and pause-state recovery;
- ad-skip and idle-dialog handling;
- custom click feedback and the Cowabunga easter egg.

## Android app

The `android/` project is a personal GeckoView app (`dev.hanenashi.ytmnt.gecko`). It embeds YouTube Music, injects the shared `ytmnt.user.js`, supports Google-domain login navigation, installs uBlock Origin from Mozilla Add-ons, and exposes Android media controls plus a foreground playback notification.

Build and lint the ARM64 debug APK from the repository root:

```sh
cd android
ANDROID_HOME=/home/beechan/.local/share/android-sdk ./gradlew :gecko:assembleDebug :gecko:lintDebug
```

The APK is written to `android/gecko/build/outputs/apk/debug/gecko-debug.apk` and is intended for personal sideloading. Gecko and the older WebView prototype use separate browser profiles, so sign-in is separate between them. See [`handoff.md`](handoff.md) for implementation notes and test caveats.

## Repository layout

```text
ytmnt.user.js   standalone userscript
android/        Android app prototypes and GeckoView build
handoff.md      app implementation notes and handoff details
```

The root userscript remains the canonical shared script; the Android build copies it into its generated extension assets.
