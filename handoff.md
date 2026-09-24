# YTMNT handoff: remaining script concerns

Date: 2026-09-24
Reviewed baseline: `55b6339b11ca30b390049e095a46d25df5a34761` on `main`
Shared script: `ytmnt.user.js`, version 5.10

## Scope

This replaces the earlier Android implementation handoff with the four findings from the script review. These are source-review findings, not newly reproduced device failures. No code was changed or fresh playback/battery tests performed during this review.

GeckoView is the current Android direction. The root userscript remains shared between the standalone browser installation and the Android build. Keep both uses in mind when fixing it.

## 1. Pause handling — partially fixed, highest priority

Version 5.10 improves pause detection: `recordPlaybackControlInteraction` recognises playback controls, and `isRecentPlaybackControlAction` uses a 1.5-second window instead of treating every recent interaction as pause intent.

The remaining weakness is in `Logic.handlePlay`: while `STATE.userPaused` is true, any recent trusted interaction accepted by `recordInteraction` lets a subsequent `play()` clear that state. An unrelated click does not itself start playback, but a page-triggered `play()` within 1.5 seconds of that click can override an intentional pause.

`Logic.handlePause` also still intercepts the media-element prototype globally and suppresses pauses outside its recognised conditions. The visible-ad-skip exception improves ad-to-track transitions, but the policy still relies on timing and UI heuristics.

Suggested next work:

- Keep intentional pause latched until an explicit playback action: play, a deliberate track selection, or native media play.
- Do not let unrelated navigation, badge interactions, or typing clear pause intent.
- Keep background recovery conditional on playback having been active and not intentionally paused.
- Preserve legitimate internal player transitions, including ad handoff; avoid making a blanket pause-blocking rule stricter.

Focused verification: pause, interact with unrelated UI, and confirm automatic playback attempts remain blocked; then confirm explicit play, track selection, native media controls, and ad-to-track transitions still work.

## 2. Audio mode — unchanged; not enforced audio-only streaming

`Logic.applyMode` hides the video with CSS and requests `tiny` quality through `setPlaybackQualityRange`. This does not remove the video stream or establish that only audio is being downloaded. The quality request is also not proof that the player honoured it.

Suggested next work:

- Describe the current mode accurately as hidden video with a lowest-quality request. The toast still says `Audio Only`.
- Avoid guaranteed bandwidth or battery-saving claims without measurement.
- Treat actual audio-only delivery as a separate investigation, not something CSS hiding already achieves.

No need to redesign streaming just to resolve the misleading wording.

## 3. AudioContext background trick — unchanged in script; Android support is separate

`fixAudioContext` creates an AudioContext with no connected sound source and periodically calls `resume()` when suspended. This is not a guarantee of background survival, and a timer cannot revive an Android-killed browser process.

The Android app adds real host support through Gecko's `suspendMediaWhenInactive(false)` and a native foreground media service. The previous handoff recorded successful background, screen-off, and native pause/play checks; those are historical reports, not tests repeated in this review. The current README has already removed the earlier exaggerated wake-up claims.

Suggested next work:

- Evaluate the standalone script workaround separately from Gecko's native background support.
- Retain or remove the AudioContext workaround based on a focused device check, without assuming it is what keeps Gecko playback alive.
- Keep documentation clear about the difference between a browser workaround and Android host support.

## 4. Badge persistence loop — unchanged; second implementation priority

`UI.persistenceLoop` schedules itself continuously with `requestAnimationFrame`. It checks for the badge every animation frame and may move it to the end of the document. This is unnecessary continuous housekeeping when the badge has not changed. Actual battery impact has not been measured; do not claim a quantified drain.

Suggested next work:

- Replace the frame loop with a narrowly scoped MutationObserver or a modest periodic fallback.
- Coalesce checks and avoid an observer reacting endlessly to its own DOM changes.
- Preserve badge recovery after YouTube Music navigation, drag behaviour, and resize/rotation bounds.

Focused verification: navigate/search within YouTube Music, confirm a removed badge is restored, and check drag plus rotation once. No broad test suite is needed for this small UI maintenance change.

## Recommended order

1. Tighten explicit pause/resume intent.
2. Replace continuous badge polling.
3. Correct audio-mode wording.
4. Assess the AudioContext workaround separately on the actual device.

Use targeted checks for the changed behaviour. Stan will handle real-world playback testing; do not turn this cleanup into an extensive testing or streaming-rewrite project.
