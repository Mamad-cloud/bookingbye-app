# Store checklist

Everything between this repo and BookingBye being live on the App Store and
Google Play, in the order it is easiest to do. Items marked **(website)** are
for the website or backend, not this repo.

## 1. Accounts

- [ ] **Apple Developer Program** as an organization (needs a D-U-N-S
      number; the listing then shows the company name). 99 USD a year.
- [ ] **Google Play Console** as an organization (D-U-N-S again). A new
      *personal* account must run a closed test with 12 testers for 14 days
      before it may publish; an organization account need not.
- [ ] **Firebase** project, for push: [docs/push.md](push.md), section 1.

## 2. Identifiers and keys

Apple (developer.apple.com › Certificates, Identifiers & Profiles):

- [ ] Identifier `com.bookingbye.app` with **Push Notifications** and
      **Associated Domains** turned on.
- [ ] APNs key (.p8), uploaded to Firebase (push.md 1.4).
- [ ] App Store Connect › Apps › New app: name **BookingBye**, bundle ID
      `com.bookingbye.app`, primary language English, SKU e.g. `bookingbye-ios`.
- [ ] App Store Connect › Users and Access › Integrations › App Store Connect
      API: a key with role **Admin** (CI uses it to sign and upload).

Google:

- [ ] Play Console › Create app: BookingBye, App, Free.
- [ ] **Upload key**, made once and kept safe (a password manager plus an
      offline copy). If it is lost, Play support can reset it, slowly:
      ```
      keytool -genkeypair -v -keystore bookingbye-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
      ```
      `keytool` ships with Android Studio:
      `"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`.
- [ ] Play App Signing: accept it at the first upload (Play keeps the real
      signing key; you sign uploads with the upload key).
- [ ] Google Cloud service account with access to the Play app (Play Console
      › Users and permissions › invite it, with release permissions), and its
      JSON key, for CI uploads.

## 3. CI

Use either; both are set up and number builds the same way.

- **Codemagic** ([codemagic.yaml](../codemagic.yaml)): connect the repo, then
  in the web app add the App Store Connect key ("BookingBye App Store
  Connect"), the upload keystore (reference `bookingbye_upload`), and the
  variable groups `firebase` and `google_play` listed at the top of the file.
- **GitHub Actions** ([.github/workflows](../.github/workflows)): push the
  repo to GitHub and add the secrets listed at the top of `android.yml` and
  `ios.yml`. macOS minutes count 10× against the free allowance.

To make a base64 secret from a file on Windows (PowerShell):
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("bookingbye-upload.jks")) | Set-Clipboard`.

- [ ] **First Android upload by hand.** Play's API cannot make an app's
      first release. Download the `.aab` from the CI run and upload it in
      Play Console › Testing › Internal testing. After that, CI uploads.
- [ ] Then read Play's **upload key and app signing key SHA-256** (Play
      Console › Test and release › App integrity) for the website (5.2).

## 4. Website, before submitting (website)

The full list, with reasons, is in [PROGRESS.md](PROGRESS.md#website-to-do).
The ones that block a submission:

- [ ] Deploy the app pages: `/app-auth` and `/.well-known/*` return 404
      today, so sign-in from the app fails.
- [ ] Set `APP_ANDROID_PACKAGE`, `APP_ANDROID_SHA256_FINGERPRINTS` and
      `APP_IOS_APP_IDS` ([README](../README.md#sign-in-and-links-into-the-app)).
- [ ] A **privacy policy** page (section 5).
- [ ] **Account deletion** started inside the app (section 6).
- [ ] Top safe-area padding on the header, so it clears the status bar
      and the notch in the app.
- [ ] In the app (`isInApp()`), configure Google Analytics with
      `allow_google_signals: false` and `allow_ad_personalization_signals: false`.
      Without it, Apple counts GA as "tracking", which needs the App Tracking
      Transparency prompt (section 7).
- [ ] Push: the permission request and the backend ([push.md](push.md)).
      Not needed to submit, but Apple's reviewer will look for the native
      features listed in section 9.

## 5. Privacy policy

Both stores require a public URL, e.g. `https://bookingbye.com/privacy`, in
English and ideally Arabic and Sorani. It should say, in plain words:

- **Who:** the company, its address, a contact email for privacy questions.
- **What is collected:** account details (name, email, phone); traveller
  details for bookings (names, dates of birth, passport numbers and
  nationality); uploaded documents (passport and visa scans, photos);
  bookings and payments (amounts, references; cards are entered on the
  payment providers' pages and not stored by BookingBye); messages to
  support (tawk.to chat); usage analytics (Google Analytics); device push
  tokens (Firebase); location, only if a page asks and the customer allows it.
- **Why:** to make and manage bookings, issue tickets and vouchers, process
  visa applications, take payments, support customers, send booking
  notifications, and improve the service.
- **Who receives it:** airlines, hotels and other suppliers, to fulfil a
  booking; payment providers (Areeba, QiCard, FIB, Nass, and the others in
  use); embassies and visa processors, for visa services; Google (Analytics,
  Firebase, Maps); tawk.to. State that data is not sold.
- **How long it is kept,** and how it is protected (HTTPS, access control).
- **The customer's rights:** to see, correct and delete their data, and how;
  a link to account deletion (section 6).
- **Children:** not directed at children under 13 (or 16, as applicable).
- **Changes:** how updates are announced, and the date of this version.

## 6. Account deletion (website)

- **Apple (guideline 5.1.1(v)).** An app that lets people create an account
  must let them *start* deleting it inside the app. A support ticket is
  acceptable only if it is reached from inside the app, needs no email or
  phone call, says clearly what happens and when, and the account really is
  deleted. Best: Account › Delete my account → confirm → the account is
  deleted, or scheduled for deletion within a stated period. Bookings and
  invoices the law requires you to keep may stay, said so on that screen.
- **Google Play.** The same path inside the app, **and** a public web page
  where someone who no longer has the app can ask for deletion (e.g.
  `https://bookingbye.com/delete-account`). Its URL goes into the Data
  safety form.

## 7. Privacy answers

### App Store Connect › App Privacy

Answer per data type: *collected*, *linked to the user*, *used for
tracking*, *purposes*. Assuming the GA settings in 4 (no tracking):

| Data type | Collected | Linked | Tracking | Purposes |
|---|---|---|---|---|
| Contact Info: Name, Email, Phone Number | yes | yes | no | App Functionality |
| User Content: Photos or Videos, Other User Content (documents) | yes | yes | no | App Functionality |
| User Content: Customer Support | yes | yes | no | App Functionality |
| Identifiers: User ID | yes | yes | no | App Functionality |
| Purchases: Purchase History | yes | yes | no | App Functionality |
| Usage Data: Product Interaction | yes | no | no | Analytics |
| Diagnostics | only if the site reports errors somewhere | | | |
| Location: Precise Location | only once the site uses location | yes | no | App Functionality |
| Financial Info: Payment Info | no: entered on the payment providers' pages | | | |

"Do you or your third-party partners use data for tracking?": **No**,
provided 4's GA settings are in place and no advertising pixels are added.

### Play Console › App content › Data safety

- Data collected, all **required**, encrypted in transit, deletable on request:
  - Personal info: name, email address, phone number, user IDs: App functionality, Account management
  - Financial info: purchase history: App functionality
  - Photos and videos; Files and docs (uploaded documents): App functionality
  - Messages: other in-app messages (support chat): App functionality, Customer support
  - App activity: app interactions: Analytics
  - Device or other IDs (push token, analytics ID): App functionality, Analytics
  - Location: approximate and precise: only once the site uses it
- Data **shared**: none, in Google's sense. Suppliers, payment providers and
  the services above act on BookingBye's behalf or on the customer's own
  request, which Google does not count as sharing.
- Account deletion URL: the page from 6.

### Other declarations

- **Export compliance (Apple):** answered in the app (`ITSAppUsesNonExemptEncryption = NO`).
- **Content rating:** Apple age rating questionnaire (no objectionable
  content → 4+); Play IARC questionnaire (Travel, no user-to-user content;
  the support chat is with staff).
- **Play target audience:** 18 and over (bookings and payments), or 13+.
- **Play ads:** "No, my app does not contain ads".
- **Play app access:** give a test account (section 9).
- **Play permissions:** camera and location are foreground only and asked
  in context; no declaration forms are needed. Notifications are asked for
  by the site.

## 8. Store listings

| | App Store | Google Play |
|---|---|---|
| Name | BookingBye (30 chars max) | BookingBye (30) |
| Subtitle / short description | e.g. "Flights, hotels and eSIMs" (30) | e.g. "Book flights, hotels, eSIMs, transfers and tours in a few taps" (80) |
| Description | up to 4000 chars | up to 4000 chars |
| Keywords | 100 chars, comma-separated | (none) |
| Category | Travel | Travel & Local |
| Support URL | https://bookingbye.com/contact | contact email (required) and website |
| Privacy policy URL | from 5 | from 5 |
| Icon | from the app (1024 px) | `resources/store/play-icon-512.png` |
| Feature graphic | | `resources/store/play-feature-graphic.png` (logo on navy; a designed one with a tagline converts better) |

Add an **Arabic** listing as well; neither store offers Sorani as a listing language.

**Screenshots** (take them in the app, from a phone or an emulator/simulator):

- App Store: **6.9" iPhone** (1320 × 2868 portrait), 3 to 10. Apple scales
  them for smaller iPhones. iPhone only, so no iPad screenshots.
- Google Play: **phone**, 2 to 8, 9:16 (e.g. 1080 × 1920), PNG or JPEG.
- Suggested set: home and search; flight results; a hotel with its map;
  checkout; My bookings with a ticket being shared; eSIMs; the same home
  screen in Arabic. Make sure the header is clear of the status bar (4) first.

## 9. App Review notes (App Store) and testing

Apple rejects apps that are only a website (guideline 4.2). In **App Review
Information › Notes**, list what the app adds natively:

> BookingBye is our booking service, built for the app with native features:
> push notifications for booking updates (tapping one opens that booking);
> bookingbye.com links open in the app (Universal Links); Google sign-in
> runs in the system browser and returns to the app; tickets and
> vouchers are saved and shared through the iOS share sheet; visa documents
> can be photographed with the camera; a native offline screen when there is
> no connection; payments, phone numbers, email and bank apps open in the
> right place.
> Test account: <email> / <password>, with a past booking to look at.

- [ ] A **demo account** with at least one booking (Apple and Play both ask).
- [ ] Mention Facebook sign-in in the notes only once it works end to end
      (on 2026-10-20 `/api/auth/facebook` returned 405 and the site's
      Facebook buttons were disabled).
- [ ] Before each submission, on a real phone of each kind:
  - [ ] first launch: navy splash, then the site, with no white flash;
  - [ ] Google sign-in comes back signed in (and Facebook, once its buttons are turned on);
  - [ ] a test payment (with 3-D Secure) stays in the app and ends on `/payment-success`;
  - [ ] a ticket PDF opens the share sheet;
  - [ ] a visa upload offers the camera; a photo uploads;
  - [ ] footer links open the browser; phone and email links open their apps;
  - [ ] airplane mode shows the offline page; turning it off reloads;
  - [ ] a bookingbye.com link in Notes or Messages opens the app; a `/payment-success` link does not;
  - [ ] a test push arrives; tapping it opens its page, from a closed app too;
  - [ ] the site's dark mode turns the status bar icons light;
  - [ ] Arabic and Sorani show right to left, the app's own texts included.

## 10. Release

- [ ] Version in `package.json`, `npm run sync`, commit, tag `v1.0.0`, push the tag (both CIs build on tags).
- [ ] TestFlight: test, then App Store Connect › the version › add the build, Submit for Review.
- [ ] Play: promote from Internal testing to Production (staged rollout, e.g. 20 %).
- [ ] After approval: watch Play Console's Android vitals and App Store
      Connect's crashes; check `/.well-known` links once more.
