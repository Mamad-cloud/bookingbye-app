# Building BookingBye for iOS on a Mac

For whoever builds the iOS app on the Mac and uploads it to App Store
Connect (TestFlight). No knowledge of the project is needed: follow the
steps in order and copy the commands as they are.

The app is a native shell (Capacitor 8) around the website
https://bookingbye.com. Building it compiles that shell; the website itself
is not in this repository.

| | |
|---|---|
| Bundle ID | `com.bookingbye.app` |
| Apple Team ID | `XZS779BUTF` |
| Xcode project | `ios/App/App.xcodeproj` (no workspace, no CocoaPods) |
| Scheme | `App` |

**Who does what.** Part A is for the owner of the Apple account. Parts B to F
are for the person at the Mac. Part G is the owner again.

---

## A. The owner, before handing over

1. **Give access to this repository** (a private GitHub repository, or a zip
   of the folder without `node_modules`).
2. **Create an App Store Connect API key**, so the Mac can sign and upload
   without anyone's Apple ID password:
   appstoreconnect.apple.com › Users and Access › Integrations ›
   App Store Connect API › *Team Keys* (the first time: *Request Access*,
   accept) › **+** › name `BookingBye Mac`, access **Admin** › Generate ›
   **Download API Key** (a file `AuthKey_XXXXXXXXXX.p8`, downloadable once).
   Note the **Key ID** (in the list) and the **Issuer ID** (above the list).
3. **Send these to the person at the Mac, privately** (password manager or
   an encrypted channel, never in the repository or plain email):
   - `AuthKey_<KeyID>.p8`, the Key ID and the Issuer ID;
   - `GoogleService-Info.plist` (Firebase › Project settings › Your apps ›
     the iOS app).

---

## B. Prepare the Mac (once)

1. **macOS 15.6 (Sequoia) or later**, and **Xcode 26 or later** from the
   App Store (App Store uploads must be built with the iOS 26 SDK). Open
   Xcode once, accept the licence and let it install the iOS platform
   (Xcode › Settings › Components › iOS).
2. In Terminal, check:
   ```bash
   xcodebuild -version        # Xcode 26.x
   xcode-select -p            # /Applications/Xcode.app/Contents/Developer
   ```
   If the second line is different:
   `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`.
3. **Node.js 22 or 24** and git. With Homebrew (https://brew.sh):
   ```bash
   brew install node@24 git
   brew link --overwrite node@24
   node -v                    # v24.x
   ```
4. **Get the code**:
   ```bash
   cd ~
   git clone <repository URL> bookingbye-app
   cd bookingbye-app
   ```
5. **Put the private files in place** (they are ignored by git; never commit them):
   ```bash
   mkdir -p ~/private_keys
   cp ~/Downloads/AuthKey_*.p8 ~/private_keys/
   cp ~/Downloads/GoogleService-Info.plist ios/App/App/
   ```

---

## C. Install and copy the plugins into the Xcode project

From the repository folder, every time you pull new code:

```bash
git pull
npm ci
npx cap sync ios
node scripts/sync-version.js
```

`npx cap sync ios` also creates `ios/App/CapApp-SPM/symlinks/`, which the
Firebase package needs. If a build later complains about a missing
`symlinks/CapacitorFirebaseMessaging`, run it again.

---

## D. First build: the simulator (no signing needed)

1. `open ios/App/App.xcodeproj`
2. Wait until Xcode has finished **Resolving Package Graph** and fetching
   packages (top of the window; Firebase takes several minutes the first time).
3. At the top, choose the scheme **App** and a simulator, e.g. **iPhone 17
   Pro Max**. Press **Run** (▶).
4. Check in the simulator:
   - a navy screen with a white ticket, then the BookingBye website;
   - tapping around the site stays in the app; a social media link in the
     footer opens Safari; a phone number tries to call;
   - Account › sign in with Google: Safari opens a sheet, and after signing
     in, the app is signed in;
   - turn the Mac's Wi-Fi off and pull to reload / open a page: the app's
     own "You're offline" page; turn Wi-Fi on: the page comes back by itself;
   - switch the website to dark mode: the status bar icons turn white.
5. **If the build fails**: Report navigator (the last icon in the left
   sidebar, or ⌘9) › the failed build › select all › copy, and send the
   whole text to the owner. Or from Terminal, which gives a log file:
   ```bash
   xcodebuild -project ios/App/App.xcodeproj -scheme App \
     -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max' build 2>&1 | tee ~/build-simulator.log
   ```

---

## E. Build and upload to App Store Connect

From the repository folder, in Terminal. Replace the three values in the
first lines with the ones the owner sent.

```bash
export ASC_KEY_ID=XXXXXXXXXX                          # Key ID
export ASC_ISSUER_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx  # Issuer ID
export ASC_KEY_PATH=~/private_keys/AuthKey_$ASC_KEY_ID.p8
export APPLE_TEAM_ID=XZS779BUTF

# 1. Version and a new build number (every upload needs a higher one)
export APP_BUILD_NUMBER=$(node scripts/build-number.js)
node scripts/sync-version.js

# 2. Archive (signed automatically through the API key)
xcodebuild archive \
  -project ios/App/App.xcodeproj \
  -scheme App \
  -configuration Release \
  -destination "generic/platform=iOS" \
  -archivePath ~/BookingBye.xcarchive \
  -allowProvisioningUpdates \
  -authenticationKeyPath "$ASC_KEY_PATH" \
  -authenticationKeyID "$ASC_KEY_ID" \
  -authenticationKeyIssuerID "$ASC_ISSUER_ID" \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" \
  CODE_SIGN_STYLE=Automatic 2>&1 | tee ~/build-archive.log

# 3. Sign for the App Store and upload
cp ios/ExportOptions.plist ~/ExportOptions.plist
plutil -replace teamID -string "$APPLE_TEAM_ID" ~/ExportOptions.plist
xcodebuild -exportArchive \
  -archivePath ~/BookingBye.xcarchive \
  -exportOptionsPlist ~/ExportOptions.plist \
  -exportPath ~/BookingBye-export \
  -allowProvisioningUpdates \
  -authenticationKeyPath "$ASC_KEY_PATH" \
  -authenticationKeyID "$ASC_KEY_ID" \
  -authenticationKeyIssuerID "$ASC_ISSUER_ID" 2>&1 | tee ~/build-upload.log
```

The last command ends with **`** EXPORT SUCCEEDED **`** and `Upload
succeeded`. The build then shows in App Store Connect › the app ›
**TestFlight** after 10–30 minutes of processing (Apple emails when it is
ready or if it has a problem).

Do not commit the changes these commands make to
`ios/App/App.xcodeproj/project.pbxproj` (version and build number); discard
them with `git checkout ios/App/App.xcodeproj/project.pbxproj`.

**Alternative, with the Xcode window** (if the owner signs in on the Mac
instead of using the API key): Xcode › Settings › Accounts › **+** › Apple
ID (the owner enters the code from their phone) › target **App** › Signing &
Capabilities › *Automatically manage signing*, Team **XZS779BUTF** › at the
top choose **Any iOS Device (arm64)** › Product › **Archive** › in the
Organizer: **Distribute App** › **App Store Connect** › Distribute. Run step
1 of the commands above first, for the build number.

---

## F. Screenshots for the App Store

The App Store needs iPhone screenshots of 6.9" (1320 × 2868 pixels).

1. Run the app on the **iPhone 17 Pro Max** simulator (part D).
2. Make each screen look right (signed in to the demo account, real data),
   then **⌘S** in the Simulator: the PNG lands on the Desktop at exactly the
   right size.
3. Suggested set (3 to 10): home and search; flight results; a hotel with its
   map; checkout; My bookings with a ticket; eSIMs; the home screen in
   Arabic (the website's language menu).
4. Send them to the owner.

---

## G. The owner: TestFlight, then publish

1. App Store Connect › the app › **TestFlight** › Internal Testing › **+** ›
   add yourself (and testers). Install with the **TestFlight** app on an
   iPhone and run the test list in `docs/store-checklist.md` §9 (sign-in,
   payment with 3-D Secure, sharing a ticket, camera upload, offline page,
   a bookingbye.com link from Notes opening the app, a test push).
2. Fill in the App Store page (`docs/store-checklist.md` §7–§9): App
   Privacy answers, screenshots, description, keywords, support URL, age
   rating, **App Review Information** with the demo account and the notes
   text.
3. On the version page › **Build** › choose the uploaded build › **Add for
   Review** › **Submit for Review**.

---

## Troubleshooting

| Message | What to do |
|---|---|
| `xcodebuild: error: … requires Xcode 26` / SDK too old | Update Xcode (part B.1). |
| Package resolution fails, or `missing package product 'CapacitorFirebaseMessaging'` | Run `npx cap sync ios` again; in Xcode: File › Packages › Reset Package Caches. |
| `No profiles for 'com.bookingbye.app' were found` | Keep `-allowProvisioningUpdates` and the three `-authentication…` options on **both** commands; the API key must have the **Admin** role. |
| `No signing certificate "iOS Development" found` during archive | Use the Xcode-window alternative in part E once (it creates the certificate), then the commands work too. |
| `Provisioning profile doesn't include the aps-environment entitlement` (or Associated Domains) | The owner: developer.apple.com › Identifiers › `com.bookingbye.app` › tick **Push Notifications** and **Associated Domains** › Save. Then archive again. |
| Upload: `The bundle version must be higher than the previously uploaded version` | Run step 1 of part E again (new build number), then archive and upload again. |
| Build warning `GoogleService-Info.plist is missing: push notifications are off` | Put the file in `ios/App/App/` (part B.5). The build works without it, but push won't. |
| Anything else | Send the owner the log file (`~/build-simulator.log`, `~/build-archive.log` or `~/build-upload.log`). |

When finished, delete `~/private_keys/AuthKey_*.p8` from the Mac if it is
shared with other people.
