# YTMNT Android app handoff

Date: 2026-09-07
Branch: `android-webview`

## What is here

This branch contains two installable Android prototypes:

- `dev.hanenashi.ytmnt`: the original Android System WebView implementation.
- `dev.hanenashi.ytmnt.gecko`: the newer GeckoView implementation, kept side-by-side so the WebView build is not lost while Gecko is being exercised.

The Gecko app is the current direction. It embeds YouTube Music, installs the root `ytmnt.user.js` as a built-in Gecko WebExtension at document start in the MAIN world, uses a desktop user agent, and keeps the session in Gecko's persistent profile. The app is still personal/sideload-only; there is no release signing or Play packaging.

## Current Gecko behavior

- YouTube Music loads in-app and the YTMNT badge appears.
- Top-level navigation is restricted to YouTube and Google account domains, including `google.co.jp`, so Google login should remain in the app instead of escaping to Chrome.
- `network.manage-offline-status` is disabled through the Gecko preference API. This is needed on the current phone/network combination where Gecko otherwise reported offline behind the Tailscale/private-DNS setup.
- `suspendMediaWhenInactive(false)` is enabled.
- Native Android foreground media service and notification are wired to Gecko's `MediaSession`: play, pause, previous, and next controls are present. Hardware `MEDIA_PAUSE` and `MEDIA_PLAY` were verified.
- The Gecko APK is ARM64-only for the Pixel and is about 188 MB debug size (the universal feasibility APK was about 504 MB).

## Verified tests

The public fixed test URL was `https://music.youtube.com/watch?v=dQw4w9WgXcQ`; no personalized content was inspected.

- Gecko build and lint passed after the AGP/Gradle upgrade.
- Extension marker `ytmnt-badge-v5` was present.
- Foreground playback advanced normally.
- Home/background: playback advanced from about 15.95 s to 34.31 s over 18 s.
- Screen-off/Doze: the phone reported `mWakefulness=Dozing`; playback advanced from about 34.3 s to 70.7 s over roughly 28 s and the audio player remained started.
- Native media controls: pause held the media clock, play resumed it.
- `node --check ytmnt.user.js` passed during the WebView work.
- The WebView implementation also has PiP, foreground playback service, persistent cookies, pause-respect logic, ad-skip debouncing, and a CDP helper; it is a useful fallback/reference but did not survive a controlled screen-off test.

## Build/install

From the repository root:

```sh
cd android
ANDROID_HOME=/home/beechan/.local/share/android-sdk ./gradlew :gecko:assembleDebug :gecko:lintDebug
adb install -r gecko/build/outputs/apk/debug/gecko-debug.apk
```

Launch a fixed test URL:

```sh
adb shell am start -W -n dev.hanenashi.ytmnt.gecko/.MainActivity \
  -d 'https://music.youtube.com/watch?v=dQw4w9WgXcQ'
```

For a debug-only Gecko inspection session:

```sh
adb forward tcp:9224 localabstract:dev.hanenashi.ytmnt.gecko/firefox-debugger-socket
node android/tools/gecko-eval.mjs 'JSON.stringify({href:location.href, paused:document.querySelector("video")?.paused, time:document.querySelector("video")?.currentTime})'
```

The helper is intentionally limited to numeric/player-state inspection; do not use it to dump account or personalized page content.

## Important caveats / next work

1. Gecko login has not yet been completed end-to-end. The WebView login was completed and persisted, but Gecko has a separate browser profile, so it will require a fresh sign-in. The Google profile-photo/identity flow should be tested next while the user is present.
2. The phone should be awake when first launching the app. Launching during the package-update/lock-screen transition produced a transient blank/about:blank page; a clean foreground relaunch loaded normally.
3. The app currently stays on the `dev.hanenashi.ytmnt.gecko` package. Once Gecko login and normal navigation are confirmed, decide whether to promote it into the main `dev.hanenashi.ytmnt` application ID or keep both packages for rollback.
4. The Gecko media notification currently uses title/artist metadata but no artwork. A later pass can map Gecko artwork to Android `MediaMetadata`.
5. Release signing, versioning, privacy text, and a proper user-facing error/retry screen are still needed before sharing the APK beyond this device.
6. The GeckoView dependency is `org.mozilla.geckoview:geckoview:155.0.20260903215306`; revisit the version and ABI packaging when making a release build.

## Key files

- `android/gecko/src/main/java/dev/hanenashi/ytmnt/gecko/MainActivity.java` — Gecko runtime, built-in extension, navigation policy, media delegate.
- `android/gecko/src/main/java/dev/hanenashi/ytmnt/gecko/PlaybackKeeperService.java` — foreground service and native notification controls.
- `android/gecko/src/main/java/dev/hanenashi/ytmnt/gecko/PlaybackGeckoController.java` — bridge from Android media buttons to Gecko media session.
- `android/gecko/src/main/assets/web_extensions/ytmnt/manifest.json` — built-in extension manifest.
- `android/tools/gecko-eval.mjs` — debug-only Firefox Remote Debugging Protocol evaluator.
- `ytmnt.user.js` — shared YTMNT script (currently version 5.10).
- `android/app/` — original WebView implementation and reference service/controller.

## Git state

The work should be committed and pushed from this branch as one handoff checkpoint. No external repository or deployment was changed beyond pushing this branch to `origin`.
