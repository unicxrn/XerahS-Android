# XerahS

An Android image sharing app — browse images, annotate them, and upload to your preferred destination.

## Features

- **Image Browser** — Pick images from your gallery using Android's Photo Picker
- **Annotation Editor** — Draw, add text, arrows, rectangles, and blur sensitive areas
- **Multi-Destination Upload** — Upload to Amazon S3, Imgur, FTP, or SFTP
- **Custom File Naming** — Pattern-based naming with tokens: `{original}`, `{date}`, `{time}`, `{timestamp}`, `{random}`
- **Upload History** — Searchable history with thumbnails, date filters, and swipe-to-delete
- **Theme Options** — System, Light, and Dark theme modes
- **Settings Backup** — Export/import all settings (including credentials) as JSON
- **Share Intent** — Receive shared images from other apps
- **Onboarding** — 3-page walkthrough on first launch

## Architecture

Multi-module clean architecture with Jetpack Compose.

```
app/                  Main application, navigation, theme, onboarding
core/
  common/             Shared utilities, extensions, thumbnail generator
  domain/             Models, repository interfaces
  data/               Room database, DataStore, encrypted credential storage
feature/
  capture/            Image browser (Photo Picker)
  annotation/         Canvas-based image markup editor
  upload/             Upload logic for S3, Imgur, FTP, SFTP + WorkManager
  history/            Upload history with search, filters, swipe gestures
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

## License

All rights reserved.
