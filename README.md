<p align="center">
  <img src="site/assets/img/logo.svg" width="88" height="88" alt="XerahS logo">
</p>

<h1 align="center">XerahS for Android</h1>

<p align="center">
  Capture. Upload. Share in a tap.<br>
  A ShareX-style uploader for Android.
</p>

<p align="center">
  <a href="https://github.com/unicxrn/XerahS-Android/releases/latest"><img src="https://img.shields.io/github/v/release/unicxrn/XerahS-Android?style=flat-square&color=b8f23a&labelColor=111217&label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-b8f23a?style=flat-square&labelColor=111217" alt="Android 8.0 or newer">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-b8f23a?style=flat-square&labelColor=111217" alt="GPL-3.0"></a>
</p>

<p align="center">
  <a href="https://unicxrn.github.io/XerahS-Android/"><b>Website</b></a> ·
  <a href="https://github.com/unicxrn/XerahS-Android/releases/latest"><b>Download</b></a> ·
  <a href="https://unicxrn.github.io/XerahS-Android/guides.html">Setup guides</a> ·
  <a href="https://unicxrn.github.io/XerahS-Android/faq.html">FAQ</a> ·
  <a href="https://github.com/unicxrn/XerahS-Android/issues">Report a bug</a>
</p>

<p align="center">
  <img src="docs/screenshots/home_dark.png" width="200" alt="Home with recent uploads">
  <img src="docs/screenshots/editor_dark.png" width="200" alt="The editor">
  <img src="docs/screenshots/share_dark.png" width="200" alt="A finished upload with its link">
  <img src="docs/screenshots/appearance_violet.png" width="200" alt="Appearance settings">
</p>

Pick a screenshot or any other file, mark it up if you want, and send it to Imgur, your own S3 bucket, Nextcloud or a server you run. When the upload finishes, the link is already on your clipboard.

> [!NOTE]
> XerahS for Android is unofficial. It's inspired by [XerahS](https://xerahs.com/) and [ShareX](https://getsharex.com/) but isn't affiliated with or endorsed by either project, and it uses none of their code. It's also still early, so expect the odd rough edge.

## Install

Download the APK from the [latest release](https://github.com/unicxrn/XerahS-Android/releases/latest) on your phone and open it. If Android asks, allow installs from your browser. New versions show up in the app under Settings, Updates.

You need Android 8.0 or newer. If Android warns about unknown apps or something else looks off, the [FAQ](https://unicxrn.github.io/XerahS-Android/faq.html) covers the usual questions.

## What it does

**Upload anything.** Images, PDFs, videos and other files. Share a file, a link or some text into XerahS from another app, use the Quick Settings tile or the home-screen widget, or pick from your gallery. Several files can go up in one batch, and the upload queue shows each one, with cancel and retry.

**Start from wherever you are.** Long-press the app icon for New upload, Upload clipboard and Copy last link. Your upload profiles show up in Android's share sheet, so sharing to one skips the destination picker. Turn on screenshot uploads and each new screenshot gets a notification with Upload and Edit, or goes straight up if you prefer.

**Links and text too.** Share a link into XerahS to shorten it, upload the file it points to, or upload it as text. Anything you've copied can be uploaded from Home.

**Mark it up first.** The editor has twelve tools: rectangle, ellipse, line, arrow, freehand, text, numbered steps, blur, pixelate, highlight, spotlight and a magnifier. You can crop, rotate and flip, add a border or shadow, and pull text out of an image with on-device OCR.

**Send it where you want.** Each destination keeps its own settings, and upload profiles let you switch between setups in one tap.

| Destination | Notes |
|---|---|
| Imgur | Anonymous uploads with your own Client ID |
| Amazon S3 | Also Cloudflare R2, MinIO, DigitalOcean Spaces and other S3-compatible storage |
| Nextcloud | Uploads to a folder and creates a share link |
| Immich | Sends photos and videos to your library |
| GitHub Gist | For text and code |
| FTP and FTPS | Passive mode, creates folders as needed |
| SFTP | Password or SSH key |
| Custom uploaders | Import ShareX `.sxcu` files, including `{json:}`, `{regex:}`, `{xml:}` and input prompts. Presets for XBackBone, Pastebin and Bitly |
| On your phone | Saves locally |

Setup steps for every destination, with the exact field names from the app, are in the [guides](https://unicxrn.github.io/XerahS-Android/guides.html). Images, text and other files can each go to a different profile automatically.

**Decide what happens next.** Copy the link, open the share sheet, open it in the browser or show a QR code, per profile or for every upload. Links can be copied as a plain URL, Markdown, HTML or BBCode, and shortened with is.gd or your Bitly account.

**Find it again later.** History keeps every upload with its link, host and thumbnail. Search it, sort uploads into albums and tags, and delete a file from the host when you're done with it. Upload the same image twice and XerahS offers the link you already have.

**Browse your bucket.** The S3 explorer lists, searches, renames, moves and downloads files. GIFs animate and videos play in the preview, and there's a stats view with storage use and a cost estimate.

**A few extra tools.** The beautifier puts a screenshot on a gradient with padding, rounded corners and a shadow. The combiner stitches up to 20 images into one. You can also resize or convert a batch of images and add a watermark, read a QR code from a picture, check a file's hash, or pick a color from an image.

**Make it look how you like.** Light, dark or system theme, seven accent colors or your own, Material You, and a true black mode for OLED screens. Every accent goes through a contrast check, so text stays readable.

**Keep it private.** No analytics and no account. Passwords and keys stay in Android's encrypted storage and are left out of Android backups. XerahS's own backups are encrypted with a passphrase you pick, and you can lock the app behind your fingerprint. The [privacy page](https://unicxrn.github.io/XerahS-Android/privacy.html) lists what's stored and every request the app makes.

## Build it yourself

You need JDK 17 or newer and the Android SDK.

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The app is written in Kotlin with Jetpack Compose and follows a multi-module layout where `app` depends on `feature/*` and features depend on `core/*`. Features never depend on each other.

```
app/            Navigation, theme, main activity
core/common     Color engine, AWS signing, .sxcu parsing, utilities
core/domain     Models and repository interfaces
core/data       Room, DataStore, encrypted credentials, uploaders
core/ui         Shared Compose components
feature/        capture, annotation, upload, history, s3explorer, tools, settings
site/           The website, deployed to GitHub Pages
```

It uses Hilt, Room, WorkManager, DataStore, OkHttp, Coil, Media3, ML Kit and ZXing. The fonts are Inter, Inter Tight and JetBrains Mono, all under the SIL Open Font License.

## Contributing

Bug reports and pull requests are welcome. If you're planning something big, open an issue first so we can talk it through.

## License

[GPL-3.0](LICENSE)
