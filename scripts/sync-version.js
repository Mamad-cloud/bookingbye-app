// Copies the version in package.json into the iOS project (MARKETING_VERSION,
// shown in the App Store as the version). Android reads package.json itself,
// in android/app/build.gradle, and the user agent gets it from
// capacitor.config.js, so package.json is the one place to change it.
//
// The build number (CURRENT_PROJECT_VERSION on iOS, versionCode on Android)
// has to grow with every upload to the stores; CI sets it, see codemagic.yaml.
//
//   node scripts/sync-version.js

const fs = require("fs");
const path = require("path");

const { version } = require("../package.json");
if (!/^\d+\.\d+\.\d+$/.test(version)) {
  throw new Error(`package.json version "${version}" must be MAJOR.MINOR.PATCH`);
}

const pbxproj = path.join(__dirname, "..", "ios", "App", "App.xcodeproj", "project.pbxproj");
const before = fs.readFileSync(pbxproj, "utf8");
const after = before.replace(/MARKETING_VERSION = [^;]+;/g, `MARKETING_VERSION = ${version};`);
if (after !== before) fs.writeFileSync(pbxproj, after);
console.log(`iOS MARKETING_VERSION = ${version}`);
