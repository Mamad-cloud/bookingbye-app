package com.bookingbye.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import androidx.core.splashscreen.SplashScreen;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {

    /** The longest the splash screen waits for the website, in milliseconds. */
    private static final long SPLASH_TIMEOUT_MS = 8000;

    private volatile boolean firstPageShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The splash screen (see AppTheme.NoActionBarLaunch) stays up until
        // the website has something to show, rather than giving way to an
        // empty white webview while the first page downloads.
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        splash.setKeepOnScreenCondition(() -> !firstPageShown);
        splash.setOnExitAnimationListener(view ->
            view.getView().animate().alpha(0f).setDuration(200).withEndAction(view::remove).start()
        );

        super.onCreate(savedInstanceState);

        // No bridge means no usable WebView on this phone; Capacitor has put
        // up its own message saying so.
        if (bridge == null) {
            firstPageShown = true;
            return;
        }

        bridge.addWebViewListener(
            new WebViewListener() {
                @Override
                public void onPageCommitVisible(WebView webView, String url) {
                    firstPageShown = true;
                }

                @Override
                public void onPageLoaded(WebView webView) {
                    firstPageShown = true;
                }

                @Override
                public void onReceivedError(WebView webView) {
                    firstPageShown = true;
                }
            }
        );
        new Handler(Looper.getMainLooper()).postDelayed(() -> firstPageShown = true, SPLASH_TIMEOUT_MS);
    }
}
