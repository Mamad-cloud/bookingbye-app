package com.bookingbye.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.webkit.WebView;
import com.getcapacitor.PluginConfig;
import java.util.List;

/**
 * Links that open the app from outside it.
 *
 *   bookingbye://auth?token=...   the end of Google or Facebook sign-in, which
 *                                 runs in the phone's browser (see the website's
 *                                 app/app-auth). The browser is closed by the
 *                                 app coming back to the front (MainActivity is
 *                                 singleTask, which clears the Custom Tab above
 *                                 it), and /oauth/success signs the webview in.
 *   https://bookingbye.com/...    App Links (the intent filter in
 *                                 AndroidManifest.xml): the page opens in the app,
 *                                 except the paths kept in the browser.
 */
final class IncomingLinks {

    /** The URL scheme the website sends sign-in back to (APP_URL_SCHEME there). */
    static final String APP_SCHEME = "bookingbye";

    /**
     * How long after the app opened the phone's browser a sign-in may come
     * back. The website gives its side of a sign-in the same 15 minutes.
     */
    private static final long SIGN_IN_WINDOW_MS = 15 * 60 * 1000;

    private static final String PREFS = "app_shell";
    private static final String SIGN_IN_STARTED_AT = "sign_in_started_at";

    private final Activity activity;
    private final Navigation navigation;
    private final List<String> appHosts;
    private final List<String> keptInBrowser;

    IncomingLinks(Activity activity, Navigation navigation, PluginConfig config) {
        this.activity = activity;
        this.navigation = navigation;
        this.appHosts = Navigation.hosts(config.getArray("appHosts", new String[0]));
        this.keptInBrowser = Navigation.hosts(config.getArray("keptInBrowser", new String[0]));
    }

    /**
     * The website opened the phone's browser (the Browser plugin), which it
     * does to sign in. Kept on disk: Android may stop the app while the
     * customer is in the browser.
     */
    void browserOpened() {
        prefs().edit().putLong(SIGN_IN_STARTED_AT, System.currentTimeMillis()).apply();
    }

    /** Opens `link` in `webView`, or wherever it belongs. Returns false when it is not one of ours. */
    boolean open(WebView webView, Uri link, String serverUrl) {
        if (link == null) return false;
        String scheme = Navigation.lower(link.getScheme());

        if (APP_SCHEME.equals(scheme)) {
            if ("auth".equals(Navigation.lower(link.getHost()))) finishSignIn(webView, link, serverUrl);
            return true;
        }
        if (("https".equals(scheme) || "http".equals(scheme)) && Navigation.matches(link.getHost(), appHosts)) {
            if (isKeptInBrowser(link.getPath())) {
                openInBrowser(link);
            } else {
                webView.loadUrl(Navigation.https(link));
            }
            return true;
        }
        return false;
    }

    private void finishSignIn(WebView webView, Uri link, String serverUrl) {
        String token = link.getQueryParameter("token");
        if (token == null || token.isEmpty()) return;

        // Any website or app can open a bookingbye:// address. Taking a token
        // from one this app did not ask for would sign the customer in as
        // someone else, so only a sign-in this app started counts, once.
        long startedAt = prefs().getLong(SIGN_IN_STARTED_AT, 0);
        long elapsed = System.currentTimeMillis() - startedAt;
        if (startedAt == 0 || elapsed < 0 || elapsed > SIGN_IN_WINDOW_MS) return;
        prefs().edit().remove(SIGN_IN_STARTED_AT).apply();

        Uri success = Uri.parse(serverUrl).buildUpon().path("/oauth/success").clearQuery().appendQueryParameter("token", token).build();
        webView.loadUrl(success.toString());
    }

    private boolean isKeptInBrowser(String path) {
        if (path == null) return false;
        path = Navigation.lower(path);
        for (String prefix : keptInBrowser) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Opens a bookingbye.com address in a browser by name. Left to Android,
     * it could come straight back to this app, whose App Link it is.
     */
    private void openInBrowser(Uri url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, url);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        String browser = browserPackage();
        if (browser != null) intent.setPackage(browser);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            navigation.openOutside(url);
        }
    }

    /** The phone's default browser, or failing that any browser but this app. */
    private String browserPackage() {
        PackageManager pm = activity.getPackageManager();
        Intent anyPage = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"));
        anyPage.addCategory(Intent.CATEGORY_BROWSABLE);

        ResolveInfo preferred = pm.resolveActivity(anyPage, PackageManager.MATCH_DEFAULT_ONLY);
        if (preferred != null && isOtherApp(preferred.activityInfo.packageName)) return preferred.activityInfo.packageName;
        for (ResolveInfo candidate : pm.queryIntentActivities(anyPage, 0)) {
            if (isOtherApp(candidate.activityInfo.packageName)) return candidate.activityInfo.packageName;
        }
        return null;
    }

    /** Not this app, and not Android's "open with" chooser. */
    private boolean isOtherApp(String packageName) {
        return packageName != null && !"android".equals(packageName) && !packageName.equals(activity.getPackageName());
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
