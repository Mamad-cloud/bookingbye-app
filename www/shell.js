// The shell's script in the website's pages.
//
// The app runs this in every page of https://bookingbye.com (and the offline
// page) after Capacitor's own script: ShellWebViewClient on Android,
// ShellViewController on iOS. It does what the app needs from the page
// without the website having to know:
//
//   status bar   dark icons on the site's light theme, light icons on its
//                dark one. The site's theme is its own setting (the "dark"
//                class on <html>), not the phone's, so the app follows the
//                page rather than the system.
//
// Plain ES5, no dependencies, safe to run twice.
(function () {
  if (window.__bookingByeShell) return;
  window.__bookingByeShell = true;

  var root = document.documentElement;
  var shownStyle = null;

  function systemBars() {
    var plugins = window.Capacitor && window.Capacitor.Plugins;
    return plugins && plugins.SystemBars;
  }

  function followTheme() {
    var bars = systemBars();
    if (!bars) return;
    // SystemBars "LIGHT" means a light background, so dark icons.
    var style = root.classList.contains("dark") ? "DARK" : "LIGHT";
    if (style === shownStyle) return;
    shownStyle = style;
    Promise.resolve(bars.setStyle({ style: style })).catch(function () {
      shownStyle = null;
    });
  }

  followTheme();
  new MutationObserver(followTheme).observe(root, { attributes: true, attributeFilter: ["class"] });
})();
