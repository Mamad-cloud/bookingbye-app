import Foundation
import WebKit
import Capacitor

/// The shell's own plugin, registered by ShellViewController. It offers the
/// website nothing yet; it is where native code hears from Capacitor: every
/// page load (shouldOverrideLoad) and every link that opens the app.
@objc(AppShellPlugin)
public class AppShellPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "AppShellPlugin"
    public let jsName = "AppShell"
    public let pluginMethods: [CAPPluginMethod] = []

    private var navigation: Navigation?
    private var incomingLinks: IncomingLinks?

    override public func load() {
        guard let config = bridge?.config.getPluginConfig("AppShell") else { return }
        navigation = Navigation(config: config)
        incomingLinks = IncomingLinks(config: config)

        // Links that open the app; Capacitor holds back the one the app was
        // started with until plugins have loaded.
        for name in [Notification.Name.capacitorSceneOpenURL, .capacitorSceneOpenUniversalLink] {
            NotificationCenter.default.addObserver(self, selector: #selector(linkOpened(_:)), name: name, object: nil)
        }
    }

    @objc private func linkOpened(_ notification: Notification) {
        guard let url = notification.userInfo?["url"] as? URL else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self = self, let bridge = self.bridge else { return }
            self.incomingLinks?.open(url, bridge: bridge)
        }
    }

    override public func shouldOverrideLoad(_ navigationAction: WKNavigationAction) -> NSNumber? {
        guard let navigation = navigation, let webView = webView else { return nil }
        return NSNumber(value: navigation.shouldOverride(navigationAction, in: webView))
    }
}
