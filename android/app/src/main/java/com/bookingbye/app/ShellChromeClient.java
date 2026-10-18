package com.bookingbye.app;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebChromeClient;

/**
 * Capacitor's chrome client (file inputs, location, alerts), plus new
 * windows: a link with target="_blank" or a window.open() call goes where
 * Navigation says, instead of replacing the page the customer was on.
 */
class ShellChromeClient extends BridgeWebChromeClient {

    private final Navigation navigation;

    ShellChromeClient(Bridge bridge, Navigation navigation) {
        super(bridge);
        this.navigation = navigation;
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        // Pop-ups nobody tapped for are refused, as a browser would.
        if (!isUserGesture) return false;

        // Android only says where a new window is going once it starts loading,
        // so the window is a throwaway webview that hands its first address
        // to Navigation and is never shown.
        WebView window = new WebView(view.getContext());
        window.setWebViewClient(
            new WebViewClient() {
                private boolean handled = false;

                @Override
                public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest request) {
                    route(w, request.getUrl());
                    return true;
                }

                @Override
                public void onPageStarted(WebView w, String url, Bitmap favicon) {
                    route(w, Uri.parse(url));
                }

                private void route(WebView w, Uri url) {
                    if (handled) return;
                    handled = true;
                    w.stopLoading();
                    navigation.openWindow(view, url);
                    w.post(w::destroy);
                }
            }
        );
        ((WebView.WebViewTransport) resultMsg.obj).setWebView(window);
        resultMsg.sendToTarget();
        return true;
    }
}
