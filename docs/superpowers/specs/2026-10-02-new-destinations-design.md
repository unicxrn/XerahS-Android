# New Destinations (Sub-project B)

Date: 2026-10-02. Branch `feature/new-destinations`, stacked on `feature/uploader-interop` (PR #2). Merge after #2.

## Scope
Native: Nextcloud, Immich, GitHub Gist. Presets (bundled .sxcu): XBackBone, Pastebin, Bitly. Dropbox is out of scope.

## Native destinations
New `UploadDestination` values `NEXTCLOUD`, `IMMICH`, `GITHUB_GIST`. Each one follows the project's destination checklist:
1. `UploadConfig` subtype
2. Uploader in `feature/upload/uploader/`
3. Secrets in `SecureCredentialStore` only (global and `profile_<id>_` keys)
4. Wired into `UploadWorker.performUpload`
5. Settings screen in `feature/settings/destinations/` plus a row in Upload settings
6. Profile editor fields
7. `DestinationCapabilities` rules
8. Destination icon and colour wherever the existing `when (destination)` blocks need them

Enum names are persisted (Room, DataStore), so the new values are appended and existing ones never renamed.

| | Nextcloud | Immich | GitHub Gist |
|---|---|---|---|
| Config | serverUrl, username, appPassword, folder (default `XerahS`), publicShare (default on) | serverUrl, apiKey, createShareLink (default on) | token, isPublic (default off) |
| Accepts | any file | images and video | text only |
| Upload | `PUT {server}/remote.php/dav/files/{user}/{folder}/{name}` with basic auth; `MKCOL` the folder on 404/409 then retry once | `POST {server}/api/assets` multipart (`assetData`, `deviceAssetId`, `deviceId=xerahs-android`, `fileCreatedAt`, `fileModifiedAt`), header `x-api-key` | `POST https://api.github.com/gists` JSON `{public, files: {name: {content}}}`, header `Authorization: Bearer` |
| Result URL | OCS share `POST {server}/ocs/v2.php/apps/files_sharing/api/v1/shares` (`path`, `shareType=3`, header `OCS-APIRequest: true`, `format=json`) → `ocs.data.url`; without share, the WebDAV URL | `POST /api/shared-links` `{type: INDIVIDUAL, assetIds:[id]}` → `{server}/share/{key}`; without share, `{server}/photos/{id}` | `html_url` |
| Delete URL | none | none (stored later by sub-project C) | none |

HTTP errors become `UploadResult(success=false)` with `"<Host> HTTP <code>: <short body>"`. Never include tokens in messages. Use the shared `OkHttpClient`. Uploads must be cancellable (same pattern as `CustomUploaderClient`).

## Presets
Bundled `.sxcu` templates in `core/common/src/main/resources/sxcu-presets/` (classpath resources, so JVM tests can read them), loaded by a `SxcuPresets` object that lists `{id, name, fields}`. Each field is a placeholder like `{{host}}` or `{{api_key}}` substituted before parsing.
- XBackBone: `POST {{host}}/upload`, multipart `upload`, argument `token={{token}}`, URL `{json:url}`, deletion `{json:raw}`.
- Pastebin: `POST https://pastebin.com/api/api_post.php`, form `api_dev_key={{api_key}}`, `api_option=paste`, `api_paste_code={input}`, TextUploader, URL `{response}`.
- Bitly: `POST https://api-ssl.bitly.com/v4/shorten`, header `Authorization: Bearer {{token}}`, JSON `{"long_url":"{input}"}`, URLShortener, URL `{json:link}`.

UI: the Import uploader screen gets an "Add from preset" button. Pick a preset, fill its fields, then the normal import preview saves it as a custom uploader profile. Bitly profiles appear in the shortener picker automatically.

## Testing
- MockWebServer tests per native uploader: success path, share-link path, error path, Nextcloud MKCOL retry.
- Preset tests: every bundled preset substitutes and parses; Bitly and Pastebin evaluate against sample responses.
- `DestinationCapabilities` tests for the new values.
- Gate: `./gradlew clean testDebugUnitTest lint assembleRelease`, release startup on the emulator, manual check of each settings screen.

## Non-goals
Dropbox, OAuth flows, delete-from-host (sub-project C), browsing remote storage.
