import UIKit
import WebKit
import SafariServices
import Capacitor

/// Links that open the app from outside it.
///
/// - `bookingbye://auth?token=...`: the end of Google or Facebook sign-in,
///   which runs in a Safari sheet the website opens with the Browser plugin
///   (see the website's app/app-auth). The sheet is closed and
///   /oauth/success signs the webview in.
/// - `https://bookingbye.com/...`: Universal Links (App.entitlements). The
///   page opens in the app. The paths kept in the browser never arrive here:
///   the site's apple-app-site-association excludes them.
///
/// The Android app does the same (IncomingLinks.java).
final class IncomingLinks {

    /// The URL scheme the website sends sign-in back to (APP_URL_SCHEME there).
    static let appScheme = "bookingbye"

    private let appHosts: [String]

    init(config: PluginConfig) {
        appHosts = (config.getArray("appHosts") ?? []).compactMap { ($0 as? String)?.lowercased() }
    }

    /// Opens `link` in the bridge's webview. Returns false when it is not one of ours.
    @discardableResult
    func open(_ link: URL, bridge: CAPBridgeProtocol) -> Bool {
        guard let webView = bridge.webView else { return false }
        let scheme = link.scheme?.lowercased()

        if scheme == Self.appScheme {
            if link.host?.lowercased() == "auth" { finishSignIn(link, bridge: bridge, webView: webView) }
            return true
        }
        if scheme == "https", let host = link.host?.lowercased(), appHosts.contains(where: { host == $0 || host.hasSuffix("." + $0) }) {
            webView.load(URLRequest(url: link))
            return true
        }
        return false
    }

    private func finishSignIn(_ link: URL, bridge: CAPBridgeProtocol, webView: WKWebView) {
        guard let token = URLComponents(url: link, resolvingAgainstBaseURL: false)?
            .queryItems?.first(where: { $0.name == "token" })?.value, !token.isEmpty else {
            return
        }
        // Any website or app can open a bookingbye:// address. Taking a token
        // from one this app did not ask for would sign the customer in as
        // someone else, so it only counts while the sign-in sheet this app
        // opened is still showing.
        guard let viewController = bridge.viewController,
              let sheet = viewController.presentedViewController as? SFSafariViewController else {
            return
        }

        var success = URLComponents(url: bridge.config.serverURL, resolvingAgainstBaseURL: false)
        success?.path = "/oauth/success"
        success?.queryItems = [URLQueryItem(name: "token", value: token)]
        guard let url = success?.url else { return }

        sheet.dismiss(animated: true)
        webView.load(URLRequest(url: url))
    }
}
