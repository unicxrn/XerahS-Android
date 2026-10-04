# Lumen gaps and S3 media playback

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Close the two Lumen spec gaps (Delete from host on the share screen, font licence notice) and let the S3 explorer play animated GIFs and videos.

**Approved design:**
- Delete from host reuses `RemoteDeleteRepository`.
- The OFL texts ship in `assets/licenses/` and are shown from the Updates screen.
- GIFs animate in the S3 full-screen preview through `coil-gif`. Videos play there through Media3 ExoPlayer. Both use new SigV4 presigned query URLs, valid for 1 hour, so long videos keep streaming.

**House rules:**
- Commit messages are plain one-liners. No co-author trailers and no tool attribution.
- `grep -rlP '\x{FEFF}' --include='*.kt' .` prints nothing before every commit.
- Do not use KDoc containing `image/*`.
- Build env: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=$HOME/Android/sdk`.
- Use the Lumen components from `core/ui/lumen`, and keep one accent `PillCta` per screen.

---

### Task 1: SigV4 presigned URLs (TDD)

**Files:** `core/common/src/main/java/com/xerahs/android/core/common/AwsV4Signer.kt`, `core/common/src/test/java/com/xerahs/android/core/common/AwsV4SignerPresignTest.kt`

1. Read `AwsV4Signer.kt` and reuse its private helpers (HMAC, SHA-256 hex, URI encoding, signing key).
2. Write a failing test using AWS's published S3 presign example ("Authenticating Requests: Using Query Parameters"):
   - access key `AKIAIOSFODNN7EXAMPLE`, secret `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY`
   - region `us-east-1`, GET `https://examplebucket.s3.amazonaws.com/test.txt`, host `examplebucket.s3.amazonaws.com`
   - time `20130524T000000Z`, expires `86400`
   - expected `X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404`
   - Also assert the result contains `X-Amz-Algorithm=AWS4-HMAC-SHA256`, `X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request`, `X-Amz-Expires=86400` and `X-Amz-SignedHeaders=host`.
3. Add:
   ```kotlin
   fun presign(method: String, url: String, accessKeyId: String, secretAccessKey: String,
               region: String, host: String, expiresSeconds: Long = 3600,
               now: java.time.Instant = java.time.Instant.now(), service: String = "s3"): String
   ```
   - Canonical query: the four `X-Amz-*` params plus `X-Amz-Date`, sorted and URI-encoded.
   - Canonical headers: `host:<host>\n`. Signed headers: `host`. Payload: `UNSIGNED-PAYLOAD`.
   - Return `url + "?" + canonicalQuery + "&X-Amz-Signature=" + sig`.
   - Keep the existing `sign()` unchanged.
4. Run `./gradlew :core:common:testDebugUnitTest` and confirm the new test passes and existing tests still pass. Commit "Add SigV4 presigned URLs".

### Task 2: Presigned preview URLs in the S3 explorer

**Files:** `feature/s3explorer/.../data/S3ApiClient.kt`, `S3ExplorerViewModel.kt`, `S3ExplorerScreen.kt`

1. In `S3ApiClient`, add `buildPresignedUrl(config, objectKey, expiresSeconds = 3600): String`. Use the same host/base URL resolution and key encoding as `buildSignedUrl`, then call `AwsV4Signer.presign`.
2. In the view model, add `getPresignedUrl(objectKey): String`, returning `""` when there is no config.
3. In `ImagePreviewDialog`, load full-size images from the presigned URL with no auth headers. Keep `diskCacheKey`/`memoryCacheKey = obj.key`. Thumbnails can stay on the header-signed path.
4. Build and commit "Use presigned URLs for S3 previews".

### Task 3: Animated GIFs

**Files:** `gradle/libs.versions.toml`, `feature/s3explorer/build.gradle.kts`, `S3ExplorerScreen.kt`

1. Add a `coil-gif` library entry with the same `coil` version ref (2.5.0), and depend on it from s3explorer.
2. In the preview request, when `obj.extension == "gif"`, add `.decoderFactory(if (Build.VERSION.SDK_INT >= 28) ImageDecoderDecoder.Factory() else GifDecoder.Factory())`. Grid and list thumbnails stay static.
3. Build and commit "Animate GIFs in S3 preview".

### Task 4: Video playback

**Files:** `gradle/libs.versions.toml`, `feature/s3explorer/build.gradle.kts`, `model/S3Object.kt`, `S3ExplorerScreen.kt`, new `S3VideoPage.kt`

1. Add `media3 = "1.3.1"` with `media3-exoplayer` and `media3-ui`, and depend on both from s3explorer. That version works with compileSdk 34 and Kotlin 1.9.
2. In `S3Object`, add `isVideo` (mp4, webm, mkv, mov, m4v, 3gp) and `isMedia = isImage || isVideo`.
3. Tapping a video opens the preview; today only images do, so update both tap handlers. The pager covers `filteredObjects.filter { it.isMedia }`. Rename the dialog to `MediaPreviewDialog`.
4. Create `S3VideoPage(url: String, isCurrentPage: Boolean, modifier: Modifier)`:
   - `remember` an `ExoPlayer`, set `MediaItem.fromUri(url)`, then `prepare()`.
   - Set `playWhenReady = isCurrentPage` in a `LaunchedEffect(isCurrentPage)`.
   - Release the player in `DisposableEffect`.
   - Render `AndroidView { PlayerView(it).apply { player = exo; useController = true } }`.
   - Zoom gestures apply to images only.
5. In list and grid items, video tiles show a video icon in an `IconTile` with a small play badge instead of a thumbnail.
6. Keep the download, delete and info overlay working for videos.
7. Build and commit "Play videos in S3 preview".

### Task 5: Delete from host on the share screen

**Files:** `feature/history/.../home/ShareViewModel.kt`, `ShareCard.kt`, `app/.../navigation/NavGraph.kt` (the ShareResult block)

1. Inject `RemoteDeleteRepository` into `ShareViewModel`. Add `canDeleteFromHost: Boolean` to `ShareUiState`, computed once the item loads.
2. Add `deleteFromHost()`, mirroring `HomeViewModel.deleteFromHost`:
   - On success, delete the history row and emit a `Deleted` one-shot event.
   - Handle `OpenInBrowserException` with an OpenUrl event.
   - Otherwise surface an error message.
   - Add an `isDeleting` flag to the state.
3. In ShareCard, when `canDeleteFromHost` is true, show a red `OutlinedButton(CircleShape)` labelled "Delete from host" (DeleteOutline icon) below the details. It opens an AlertDialog confirm. While `isDeleting`, show a spinner.
4. In NavGraph, show a "Deleted from <host>" toast on `Deleted` and pop back to Home. Open the browser on OpenUrl, and toast errors.
5. Build and commit "Add delete from host to share screen".

### Task 6: Font licences

**Files:** `app/src/main/assets/licenses/*.txt`, `feature/settings/.../AppUpdateScreen.kt` (or a small new `LicensesDialog.kt` in feature/settings)

1. Download the official OFL texts:
   - `https://raw.githubusercontent.com/google/fonts/main/ofl/inter/OFL.txt` → `inter-OFL.txt`
   - `.../ofl/intertight/OFL.txt` → `intertight-OFL.txt`
   - `.../ofl/jetbrainsmono/OFL.txt` → `jetbrainsmono-OFL.txt`
2. On the Updates screen, add a `LumenCard` row "Open-source licenses" with an IconTile and a chevron. It opens a full-height dialog or bottom sheet listing the three fonts. Each font is an expandable section showing its licence text, loaded from assets, in `monoStyle(11)`.
3. Build and commit "Ship font licenses".

### Task 7: Verify

1. Run `./gradlew clean testDebugUnitTest lint assembleRelease`.
2. On the emulator, open the S3 explorer. If S3 isn't configured on the emulator, skip it and say so. Then check that the share-screen delete button shows for supported hosts and that the licence dialog opens.
