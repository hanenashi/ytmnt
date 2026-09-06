package dev.hanenashi.ytmnt.gecko;

import android.os.Handler;
import android.os.Looper;

import org.mozilla.geckoview.MediaSession;

import java.lang.ref.WeakReference;

final class PlaybackGeckoController {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static WeakReference<MediaSession> mediaSessionReference = new WeakReference<>(null);

    private PlaybackGeckoController() {}

    static void attach(MediaSession mediaSession) {
        mediaSessionReference = new WeakReference<>(mediaSession);
    }

    static void detach(MediaSession mediaSession) {
        if (mediaSessionReference.get() == mediaSession) mediaSessionReference.clear();
    }

    static void play() {
        withMediaSession(MediaSession::play);
    }

    static void pause() {
        withMediaSession(MediaSession::pause);
    }

    static void next() {
        withMediaSession(MediaSession::nextTrack);
    }

    static void previous() {
        withMediaSession(MediaSession::previousTrack);
    }

    private static void withMediaSession(java.util.function.Consumer<MediaSession> action) {
        MAIN_HANDLER.post(() -> {
            MediaSession mediaSession = mediaSessionReference.get();
            if (mediaSession != null && mediaSession.isActive()) action.accept(mediaSession);
        });
    }
}
