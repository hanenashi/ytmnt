# YTMNT Android prototype

This is a personal sideloading prototype that loads `https://music.youtube.com/`
in a restricted Android WebView and injects the repository's `ytmnt.user.js` at
document start.

The build copies `../ytmnt.user.js` into generated app assets, so there is only
one script to maintain. The Android app and standalone userscript are maintained
together on the repository's `main` branch.

## Build and install

Set the Android SDK path, then use the checked-in Gradle wrapper:

```sh
export ANDROID_HOME="$HOME/.local/share/android-sdk"
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Inspect runtime messages with:

```sh
adb logcat -s YTMNT chromium
```

For development builds, forward the WebView debugging socket and evaluate a
read-only browser expression with the included helper:

```sh
adb forward tcp:9223 localabstract:webview_devtools_remote_$(adb shell pidof dev.hanenashi.ytmnt)
node tools/cdp-eval.mjs "location.href"
```

The app deliberately does not expose an `addJavascriptInterface` bridge. Script
injection is restricted to the exact `https://music.youtube.com` origin.
