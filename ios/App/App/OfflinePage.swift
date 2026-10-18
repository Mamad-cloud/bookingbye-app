import Foundation
import WebKit

/// The bundled offline page (www/offline.html, copied to public/ by cap sync),
/// shown in place of a page that could not be loaded because the phone has no
/// connection or the server cannot be reached. Never for an HTTP error page,
/// which the site draws itself. The Android app does the same (ShellPages.java).
final class OfflinePage {

    /// Where offline.html takes the address of the page that failed.
    private static let failedURLToken = "\"__BOOKINGBYE_FAILED_URL__\""

    /// The errors that mean no connection, rather than a load that was
    /// cancelled or replaced (NSURLErrorCancelled, WebKit's policy change).
    private static let connectionErrors: Set<Int> = [
        NSURLErrorNotConnectedToInternet,
        NSURLErrorNetworkConnectionLost,
        NSURLErrorCannotFindHost,
        NSURLErrorCannotConnectToHost,
        NSURLErrorDNSLookupFailed,
        NSURLErrorTimedOut,
        NSURLErrorInternationalRoamingOff,
        NSURLErrorDataNotAllowed
    ]

    private let origin: URL

    /// `serverURL`: https://bookingbye.com, whose address the page is shown at,
    /// so it can read the site's language and theme and go back to it.
    init(serverURL: URL) {
        var components = URLComponents()
        components.scheme = serverURL.scheme
        components.host = serverURL.host
        components.port = serverURL.port
        components.path = "/"
        origin = components.url ?? serverURL
    }

    /// Shows the page in `webView` if `error` was a want of connection.
    func show(in webView: WKWebView, after error: Error) {
        let error = error as NSError
        guard error.domain == NSURLErrorDomain, Self.connectionErrors.contains(error.code),
              let file = Bundle.main.url(forResource: "offline", withExtension: "html", subdirectory: "public"),
              var page = try? String(contentsOf: file, encoding: .utf8) else {
            return
        }
        let failedURL = (error.userInfo[NSURLErrorFailingURLErrorKey] as? URL)?.absoluteString ?? origin.absoluteString
        page = page.replacingOccurrences(of: Self.failedURLToken, with: Self.jsString(failedURL))
        webView.loadHTMLString(page, baseURL: origin)
    }

    /// `value` as a JavaScript string literal that cannot end the script.
    private static func jsString(_ value: String) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: [value]),
              let array = String(data: data, encoding: .utf8) else {
            return "\"\""
        }
        return String(array.dropFirst().dropLast()).replacingOccurrences(of: "</", with: "<\\/")
    }
}

/// Stands in as the webview's navigation delegate to hear about failed loads,
/// and hands every call, those included, on to Capacitor's own delegate.
final class NavigationFailureWatcher: NSObject, WKNavigationDelegate {

    private weak var capacitor: WKNavigationDelegate?
    private let onFailure: (WKWebView, Error) -> Void

    init(capacitor: WKNavigationDelegate, onFailure: @escaping (WKWebView, Error) -> Void) {
        self.capacitor = capacitor
        self.onFailure = onFailure
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        capacitor?.webView?(webView, didFailProvisionalNavigation: navigation, withError: error)
        onFailure(webView, error)
    }

    // Everything else goes straight to Capacitor.
    override func responds(to aSelector: Selector!) -> Bool {
        super.responds(to: aSelector) || (capacitor?.responds(to: aSelector) ?? false)
    }

    override func forwardingTarget(for aSelector: Selector!) -> Any? {
        if let capacitor = capacitor, capacitor.responds(to: aSelector) { return capacitor }
        return super.forwardingTarget(for: aSelector)
    }
}
