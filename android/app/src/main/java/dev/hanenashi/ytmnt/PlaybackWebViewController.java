package dev.hanenashi.ytmnt;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

final class PlaybackWebViewController {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static WeakReference<WebView> webViewReference = new WeakReference<>(null);

    private PlaybackWebViewController() {}

    static void attach(WebView webView) {
        webViewReference = new WeakReference<>(webView);
    }

    static void detach(WebView webView) {
        if (webViewReference.get() == webView) webViewReference.clear();
    }

    static void play() {
        evaluate("window.__YTMNT?.playByUser?.()");
    }

    static void pause() {
        evaluate("window.__YTMNT?.pauseByUser?.()");
    }

    private static void evaluate(String script) {
        MAIN_HANDLER.post(() -> {
            WebView webView = webViewReference.get();
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }
}

