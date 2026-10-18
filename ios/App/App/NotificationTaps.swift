import Foundation
import UserNotifications
import Capacitor

/// Opens the page a push notification points to when it is tapped: the
/// "url" in its data, if it is a bookingbye.com address (IncomingLinks),
/// from a cold start too. Stands in as Capacitor's push notification handler
/// and passes everything on to the Firebase Messaging plugin's, so the
/// website still hears about notifications. The Android app does the same
/// (MainActivity.notificationLink).
final class NotificationTaps: NSObject, NotificationHandlerProtocol {

    /// The data key that names the page to open.
    static let urlKey = "url"

    private let firebase: NotificationHandlerProtocol?
    private let open: (URL) -> Void

    init(firebase: NotificationHandlerProtocol?, open: @escaping (URL) -> Void) {
        self.firebase = firebase
        self.open = open
    }

    func willPresent(notification: UNNotification) -> UNNotificationPresentationOptions {
        firebase?.willPresent(notification: notification) ?? []
    }

    func didReceive(response: UNNotificationResponse) {
        firebase?.didReceive(response: response)
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier,
              let value = response.notification.request.content.userInfo[Self.urlKey] as? String,
              let url = URL(string: value) else {
            return
        }
        DispatchQueue.main.async { self.open(url) }
    }
}
