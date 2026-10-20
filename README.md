# BookingBye app

BookingBye for iOS and Android: a native shell, built with
[Capacitor](https://capacitorjs.com) 8, around the live website
https://bookingbye.com.

The webview loads the site itself, so website releases reach the app without
an app release. Capacitor injects `window.Capacitor` into the site, which is
how the site reaches the native plugins; the contract between the two is
described in the website's `src/utils/appShell.ts`. The shell adds
`BookingByeApp/<version>` to the user agent so the site knows it is inside
the app.

| | |
|---|---|
| App ID (both stores) | `com.bookingbye.app` (permanent) |
| Name | BookingBye |
| Version | `version` in `package.json`, the only place to change it |
| Config | `capacitor.config.js`; `npx cap sync` copies it into both platforms |

## Layout

```
capacitor.config.js   server URL, user agent, shell settings
www/                  bundled offline page and shell script (cap sync copies them)
android/              Android Studio project
ios/                  Xcode project (Swift Package Manager, no CocoaPods)
resources/            icon artwork and store listing images
scripts/              version sync, icon and splash generator, Xcode project helpers
docs/PROGRESS.md      build record: steps, decisions, what is left
docs/push.md          push notifications: Firebase, website and backend
docs/store-checklist.md  everything needed to publish
codemagic.yaml, .github/workflows/  store builds in the cloud
```

## Setting up on Windows (Android)

1. Install [Android Studio](https://developer.android.com/studio) (current
   stable). It brings its own JDK 21, which is what Capacitor 8 needs, so no
   separate Java install is required.
2. First run: accept the setup wizard's defaults. Then in
   *Settings › Languages & Frameworks › Android SDK* make sure these are installed:
   - SDK Platforms: Android 16 (API 36)
   - SDK Tools: Android SDK Build-Tools, Android SDK Platform-Tools,
     Android Emulator, Android SDK Command-line Tools
3. For command-line builds, set two user environment variables, then open a new terminal:
   - `JAVA_HOME` = `C:\Program Files\Android\Android Studio\jbr`
   - `ANDROID_HOME` = `%LOCALAPPDATA%\Android\Sdk`
4. Create an emulator in *Device Manager* (a Pixel with a recent "Google Play"
   system image, so Google sign-in and push can be tested), or plug in a phone
   with USB debugging turned on.

Then:

```
npm ci
npm run sync
npm run android          # opens Android Studio; press Run
```

or, without the IDE: `cd android && gradlew.bat assembleDebug`.

## iOS

iOS builds need macOS with Xcode. There is no Mac here, so they run in the
cloud (below). On a Mac it is `npm ci && npm run sync && npm run ios`;
`npx cap sync` there also creates `ios/App/CapApp-SPM/symlinks/`, which the
Firebase plugin's Swift package needs.

## Store builds (CI)

Two equivalent setups; pick one:

- [codemagic.yaml](codemagic.yaml) for [Codemagic](https://codemagic.io);
- [.github/workflows/](.github/workflows) for GitHub Actions.

Each builds iOS (signed, uploaded to TestFlight) and Android (a signed `.aab`
for Play's internal testing track), when started by hand or when a tag like
`v1.0.0` is pushed. The secrets each needs are listed at the top of its
file. Build numbers come from `scripts/build-number.js` (minutes since
2026-01-01), so builds from either never collide.

Android release signing reads the upload key from `android/keystore.properties`
(never committed) or from the `ANDROID_KEYSTORE_*` variables:

```
storeFile=C:/keys/bookingbye-upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

Everything else needed to publish (accounts, keys, privacy answers,
screenshots, review notes) is in [docs/store-checklist.md](docs/store-checklist.md).

## What opens where

The shell decides every page load, with the same rules on both platforms
(`Navigation.java`, `Navigation.swift`), from `plugins.AppShell` in
`capacitor.config.js`:

| Address | Opens |
|---|---|
| bookingbye.com and its subdomains (`appHosts`) | in the app |
| a payment gateway (`paymentHosts`) | in the app |
| any https page a gateway or bank page moves on to without a tap (3-D Secure) | in the app |
| a frame inside a page (maps, chat) | in the app |
| a form posted to another site (checkout to a gateway) | in the app |
| `target="_blank"` / `window.open` to the hosts above | in the app, same screen |
| any other website, `_blank` links, a link tapped on a payment page | the phone's browser |
| `tel:`, `mailto:`, `sms:`, `intent://`, FIB and bank app links | the app that handles them |

A new payment gateway that redirects back to bookingbye.com needs its domain
in `paymentHosts`, then `npm run sync` and an app release; until then the
checkout sends the customer to the browser for it.

Only https://bookingbye.com can call the native plugins. Payment and bank
pages open in the app but get no answer from it.

## Sign-in and links into the app

**Google (and Facebook) sign-in** runs in the phone's browser, because Google
refuses embedded webviews. The website opens
`https://bookingbye.com/app-auth?provider=…` with the Browser plugin. When
sign-in is done, the browser opens `bookingbye://auth?token=…`; the app closes
the browser and loads `/oauth/success?token=…` in the webview, which signs it
in. The app only accepts that token while a sign-in it started is under way
(the Safari sheet is still open on iOS; the browser was opened within the
last 15 minutes on Android), so another app or website cannot sign the
customer in as someone else. It also passes on the website's one-time
`nonce` (`bookingbye://auth?token=…&nonce=…` → `/oauth/success?token=…&nonce=…`),
which the webview made when the sign-in started and checks at the end.

**Google sign-in** is the one on offer; Facebook sign-in is turned off on the
website for now. Turning it back on needs no app change.

**Links to bookingbye.com** open in the app (Android App Links, iOS Universal
Links), except `/api/*`, `/_next/*`, `/oauth/*`, `/app-auth*`, `/payment-*`
and `/pay-done*`, which stay in the browser. For that, the website has to
vouch for the app. Set these on the website's server once the values exist:

| Setting | Value | Where it comes from |
|---|---|---|
| `APP_ANDROID_PACKAGE` | `com.bookingbye.app` | |
| `APP_ANDROID_SHA256_FINGERPRINTS` | `AA:BB:…,CC:DD:…` | Play Console › Test and release › App integrity › App signing: the SHA-256 of the *app signing key* and of the *upload key*, comma-separated |
| `APP_IOS_APP_IDS` | `TEAMID.com.bookingbye.app` | Apple Developer › Membership details › Team ID |

Then check:

- https://bookingbye.com/.well-known/assetlinks.json and
  https://bookingbye.com/.well-known/apple-app-site-association return JSON
  naming the app, with no redirect;
- Android: `adb shell pm get-app-links com.bookingbye.app` shows
  `bookingbye.com: verified` (re-run with `pm verify-app-links --re-verify`);
- iOS: a bookingbye.com link tapped in Notes opens the app. Apple caches the
  file through its CDN, so a change can take a day to arrive.

## Offline, and the shell's script

When a page can't load for want of a connection, the app shows its own
offline page (`www/offline.html`, adapted from the website's), in the site's
language and theme. "Try again" reloads the page that failed, and it does so
by itself when the phone comes back online. An HTTP error page (a 404, a 500)
is the site's and is shown as it is.

`www/shell.js` runs in every bookingbye.com page inside the app. Today it
keeps the status bar icons readable: dark on the site's light theme, light on
its dark one.

## Push notifications

Firebase Cloud Messaging on both platforms (`@capacitor-firebase/messaging`).
The app asks for nothing at launch: the website asks for permission when it
makes sense and sends the token to the backend; tapping a notification opens
its `data.url` in the app. Setup, the website's part and the backend's are in
[docs/push.md](docs/push.md). Builds without the Firebase files work, with
push off.

## Permissions

Asked for only when needed, never at launch:

| | Android | iOS |
|---|---|---|
| Camera | "Take a photo" in a file upload | the same, from the system's upload menu |
| Photos | none needed (system picker) | when choosing a photo to upload |
| Location | when a page asks for it | when a page asks for it |
| Notifications | when the website asks (Android 13+) | when the website asks |

The permission texts are in English, Arabic and Sorani (`InfoPlist.strings`).
On Android every upload that takes images offers "Take a photo / Choose a
file"; Capacitor alone would only offer the camera to inputs with `capture`.

## Icons and splash

Every icon and splash image is drawn from `resources/icon-source.png`, the
website's favicon (a navy ticket with a white "b"), by `npm run assets`:

- app icons: the ticket in brand navy #083882 on white, inside the area each
  platform never crops (Android adaptive and themed icons, the iOS icon);
- splash: the ticket in white on navy, shown natively until the website has
  drawn its first page (at most 8 seconds);
- Android notification icon, and the Play Store icon and feature graphic in
  `resources/store/`.

The output is committed. Run it again only when the artwork changes.

## Versions

Change `version` in `package.json`, then run `npm run sync`, which also writes it
into the iOS project. The build number, which must grow with every store
upload, comes from CI (`APP_BUILD_NUMBER`, see above). A local Android build derives a small one from the version
(1.2.3 → 10203); upload only CI builds, whose numbers are far larger.
