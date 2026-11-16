# CDesk / C桌面

> **C = Car · Clean · Cheap**
> A clean, ad-free home screen for old car head units, TV boxes, and Android TV.
>
> **Official site (direct access from mainland China): [https://gcy5.cn.mt](https://gcy5.cn.mt)**

[中文](README.md) | [English](README.en.md)

## What is this

Millions of car head units and TV boxes made between 2015–2022 still work fine, but their stock
launchers are slow, ugly, and full of ads. **CDesk does four things:**

| Feature | Notes |
|---|---|
| 🖥 Home replacement | Three pages (Recent / Apps / Media), Recent first, D-pad navigation |
| 🌙 Built-in 4K wallpapers | Daylight Girl / Whale Night / Morning Light / Golden Hour + solid color + up to 3 custom photos, auto day-night switch (Morning Light 6:00–18:00) |
| 📶 Live network speed | Floating badges in both bottom corners, pure `TrafficStats` sampling |
| 📁 LAN file transfer | Built-in HTTP server — scan a QR code from your phone and send APKs to the unit |

Also: in-app language switch (Simplified / Traditional / English), IP-based weather. No first-run
pop-ups — the terms of use and the default-launcher guide live in Settings.

> Wallpapers, updates, bug reports and suggestions: WeChat official account **韭菜超先生 (JiuCai Chao)** —
> if you lost your APK, you can get it back there too.

## Install

1. Push to GitHub — the [workflow](.github/workflows/android.yml) builds the APK automatically
2. Download `cdesk-com.c.launcher` from the run's Artifacts
3. Copy to your device and install
4. Press Home, pick CDesk as the default launcher

Local build: `./gradlew assembleDebug` (JDK 17 + Android SDK).

## Why it runs on old devices

One APK from **minSdk 16 (Android 4.1)** to **targetSdk 34**. Every API gap is branch-handled:
overlay window types, foreground service start, notification channels, `PendingIntent`
`FLAG_IMMUTABLE` on 23+, headered `setVideoURI` on 21+, `JSONArray.remove` avoided (API 19),
Material theme on v21+, `<queries>` for Android 11 package visibility, and
`touchscreen required=false` so TV boxes can install it. 4K wallpapers decode with an
`inSampleSize` guard against OOM on 1080p panels.

## Tests

`.github/workflows/test.yml` runs on every PR and beta push:

- **Robolectric all-component smoke** — every Activity declared in the manifest is launched for
  real; new screens are covered automatically, plus row-click, media-card routing, page order,
  four-locale string parity, and no hardcoded Chinese on the English UI (40+ cases)
- **lint `NewApi` + `UnspecifiedImmutableFlag`** — blocks minSdk-16 API misuse before merge

## Theming

A few palettes cycle in solid-color mode; text auto-inverts against the wallpaper.

## Support / Donate

If CDesk gave your old head unit a few more years, a donation is appreciated. **Donations unlock
nothing — everything is free forever.** WeChat QR code:

No payment SDK, no "donate to unlock" — just a static image.

## AI assets

Wallpaper UI is fully AI-generated. For 4K wallpapers, visit 「韭菜超先生」 to obtain them.

## License

[CC BY-NC-ND 4.0](LICENSE)


---

## Disclaimer

**1. Nature of the work.** CDesk is a free tool independently developed by an individual developer and is provided "AS IS", without warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose and non-infringement. Use is entirely at your own risk. To the maximum extent permitted by law, the author shall not be liable for any direct, indirect, incidental, special or consequential damages arising from the use of, or inability to use, this application.

**2. Content and player.** CDesk does not provide, host, index, search, recommend or distribute any audio or video content, nor any content source, and does not provide any subscription, paid, live or on-demand service. The media pages are merely entry points to a generic playback capability; all played content is imported solely by the user, who is solely responsible for verifying its lawful origin, copyright ownership and authorization to use. Users must ensure their conduct complies with applicable local laws and third-party terms of service.

**3. Third-party names.** CDesk is not affiliated with, authorized by, or in partnership with any content platform, rights holder or device manufacturer. All trademarks and names belong to their respective owners.

**4. Source and license.** Source is available in this repository under [CC BY-NC-ND 4.0](LICENSE); the APK is free of charge and contains no payment SDK.
