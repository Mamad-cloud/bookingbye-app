// Prints the build number for a store upload: the minutes since
// 2026-01-01 UTC. It only ever grows, whichever CI (or person) builds, with no
// need to ask either store what the last one was. Both stores want that:
// Android's versionCode and iOS's CFBundleVersion must grow with each upload.
//
//   export APP_BUILD_NUMBER=$(node scripts/build-number.js)
//
// Android's build.gradle and scripts/sync-version.js (iOS) read
// APP_BUILD_NUMBER. It stays under Android's limit (2,100,000,000) for
// about 4,000 years.

const EPOCH = Date.UTC(2026, 0, 1);
console.log(Math.floor((Date.now() - EPOCH) / 60000));
