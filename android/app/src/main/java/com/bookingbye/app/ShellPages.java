package com.bookingbye.app;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.getcapacitor.Bridge;
import com.getcapacitor.Logger;
import com.getcapacitor.WebViewListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.json.JSONObject;

/**
 * The two pages the app bundles (www/, copied to assets/public by cap sync):
 *
 *   shell.js       the shell's script, run in every page of the website;
 *   offline.html   shown in place of a page that could not be loaded.
 *
 * The iOS app does the same (ShellViewController, OfflinePage.swift).
 */
final class ShellPages {

    private static final String SHELL_SCRIPT = "public/shell.js";
    private static final String OFFLINE_PAGE = "public/offline.html";
    /** Where offline.html takes the address of the page that failed. */
    private static final String FAILED_URL = "\"__BOOKINGBYE_FAILED_URL__\"";

    private final Context context;
    /** https://bookingbye.com, the origin of server.url. */
    private final String origin;

    ShellPages(Context context, String serverUrl) {
        this.context = context;
        Uri server = Uri.parse(serverUrl);
        this.origin = server.getScheme() + "://" + server.getAuthority();
    }

    /** Runs shell.js in every page of the website, after Capacitor's own script. */
    void installScript(Bridge bridge) {
        String script = read(SHELL_SCRIPT);
        if (script == null) return;
        WebView webView = bridge.getWebView();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, Collections.singleton(origin));
            return;
        }
        // Older WebViews: once each page has loaded.
        bridge.addWebViewListener(
            new WebViewListener() {
                @Override
                public void onPageLoaded(WebView view) {
                    String url = view.getUrl();
                    if (url != null && (url.equals(origin) || url.startsWith(origin + "/"))) {
                        view.evaluateJavascript(script, null);
                    }
                }
            }
        );
    }

    /**
     * Whether a page that failed with this error gets the offline page: when
     * the phone has no connection or the server cannot be reached. Not for
     * a load that was cancelled or replaced (ERROR_UNKNOWN covers those),
     * and never for an HTTP error page, which the site draws itself.
     */
    static boolean isConnectionError(int errorCode) {
        switch (errorCode) {
            case WebViewClient.ERROR_HOST_LOOKUP:
            case WebViewClient.ERROR_CONNECT:
            case WebViewClient.ERROR_TIMEOUT:
            case WebViewClient.ERROR_IO:
                return true;
            default:
                return false;
        }
    }

    /**
     * Shows the offline page in place of `failedUrl`. It is loaded as text at
     * the website's address, so it can read the site's language and theme
     * and its "Try again" stays in the app.
     */
    void showOffline(WebView webView, String failedUrl) {
        String page = read(OFFLINE_PAGE);
        if (page == null) return;
        // JSONObject.quote also escapes "/", so the address cannot end the script.
        page = page.replace(FAILED_URL, JSONObject.quote(failedUrl));
        webView.loadDataWithBaseURL(origin + "/", page, "text/html", "utf-8", failedUrl);
    }

    private String read(String asset) {
        try (InputStream in = context.getAssets().open(asset)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            Logger.error("AppShell", "Missing " + asset + "; run npx cap sync", e);
            return null;
        }
    }
}
