import UIKit
import WebKit
import Capacitor

/// Which addresses the app shows itself, and which it hands to the phone.
///
/// Shown in the app:
/// - bookingbye.com (`plugins.AppShell.appHosts` in capacitor.config.js);
/// - the card payment pages the checkout sends the customer to (`paymentHosts`);
/// - while one of those, or any page that is not bookingbye.com, is showing:
///   whatever it moves on to by itself (a redirect, a script, a form), such as
///   a bank's 3-D Secure check, since the banks cannot be listed;
/// - frames inside a page (maps, chat, 3-D Secure frames), wherever they are from.
///
/// A form posted to another site (the checkout posting to a gateway) loads in
/// the app too: Safari could not be given it.
///
/// Handed to the phone: every other website, links that open a new window
/// (`target="_blank"`, `window.open`) unless they lead to one of the hosts
/// above, and every other kind of address: tel:, mailto:, sms:, the FIB and
/// bank apps' own schemes.
///
/// The Android app applies the same rules
/// (android/app/src/main/java/com/bookingbye/app/Navigation.java); keep the
/// two in step.
final class Navigation {

    private let appHosts: [String]
    private let paymentHosts: [String]

    /// The page the next navigation comes from: the last main-frame address
    /// let through. WKWebView.url is unreliable in the middle of a redirect,
    /// which is exactly when a payment needs it. Every main-frame load,
    /// redirects and form posts included, is decided here, so this is complete.
    private var lastPage: URL?

    init(config: PluginConfig) {
        appHosts = Self.hosts(config.getArray("appHosts"))
        paymentHosts = Self.hosts(config.getArray("paymentHosts"))
    }

    /// Decides a navigation in `webView`. Returns true when the webview must
    /// not load the address itself, because it went to the phone or was
    /// loaded some other way.
    func shouldOverride(_ action: WKNavigationAction, in webView: WKWebView) -> Bool {
        guard let url = action.request.url, let scheme = url.scheme?.lowercased() else { return true }
        let newWindow = action.targetFrame == nil
        let mainFrame = newWindow || action.targetFrame?.isMainFrame == true
        let tapped = action.navigationType == .linkActivated

        if Self.isInert(scheme) { return newWindow }
        guard Self.isWeb(scheme) else {
            // Only a tap or the page itself may open another app; a frame
            // (an advert, say) may not do it on its own.
            if mainFrame || tapped { openOutside(url, from: webView) }
            return true
        }
        guard mainFrame else { return false }

        // A form posted to another site (the checkout posting to a gateway)
        // cannot be handed to Safari, which would lose what was posted. Android
        // never asks about posts at all, so they load in the app there too.
        if action.request.httpMethod?.uppercased() == "POST" && !newWindow {
            lastPage = url
            return false
        }

        // A new window counts as a tap: it never follows a payment along.
        if staysInApp(url, current: lastPage ?? webView.url, tapped: tapped || newWindow) {
            if newWindow || scheme == "http" {
                webView.load(URLRequest(url: Self.https(url)))
                return true
            }
            lastPage = url
            return false
        }
        openOutside(url, from: webView)
        return true
    }

    private func staysInApp(_ url: URL, current: URL?, tapped: Bool) -> Bool {
        if Self.matches(url.host, appHosts) || Self.matches(url.host, paymentHosts) { return true }

        // In a payment: the page showing is a gateway's (or a bank's), and it
        // is moving on by itself, over https.
        let inPayment = current?.scheme?.lowercased() == "https" && current?.host != nil && !Self.matches(current?.host, appHosts)
        return inPayment && !tapped && url.scheme?.lowercased() == "https"
    }

    /// Opens `url` in whatever the phone uses for it: Safari, the dialer, another app.
    private func openOutside(_ url: URL, from webView: WKWebView) {
        // Not while the app is in the background, where a redirect could
        // otherwise pull the customer into another app (as Capacitor does).
        guard webView.window?.windowScene?.activationState == .foregroundActive else { return }
        UIApplication.shared.open(url, options: [:]) { [weak webView] opened in
            guard !opened, let viewController = webView?.window?.rootViewController else { return }
            let alert = UIAlertController(title: nil, message: NSLocalizedString("no_app_for_link", comment: ""), preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: NSLocalizedString("ok", comment: ""), style: .default))
            (viewController.presentedViewController ?? viewController).present(alert, animated: true)
        }
    }

    // MARK: - Helpers

    /// Addresses that load nothing from anywhere, which the webview handles itself.
    private static func isInert(_ scheme: String) -> Bool {
        ["about", "data", "blob", "javascript"].contains(scheme)
    }

    private static func isWeb(_ scheme: String) -> Bool {
        scheme == "https" || scheme == "http"
    }

    private static func https(_ url: URL) -> URL {
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.scheme = "https"
        return components?.url ?? url
    }

    /// True when `host` is one of `hosts` or a subdomain of one.
    private static func matches(_ host: String?, _ hosts: [String]) -> Bool {
        guard let host = host?.lowercased() else { return false }
        return hosts.contains { host == $0 || host.hasSuffix("." + $0) }
    }

    private static func hosts(_ values: JSArray?) -> [String] {
        (values ?? []).compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespaces).lowercased() }
    }
}
