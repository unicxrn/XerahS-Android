# Uploader Interop — Design (Sub-project A)

Date: 2026-10-02 · Branch: `feature/uploader-interop` · Status: approved in brainstorming

## Context: upstream parity programme

Goal: bring XerahS Android as close as practical to upstream ShareX/XerahS (v0.31.4, 2026-09-29). Gap analysis split the work into sub-projects, each with its own spec → plan → build cycle, in this order:

| # | Sub-project | Scope |
|---|---|---|
| **A** | **Uploader interop (this spec)** | `.sxcu` / `.xsdc` import, any-file uploads, HEIC→PNG |
| B | New destinations | Dropbox, Nextcloud, Immich, XBackBone, GitHub Gist, Pastebin, Bitly |
| C | After-upload workflows | Per-profile task pipelines, delete-from-host, settings backup/restore |
| D+E | Editor & tools | Speech balloon, sticker, smart eraser, highlighter pen, effects subset; resizer/converter/watermark, hash checker, QR decode, color picker |

Out of scope for the programme: XerahS Cloud sign-in, screen recording, desktop-only features (hotkeys, pin-to-screen, tray).

Licensing note: upstream is GPL-3. Implement from the file formats, do not copy upstream code.

## Goals

1. Import ShareX custom uploaders (`.sxcu`) with full request/response syntax support.
2. Import XerahS encrypted destination configs (`.xsdc`) for every provider the app supports.
3. Upload any file type, not only images.
4. Convert HEIC/HEIF to PNG before upload (opt-out setting).

## 1. Data model & parsing

### Approach
Each imported custom uploader becomes an **Upload Profile** with destination `CUSTOM_HTTP`. This reuses the existing profile system and `SecureCredentialStore` encrypted config storage instead of adding a parallel "custom uploader library".

### `core:domain`
`CustomUploaderConfig` replaces `UploadConfig.CustomHttpConfig`:

| Field | Type | Notes |
|---|---|---|
| `name` | String | |
| `destinationType` | Set<`CustomDestinationType`> | `IMAGE`, `TEXT`, `FILE`, `URL_SHORTENER` (sxcu allows combined flags) |
| `requestMethod` | String | GET/POST/PUT/PATCH/DELETE |
| `requestURL` | String | may contain syntax |
| `parameters` | Map<String,String> | query string, may contain syntax |
| `headers` | Map<String,String> | may contain syntax |
| `body` | `CustomBodyType` | `NONE`, `MULTIPART_FORM_DATA`, `FORM_URL_ENCODED`, `JSON`, `XML`, `BINARY` |
| `arguments` | Map<String,String> | form fields |
| `fileFormName` | String | multipart file field |
| `data` | String | raw JSON/XML body template |
| `url`, `thumbnailURL`, `deletionURL`, `errorMessage` | String | response templates |

`ShareXSyntax` — pure Kotlin engine, no Android deps, TDD:

- **Input (request) functions:** `{filename}`, `{random:a|b|c}`, `{select:a|b}` (non-interactive: first option), `{inputbox:Title|Default}` (value supplied by UI before enqueue), `{base64:text}`.
- **Output (response) functions:** `{response}`, `{responseurl}`, `{header:Name}`, `{json:path}` (dot/bracket paths, array indexes), `{xml:xpath}`, `{regex:pattern|group}` (group index or name; regex runs on the response).
- Nested syntax (`{json:{regex:…}}` style arguments evaluated inside-out), backslash escaping of `{`, `}`, `|`, `\`.
- Unresolvable expressions raise `SyntaxEvaluationException(expression, reason)` — never silently empty.

### `core:data`
- `SxcuImporter`: parses `.sxcu` JSON case-insensitively; supports legacy `RequestType` → `RequestMethod`, legacy `ResponseType`, `$json:…$`-style legacy syntax (convert to `{json:…}`). Returns a `ProfileDraft`.
- `XsdcImporter`: envelope `{Format: "XerahS.DestinationConfig", FormatVersion: 1, Encryption{Method: Passphrase, Kdf: PBKDF2-HMAC-SHA256, Iterations, Salt, Cipher: AES-256-GCM, Nonce, Tag}, Payload}` (Base64 fields; GCM tag appended to ciphertext). Decrypted payload lists destinations; map each supported provider (Amazon S3, FTP/FTPS, SFTP, Imgur, custom uploader) to a `ProfileDraft`; unsupported providers are reported and skipped.
- **Legacy migration:** on first load, a stored legacy `CustomHttpConfig` JSON is converted to a `CustomUploaderConfig` (`url` template → `{json:<responseUrlJsonPath>}`, body MULTIPART, `fileFormName` from `formFieldName`) and saved as a profile. Idempotent via a DataStore flag.

## 2. Import entry points & any-file upload

### Import
- **Open-with / share:** manifest `VIEW` + `SEND` intent filters for `.sxcu`/`.xsdc`, including `application/octet-stream` and `application/json`; the handler sniffs extension + content and ignores non-matching files.
- **Settings → Destinations → Import** via SAF file picker.
- **Paste** `.sxcu` JSON from the clipboard.
- All three route to an **Import preview** screen: name, destination types, request host, body type, plain-`http` warning; Import (+ "set as default"). `.xsdc` first prompts for passphrase, then shows a checklist of destinations.

### Any-file upload
- Share sheet accepts `*/*` (`SEND`, `SEND_MULTIPLE`). Images → existing flow (optional editor → upload). Non-images → Upload screen directly with a file card (type icon, name, size); no editor.
- Shared `text/plain` (no stream) is written to a `.txt` cache file and treated as text.
- `UploadWorker` input keys renamed `KEY_IMAGE_PATH(S)` → `KEY_FILE_PATH(S)`. Image processing (resize/quality/format) only for decodable raster images. SHA-256 duplicate detection unchanged for all types.
- **Destination capability filter:** Imgur = images only; custom profiles = their `destinationType`; S3/FTP/SFTP/Local = any. Upload screen shows only compatible destinations/profiles for the selected file(s); a batch is restricted to destinations that accept every file.
- **HEIC/HEIF → PNG:** setting `convertHeicToPng` (default on), via `ImageDecoder` (minSdk-guarded, API 28+).

### Room (version 3 → 4)
- `ALTER TABLE history ADD COLUMN mimeType TEXT`; backfill `UPDATE history SET mimeType = 'image/*' WHERE mimeType IS NULL`.
- Bump `XerahSDatabase` to 4, add `MIGRATION_3_4`, register in `DatabaseModule`.
- `HistoryItem.mimeType: String?`. Timeline shows a file-type tile for non-images; Editor/OCR actions hidden for them.

## 3. Response handling, errors, security

- `CustomHttpUploader` rewritten on `ShareXSyntax`: builds request from parameters/headers/body/arguments; `{inputbox:}` values collected on the Upload screen before enqueue and passed in worker input.
- 2xx: evaluate `url`, `thumbnailURL`, `deletionURL`. Blank `url` template → `{response}` for URL shorteners, otherwise `{responseurl}`. `deletionURL` → `HistoryItem.deleteUrl`.
- A URL_SHORTENER custom profile can be chosen as the app shortener (setting alongside is.gd); `UrlShortenerRepository` delegates to it.
- Non-2xx: fail with evaluated `errorMessage`, else `HTTP <code>: <first 300 chars of body>`; copyable in the upload result.
- Syntax evaluation failure on success response → upload fails with "Couldn't find `<expr>` in response".
- Import errors: not a valid `.sxcu`, wrong passphrase / damaged file, unsupported provider (row skipped, others import), unsupported body type.
- **Security:** headers/arguments/`.xsdc` secrets only in `SecureCredentialStore`; `.xsdc` passphrases never persisted; plain-HTTP request URLs require explicit confirmation; logging redacts `Authorization` and any key/token/secret/password-named fields; ProGuard keeps new Gson DTOs.

## 4. Testing & verification

- **Unit (TDD):** `ShareXSyntax` (all functions, nesting, escaping, failures); `SxcuImporter` against a fixture set of real-world `.sxcu` files (multipart, URL-encoded, JSON, binary, text uploader, shortener, mixed-case keys, legacy RequestType/syntax); `XsdcImporter` against a fixture encrypted with a known passphrase (+ wrong passphrase, provider mapping); destination capability filter; legacy config migration.
- **Integration:** `CustomHttpUploader` vs OkHttp `MockWebServer` per body type + error paths; Room `MigrationTestHelper` 3→4 (backfill, history preserved).
- **On-device:** import `.sxcu` from a file manager; upload image, PDF, shared text through it; HEIC conversion; non-image to S3 (history tile, hidden editor actions).
- **Gates:** `./gradlew test lint assembleRelease`; launch release APK to validate startup (R8 + Gson DTOs).

## Non-goals (this sub-project)
New native destinations (B), delete-from-host UI and after-upload task pipelines (C), editor/tool work (D+E), `.sxcu` export, `{inputbox}` prompts during background/batch retries.
