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
///   page opens in the app. The site's apple-app-site-association keeps the
///   paths that belong in the browser (keptInBrowser) away from the app; a
///   variant that slips past it, such as "//oauth/success", which the website
///   redirects to "/oauth/success", is ignored here.
///
/// The Android app does the same (IncomingLinks.java).
final class IncomingLinks {

    /// The URL scheme the website sends sign-in back to (APP_URL_SCHEME there).
    static let appScheme = "bookingbye"

    /// The query parameter that carries the website's sign-in nonce, passed on unchanged.
    static let signInNonce = "nonce"

    private let appHosts: [String]
    private let keptInBrowser: [String]

    init(config: PluginConfig) {
        appHosts = (config.getArray("appHosts") ?? []).compactMap { ($0 as? String)?.lowercased() }
        keptInBrowser = (config.getArray("keptInBrowser") ?? []).compactMap { ($0 as? String)?.lowercased() }
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
        if scheme == "https", let host = link.host?.lowercased(), appHosts.contains(where: { host == $0 || host.hasSuffix("." + $0) }),
           var components = URLComponents(url: link, resolvingAgainstBaseURL: false) {
            // Judge the path as the website will serve it: it redirects
            // "/a//b" to "/a/b".
            let path = components.path.replacingOccurrences(of: "/{2,}", with: "/", options: .regularExpression)
            components.path = path.isEmpty ? "/" : path
            let lowered = components.path.lowercased()
            if keptInBrowser.contains(where: { lowered.hasPrefix($0) }) { return true }
            if let page = components.url { webView.load(URLRequest(url: page)) }
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
        // The website's one-time value for this sign-in, which it made in the
        // webview when the sign-in started and checks there, so that only a
        // sign-in the app itself brings back can sign the webview in.
        if let nonce = URLComponents(url: link, resolvingAgainstBaseURL: false)?
            .queryItems?.first(where: { $0.name == Self.signInNonce })?.value, !nonce.isEmpty {
            success?.queryItems?.append(URLQueryItem(name: Self.signInNonce, value: nonce))
        }
        guard let url = success?.url else { return }

        sheet.dismiss(animated: true)
        webView.load(URLRequest(url: url))
    }
}
