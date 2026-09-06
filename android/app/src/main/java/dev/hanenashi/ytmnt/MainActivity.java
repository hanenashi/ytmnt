package dev.hanenashi.ytmnt;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Insets;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public final class MainActivity extends Activity {
    private static final String TAG = "YTMNT";
    private static final String HOME_URL = "https://music.youtube.com/";
    private static final long BACKGROUND_WATCHDOG_INTERVAL_MS = 2_000;

    private View rootView;
    private WebView webView;
    private String ytmntScript;
    private String injectedScript;
    private boolean documentStartInjectionAvailable;
    private final Handler backgroundHandler = new Handler(Looper.getMainLooper());
    private final Runnable backgroundPlaybackWatchdog = new Runnable() {
        @Override
        public void run() {
            if (webView == null) return;
            webView.evaluateJavascript(
                    "window.__YTMNT?.ensurePlaying?.()",
                    null);
            backgroundHandler.postDelayed(this, BACKGROUND_WATCHDOG_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootView = findViewById(R.id.root);
        webView = findViewById(R.id.web_view);
        PlaybackWebViewController.attach(webView);
        applySystemBarInsets();
        ytmntScript = readAsset("ytmnt.user.js");
        injectedScript = "window.__YTMNT_ANDROID_HOST = true;\n" + ytmntScript;
        configureWebView();
        configureInjection();
        configurePictureInPicture();
        requestNotificationPermission();

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(HOME_URL);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void configurePictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            webView.post(() -> {
                android.graphics.Rect sourceRect = new android.graphics.Rect();
                webView.getGlobalVisibleRect(sourceRect);
                setPictureInPictureParams(new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9))
                        .setAutoEnterEnabled(true)
                        .setSeamlessResizeEnabled(true)
                        .setSourceRectHint(sourceRect)
                        .build());
            });
        }
    }

    @Override
    public void onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                && !isInPictureInPictureMode()) {
            enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build());
        }
        super.onUserLeaveHint();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
                                              Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        Log.i(TAG, "Picture-in-picture: " + isInPictureInPictureMode);
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

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Log.d(TAG, message.message() + " @" + message.lineNumber());
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.i(TAG, "Loading " + redactUrl(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!documentStartInjectionAvailable && isMusicOrigin(Uri.parse(url))) {
                    view.evaluateJavascript(injectedScript, null);
                }
                if (isMusicOrigin(Uri.parse(url))) {
                    view.evaluateJavascript(
                            "Boolean(document.getElementById('ytmnt-badge-v5'))",
                            value -> Log.i(TAG, "Injection marker present: " + value));
                }
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isAllowedTopLevelNavigation(uri)) {
                    return false;
                }
                if (request.isForMainFrame()) {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.e(TAG, "Rejected TLS error for " + redactUrl(error.getUrl()));
                handler.cancel();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.e(TAG, "WebView renderer exited; recreating Activity. Crashed: "
                        + detail.didCrash());
                backgroundHandler.removeCallbacks(backgroundPlaybackWatchdog);
                stopService(new Intent(MainActivity.this, PlaybackKeeperService.class));
                PlaybackWebViewController.detach(view);
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null) parent.removeView(view);
                view.destroy();
                webView = null;
                recreate();
                return true;
            }
        });

        webView.setOnLongClickListener(view -> false);
        webView.setKeepScreenOn(false);
    }

    private void configureInjection() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            documentStartInjectionAvailable = true;
            WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    injectedScript,
                    Collections.singleton("https://music.youtube.com"));
            Log.i(TAG, "Installed document-start injection");
        } else {
            documentStartInjectionAvailable = false;
            Log.w(TAG, "Document-start injection unavailable; using page-finished fallback");
        }
    }

    private static boolean isMusicOrigin(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && "music.youtube.com".equalsIgnoreCase(uri.getHost());
    }

    private static boolean isAllowedTopLevelNavigation(Uri uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        return host.equals("youtube.com")
                || host.endsWith(".youtube.com")
                || host.equals("google.com")
                || host.endsWith(".google.com")
                || host.equals("google.co.jp")
                || host.endsWith(".google.co.jp");
    }

    private static String redactUrl(String url) {
        Uri uri = Uri.parse(url);
        return uri.getScheme() + "://" + uri.getHost() + uri.getPath();
    }

    private String readAsset(String name) {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException error) {
            throw new IllegalStateException("Could not load bundled " + name, error);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        stopService(new Intent(this, PlaybackKeeperService.class));
        backgroundHandler.removeCallbacks(backgroundPlaybackWatchdog);
        if (webView != null) {
            webView.evaluateJavascript("window.__YTMNT?.setAppBackgrounded?.(false)", null);
        }
    }

    @Override
    protected void onPause() {
        Intent keeperIntent = new Intent(this, PlaybackKeeperService.class);
        startForegroundService(keeperIntent);
        if (webView != null) {
            webView.evaluateJavascript(
                    "Boolean(window.__YTMNT?.setAppBackgrounded?.(true)?.resumeInBackground)",
                    shouldKeepPlaying -> {
                        if (!"true".equals(shouldKeepPlaying)) {
                            stopService(new Intent(this, PlaybackKeeperService.class));
                        }
                    });
            backgroundHandler.removeCallbacks(backgroundPlaybackWatchdog);
            backgroundHandler.postDelayed(backgroundPlaybackWatchdog, 500);
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        backgroundHandler.removeCallbacks(backgroundPlaybackWatchdog);
        if (webView != null) PlaybackWebViewController.detach(webView);
        if (isFinishing() && webView != null) {
            stopService(new Intent(this, PlaybackKeeperService.class));
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }
}
