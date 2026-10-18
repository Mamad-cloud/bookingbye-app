package com.bookingbye.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.widget.Toast;
import com.getcapacitor.PluginConfig;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which addresses the app shows itself, and which it hands to the phone.
 *
 * Shown in the app:
 *   - bookingbye.com (plugins.AppShell.appHosts in capacitor.config.js);
 *   - the card payment pages the checkout sends the customer to (paymentHosts);
 *   - while one of those, or any page that is not bookingbye.com, is showing:
 *     whatever it moves on to by itself (a redirect, a script, a form), such
 *     as a bank's 3-D Secure check, since the banks cannot be listed;
 *   - frames inside a page (maps, chat, 3-D Secure frames), wherever they are from.
 *
 * A form posted to another site (the checkout posting to a gateway) loads
 * in the app too: Android does not ask about posts, and a browser could not
 * be given one anyway.
 *
 * Handed to the phone: every other website, links that open a new window
 * (target="_blank", window.open) unless they lead to one of the hosts above,
 * and every other kind of address: tel:, mailto:, sms:, intent:, the FIB and
 * bank apps' own schemes.
 *
 * The iOS app applies the same rules (ios/App/App/Navigation.swift); keep the
 * two in step.
 */
final class Navigation {

    private final Activity activity;
    private final List<String> appHosts;
    private final List<String> paymentHosts;

    /**
     * The page the next navigation comes from: the last main-frame address
     * let through or started. Asking the webview instead is unreliable in the
     * middle of a redirect, which is exactly when a payment needs it.
     */
    private String lastPage;

    Navigation(Activity activity, PluginConfig config) {
        this.activity = activity;
        this.appHosts = hosts(config.getArray("appHosts", new String[0]));
        this.paymentHosts = hosts(config.getArray("paymentHosts", new String[0]));
    }

    /**
     * Decides a navigation in `webView`. Returns true when the webview must
     * not load `url` itself, because it went to the phone or was redirected.
     *
     * @param mainFrame false for a frame inside the page
     * @param tapped    the customer tapped a link, as opposed to a redirect
     *                  or the page moving on by itself
     */
    boolean override(WebView webView, Uri url, boolean mainFrame, boolean tapped) {
        String scheme = lower(url.getScheme());
        if (scheme == null) return true;
        if (isInert(scheme)) return false;
        if (!isWeb(scheme)) {
            // Only a tap or the page itself may open another app; a frame
            // (an advert, say) may not do it on its own.
            if (mainFrame || tapped) openOutside(url);
            return true;
        }
        if (!mainFrame) return false;

        if (staysInApp(url, lastPage != null ? lastPage : webView.getUrl(), tapped)) {
            if ("http".equals(scheme)) {
                webView.loadUrl(https(url));
                return true;
            }
            lastPage = url.toString();
            return false;
        }
        openOutside(url);
        return true;
    }

    /** A main-frame page started loading, however it was started. */
    void pageStarted(String url) {
        lastPage = url;
    }

    /** A page asked for a new window (target="_blank", window.open) on `url`. */
    void openWindow(WebView webView, Uri url) {
        String scheme = lower(url.getScheme());
        if (scheme == null || isInert(scheme)) return;
        if (isWeb(scheme) && staysInApp(url, null, true)) {
            webView.loadUrl(https(url));
        } else {
            openOutside(url);
        }
    }

    private boolean staysInApp(Uri url, String currentUrl, boolean tapped) {
        String host = url.getHost();
        if (matches(host, appHosts) || matches(host, paymentHosts)) return true;

        // In a payment: the page showing is a gateway's (or a bank's), and it
        // is moving on by itself, over https.
        Uri current = currentUrl == null ? null : Uri.parse(currentUrl);
        boolean inPayment =
            current != null && "https".equals(lower(current.getScheme())) && current.getHost() != null && !matches(current.getHost(), appHosts);
        return inPayment && !tapped && "https".equals(lower(url.getScheme()));
    }

    /** Opens `url` in whatever the phone uses for it: the browser, the dialer, another app. */
    void openOutside(Uri url) {
        if ("intent".equals(lower(url.getScheme()))) {
            openIntentUrl(url);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, url);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            sayNoApp();
        }
    }

    /**
     * intent:// addresses, which name an Android app directly. Opened the way
     * Chrome opens them: only as a link (never a particular screen inside an
     * app), then the page's own fallback address, then the app's Play page.
     */
    private void openIntentUrl(Uri url) {
        Intent intent;
        try {
            intent = Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            return;
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setComponent(null);
        intent.setSelector(null);
        try {
            activity.startActivity(intent);
            return;
        } catch (ActivityNotFoundException ignored) {}

        String fallback = intent.getStringExtra("browser_fallback_url");
        if (fallback != null && isWeb(lower(Uri.parse(fallback).getScheme()))) {
            openOutside(Uri.parse(fallback));
            return;
        }
        String appPackage = intent.getPackage();
        if (appPackage != null) {
            openOutside(Uri.parse("market://details?id=" + Uri.encode(appPackage)));
            return;
        }
        sayNoApp();
    }

    private void sayNoApp() {
        Toast.makeText(activity, R.string.no_app_for_link, Toast.LENGTH_LONG).show();
    }

    // ---- helpers ----

    /** Addresses that load nothing from anywhere, which the webview handles itself. */
    private static boolean isInert(String scheme) {
        return "about".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme) || "javascript".equals(scheme);
    }

    private static boolean isWeb(String scheme) {
        return "https".equals(scheme) || "http".equals(scheme);
    }

    static String https(Uri url) {
        return url.buildUpon().scheme("https").build().toString();
    }

    /** True when `host` is one of `hosts` or a subdomain of one. */
    static boolean matches(String host, List<String> hosts) {
        if (host == null) return false;
        host = lower(host);
        for (String candidate : hosts) {
            if (host.equals(candidate) || host.endsWith("." + candidate)) return true;
        }
        return false;
    }

    static List<String> hosts(String[] values) {
        List<String> list = new ArrayList<>();
        for (String value : values) list.add(lower(value.trim()));
        return list;
    }

    static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
