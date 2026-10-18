package com.bookingbye.app;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import androidx.core.splashscreen.SplashScreen;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.PluginConfig;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {

    /** The longest the splash screen waits for the website, in milliseconds. */
    private static final long SPLASH_TIMEOUT_MS = 8000;

    /** The Browser plugin's screen, which the website opens to sign in. */
    private static final String BROWSER_ACTIVITY = "com.capacitorjs.plugins.browser.BrowserControllerActivity";

    private volatile boolean firstPageShown = false;

    /** Set when Android is bringing back an activity it had closed. */
    private boolean restoring = false;

    private Navigation navigation;
    private IncomingLinks incomingLinks;

    private final Application.ActivityLifecycleCallbacks browserWatcher = new LifecycleAdapter() {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            if (BROWSER_ACTIVITY.equals(activity.getClass().getName()) && bridge != null) {
                incomingLinks().browserOpened();
            }
        }
    };

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

        restoring = savedInstanceState != null;
        // Opens the link the app was started with (onNewIntent, below).
        super.onCreate(savedInstanceState);
        restoring = false;

        // No bridge means no usable WebView on this phone; Capacitor has put
        // up its own message saying so.
        if (bridge == null) {
            firstPageShown = true;
            return;
        }

        // Where pages open (Navigation), including new windows. The bridge
        // itself only answers https://bookingbye.com: Capacitor injects it
        // into, and takes messages from, the server.url origin alone, since
        // capacitor.config.js has no allowNavigation.
        WebView webView = bridge.getWebView();
        ShellPages pages = new ShellPages(this, bridge.getServerUrl());
        bridge.setWebViewClient(new ShellWebViewClient(bridge, navigation(), pages));
        pages.installScript(bridge);
        webView.getSettings().setSupportMultipleWindows(true);
        webView.setWebChromeClient(new ShellChromeClient(bridge, navigation()));
        getApplication().registerActivityLifecycleCallbacks(browserWatcher);

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

    /**
     * A link opened the app: sign-in coming back (bookingbye://auth) or an
     * App Link to bookingbye.com. Capacitor also calls this with the intent
     * the app was started with, from inside super.onCreate.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (bridge == null || intent == null || intent.getData() == null) return;
        // A link already opened before Android closed the activity.
        if (restoring && intent == getIntent()) return;

        if (incomingLinks().open(bridge.getWebView(), intent.getData(), bridge.getServerUrl())) {
            intent.setData(null);
        }
    }

    @Override
    public void onDestroy() {
        getApplication().unregisterActivityLifecycleCallbacks(browserWatcher);
        super.onDestroy();
    }

    private PluginConfig shellConfig() {
        return bridge.getConfig().getPluginConfiguration("AppShell");
    }

    private Navigation navigation() {
        if (navigation == null) navigation = new Navigation(this, shellConfig());
        return navigation;
    }

    private IncomingLinks incomingLinks() {
        if (incomingLinks == null) incomingLinks = new IncomingLinks(this, navigation(), shellConfig());
        return incomingLinks;
    }

    /** Application.ActivityLifecycleCallbacks with nothing to do by default. */
    private abstract static class LifecycleAdapter implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityStarted(Activity activity) {}

        @Override
        public void onActivityResumed(Activity activity) {}

        @Override
        public void onActivityPaused(Activity activity) {}

        @Override
        public void onActivityStopped(Activity activity) {}

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

        @Override
        public void onActivityDestroyed(Activity activity) {}
    }
}
