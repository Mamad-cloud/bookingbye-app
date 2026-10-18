// Draws every app icon and splash image from one piece of artwork,
// resources/icon-source.png: the website's favicon (frontend/src/app/favicon.ico,
// which is really a 2362px PNG), a navy ticket with a white "b" on a
// transparent background.
//
//   npm run assets
//
// Two versions of the ticket are drawn from it:
//   badge       the ticket in brand navy with its white "b" (app icons, on white)
//   silhouette  the ticket in white with the "b" cut out (splash and
//               notification icons, on navy or tinted by the system)
// The artwork's navy is a little off the brand's, so both are recoloured to
// exactly #083882.
//
// Each icon keeps the ticket inside the area its platform promises never to
// crop: the circle in the middle of an Android adaptive icon or splash icon,
// the rounded square of an iOS icon. The output is committed; run this
// again only when the artwork changes.

const fs = require("fs");
const path = require("path");
const sharp = require("sharp");

const ROOT = path.join(__dirname, "..");
const SOURCE = path.join(ROOT, "resources", "icon-source.png");
const NAVY = { r: 0x08, g: 0x38, b: 0x82 };
const WHITE = { r: 255, g: 255, b: 255 };

const ANDROID_RES = path.join(ROOT, "android", "app", "src", "main", "res");
const IOS_ASSETS = path.join(ROOT, "ios", "App", "App", "Assets.xcassets");
const STORE = path.join(ROOT, "resources", "store");

// Android densities: scale against mdpi (1 dp = 1 px).
const DENSITIES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

/**
 * Reads the artwork and works out, for every pixel, how much of it is the
 * navy ticket ("ink") as opposed to the white "b". Returns both versions of
 * the ticket cropped to its edges, and how far its outline reaches from the
 * centre, as a share of half its width: 1 for a circle, about 1.41 for a
 * square. That figure is what lets a ticket be sized to fit a circle.
 */
async function loadTicket() {
  const { data, info } = await sharp(SOURCE).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const { width, height } = info;

  // The artwork's navy, measured, and its white.
  const inkLuma = 0.299 * 34 + 0.587 * 62 + 0.114 * 127;
  const badge = Buffer.alloc(width * height * 4);
  const silhouette = Buffer.alloc(width * height * 4);
  let left = width, top = height, right = -1, bottom = -1;

  for (let i = 0, p = 0; i < data.length; i += 4, p++) {
    const alpha = data[i + 3];
    const luma = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
    const ink = Math.min(1, Math.max(0, (255 - luma) / (255 - inkLuma)));

    badge[i] = Math.round(WHITE.r + (NAVY.r - WHITE.r) * ink);
    badge[i + 1] = Math.round(WHITE.g + (NAVY.g - WHITE.g) * ink);
    badge[i + 2] = Math.round(WHITE.b + (NAVY.b - WHITE.b) * ink);
    badge[i + 3] = alpha;

    silhouette[i] = silhouette[i + 1] = silhouette[i + 2] = 255;
    silhouette[i + 3] = Math.round(alpha * ink);

    if (alpha > 8) {
      const x = p % width, y = Math.floor(p / width);
      if (x < left) left = x;
      if (x > right) right = x;
      if (y < top) top = y;
      if (y > bottom) bottom = y;
    }
  }

  const crop = { left, top, width: right - left + 1, height: bottom - top + 1 };
  const cx = left + crop.width / 2, cy = top + crop.height / 2;
  let reach = 0;
  for (let p = 0; p < width * height; p++) {
    if (data[p * 4 + 3] < 128) continue;
    const dx = (p % width) - cx, dy = Math.floor(p / width) - cy;
    reach = Math.max(reach, Math.hypot(dx, dy));
  }

  const raw = { raw: { width, height, channels: 4 } };
  return {
    badge: await sharp(badge, raw).extract(crop).png().toBuffer(),
    silhouette: await sharp(silhouette, raw).extract(crop).png().toBuffer(),
    reach: reach / (Math.max(crop.width, crop.height) / 2),
  };
}

/**
 * A square PNG of `size` px with the ticket centred on it, `ticket` px wide,
 * on `background` (an {r,g,b} colour, or transparent when left out).
 * `opaque` drops the alpha channel, which the App Store requires of its icon.
 */
async function draw(image, { size, ticket, background, circle = false, opaque = false }) {
  const t = Math.round(ticket);
  const mark = await sharp(image).resize(t, t, { fit: "contain", background: { r: 0, g: 0, b: 0, alpha: 0 } }).toBuffer();
  const offset = Math.round((size - t) / 2);

  let base;
  if (background && circle) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}"><circle cx="${size / 2}" cy="${size / 2}" r="${size / 2}" fill="rgb(${background.r},${background.g},${background.b})"/></svg>`;
    base = sharp(Buffer.from(svg));
  } else {
    base = sharp({
      create: { width: size, height: size, channels: 4, background: background ? { ...background, alpha: 1 } : { r: 0, g: 0, b: 0, alpha: 0 } },
    });
  }
  let out = base.composite([{ input: mark, left: offset, top: offset }]);
  if (opaque) out = out.flatten({ background: WHITE });
  return out.png({ compressionLevel: 9 }).toBuffer();
}

async function write(file, buffer) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, buffer);
  console.log(path.relative(ROOT, file));
}

/** The width of a ticket whose outline just fits a circle of `radius`. */
const fitCircle = (reach, radius) => (2 * radius) / reach;

async function main() {
  const { badge, silhouette, reach } = await loadTicket();

  // ---- Android -----------------------------------------------------------

  for (const [density, scale] of Object.entries(DENSITIES)) {
    const dir = (kind) => path.join(ANDROID_RES, `${kind}-${density}`);

    // Adaptive icon (Android 8+): a 108dp layer, of which launchers may show
    // as little as the 66dp circle in the middle. White background layer.
    const layer = 108 * scale;
    const inSafeZone = fitCircle(reach, 33 * scale);
    await write(path.join(dir("mipmap"), "ic_launcher_foreground.png"), await draw(badge, { size: layer, ticket: inSafeZone }));
    // Themed icons (Android 13+) tint this one; only its shape counts.
    await write(path.join(dir("mipmap"), "ic_launcher_monochrome.png"), await draw(silhouette, { size: layer, ticket: inSafeZone }));

    // Icons for Android 7 (minSdk 24), which has no adaptive icons.
    const legacy = 48 * scale;
    await write(path.join(dir("mipmap"), "ic_launcher.png"), await draw(badge, { size: legacy, ticket: legacy * 0.92 }));
    await write(
      path.join(dir("mipmap"), "ic_launcher_round.png"),
      await draw(badge, { size: legacy, ticket: fitCircle(reach, legacy * 0.42), background: WHITE, circle: true })
    );

    // Splash icon (androidx core-splashscreen): a 288dp image shown on the
    // splash background, cropped to the 192dp circle in its middle.
    const splash = 288 * scale;
    await write(path.join(dir("drawable"), "splash_icon.png"), await draw(silhouette, { size: splash, ticket: fitCircle(reach, 84 * scale) }));

    // Status bar icon for notifications: white on transparent, 24dp with
    // the 2dp margin Android's guidelines ask for.
    await write(path.join(dir("drawable"), "ic_stat_notify.png"), await draw(silhouette, { size: 24 * scale, ticket: 22 * scale }));
  }

  // ---- iOS ---------------------------------------------------------------

  // One 1024px icon; Xcode draws every other size from it. iOS rounds its
  // corners, so the ticket sits on white inside them, as on the website.
  await write(
    path.join(IOS_ASSETS, "AppIcon.appiconset", "AppIcon-512@2x.png"),
    await draw(badge, { size: 1024, ticket: 1024 * 0.8, background: WHITE, opaque: true })
  );

  // The launch screen's logo, 120pt, in the middle of a navy screen
  // (Base.lproj/LaunchScreen.storyboard).
  const splashSet = path.join(IOS_ASSETS, "Splash.imageset");
  for (const file of fs.readdirSync(splashSet)) if (file.endsWith(".png")) fs.unlinkSync(path.join(splashSet, file));
  for (const scale of [1, 2, 3]) {
    const name = scale === 1 ? "splash-logo.png" : `splash-logo@${scale}x.png`;
    await write(path.join(splashSet, name), await draw(silhouette, { size: 120 * scale, ticket: 120 * scale }));
  }
  fs.writeFileSync(
    path.join(splashSet, "Contents.json"),
    JSON.stringify(
      {
        images: [1, 2, 3].map((scale) => ({
          idiom: "universal",
          filename: scale === 1 ? "splash-logo.png" : `splash-logo@${scale}x.png`,
          scale: `${scale}x`,
        })),
        info: { author: "xcode", version: 1 },
      },
      null,
      2
    ) + "\n"
  );

  // ---- Store listings ----------------------------------------------------

  // Google Play: 512px icon (Play rounds its corners) and the 1024x500
  // feature graphic. The App Store takes its icon from the app itself.
  await write(path.join(STORE, "play-icon-512.png"), await draw(badge, { size: 512, ticket: 512 * 0.8, background: WHITE, opaque: true }));
  const mark = await sharp(silhouette).resize(260, 260, { fit: "contain", background: { r: 0, g: 0, b: 0, alpha: 0 } }).toBuffer();
  await write(
    path.join(STORE, "play-feature-graphic.png"),
    await sharp({ create: { width: 1024, height: 500, channels: 3, background: NAVY } })
      .composite([{ input: mark, left: (1024 - 260) / 2, top: (500 - 260) / 2 }])
      .png({ compressionLevel: 9 })
      .toBuffer()
  );
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
