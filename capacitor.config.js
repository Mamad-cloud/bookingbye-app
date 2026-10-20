// @ts-check
//
// The BookingBye app: a native shell around https://bookingbye.com.
//
// The webview loads the live site (server.url), so the site ships without an
// app release, and Capacitor injects window.Capacitor into it, which is how
// the site reaches the native plugins (see the website's src/utils/appShell.ts).
// What the shell itself does lives in native code, in the AppShell plugin
// (android/app/src/main/java/com/bookingbye/app, ios/App/App), configured
// below under plugins.AppShell. `npx cap sync` copies this file into both
// platforms; edit it here, never the copies.

const { version } = require("./package.json");

/** @type {import('@capacitor/cli').CapacitorConfig} */
const config = {
  appId: "com.bookingbye.app",
  appName: "BookingBye",
  // Never loaded as the app, which is the live site; it holds the offline
  // screen, and iOS refuses to start without an index.html here.
  webDir: "www",

  // The site looks for this to know it is inside the app.
  appendUserAgent: `BookingByeApp/${version}`,
  backgroundColor: "#ffffff",

  server: {
    url: "https://bookingbye.com",
    cleartext: false,
    // No allowNavigation: which pages stay in the app is decided by AppShell,
    // from the lists below, and Capacitor's own list would bypass it.
  },

  ios: {
    // The page runs edge to edge and pads itself with env(safe-area-inset-*).
    contentInset: "never",
  },

  experimental: {
    ios: {
      spm: {
        // Avoids a Swift Package Manager identity clash between this plugin
        // and Firebase's own package (capacitor-firebase issue 959).
        packageOptions: { "@capacitor-firebase/messaging": { symlink: true } },
      },
    },
  },

  plugins: {
    FirebaseMessaging: {
      // How a notification shows while the app is open (iOS; Android leaves
      // that to the page).
      presentationOptions: ["alert", "badge", "sound"],
    },

    SystemBars: {
      // Dark icons, for the site's white header. AppShell switches them when
      // the site is put in dark mode.
      style: "LIGHT",
      insetsHandling: "css",
      initialViewportFitValueHint: "cover",
    },

    AppShell: {
      // Pages on these hosts (and their subdomains) open in the app.
      appHosts: ["bookingbye.com"],

      // Card payment pages the checkout sends the customer to, which return
      // to bookingbye.com when done; these open in the app too. While one of
      // them (or any page that is not bookingbye.com) is showing, the pages
      // it moves on to without a tap, such as a bank's 3-D Secure check,
      // also stay in the app, since those banks cannot be listed. A link the
      // customer taps there to some other site still goes to the browser.
      // From the backend's gateways (api/app/Services/Payments/Gateways).
      paymentHosts: [
        "areebapayment.com",
        "areeba.com",
        "gateway.mastercard.com",
        "nass.iq",
        "qi.iq",
        "ngenius-payments.com",
        "payby.com",
        "test2pay.com",
        "myfatoorah.com",
        "tap.company",
        "stripe.com",
        "stripe.network",
      ],

      // bookingbye.com paths that a link must never open in the app, as the
      // website's /.well-known files say (api/well-known/[file]/route.ts,
      // KEPT_IN_BROWSER): payment returns belong to the browser that holds
      // that payment, sign-in finishes in the phone's browser, and the rest
      // are never pages. Android's intent filter (AndroidManifest.xml) lists
      // the same paths; this list catches the phones too old to read it, and,
      // on both platforms, variants that slip past the filters ("//oauth/...",
      // which the website redirects to "/oauth/...").
      keptInBrowser: ["/api/", "/_next/", "/oauth/", "/app-auth", "/payment-", "/pay-done"],
    },
  },
};

module.exports = config;
