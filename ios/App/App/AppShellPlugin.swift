import Foundation
import WebKit
import Capacitor

/// The shell's own plugin, registered by ShellViewController. It offers the
/// website nothing yet; it exists because a plugin is how Capacitor lets
/// native code decide on every page load (shouldOverrideLoad).
@objc(AppShellPlugin)
public class AppShellPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "AppShellPlugin"
    public let jsName = "AppShell"
    public let pluginMethods: [CAPPluginMethod] = []

    private var navigation: Navigation?

    override public func load() {
        guard let config = bridge?.config.getPluginConfig("AppShell") else { return }
        navigation = Navigation(config: config)
    }

    override public func shouldOverrideLoad(_ navigationAction: WKNavigationAction) -> NSNumber? {
        guard let navigation = navigation, let webView = webView else { return nil }
        return NSNumber(value: navigation.shouldOverride(navigationAction, in: webView))
    }
}
