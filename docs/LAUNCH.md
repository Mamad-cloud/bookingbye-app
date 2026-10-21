# Launch runbook

Everything needed to take BookingBye to the App Store and Google Play, in
the order to do it: accounts, keys, every setting on every console, every
environment variable on the website and API servers, the CI secrets, the
first builds, and submission. Do the phases in order; each says what it
produces and where that goes next.

Written 2026-10-21 from the code of all three repos (app, frontend, api).
Facebook sign-in is turned off for now and left out; turning it back on is
in the appendix.

Placeholders: `<TEAMID>` Apple Team ID, `<KEYID>` a key's ID, and so on.
Never commit any value marked **secret**.

---

## Phase 0 – Deploy the finished code (before anything below)

The code is done in all three repos (checked 2026-10-21; see
[PROGRESS.md](PROGRESS.md)). Make sure the latest commits are live:

1. **API** (HEAD `71af722` or later): first add the supplier variables from
   Phase 6.2 ("moved out of the code"), then deploy (Phase 6.3).
2. **Website** (HEAD `eeee8c2` or later): `git pull && npm ci && npm run build
   && pm2 restart bookingbynext_app --update-env`. Check that it is live:
   signing in with Google inside the app must work, and
   `https://bookingbye.com//oauth/success?token=x` opened in the app must
   not sign anyone in.
3. **Secrets found in the repos.** The owner confirmed (2026-10-21) that the
   supplier and payment credentials only work from the server's IP, so the
   copies found in the code need no rotation. The one exception: the Google
   OAuth client secret is not IP-bound, so if the `GOCSPX-…` value that was
   in the frontend's `.env.example` is the one the API uses in production,
   reset it (Google Cloud Console › Credentials › the OAuth client › *Reset
   secret*) and put the new one in the API's `GOOGLE_CLIENT_SECRET`.

---

## Phase 1 – Accounts

| Account | Notes |
|---|---|
| **Apple Developer Program** | As an *organization* (needs a D-U-N-S number). developer.apple.com/programs. 99 USD/year. |
| **Google Play Console** | As an *organization* (D-U-N-S). play.google.com/console. 25 USD once. Organization accounts skip the 12-tester/14-day rule. |
| **Firebase** | console.firebase.google.com, signed in with the company Google account. |
| **CI** | Either **Codemagic** (codemagic.io, connect the Git provider) or **GitHub Actions** (push the app repo to a private GitHub repo). Pick one. |

---

## Phase 2 – Apple

1. **Team ID**: developer.apple.com › Account › Membership details › Team ID
   → `<TEAMID>` (10 characters).
2. **App ID**: Certificates, Identifiers & Profiles › Identifiers › **+** ›
   App IDs › App:
   - Description `BookingBye`, Bundle ID **Explicit** `com.bookingbye.app`
   - Capabilities: tick **Associated Domains** and **Push Notifications**.
3. **APNs key** (for Firebase): Keys › **+** › name `BookingBye APNs`, tick
   **Apple Push Notifications service (APNs)** › Continue › Register ›
   **Download** the `.p8` (only downloadable once; **secret**). Note its
   **Key ID**.
4. **App Store Connect record**: appstoreconnect.apple.com › Apps › **+** ›
   New App: platform iOS, name **BookingBye**, primary language English,
   bundle ID `com.bookingbye.app`, SKU `bookingbye-ios`, full access.
5. **App Store Connect API key** (CI signs and uploads with it): Users and
   Access › Integrations › App Store Connect API › Team Keys › **+** ›
   name `BookingBye CI`, access **Admin** › Generate › **Download** the
   `.p8` (**secret**, once). Note the **Key ID** and the **Issuer ID** shown
   above the list.

Produces: `<TEAMID>`, APNs `.p8` + Key ID, API `.p8` + Key ID + Issuer ID.

---

## Phase 3 – Firebase (push notifications)

1. Create project **BookingBye** (Google Analytics for the project: off; the
   site already has its own).
2. **Android app**: Project settings › Your apps › Add app › Android, package
   `com.bookingbye.app`, nickname `BookingBye Android` › Register ›
   **Download `google-services.json`** (**secret-ish**: keep out of git).
3. **iOS app**: Add app › iOS, bundle ID `com.bookingbye.app` › Register ›
   **Download `GoogleService-Info.plist`** (same).
4. **APNs**: Project settings › Cloud Messaging › Apple app configuration ›
   APNs Authentication Key › Upload the APNs `.p8` from Phase 2.3 with its
   Key ID and `<TEAMID>`.
5. **Backend credential**: Project settings › Service accounts › Firebase
   Admin SDK › **Generate new private key** → a JSON file (**secret**), for
   the API server (Phase 6).
6. Cloud Messaging API (V1) must show **Enabled** (Project settings › Cloud
   Messaging). The legacy API is not used.

Produces: `google-services.json`, `GoogleService-Info.plist`, the service
account JSON.

---

## Phase 4 – Google Play

1. **Create the app**: Play Console › Create app: name **BookingBye**, default
   language English (United States), App, Free, accept the declarations.
2. **Upload key** (on your PC, once; keep the file and passwords in a
   password manager plus an offline copy):
   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
     -keystore bookingbye-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
   Produces `bookingbye-upload.jks`, its store password, alias `upload`, key
   password (**secret**).
3. **Upload key fingerprint** (for the website, Phase 9):
   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore bookingbye-upload.jks -alias upload
   ```
   Copy the **SHA256** line (`AA:BB:…`).
4. **Service account for CI uploads** (skip if you upload by hand):
   - console.cloud.google.com › pick (or create) a project › IAM & Admin ›
     Service Accounts › Create: name `play-upload` › Done › open it › Keys ›
     Add key › JSON → download (**secret**).
   - APIs & Services › Library › enable **Google Play Android Developer API**.
   - Play Console › Users and permissions › Invite new users › the service
     account's email › App permissions: BookingBye › **Release apps to
     testing tracks** and **Release to production…** (or Admin) › Invite.

Produces: the upload keystore and passwords, its SHA-256, the Play service
account JSON.

---

## Phase 5 – Google Cloud: sign-in and maps

In the Google Cloud project that owns the existing OAuth client and the Maps
key (console.cloud.google.com):

1. **OAuth client** (APIs & Services › Credentials › the Web client used by
   the API):
   - Authorized redirect URIs: `https://api.bookingbye.com/api/v2/auth/google/callback`
     (only this one is needed).
   - Reset the secret (Phase 0.3) if not done.
2. **OAuth consent screen / Branding**: app name **BookingBye**, support
   email, logo, home page `https://bookingbye.com`, privacy policy
   `https://bookingbye.com/privacy`, terms `https://bookingbye.com/terms`,
   authorized domain `bookingbye.com`; scopes `openid`, `email`,
   `profile`; publishing status **In production**.
3. **Maps browser key** (`NEXT_PUBLIC_GOOGLE_MAP_KEY`): Credentials › the key
   › Application restrictions: **Websites**, add `https://bookingbye.com/*`
   (the app's webview loads that origin, so the same restriction works in the
   app); API restrictions: Maps JavaScript API (and any other the site uses).

---

## Phase 6 – API server (`api.bookingbye.com`)

### 6.1 Firebase file

Copy the service account JSON (Phase 3.5) to the server, outside the web
root, readable only by the PHP user:

```bash
sudo mkdir -p /etc/bookingbye
sudo cp firebase-service-account.json /etc/bookingbye/
sudo chown www-data:www-data /etc/bookingbye/firebase-service-account.json
sudo chmod 600 /etc/bookingbye/firebase-service-account.json
```

### 6.2 Environment (`.env`)

**New or changed for the app** – check every one:

| Variable | Value |
|---|---|
| `APP_ENV` | `production` |
| `APP_DEBUG` | `false` |
| `APP_URL` | `https://api.bookingbye.com` |
| `TRUSTED_HOSTS` | `api.bookingbye.com` (add any internal host or IP that health checks or the website server use) |
| `NEXT_PUBLIC_FRONTEND_URL` | `https://bookingbye.com` (origin only, no path) |
| `QUEUE_CONNECTION` | `redis` (never `sync`) |
| `CACHE_DRIVER` | `redis` |
| `SESSION_DRIVER` | `database` |
| `SESSION_SECURE_COOKIE` | `true` |
| `JWT_SECRET` | **secret**, existing; must be 32+ characters (else `php artisan jwt:secret` once; that signs everyone out) |
| `GOOGLE_CLIENT_ID` | **secret-ish**: the OAuth client ID (Phase 5) |
| `GOOGLE_CLIENT_SECRET` | **secret**: the reset secret (Phase 0.3) |
| `GOOGLE_REDIRECT_URI` | `https://api.bookingbye.com/api/v2/auth/google/callback` |
| `FIREBASE_CREDENTIALS` | `/etc/bookingbye/firebase-service-account.json` |
| `FIREBASE_PROJECT` | `app` |
| `PUSH_ENABLED` | `true` |
| `PUSH_SITE_URL` | `https://bookingbye.com` |
| `PUSH_QUEUE` | `default` |
| `PUSH_PAYMENT_FAILED_DELAY` | `10` |
| `PUSH_UNPAID_AFTER_MINUTES` | `30` |
| `PUSH_SINCE` | leave empty (the migration records the start) |
| `LARAVEL_CLOUDFLARE_ENABLED` | `true` |
| `LARAVEL_CLOUDFLARE_REPLACE_IP` | `false` |
| `LOG_LEVEL` | `warning` |
| `NASS_ENV` | `prod` |
| `QICARD_ENV` | `prod` |

**Supplier credentials that moved out of the code** (API commit `71af722`
and the two before it). They used to be written in `config/site.php` /
`TourVisioService.php`; now they come only from `.env`, with empty defaults.
**Add them before deploying that version**, or those suppliers stop
working. Use the production values the server already has:

| Variable | Value |
|---|---|
| `TRAVELOPRO_USER_ID`, `TRAVELOPRO_USER_PASSWORD`, `TRAVELOPRO_IP_ADDRESS` | **secret**: Travelopro account |
| `TRAVELOPRO_ACCESS` | `Production` |
| `BILETDUKKANI_USERNAME`, `BILETDUKKANI_PASSWORD` | **secret**: Bilet Dükkanı account |
| `TOURVISIO_API_AGENT`, `TOURVISIO_API_USERNAME` | were `BOOKINGBYE` by default; set them explicitly |
| `TOURVISIO_API_PASSWORD` | **secret** |
| `EASYBOOKING_API_URL` | the production URL (the default is the **test** API) |
| `EASYBOOKING_API_USERNAME`, `EASYBOOKING_API_PASSWORD` | **secret** |

**Existing, keep as they are** (listed so nothing is forgotten; all
**secret** unless a value is given):

- Core: `APP_NAME=BookingBye`, `APP_KEY` (never rotate), `BUSINESS_TIMEZONE=Asia/Baghdad`,
  `ADMIN_NOTIFY_EMAILS`, `BOOKING_COPY_EMAILS`, `PAYMENT_RESUME_WINDOW_HOURS=24`,
  `CURRENCY_RATES_ENDPOINT`, `CURRENCY_RATES_API_KEY`, `CURRENCY_RATES_BASE=USD`.
- Database: `DB_CONNECTION=mysql`, `DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD`.
- Redis: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` (+ `REDIS_CLIENT`, `REDIS_DB`, `REDIS_CACHE_DB` defaults).
- Mail: `MAIL_MAILER=smtp`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`,
  `MAIL_ENCRYPTION`, `MAIL_FROM_ADDRESS`, `MAIL_FROM_NAME=BookingBye`.
- WhatsApp sign-in codes: `TWILIO_SID`, `TWILIO_TOKEN`, `TWILIO_WHATSAPP_FROM` (`+E.164`, no `whatsapp:`).
- Payments (live: Tap, Nass, QiCard): `TAP_MERCHANT_ID`, `TAP_SECRET_KEY`, `TAP_PUBLISHABLE_KEY`;
  `NASS_API_USERNAME`, `NASS_API_PASSWORD` (bases have defaults);
  `QICARD_API_USERNAME`, `QICARD_API_PASSWORD`, `QICARD_TERMINAL_ID` (prod base defaults to `https://3ds-api.qi.iq`).
  Not offered today, keep if present: FIB_*, AREEBA_*, STRIPE_*, PAYBY_*, NGENIUS_*, MYFATOORAH_*.
- Suppliers: TOURVISIO_* (URL, AGENT, USERNAME, PASSWORD, CULTURE), EASYBOOKING_* (URL, USERNAME,
  PASSWORD), WONDERTRAVEL_* (BASE_URL, API_KEY, CAPTURE, HOLD_FIRST), BRK_* (API_URL, EMAIL,
  PASSWORD, ACCESS_TOKEN), ALTHURRA_*, BILETDUKKANI_* (production URLs
  `https://api.biletdukkani.com` and `https://yetki.biletdukkani.com`), ETG_* (API_URL production,
  KEY_ID, API_KEY, …), EBOOKING_CLIENT_ID, EBOOKING_CLIENT_SECRET, IWAY_* (production URL),
  ZETEXA_*. The API's `.env.example` lists them all.
- Storage: `FILESYSTEM_DISK=local`, `FILAMENT_FILESYSTEM_DISK=public`.
- Logging: `LOG_CHANNEL=stack` (or `daily`).

### 6.3 Deploy commands (in this order)

```bash
cd /path/to/api
git pull
composer install --no-dev -o
php artisan migrate --force          # push log, deletion requests, device_tokens widening, users.facebook_id
php artisan config:cache             # only after the env() fixes from Phase 0.2
php artisan route:cache
php artisan view:cache
php artisan storage:link             # once
php artisan cloudflare:reload
php artisan horizon:terminate        # supervisor restarts Horizon with the new code and config
```

Running on the server permanently:

- **Horizon** under supervisor/systemd (`php artisan horizon`); push, mail and
  account-deletion jobs run on the `default` queue in `supervisor-1`.
- **Scheduler**: crontab `* * * * * cd /path/to/api && php artisan schedule:run >> /dev/null 2>&1`
  (runs the unpaid-payment sweep, ticketing, reconciliation, Cloudflare ranges, etc.).

### 6.4 Cloudflare (both hostnames)

- The API must receive `Host: api.bookingbye.com` (default).
- **Security › WAF › Custom rules**: add a rule *Skip* (all remaining custom
  rules, Bot Fight Mode / Super Bot Fight Mode, rate limiting) for
  `http.request.uri.path starts_with "/.well-known/"` on `bookingbye.com`,
  so Apple's and Google's verifiers always get the JSON, never a challenge.
- Do not cache `/.well-known/*` for long (the site sends `max-age=3600`).

---

## Phase 7 – Website server (`bookingbye.com`, PM2 on the VPS)

Variables go in `/var/www/bookingbyenext/.env.production`. `NEXT_PUBLIC_*`
are baked in at build time; the others are read when the server starts.

| Variable | Value | When read |
|---|---|---|
| `NEXT_PUBLIC_BASE_URL` | `https://api.bookingbye.com/api/v2` | build |
| `NEXT_PUBLIC_BACKEND_ORIGIN` | `https://api.bookingbye.com` | build |
| `NEXT_PUBLIC_GOOGLE_MAP_KEY` | the Maps browser key (Phase 5.3) | build |
| `NEXT_PUBLIC_GA_ID` | `G-85M5Z10LVT` (or the GA4 property's ID) | build |
| `GOOGLE_CLIENT_ID` | same as the API's | start |
| `GOOGLE_REDIRECT_URI` | `https://api.bookingbye.com/api/v2/auth/google/callback` | start |
| `APP_ANDROID_PACKAGE` | `com.bookingbye.app` | start |
| `APP_ANDROID_SHA256_FINGERPRINTS` | set in Phase 9 | start |
| `APP_IOS_APP_IDS` | `<TEAMID>.com.bookingbye.app` | start |

Remove if present: `GOOGLE_CLIENT_SECRET`, `CLOUDINARY_*`,
`NEXT_PUBLIC_AUTH_BASE_URL` (unused). Leave `FACEBOOK_*` unset while Facebook
is off. Never set `NODE_ENV` by hand.

```bash
cd /var/www/bookingbyenext
git pull
npm ci
npm run build                               # after any NEXT_PUBLIC_* change or code change
pm2 restart bookingbynext_app --update-env  # after any change
```

Check: https://bookingbye.com/.well-known/apple-app-site-association shows
`"appIDs":["<TEAMID>.com.bookingbye.app"]` once `APP_IOS_APP_IDS` is set.

---

## Phase 8 – CI secrets

Base64 of a file, on Windows (PowerShell), copied to the clipboard:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\keys\bookingbye-upload.jks")) | Set-Clipboard
```

### Option A – Codemagic (`codemagic.yaml`)

| Where in Codemagic | What |
|---|---|
| Team settings › Team integrations › Developer Portal › Manage keys › Add key | Name **`BookingBye App Store Connect`** (exactly), Issuer ID, Key ID, the API `.p8` (Phase 2.5) |
| Team settings › Code signing identities › Android keystores › Add | the `.jks`, its passwords, alias `upload`, reference name **`bookingbye_upload`** |
| App › Environment variables, group **`firebase`** (mark secure) | `GOOGLE_SERVICES_JSON_BASE64`, `GOOGLE_SERVICE_INFO_PLIST_BASE64` |
| App › Environment variables, group **`google_play`** (secure) | `GCLOUD_SERVICE_ACCOUNT_CREDENTIALS` = the Play service account JSON's content (Phase 4.4) |

### Option B – GitHub Actions (`.github/workflows/`)

Repository › Settings › Secrets and variables › Actions › New repository secret:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | base64 of `bookingbye-upload.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | store password |
| `ANDROID_KEY_ALIAS` | `upload` |
| `ANDROID_KEY_PASSWORD` | key password |
| `GOOGLE_SERVICES_JSON_BASE64` | base64 of `google-services.json` |
| `PLAY_SERVICE_ACCOUNT_JSON` | the Play service account JSON's content (plain text) |
| `APPLE_TEAM_ID` | `<TEAMID>` |
| `APP_STORE_CONNECT_KEY_ID` | API key ID (Phase 2.5) |
| `APP_STORE_CONNECT_ISSUER_ID` | Issuer ID |
| `APP_STORE_CONNECT_KEY_BASE64` | base64 of the API `.p8` |
| `GOOGLE_SERVICE_INFO_PLIST_BASE64` | base64 of `GoogleService-Info.plist` |

The app repo itself has no environment variables: its settings are in
`capacitor.config.js` (website address, allowed hosts) and the version in
`package.json`.

---

## Phase 9 – First builds and the deep-link files

1. **Android, first time by hand**: run the Android workflow; download the
   `.aab` artifact. Play Console › BookingBye › Test and release › Testing ›
   **Internal testing** › Create new release › accept **Play App Signing** ›
   upload the `.aab` › name `1.0.0` › Save › Review › Start rollout. Add
   yourself (and testers) under Testers.
2. **Fingerprints**: Play Console › Test and release › App integrity › App
   signing › copy the **SHA-256** of the *App signing key certificate* and of
   the *Upload key certificate*. On the website server:
   `APP_ANDROID_SHA256_FINGERPRINTS=<app signing SHA-256>,<upload SHA-256>` →
   `pm2 restart bookingbynext_app --update-env`. Check
   https://bookingbye.com/.well-known/assetlinks.json names both.
3. **iOS**: run the iOS workflow; the build appears in App Store Connect ›
   TestFlight after processing (10–30 min). Add internal testers.
4. **Install and test** on real phones: Play internal testing link; TestFlight.
   Run the test list in [store-checklist.md](store-checklist.md) §9. Also:
   - Android: `adb shell pm verify-app-links --re-verify com.bookingbye.app`
     then `adb shell pm get-app-links com.bookingbye.app` → `verified`;
   - iOS: a bookingbye.com link in Notes opens the app (Apple's CDN can take
     up to a day after `APP_IOS_APP_IDS` is set);
   - send yourself a push (Firebase › Messaging › New campaign › test on a
     device token, with custom data `url` = a bookingbye.com page).
5. After the next CI run, CI uploads to Play by itself (draft on internal
   testing until the first production release).

---

## Phase 10 – Store listings and review

Everything is spelled out in [store-checklist.md](store-checklist.md):
§7 privacy answers (App Store App Privacy, Play Data safety, content
ratings, ads, target audience), §8 listings and screenshots, §9 review notes
and the demo account. Short version:

- **Both**: privacy policy URL `https://bookingbye.com/privacy`; account
  deletion inside the app (Account › Delete) and on the web
  `https://bookingbye.com/delete-account` (Play's *Data deletion* URL).
- **App Store**: App Information (category Travel, age rating 4+), Pricing
  (Free), App Privacy, the version page (screenshots 6.9", description,
  keywords, support URL `https://bookingbye.com/contact`), App Review
  Information (demo account + the notes text from §9), select the build ›
  **Add for Review** › Submit.
- **Play**: Main store listing (short and full description, icon
  `resources/store/play-icon-512.png`, feature graphic
  `resources/store/play-feature-graphic.png`, phone screenshots), App content
  (privacy policy, app access with the demo account, ads: no, content
  rating, target audience, data safety, government app: no, financial
  features: none), then Production › Create release › promote the tested
  build › staged rollout 20 % › Send for review.

---

## Phase 11 – Release and after

- Release a new version: `version` in `package.json` → `npm run sync` →
  commit → `git tag v1.0.1 && git push --tags` (both CIs build tags).
- Watch: Play Console › Android vitals; App Store Connect › TestFlight
  crashes / Xcode Organizer; the API's Horizon dashboard (`/horizon-admin`)
  for failed push jobs; Firebase › Messaging reports.
- The website ships without an app release; only changes to the app's own
  behaviour (new payment gateway domain, new plugin) need one.

---

## Appendix – iOS first, by hand on a Mac (no CI)

For building and uploading from a (cloud) Mac with Xcode instead of
Codemagic/GitHub Actions. Needs Xcode **26** or later (App Store uploads must
use the iOS 26 SDK), Node 24 and git on the Mac.

1. **Get the code there**: push this repo to a private Git host (GitHub:
   create an empty private repo, then on the PC
   `git remote add origin <url>` and `git push -u origin main`), and clone it
   on the Mac.
2. **Prepare** (on the Mac, in the repo):
   ```bash
   npm ci
   npx cap sync ios          # also creates ios/App/CapApp-SPM/symlinks
   node scripts/sync-version.js
   open ios/App/App.xcodeproj
   ```
   Wait for Xcode to finish "Resolving Package Graph" (Firebase is large).
3. **Simulator** (no paid account needed): pick an iPhone simulator at the
   top, Product › Run. Check the splash, the site, links, offline page
   (Simulator › Features › Network Link Conditioner, or switch the Mac's
   Wi-Fi off), Google sign-in.
4. **Signing** (after Phase 2): Xcode › Settings › Accounts › add the Apple ID
   of the developer account. Target **App** › Signing & Capabilities: tick
   *Automatically manage signing*, Team = the company. Push Notifications
   and Associated Domains appear from `App.entitlements`.
5. **Firebase** (after Phase 3): put `GoogleService-Info.plist` in
   `ios/App/App/` (not committed; the build phase copies it in).
6. **Build number**: each upload needs a higher one. On the Mac:
   `APP_BUILD_NUMBER=$(node scripts/build-number.js) node scripts/sync-version.js`.
7. **Upload**: device menu › *Any iOS Device (arm64)* › Product › Archive ›
   Organizer › Distribute App › **App Store Connect** › Upload (automatic
   signing). After processing it appears in App Store Connect › TestFlight;
   install it on your iPhone with the TestFlight app (a cloud Mac cannot
   reach a USB iPhone).
8. **Screenshots**: run the *iPhone 17 Pro Max* (6.9") simulator and press
   ⌘S: the saved images are the size App Store Connect wants.
9. Then Phase 9 step 4 (tests), set `APP_IOS_APP_IDS` on the website, and
   Phase 10 (App Store part).

## Appendix – turning Facebook sign-in back on

1. developers.facebook.com › the app › Facebook Login › Settings › Valid OAuth
   Redirect URIs: `https://api.bookingbye.com/api/v2/auth/facebook/callback`;
   Settings › Basic: privacy policy `https://bookingbye.com/privacy`, data
   deletion URL `https://bookingbye.com/delete-account`; app mode **Live**.
2. API `.env`: `FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET` (**secret**),
   `FACEBOOK_REDIRECT_URI=https://api.bookingbye.com/api/v2/auth/facebook/callback`,
   `FACEBOOK_GRAPH_VERSION=v26.0` (a version Meta still supports).
3. Website: the same `FACEBOOK_CLIENT_ID`, `FACEBOOK_REDIRECT_URI`,
   `FACEBOOK_GRAPH_VERSION`; enable the buttons in the code; restart.
4. Add Facebook to the App Review notes (store-checklist §9). No app release needed.
