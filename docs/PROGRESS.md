# Build progress

The working record of building this app: the steps, what each one did, what
is left, and the decisions and findings behind them. Anyone (or any session)
picking the work up starts here. Update it with every step.

## How the work is done

- One step at a time, each committed on `main` when done (message ends with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`), then on to the next.
- The website (`G:\dev\bookingbye\frontend`, Next.js 15) and the backend
  (`G:\dev\bookingbye\api`, Laravel) are reference only. Never edit them;
  anything the website needs goes in [Website to-do](#website-to-do).
- This machine is Windows with Node 24 and git: no Android SDK, no working
  Java, no Mac. Nothing native can be compiled here, so every Java and Swift
  change is checked by reading it against the Capacitor source in
  `node_modules/@capacitor/{android,ios}`. The first real build happens after
  Android Studio is installed (README) or in CI (step 8).
- Backslashes typed into shell heredocs reach the file halved (two become
  one): write Java/Swift strings with escapes through the editor, then grep
  the sources for them.
- The Xcode project is edited without Xcode with `scripts/xcode-add-file.py`
  and `scripts/xcode-add-localized.py`. Check braces and parentheses still
  balance in `project.pbxproj` afterwards.

## Fixed decisions

| | |
|---|---|
| App ID | `com.bookingbye.app` on both stores (confirmed by the owner; permanent) |
| Name | BookingBye |
| Stack | Capacitor 8.5 (current stable), `server.url` = https://bookingbye.com, iOS through Swift Package Manager |
| Devices | iPhone only (`TARGETED_DEVICE_FAMILY = 1`); Android minSdk 24, target 36 |
| Plugins | @capacitor/app, browser, filesystem, share; push in step 7. No splash-screen or status-bar plugin: the splash is native, the status bar is Capacitor 8's built-in SystemBars |
| Shell code | Android `android/app/src/main/java/com/bookingbye/app/`, iOS `ios/App/App/`; settings in `capacitor.config.js` under `plugins.AppShell` |
| Languages | Native strings in English, Arabic, Sorani (`values-ar`, `values-b+ckb`; `en/ar/ckb.lproj`). Sorani wording needs a native speaker's check |

## Findings from the Capacitor source that shaped the design

- Android injects the bridge only into the `server.url` origin
  (`addDocumentStartJavaScript`), and the message listener only accepts that
  origin plus `allowNavigation`. So `allowNavigation` stays empty.
- iOS injects the bridge into every main-frame page and has no origin check.
  `ShellViewController` wraps the `bridge` message handler (BridgeOriginGuard).
- Capacitor's `server.errorPath` also fires on HTTP 4xx/5xx (it would replace
  the site's 404 page with "offline"), and on Android it is served at
  `https://bookingbye.com/<errorPath>`, colliding with the site's own
  `/offline.html`. Not used: step 6 does its own offline page.
- Capacitor's plugin `shouldOverrideLoad` on Android only gets the URL (no
  frame, gesture, or redirect), so Android navigation is a `BridgeWebViewClient`
  subclass; on iOS a plugin's `shouldOverrideLoad` gets the full `WKNavigationAction`.
- Android `BridgeWebChromeClient` offers the camera only for inputs with a
  `capture` attribute; none of the site's inputs have one (step 5 adds a chooser).
- `BridgeActivity` replays the launch intent through `onNewIntent` from inside
  `super.onCreate`. iOS `SceneDelegateProxy` holds cold-start links until the
  first `viewDidAppear`, then posts `.capacitorSceneOpenURL` /
  `.capacitorSceneOpenUniversalLink` (userInfo `url`).
- `MainActivity` is `singleTask`: a `bookingbye://` intent brings it forward
  and clears the Browser plugin's Custom Tab above it.
- Site facts: `www.bookingbye.com` 301s to the apex; dark mode is the `dark`
  class on `<html>` (user choice, not the system's); the site never calls
  `navigator.geolocation`; file inputs: `account_form.tsx` (image/*),
  `agency-signup` (.pdf,.jpg,.jpeg,.png), `PackageDocumentsUploader` (visa documents).

## Steps

| # | Step | State | Commit |
|---|---|---|---|
| 1 | Scaffold: Capacitor, platforms, user agent, version wiring, README | done | `dc79feb` |
| 2 | Icons and native splash (`npm run assets`), `.gitattributes` | done | `64c4c75` |
| 3 | Navigation: what opens in the app, what goes to the phone; iOS bridge limited to the site | done | `1784a45` |
| 4 | `bookingbye://auth` sign-in return; App Links / Universal Links | done | `ba326ba` |
| 5 | File uploads with camera, location, permission texts | done | see git log |
| 6 | Offline screen; status bar following the site's dark mode | done | see git log |
| 7 | Push notifications (FCM) | done | see git log |
| 8 | CI (Codemagic + GitHub Actions), signing, store checklist | done | see git log |

### Done, in short

1. `capacitor.config.js` (JS, typed through JSDoc; TypeScript 7 dropped the API the CLI uses for .ts configs), `appendUserAgent: BookingByeApp/<package.json version>`. Version: `package.json` → Android `build.gradle` reads it; `scripts/sync-version.js` writes iOS `MARKETING_VERSION` (`npm run sync`). Build numbers: `APP_BUILD_NUMBER` env (Android), `CURRENT_PROJECT_VERSION` (iOS), from CI.
2. `scripts/generate-assets.js` (sharp) from `resources/icon-source.png`: navy ticket (#083882) on white for icons, white ticket for the splash and notification icon (`ic_stat_notify`), Play icon and feature graphic in `resources/store/`. Android splash: core-splashscreen theme, held by `MainActivity` until the first page commits (max 8 s). iOS: navy `LaunchScreen.storyboard`, held as an overlay by `ShellViewController`.
3. `Navigation.java` / `Navigation.swift` (same rules; README table). appHosts, paymentHosts (12 gateway domains from the backend's gateways), payment flow judged from the last page let through, form POSTs stay in the app, new windows via `ShellChromeClient.onCreateWindow` (Android) / `targetFrame == nil` (iOS), intent:// opened like Chrome, a no-app message in three languages.
4. `IncomingLinks.java` / `IncomingLinks.swift`. Token accepted only during an app-started sign-in (Android: Browser activity opened within 15 min, stored in SharedPreferences; iOS: the SFSafariViewController is still presented). Manifest: scheme filter + autoVerify App Links with `uri-relative-filter-group` exclusions; `keptInBrowser` in config for Android < 15. iOS: `CFBundleURLTypes`, `App.entitlements` (applinks + webcredentials), `CODE_SIGN_ENTITLEMENTS`.

### Step 5: file uploads, camera, location (done)

- Android manifest: `CAMERA`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`;
  `uses-feature` camera and location with `required="false"`.
- `ShellChromeClient.onShowFileChooser`: when the input accepts images (or
  anything) and has no `capture`, show a sheet "Take a photo / Choose a file";
  the photo path calls `super` with FileChooserParams that say capture + image/*,
  which reuses Capacitor's camera code and permission request. Strings
  `upload_take_photo`, `upload_choose_file` in en/ar/ckb.
- Location needs nothing more on Android: `BridgeWebChromeClient` already asks.
- iOS `Info.plist`: `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`,
  `NSLocationWhenInUseUsageDescription`, in English, and translated in
  `en/ar/ckb.lproj/InfoPlist.strings` (variant group via `xcode-add-localized.py`).
  iOS asks for location twice (system, then the page); WebKit has no API to skip the second.
- Done as described. The visa uploader accepts image/jpeg, image/png and
  application/pdf (frontend `DEFAULT_ACCEPTED`), so a camera photo (JPEG) fits.

### Step 6: offline screen, status bar (done)

- `www/offline.html`, self-contained, in en/ar/ckb (ar and ckb right-to-left;
  pick by `navigator.language`, or the site's locale cookie when readable),
  brand navy, a Try again button that reloads the page that failed, and
  automatic retry on the `online` event.
- Android: in `ShellWebViewClient.onReceivedError`, for the main frame only
  and a connection error (not HTTP status pages), load the page with
  `loadDataWithBaseURL("https://bookingbye.com/", html, …, failedUrl)` so it
  can navigate back; the failed URL passed in by text replacement.
- iOS: `didFailProvisionalNavigation` / `didFail` are Capacitor's. Observe
  through KVO, or wrap the navigation delegate, then
  `loadHTMLString(html, baseURL: https://bookingbye.com/)`. Skip
  `NSURLErrorCancelled` (-999).
- Status bar: a small script injected into the site (Android
  `WebViewCompat.addDocumentStartJavaScript` for the server origin; iOS
  `WKUserScript` checking `location.origin`) that watches `html.dark` and
  calls `Capacitor.Plugins.SystemBars.setStyle({ style: dark ? "DARK" : "LIGHT" })`.
- Done as: `www/offline.html` (the website's own offline page, adapted:
  retries the failed URL, says "can't reach" when online), `www/shell.js`;
  Android `ShellPages.java` (+ `androidx.webkit` dependency), iOS
  `OfflinePage.swift` with `NavigationFailureWatcher` (stands in as the
  navigation delegate, forwards everything to Capacitor's). Connection errors
  only: Android HOST_LOOKUP/CONNECT/TIMEOUT/IO; iOS NSURLError
  not-connected/lost/host/DNS/timeout/roaming/data-not-allowed.

### Step 7: push notifications (done)

- `@capacitor-firebase/messaging` (FCM tokens on both platforms; plain
  @capacitor/push-notifications would give APNs tokens on iOS).
- Android: `google-services.json` (gitignored, from CI secrets; the template's
  build.gradle applies the plugin only when the file exists). Default icon
  `@drawable/ic_stat_notify`, colour `@color/brand_navy`, a default channel,
  `POST_NOTIFICATIONS` (Android 13+). Check the app does not crash without the file.
- iOS: `GoogleService-Info.plist` (gitignored, from CI), `FirebaseApp.configure()`
  only when the file is present, Push Notifications capability
  (`aps-environment` in App.entitlements), APNs key uploaded to Firebase.
- Tapping a notification: the payload's `data.url` (a bookingbye.com address)
  opens in the webview, from a cold start too. Route it through IncomingLinks.
- The website asks for permission (after sign-in, not at launch) and sends
  the token to the backend: document the contract (`docs/push.md`) and the
  endpoints. Proposal: `POST /api/v1/devices` {token, platform, app_version,
  locale} (authenticated, upsert by token), `DELETE /api/v1/devices/{token}`
  on sign-out; the backend sends with the FCM HTTP v1 API, removes tokens
  FCM reports as unregistered, sends `data.url` for the screen to open.
- Done as: `docs/push.md` (Firebase setup, website code, backend table,
  endpoints and payload). Android: manifest meta-data (icon, colour, channel
  `booking_updates`, auto-init off), channel created in `MainActivity`, taps
  read from the launch intent's `url` extra. iOS: AppDelegate forwards APNs
  callbacks, `aps-environment` entitlement, `FirebaseMessagingAutoInitEnabled`
  NO, `NotificationTaps` wraps Capacitor's push handler (the plugin's), a
  build phase copies `GoogleService-Info.plist` only when present.
- Windows cannot make the symlink the plugin's SPM option needs
  (`experimental.ios.spm.packageOptions`), so `CapApp-SPM/Package.swift` was
  written by hand to match the CLI; `cap sync` on the Mac rewrites it and
  creates `symlinks/` (gitignored).
- `npm audit`: 3 moderate, in the Capacitor CLI's `xcode` dependency
  (build-time only; the fix is a breaking CLI upgrade). Recheck on Capacitor updates.

### Step 8: CI, signing, store checklist (done)

- `codemagic.yaml`: iOS (App Store Connect API key, automatic signing,
  `agvtool` build number, TestFlight upload) and Android (keystore, AAB, Play
  internal track). `.github/workflows/`: same for GitHub Actions (macos runner).
  Both run `npm ci && npx cap sync`, write the Firebase files from secrets.
- Android release signing in `build.gradle` from `keystore.properties` or
  env vars; how to make the upload keystore (`keytool`), and to keep it safe.
- `docs/store-checklist.md`: privacy policy URL, account deletion (in-app
  path; today it is a support ticket), App Store privacy labels and Play data
  safety answers, screenshots (6.9" iPhone; Android phone), age rating,
  review notes with a test account, and the website to-do below.
- Done as: `codemagic.yaml`, `.github/workflows/{android,ios}.yml` (YAML
  checked to parse), release signing in `android/app/build.gradle`,
  `scripts/build-number.js` (minutes since 2026-01-01, shared by both CIs),
  `sync-version.js` also writes `CURRENT_PROJECT_VERSION`, a shared Xcode
  scheme, `ios/ExportOptions.plist` (upload to App Store Connect),
  `ITSAppUsesNonExemptEncryption = NO`, `gradlew` made executable in git,
  and `docs/store-checklist.md`.

## What is left after step 8

The app's code is complete; nothing has been compiled yet. In order:

1. Install Android Studio (README) and build a debug app: fix whatever the
   first compile finds in the Java (`gradlew.bat assembleDebug`), then test
   on a phone with the checklist's test list (store-checklist.md section 9).
2. Set up one CI (store-checklist.md section 3) and run the iOS workflow: the
   first Swift compile; fix what it finds.
3. The accounts, keys and website items in store-checklist.md, then submit.

## Website to-do

Collected for the website owner; nothing here is done in this repo.

1. **Deploy the app pages.** The live site returns 404 for `/app-auth` and
   `/.well-known/*` (checked 2026-10-19), so app sign-in and app links do not
   work yet.
2. **`.well-known` settings** once the values exist (README, "Sign-in and
   links into the app"): `APP_ANDROID_PACKAGE`, `APP_ANDROID_SHA256_FINGERPRINTS`,
   `APP_IOS_APP_IDS`.
3. **Privacy policy page.** Only `/terms` exists; both stores require a privacy policy URL.
4. **Safe area at the top.** The site pads left, right and bottom
   (`env(safe-area-inset-*)`) but not the top; in the app the header sits
   under the status bar. The sticky header needs
   `padding-top: env(safe-area-inset-top)`.
5. **Account deletion** started from inside the app (Apple 5.1.1(v), Google
   Play policy). Today it is a support ticket; that is allowed if it is
   reachable in the app and clearly explained, but a real deletion flow is better.
6. **Push**: everything in `docs/push.md`: Firebase project, the site asking
   for permission and posting the token, the backend's `device_tokens` table,
   endpoints and sending.
7. **Location**: the site never asks for it; maps show no "near me" until it does.
8. **Google Analytics in the app**: `allow_google_signals: false` and
   `allow_ad_personalization_signals: false` when `isInApp()`, so Apple does
   not count it as tracking (no App Tracking Transparency prompt needed).
9. **Public account-deletion page** for Google Play (e.g. /delete-account).

**Status 2026-10-20, second check** (after the fix prompts were deployed):
the earlier findings are fixed in both repos (api: 76 feature + 195 unit
tests pass; frontend: no TypeScript errors in the changed files). Still open:
production Facebook env on the website (`FACEBOOK_CLIENT_ID` is a
placeholder, `FACEBOOK_REDIRECT_URI` lacks `/v2`); api trusts
`X-Forwarded-Host` from Cloudflare with TrustHosts off (deletion link host
spoofable on a sync queue); a push dispatch error inside a commit escapes to
the caller (IPN 500s); `PUSH_UNPAID_AFTER_MINUTES` missing from
`.env.example`; phpunit.xml lets Jetstream tests migrate the `.env` database;
first sync after deploy may send late duplicate pushes; privacy page names
the wrong payment providers and omits WhatsApp; frontend: push offer pops
on a Google re-sign-in, dot-segment paths in the sign-in return, no cap on
`app_version`; a Google client secret sits in the frontend's `.env.example`
(private repo; rotate it); `/oauth/success` login CSRF (state never checked).

**Status 2026-10-20** (audit of the frontend and api repos after their work
on the items above): deployed; `/app-auth`, `/.well-known/*`, `/privacy`,
`/delete-account` and the API's `/v2/devices`, `/v2/me`,
`/v2/account-deletion-requests` are live, and the frontend and backend
request shapes match. Open, in fix prompts given to the owner (backend
first, then frontend): privacy page still shows 14 `[TODO:]` placeholders;
Facebook sign-in broken (`/api/auth/facebook` 405, backend on Graph v12);
push offer never shown after Google sign-in in the app (oauth/success fires
`auth:changed` before PushManager listens); backend deletes tokens on any
FCM 400; `visaDocumentsRequested` never called; payment-failed push only on
browser return; push tied to email for some products; files deleted inside
the deletion transaction; TrustProxies unset behind Cloudflare;
`.env.example` lacks the Firebase/PUSH keys; production must not use the
`sync` queue and needs `JWT_SECRET` of 32+ characters.
