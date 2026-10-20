# Push notifications: what the website and backend need to do

The app is ready for push notifications through Firebase Cloud Messaging
(FCM), on Android and iOS alike. Three pieces are outside this repo: the
Firebase project, the website asking for permission and passing on the
device's token, and the backend storing tokens and sending messages.

## 1. Firebase project (once)

1. Create a Firebase project ("BookingBye") at https://console.firebase.google.com.
2. Add an **Android app**, package `com.bookingbye.app`. Download
   `google-services.json`.
3. Add an **iOS app**, bundle ID `com.bookingbye.app`. Download
   `GoogleService-Info.plist`.
4. In Apple Developer › Keys, create an **APNs key** (.p8). In Firebase ›
   Project settings › Cloud Messaging › Apple app configuration, upload it
   with its Key ID and your Team ID.
5. Give both files to CI (step 8: secrets), never to git. For a local
   build, put them at `android/app/google-services.json` and
   `ios/App/App/GoogleService-Info.plist`.
6. For the backend: Project settings › Service accounts › Generate new
   private key. That JSON is the backend's credential for sending; keep it
   secret.

Without the two files the app still builds and runs, with push turned off.

## 2. The website

Inside the app, `window.Capacitor.Plugins.FirebaseMessaging` is the
[@capacitor-firebase/messaging](https://capawesome.io/docs/plugins/firebase/cloud-messaging/)
plugin, reached the same way as Filesystem and Share in `src/utils/appShell.ts`.

**When to ask.** Not on the first screen. Ask after sign-in, or right after a
booking ("Get updates about this booking?"), with a sentence on what the
customer will receive. Apple and Google both look for that.

```ts
// In appShell.ts style: only inside the app, only when the plugin is there.
const push = plugins()?.FirebaseMessaging;

async function enablePush(): Promise<void> {
  if (!isInApp() || !push) return;
  let { receive } = await push.checkPermissions();
  if (receive === "prompt" || receive === "prompt-with-rationale") {
    ({ receive } = await push.requestPermissions());  // Android 13+ and iOS ask here
  }
  if (receive !== "granted") return;
  const { token } = await push.getToken();
  const version = appVersion();  // null when the user agent has none
  await api.post("/v2/devices", { token, platform: platform(), locale: currentLocale(), ...(version ? { app_version: version } : {}) });
}
```

- `platform()`: `window.Capacitor.getPlatform()` gives `"ios"` or `"android"`.
- `appVersion()`: the number after `BookingByeApp/` in the user agent, at
  most 32 characters (the backend's limit); leave it out when there is none.
- `token`: up to 512 characters (the backend's limit).
- **Token changes.** Firebase can replace a token. Listen once per page:
  `push.addListener("tokenReceived", ({ token }) => post it again)`.
- **Sign-out.** `DELETE /v2/devices/{token}`, then `push.deleteToken()`.
- **Signed in again, maybe as someone else.** Post the token again; the
  backend moves it to the new account.
- **Taps.** The app opens the notification's `data.url` itself, from a cold
  start too. The website does not need to listen for taps, and should not
  navigate on `notificationActionPerformed` as well.
- **Foreground.** With the app open, iOS shows the notification as a banner.
  Android does not; the site may listen for `notificationReceived` and show
  its own message.

## 3. The backend (Laravel, `api/`)

### Storing tokens

Table `device_tokens`:

| column | |
|---|---|
| `id` | |
| `token` | string up to 512, case-sensitive (utf8mb4_bin), unique: one row per installed app |
| `user_id` | nullable, the customer (`auth:api`) |
| `agency_id` | nullable, the agency user (`agency` guard) |
| `platform` | `ios` or `android` |
| `app_version` | nullable, up to 32, e.g. `1.0.0` |
| `locale` | `en`, `ar` or `ckb`: the language to write that phone's notifications in |
| `last_seen_at`, timestamps | |

Endpoints, in `routes/api.php` under the `v2` prefix:

| | |
|---|---|
| `POST /api/v2/devices` | `auth:api,agency`. Body `{token, platform, app_version, locale}`. **Upsert by token**: if the token exists, move it to the signed-in account and update the rest. 204. |
| `DELETE /api/v2/devices/{token}` | No auth needed (holding the token is enough), so sign-out works with an expired session. Deletes the row. 204. |

### Sending

Use the **FCM HTTP v1 API** with the service account from 1.6. The
`kreait/laravel-firebase` package handles authentication.

```
POST https://fcm.googleapis.com/v1/projects/<project-id>/messages:send
{
  "message": {
    "token": "<device token>",
    "notification": { "title": "Your ticket is ready", "body": "Erbil → Istanbul, 12 Nov. Tap to view." },
    "data": { "url": "https://bookingbye.com/account-bookings/..." },
    "android": { "notification": { "channel_id": "booking_updates" } },
    "apns": { "payload": { "aps": { "sound": "default" } } }
  }
}
```

- `data.url` is the page to open when the notification is tapped. It must be
  a **bookingbye.com** address; the app ignores anything else. Leave it out
  to just open the app.
- `channel_id` `booking_updates` is the channel the app creates ("Booking
  updates"). Marketing messages, if any are sent later, need a channel of
  their own (an app release) and the customer's consent.
- Write `title` and `body` in the row's `locale`.
- When FCM answers `UNREGISTERED` (404) or `INVALID_ARGUMENT` for the
  token, delete the row: the app was uninstalled or the token replaced.
- Send from a queued job, one request per token, and keep the request
  outside any database transaction.

Good first notifications: booking confirmed, ticket or voucher issued,
payment received or failed, visa document requested or rejected, flight
schedule change.
