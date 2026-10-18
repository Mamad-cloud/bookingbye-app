// Copies the version in package.json into the iOS project (MARKETING_VERSION,
// shown in the App Store as the version). Android reads package.json itself,
// in android/app/build.gradle, and the user agent gets it from
// capacitor.config.js, so package.json is the one place to change it.
//
// With APP_BUILD_NUMBER set (CI: scripts/build-number.js), also writes the
// build number (CURRENT_PROJECT_VERSION), which has to grow with every
// upload to the App Store. Android's build.gradle reads APP_BUILD_NUMBER itself.
//
//   node scripts/sync-version.js

const fs = require("fs");
const path = require("path");

const { version } = require("../package.json");
if (!/^\d+\.\d+\.\d+$/.test(version)) {
  throw new Error(`package.json version "${version}" must be MAJOR.MINOR.PATCH`);
}
const buildNumber = process.env.APP_BUILD_NUMBER;
if (buildNumber !== undefined && !/^\d+$/.test(buildNumber)) {
  throw new Error(`APP_BUILD_NUMBER "${buildNumber}" must be a whole number`);
}

const pbxproj = path.join(__dirname, "..", "ios", "App", "App.xcodeproj", "project.pbxproj");
const before = fs.readFileSync(pbxproj, "utf8");
let after = before.replace(/MARKETING_VERSION = [^;]+;/g, `MARKETING_VERSION = ${version};`);
if (buildNumber) {
  after = after.replace(/CURRENT_PROJECT_VERSION = [^;]+;/g, `CURRENT_PROJECT_VERSION = ${buildNumber};`);
}
if (after !== before) fs.writeFileSync(pbxproj, after);
console.log(`iOS MARKETING_VERSION = ${version}` + (buildNumber ? `, CURRENT_PROJECT_VERSION = ${buildNumber}` : ""));
