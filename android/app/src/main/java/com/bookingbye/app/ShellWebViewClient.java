package com.bookingbye.app;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

/**
 * Capacitor's webview client, with the decision on where each page opens
 * taken over by Navigation. Capacitor's own decision only sees the address;
 * this one also knows whether it was a tap, a redirect or a frame.
 */
class ShellWebViewClient extends BridgeWebViewClient {

    private final Navigation navigation;

    ShellWebViewClient(Bridge bridge, Navigation navigation) {
        super(bridge);
        this.navigation = navigation;
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
}
