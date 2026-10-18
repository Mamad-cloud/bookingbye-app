package com.bookingbye.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AlertDialog;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebChromeClient;
import java.util.Locale;

/**
 * Capacitor's chrome client (file inputs, location, alerts), plus:
 *
 *   new windows   a link with target="_blank" or a window.open() call goes
 *                 where Navigation says, instead of replacing the page;
 *   the camera    a file input that takes images offers "Take a photo" as
 *                 well as "Choose a file", as a phone's browser does.
 *                 Capacitor only offers the camera to inputs with a
 *                 `capture` attribute, and the website's have none.
 */
class ShellChromeClient extends BridgeWebChromeClient {

    private final Bridge bridge;
    private final Navigation navigation;

    ShellChromeClient(Bridge bridge, Navigation navigation) {
        super(bridge);
        this.bridge = bridge;
        this.navigation = navigation;
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams params) {
        if (params.isCaptureEnabled() || !acceptsImages(params.getAcceptTypes())) {
            return super.onShowFileChooser(webView, filePathCallback, params);
        }

        // Both choices end in Capacitor's own code: the camera one (with its
        // permission request) when told the input wants a photo taken.
        CharSequence[] choices = {
            bridge.getActivity().getString(R.string.upload_take_photo),
            bridge.getActivity().getString(R.string.upload_choose_file)
        };
        new AlertDialog.Builder(bridge.getActivity())
            .setItems(choices, (dialog, which) -> {
                if (which == 0) {
                    super.onShowFileChooser(webView, filePathCallback, new TakePhoto(params));
                } else {
                    super.onShowFileChooser(webView, filePathCallback, params);
                }
            })
            // The page waits for an answer; "nothing" is one.
            .setOnCancelListener(dialog -> filePathCallback.onReceiveValue(null))
            .show();
        return true;
    }

    /** True when an input's `accept` list allows images (an empty list allows anything). */
    private static boolean acceptsImages(String[] acceptTypes) {
        boolean anything = true;
        for (String type : acceptTypes) {
            String t = type.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            anything = false;
            if (t.startsWith("image/") || t.equals("*/*") || t.matches("\\.(jpe?g|png|heic|heif|webp|gif)")) return true;
        }
        return anything;
    }

    /** The same input, as if it said capture with accept="image/*". */
    private static final class TakePhoto extends FileChooserParams {

        private final FileChooserParams original;

        TakePhoto(FileChooserParams original) {
            this.original = original;
        }

        @Override
        public int getMode() {
            return MODE_OPEN;
        }

        @Override
        public String[] getAcceptTypes() {
            return new String[] { "image/*" };
        }

        @Override
        public boolean isCaptureEnabled() {
            return true;
        }

        @Override
        public CharSequence getTitle() {
            return original.getTitle();
        }

        @Override
        public String getFilenameHint() {
            return original.getFilenameHint();
        }

        @Override
        public Intent createIntent() {
            return original.createIntent();
        }
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
