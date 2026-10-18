import UIKit
import WebKit
import Capacitor

/// The app's one screen: Capacitor's webview, showing https://bookingbye.com.
class ShellViewController: CAPBridgeViewController {

    /// The longest the splash screen waits for the website.
    private static let splashTimeout: TimeInterval = 8

    private var splash: UIView?
    private var loadingObservation: NSKeyValueObservation?
    /// Held here: a webview keeps its navigation delegate weakly.
    private var failureWatcher: NavigationFailureWatcher?

    override func capacitorDidLoad() {
        super.capacitorDidLoad()
        bridge?.registerPluginInstance(AppShellPlugin())
        admitOnlyTheWebsite()
        runShellScript()
        showOfflinePageOnFailure()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        coverWithSplash()
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        // White on the navy splash; afterwards whatever SystemBars has set.
        splash != nil ? .lightContent : super.preferredStatusBarStyle
    }

    // MARK: - Bridge

    /// Capacitor puts its bridge into every page the webview shows and takes
    /// calls from any of them; payment and bank pages also open in the app
    /// (see Navigation). Only the website itself (server.url) gets answers.
    private func admitOnlyTheWebsite() {
        guard let bridge = bridge as? CapacitorBridge else { return }
        let handler = bridge.webViewDelegationHandler
        handler.contentController.removeScriptMessageHandler(forName: BridgeOriginGuard.name)
        handler.contentController.add(BridgeOriginGuard(handler: handler, origin: bridge.config.serverURL), name: BridgeOriginGuard.name)
    }

    // MARK: - Shell script and offline page

    /// Runs www/shell.js (copied to public/ by cap sync) in every page of the
    /// website, after Capacitor's own script. See that file for what it does.
    private func runShellScript() {
        guard let bridge = bridge,
              let file = Bundle.main.url(forResource: "shell", withExtension: "js", subdirectory: "public"),
              let script = try? String(contentsOf: file, encoding: .utf8),
              let host = bridge.config.serverURL.host else {
            return
        }
        // WKUserScript cannot be limited to one site, so the script checks.
        let origin = "https://\(host)"
        let source = "if (location.origin === \"\(origin)\") {\n\(script)\n}"
        bridge.webView?.configuration.userContentController.addUserScript(
            WKUserScript(source: source, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        )
    }

    private func showOfflinePageOnFailure() {
        guard let webView = webView, let capacitor = webView.navigationDelegate, let bridge = bridge else { return }
        let offlinePage = OfflinePage(serverURL: bridge.config.serverURL)
        let watcher = NavigationFailureWatcher(capacitor: capacitor) { webView, error in
            offlinePage.show(in: webView, after: error)
        }
        failureWatcher = watcher
        webView.navigationDelegate = watcher
    }

    // MARK: - Splash

    /// Keeps the launch screen up until the website has finished loading its
    /// first page (or failed to), rather than giving way to an empty white
    /// webview while that page downloads.
    private func coverWithSplash() {
        guard let webView = webView,
              let cover = UIStoryboard(name: "LaunchScreen", bundle: nil).instantiateInitialViewController()?.view else {
            return
        }
        cover.frame = view.bounds
        cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(cover)
        splash = cover

        loadingObservation = webView.observe(\.isLoading, options: [.new]) { [weak self] webView, _ in
            guard !webView.isLoading else { return }
            DispatchQueue.main.async { self?.removeSplash() }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.splashTimeout) { [weak self] in
            self?.removeSplash()
        }
    }

    private func removeSplash() {
        guard let cover = splash else { return }
        splash = nil
        loadingObservation = nil
        setNeedsStatusBarAppearanceUpdate()
        UIView.animate(withDuration: 0.2, animations: { cover.alpha = 0 }, completion: { _ in cover.removeFromSuperview() })
    }
}

/// Passes the bridge's messages to Capacitor only when they come from the
/// main frame of one origin.
private final class BridgeOriginGuard: NSObject, WKScriptMessageHandler {
    /// The name Capacitor's bridge script posts its messages to.
    static let name = "bridge"

    private weak var handler: WKScriptMessageHandler?
    private let scheme: String?
    private let host: String?
    private let port: Int

    init(handler: WKScriptMessageHandler, origin: URL) {
        self.handler = handler
        self.scheme = origin.scheme?.lowercased()
        self.host = origin.host?.lowercased()
        self.port = origin.port ?? 0
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        let origin = message.frameInfo.securityOrigin
        guard message.frameInfo.isMainFrame,
              origin.protocol.lowercased() == scheme,
              origin.host.lowercased() == host,
              origin.port == port else {
            return
        }
        handler?.userContentController(userContentController, didReceive: message)
    }
}
