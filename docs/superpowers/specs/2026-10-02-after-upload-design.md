# After-upload Actions, Delete from Host, Encrypted Backup (Sub-project C)

Date: 2026-10-02. Branch `feature/after-upload`, stacked on `feature/new-destinations` (PR #3). Merge after #2 and #3.

## 1. After-upload actions
- `enum class AfterUploadAction { COPY_URL, SHORTEN_URL, SHARE_SHEET, OPEN_URL }` in core:domain.
- Each upload profile has a set of actions. Uploads without a profile use a global default set.
- Storage: DataStore (not secrets). Key `after_upload_actions` for the default, `after_upload_actions_<profileId>` per profile. Value is a comma-separated list of enum names. Missing per-profile key means "use the default".
- Migration of the old setting: if the default key is missing, it is derived from `auto_copy_url` (true → `COPY_URL`). The "Auto-copy URL" switch in Upload settings is replaced by a multi-select of the four actions. The profile editor gets the same multi-select plus "Use default".
- SHORTEN_URL runs in `UploadWorker` after a successful upload, through `UrlShortenerRepository`. Success: the short URL replaces `url` in the history item and in the worker output. Failure: keep the long URL and finish normally.
- The worker writes the resolved action set to its output (`KEY_ACTIONS`).
- COPY_URL, SHARE_SHEET and OPEN_URL run in the UI, because Android blocks clipboard access and activity starts from the background:
  - Upload screen open when the work succeeds: `UploadViewModel` emits one-shot events; the screen copies (snackbar), opens the share sheet, or opens the browser. Batch uploads: copy joins all URLs with newlines; share and open use the first URL only.
  - Otherwise the completion notification shows a button for each selected action. Copy uses the existing `CopyUrlReceiver`; Share and Open use activity PendingIntents.

## 2. Delete from host
- History gains `remoteKey TEXT` (Room v4 → v5, `MIGRATION_4_5`, nullable, no backfill). Uploaders return it in `UploadResult.remoteKey`:
  - S3: the object key.
  - FTP/SFTP: the full remote path.
  - Nextcloud: the WebDAV path relative to the user root (`folder/name`).
  - Local: the absolute file path.
- `HistoryItem` also needs to know which profile it used, for credentials. Add `profileId TEXT` in the same migration and save the worker's profile id. If the profile is gone or null, use the global config.
- Domain interface `RemoteDeleteRepository` (core:domain) with `canDelete(item): Boolean` and `suspend delete(item): Result<Unit>`. Impl in core:data, bound in app/di. It uses per-destination deleters:
  - Custom uploader with `deleteUrl`: GET the URL. A 2xx response is a success. Otherwise the result is `Result.failure(OpenInBrowser(url))` and the UI opens the URL.
  - S3: signed `DELETE` on the object (reuse the existing SigV4 signer).
  - FTP: `deleteFile`. SFTP: `rm`.
  - Nextcloud: WebDAV `DELETE` with basic auth.
  - Local: `File.delete()`.
  - Everything else, and rows without the needed data: `canDelete = false`.
- UI: history item menu (Home row menu and History screen) gets "Delete from host" when `canDelete`. It has a confirmation dialog; on success the history item is removed as well. Errors show in a snackbar. 404 counts as success (already gone).

## 3. Encrypted backup
- New format `.xsbk`: JSON envelope `{Format: "XerahS.Backup", FormatVersion: 1, Encryption: {...}, Payload}`, the same crypto as `.xsdc` (PBKDF2-HMAC-SHA256, 600 000 iterations, AES-256-GCM, tag separate). A shared `PassphraseCrypto` (core:data) handles encrypt and decrypt, and `XsdcDecoder` uses it too.
- Payload: the current export JSON (from `ExportImportManager`) plus `profiles: [{name, destination, isDefault, config (same per-destination objects as the export), afterUploadActions}]` and the default after-upload actions.
- Backup screen: Export asks for a passphrase twice, minimum 8 characters, and writes `xerahs-backup-<date>.xsbk` via SAF. Import detects encrypted vs legacy plain JSON. Encrypted files ask for the passphrase, then go through the existing preview/apply flow. Profiles are added as new profiles, and a same-named profile for the same destination is replaced.
- Not included: history, local files, themes.

## Testing
- Unit: action set storage and default migration; worker shorten step (fake shortener); `PassphraseCrypto` round trip and wrong passphrase; backup payload build/parse round trip with profiles; each deleter (MockWebServer for HTTP/S3/Nextcloud, temp dir for Local); `canDelete` rules.
- Emulator: v4 → v5 upgrade over a seeded DB; actions on the Upload screen and on the notification; delete a Local upload from history; export, wipe and import a backup with a profile.
- Gate: `./gradlew clean testDebugUnitTest lint assembleRelease` plus a release startup check.

## Non-goals
Deleting from Imgur, Immich or Gist; backing up history; cloud sync.
