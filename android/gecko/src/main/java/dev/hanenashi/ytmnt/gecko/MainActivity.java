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
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.os.SystemClock;
import org.json.JSONObject;

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
    private static final String UBLOCK_ID = "uBlock0@raymondhill.net";
    private static final String UBLOCK_XPI =
            "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi";

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
        geckoRuntime.getWebExtensionController().setPromptDelegate(
                new org.mozilla.geckoview.WebExtensionController.PromptDelegate() {
                    @Override
                    public GeckoResult<org.mozilla.geckoview.WebExtension.PermissionPromptResponse>
                    onInstallPromptRequest(org.mozilla.geckoview.WebExtension extension,
                            String[] permissions, String[] origins, String[] dataCollection) {
                        return GeckoResult.fromValue(
                                new org.mozilla.geckoview.WebExtension.PermissionPromptResponse(
                                        true, true, true));
                    }
                });
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
                            runOnUiThread(() -> extension.setMessageDelegate(
                                    new org.mozilla.geckoview.WebExtension.MessageDelegate() {
                                        @Override
                                        public GeckoResult<Object> onMessage(
                                                String nativeApp,
                                                Object message,
                                                org.mozilla.geckoview.WebExtension.MessageSender sender) {
                                            if (!(message instanceof JSONObject)) return null;
                                            JSONObject json = (JSONObject) message;
                                            Log.d(TAG, "Extension message: " + json);
                                            if (!"ad-tap".equals(json.optString("type"))) return null;
                                            dispatchAdTap(json.optDouble("x", Double.NaN),
                                                    json.optDouble("y", Double.NaN),
                                                    json.optDouble("dpr", 1.0));
                                            return null;
                                        }
                                    }, "browser"));
                            installUblockThenLoad(geckoRuntime);
                        },
                        error -> {
                            Log.e(TAG, "Could not install YTMNT extension", error);
                            installUblockThenLoad(geckoRuntime);
                        });
    }

    private void installUblockThenLoad(GeckoRuntime geckoRuntime) {
        geckoRuntime.getWebExtensionController().list().accept(
                extensions -> {
                    boolean installed = extensions.stream()
                            .anyMatch(extension -> UBLOCK_ID.equals(extension.id));
                    if (installed) {
                        Log.i(TAG, "uBlock Origin already installed");
                        session.loadUri(getStartUrl());
                        return;
                    }
                    Log.i(TAG, "Installing uBlock Origin from AMO");
                    runOnUiThread(() -> geckoRuntime.getWebExtensionController()
                            .install(UBLOCK_XPI, org.mozilla.geckoview.WebExtensionController
                                    .INSTALLATION_METHOD_ONBOARDING)
                            .accept(
                                    extension -> {
                                        Log.i(TAG, "uBlock Origin installed: " + extension.id);
                                        session.loadUri(getStartUrl());
                                    },
                                    error -> {
                                        Log.w(TAG, "Could not install uBlock Origin; continuing", error);
                                        session.loadUri(getStartUrl());
                                    }));
                },
                error -> {
                    Log.w(TAG, "Could not inspect extensions; continuing", error);
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

    private void dispatchAdTap(double cssX, double cssY, double dpr) {
        if (!Double.isFinite(cssX) || !Double.isFinite(cssY) || !Double.isFinite(dpr)
                || dpr <= 0 || dpr > 8) return;
        float x = (float) (cssX * dpr);
        float y = (float) (cssY * dpr);
        long now = SystemClock.uptimeMillis();
        geckoView.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
        geckoView.onTouchEvent(MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0));
        Log.d(TAG, "Dispatched native ad tap at " + x + "," + y);
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
