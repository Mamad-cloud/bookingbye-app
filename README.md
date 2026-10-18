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
www/                  bundled pages (never the app itself; see the config)
android/              Android Studio project
ios/                  Xcode project (Swift Package Manager, no CocoaPods)
resources/            icon artwork and store listing images
scripts/              version sync, icon and splash generator
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
cloud: see the CI section (added in a later step). On a Mac it is
`npm ci && npm run sync && npm run ios`.

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
upload, comes from CI (`APP_BUILD_NUMBER` on Android, `CURRENT_PROJECT_VERSION`
on iOS). A local Android build derives one from the version
(1.2.3 → 10203); upload only CI builds so the two never collide.
