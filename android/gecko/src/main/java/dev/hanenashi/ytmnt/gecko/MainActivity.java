package dev.hanenashi.ytmnt.gecko;

import android.Manifest;
import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoPreferenceController;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.MediaSession;

public final class MainActivity extends Activity {
    private static final String TAG = "YTMNT-Gecko";
    private static final String HOME_URL = "https://music.youtube.com/";
    private static final String EXTENSION_LOCATION =
            "resource://android/assets/web_extensions/ytmnt/";
    private static final String EXTENSION_ID = "ytmnt@hanenashi.dev";

    private static GeckoRuntime runtime;

    private GeckoSession session;
    private GeckoView geckoView;
    private View rootView;
    private MediaSession activeMediaSession;
    private boolean canGoBack;

    @Override
    @SuppressLint("UnsafeOptInUsageError")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootView = findViewById(R.id.root);
        geckoView = findViewById(R.id.gecko_view);
        applySystemBarInsets();
        GeckoSessionSettings sessionSettings = new GeckoSessionSettings.Builder()
                .suspendMediaWhenInactive(false)
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
                .build();
        session = new GeckoSession(sessionSettings);
        session.setNavigationDelegate(createNavigationDelegate());
        session.setMediaSessionDelegate(createMediaSessionDelegate());

        GeckoRuntime geckoRuntime = getRuntime();
        session.open(geckoRuntime);
        geckoView.setSession(session);

        requestNotificationPermission();

        GeckoPreferenceController.setGeckoPref(
                        "network.manage-offline-status",
                        false,
                        GeckoPreferenceController.PREF_BRANCH_DEFAULT)
                .accept(
                        ignored -> installExtensionAndLoad(geckoRuntime),
                        error -> {
                            Log.w(TAG, "Could not override Gecko offline detection", error);
                            installExtensionAndLoad(geckoRuntime);
        });
    }

    private void applySystemBarInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rootView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return windowInsets;
            });
        } else {
            rootView.setFitsSystemWindows(true);
        }
    }

    private void installExtensionAndLoad(GeckoRuntime geckoRuntime) {
        geckoRuntime.getWebExtensionController()
                .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
                .accept(
                        extension -> {
                            Log.i(TAG, "YTMNT extension ready: " + extension.id);
                            session.loadUri(getStartUrl());
                        },
                        error -> {
                            Log.e(TAG, "Could not install YTMNT extension", error);
                            session.loadUri(getStartUrl());
                        });
    }

    private GeckoRuntime getRuntime() {
        if (runtime == null) {
            boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .remoteDebuggingEnabled(debuggable)
                    .build();
            runtime = GeckoRuntime.create(getApplicationContext(), settings);
        }
        return runtime;
    }

    private GeckoSession.NavigationDelegate createNavigationDelegate() {
        return new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession geckoSession, boolean value) {
                canGoBack = value;
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(
                    GeckoSession geckoSession,
                    GeckoSession.NavigationDelegate.LoadRequest request) {
                Uri uri = Uri.parse(request.uri);
                boolean allowed = "https".equalsIgnoreCase(uri.getScheme())
                        && isAllowedHost(uri.getHost());
                if (!allowed) {
                    Log.w(TAG, "Blocked top-level navigation to host: " + uri.getHost());
                }
                return GeckoResult.fromValue(allowed ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
            }
        };
    }

    private MediaSession.Delegate createMediaSessionDelegate() {
        return new MediaSession.Delegate() {
            @Override
            public void onActivated(GeckoSession geckoSession, MediaSession mediaSession) {
                activeMediaSession = mediaSession;
                PlaybackGeckoController.attach(mediaSession);
                startPlaybackService(PlaybackKeeperService.ACTION_MEDIA_ACTIVE);
            }

            @Override
            public void onDeactivated(GeckoSession geckoSession, MediaSession mediaSession) {
                PlaybackGeckoController.detach(mediaSession);
                if (activeMediaSession == mediaSession) activeMediaSession = null;
                stopService(new Intent(MainActivity.this, PlaybackKeeperService.class));
            }

            @Override
            public void onMetadata(
                    GeckoSession geckoSession,
                    MediaSession mediaSession,
                    MediaSession.Metadata metadata) {
                Intent intent = new Intent(MainActivity.this, PlaybackKeeperService.class)
                        .setAction(PlaybackKeeperService.ACTION_METADATA)
                        .putExtra(PlaybackKeeperService.EXTRA_TITLE, metadata.title)
                        .putExtra(PlaybackKeeperService.EXTRA_ARTIST, metadata.artist)
                        .putExtra(PlaybackKeeperService.EXTRA_ALBUM, metadata.album);
                startService(intent);
            }

            @Override
            public void onPlay(GeckoSession geckoSession, MediaSession mediaSession) {
                startPlaybackService(PlaybackKeeperService.ACTION_STATE_PLAYING);
            }

            @Override
            public void onPause(GeckoSession geckoSession, MediaSession mediaSession) {
                startPlaybackService(PlaybackKeeperService.ACTION_STATE_PAUSED);
            }

            @Override
            public void onStop(GeckoSession geckoSession, MediaSession mediaSession) {
                startPlaybackService(PlaybackKeeperService.ACTION_STATE_PAUSED);
            }
        };
    }

    private void startPlaybackService(String action) {
        Intent intent = new Intent(this, PlaybackKeeperService.class).setAction(action);
        startForegroundService(intent);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 208);
        }
    }

    private String getStartUrl() {
        Uri data = getIntent().getData();
        if (data != null
                && "https".equalsIgnoreCase(data.getScheme())
                && "music.youtube.com".equalsIgnoreCase(data.getHost())) {
            return data.toString();
        }
        return HOME_URL;
    }

    private static boolean isAllowedHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("youtube.com")
                || normalized.endsWith(".youtube.com")
                || normalized.equals("google.com")
                || normalized.endsWith(".google.com")
                || normalized.equals("google.co.jp")
                || normalized.endsWith(".google.co.jp");
    }

    @Override
    public void onBackPressed() {
        if (canGoBack) session.goBack();
        else finish();
    }

    @Override
    protected void onDestroy() {
        if (activeMediaSession != null) PlaybackGeckoController.detach(activeMediaSession);
        geckoView.releaseSession();
        session.close();
        super.onDestroy();
    }
}
