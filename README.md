# XerahS

> **Early Development** — This project is under active development. Expect bugs, breaking changes, and incomplete features. Feedback and bug reports are welcome.

An Android image sharing app — browse images, annotate them, and upload to your preferred destination.

## Features

- **Image Browser** — Pick images from your gallery using Android's Photo Picker
- **Annotation Editor** — Draw freehand, circles, arrows, rectangles, text with backgrounds, blur sensitive areas, and numbered steps
- **Crop Tool** — Crop images with draggable handles and grid overlay before or after annotating
- **Annotation Selection** — Tap to select individual annotations, delete them, or adjust per-annotation opacity
- **HSV Color Picker** — Full color picker with hue/saturation canvas, brightness slider, and hex input
- **Canvas Zoom/Pan** — Pinch-to-zoom and two-finger pan while annotating
- **Multi-Image Upload** — Pick and upload multiple images at once with batch progress
- **Multi-Destination Upload** — Upload to Amazon S3, Imgur, FTP, SFTP, or save locally
- **Image Quality Controls** — JPEG compression slider and max dimension resize before upload
- **Connection Testing** — Test S3, FTP, and SFTP connections from settings
- **Custom File Naming** — Pattern-based naming with tokens: `{original}`, `{date}`, `{time}`, `{timestamp}`, `{random}`
- **S3 Explorer** — Browse, search, preview, download, and delete files in your S3 bucket with folder navigation, breadcrumbs, list/grid views, image thumbnails, sorting (name/size/date/type), create folders, rename, and move files
- **Albums & Tags** — Organize uploads into albums and assign multiple tags; filter history by album or tag; manage albums and tags from the history screen
- **Upload History** — Searchable history with thumbnails, date filters, album/tag filtering, swipe-to-delete, and fullscreen image preview with pinch-to-zoom
- **Auto-Copy URL** — Optionally copy the upload URL to clipboard automatically after a successful upload
- **Biometric Lock** — Lock the entire app or just credential screens behind fingerprint/face authentication
- **Theme Options** — System, Light, and Dark modes with multiple color themes and OLED pure black mode
- **Settings Backup** — Export/import all settings (including credentials) as JSON
- **Share Intent** — Receive shared images from other apps
- **Onboarding** — 3-page walkthrough on first launch

## Architecture

Multi-module clean architecture with Jetpack Compose.

```
app/                  Main application, navigation, theme, onboarding
core/
  common/             Shared utilities, extensions, thumbnail generator, AWS V4 signer
  domain/             Models, repository interfaces
  data/               Room database, DataStore, encrypted credential storage
  ui/                 Shared Compose components (cards, banners, section headers)
feature/
  capture/            Image browser (Photo Picker)
  annotation/         Canvas-based image markup editor
  upload/             Upload logic for S3, Imgur, FTP, SFTP + WorkManager
  history/            Upload history with search, filters, swipe gestures
  s3explorer/         S3 bucket file browser with folder navigation and preview
  settings/           App settings, destination configs, export/import
```

## Tech Stack

| Category | Libraries |
|---|---|
| UI | Jetpack Compose, Material 3, Compose Navigation |
| DI | Dagger Hilt |
| Database | Room |
| Preferences | DataStore, EncryptedSharedPreferences |
| Networking | Retrofit, OkHttp |
| Image Loading | Coil |
| Background Work | WorkManager |
| Security | AndroidX Biometric |
| FTP/SFTP | Apache Commons Net, JSch |
| Serialization | Gson |

## Requirements

- Android SDK 26+ (Android 8.0)
- JDK 17+

## Building

```bash
./gradlew assembleDebug
```

Install on a connected device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Upload Destinations

| Destination | Features |
|---|---|
| **Amazon S3** | Custom endpoints (MinIO, DigitalOcean Spaces), path-style, ACL, custom public URL |
| **Imgur** | Anonymous or OAuth, token refresh |
| **FTP** | FTPS support, passive mode, auto directory creation |
| **SFTP** | SSH key authentication, passphrase support |
| **Local** | Save to device storage |

## License

All rights reserved.
