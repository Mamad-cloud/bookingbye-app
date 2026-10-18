package com.bookingbye.app;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

/**
 * Capacitor's webview client, plus:
 *
 *   where pages open   decided by Navigation. Capacitor's own decision only
 *                      sees the address; this one also knows whether it was
 *                      a tap, a redirect or a frame.
 *   offline            a page that cannot be loaded for want of a connection
 *                      is replaced by the bundled offline page (ShellPages).
 */
class ShellWebViewClient extends BridgeWebViewClient {

    private final Navigation navigation;
    private final ShellPages pages;

    ShellWebViewClient(Bridge bridge, Navigation navigation, ShellPages pages) {
        super(bridge);
        this.navigation = navigation;
        this.pages = pages;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri url = request.getUrl();
        // Capacitor's HTTP proxy path, never a page (as in Bridge.launchIntent).
        String path = url.getPath();
        if (path != null && path.startsWith(Bridge.CAPACITOR_HTTP_INTERCEPTOR_START)) return true;

        boolean tapped = request.hasGesture() && !request.isRedirect();
        return navigation.override(view, url, request.isForMainFrame(), tapped);
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        navigation.pageStarted(url);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (request.isForMainFrame() && ShellPages.isConnectionError(error.getErrorCode())) {
            pages.showOffline(view, request.getUrl().toString());
        }
    }
}
