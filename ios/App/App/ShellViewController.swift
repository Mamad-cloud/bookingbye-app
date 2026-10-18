import UIKit
import WebKit
import Capacitor

/// The app's one screen: Capacitor's webview, showing https://bookingbye.com.
class ShellViewController: CAPBridgeViewController {

    /// The longest the splash screen waits for the website.
    private static let splashTimeout: TimeInterval = 8

    private var splash: UIView?
    private var loadingObservation: NSKeyValueObservation?

    override func viewDidLoad() {
        super.viewDidLoad()
        coverWithSplash()
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        // White on the navy splash; afterwards whatever SystemBars has set.
        splash != nil ? .lightContent : super.preferredStatusBarStyle
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
