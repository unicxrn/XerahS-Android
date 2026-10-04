# Lumen redesign

Full visual redesign of XerahS Android, based on Option D "Lumen" from the redesign canvas.

## Goals

- Modern, premium look that still feels like XerahS.
- Works with every theme setting we have today: System / Light / Dark, true black, the seven accent presets (Signal Lime default, Cyan, Violet, Blue, Amber, Pink, Green), the custom accent, saved custom themes and Material You.
- Same navigation routes, data, settings keys and upload behaviour. Visual change only, apart from the bottom bar.

## Theme layer

**Fonts.** Bundle `InterTight` variable (OFL) in `app/src/main/res/font`. Display and headline styles use Inter Tight Bold with negative tracking (-0.04em to -0.045em). Body and labels stay on Inter. Links, file sizes, timestamps and eyebrow labels use JetBrains Mono.

**Colour scheme.** Rewrite `colorSchemeForAccent` so the accent tints the neutrals:

| Role | Light | Dark | True black |
|---|---|---|---|
| background | 5% accent over #F5F5F8 | 6% accent over #0D0D11 | #000000 |
| surface | #FFFFFF | 6% accent over #16161B | 5% accent over #0B0B0E |
| surfaceContainer | 7% accent over #FFFFFF | 10% accent over #1C1C22 | 9% accent over #111115 |
| primary | accent | accent | accent |
| primaryContainer | 13% accent over white | 24% accent over surface | 20% accent over surface |
| onPrimaryContainer | accent darkened (82% accent + black) | accent lightened (58% accent + white) | same as dark |

Mixing is done in OkLab, matching the concept's `color-mix`. Body text keeps at least 4.5:1 contrast and is unit-tested.

**LumenTokens.** A `CompositionLocal` in `core/ui` with `tint`, `ink`, `hairline`, `glow`, `softShadowColor` and `navContainer`. `XerahSTheme` provides it, computed from the final `ColorScheme` after the theme source is resolved. Because it only depends on `colorScheme.primary` and the light/dark/true-black mode, Material You and custom themes get the full Lumen look automatically.

**Shapes.** small 14dp, medium 20dp, large 26dp, extraLarge 30dp. Buttons and chips are fully rounded pills.

## Shared components (`core/ui/lumen`)

| Component | Purpose |
|---|---|
| `LumenCard` | Surface card with hairline outline and soft shadow |
| `BezelCard` | Double-bezel card: tinted 5dp shell around an inner surface |
| `PillCta` | Full-width accent pill with the icon in a nested circle |
| `HostChip` | Host name with its brand dot (Imgur, S3, FTP, SFTP, Nextcloud, custom) |
| `Eyebrow` | Small uppercase mono label |
| `AccentGlow` | Radial accent glow behind screen headers |
| `CaptureCorners` | Screenshot-corner brackets drawn over image previews |
| `LumenNavPill` | Floating blurred bottom nav |
| `LumenSwitch` | Accent switch |
| `SegmentedTiles` | Icon + label segmented choice (theme mode) |
| `IconTile` | Tinted rounded-square icon holder |
| `LumenTopBar` | Large Inter Tight title with a circular back button |

The existing `XerahSComponents` (`SectionHeader`, `SettingsGroupCard`, `EmptyState`, `StatusBanner`, `GradientBorderCard`, `StatCard`) keep their signatures and are restyled on top of these, so every screen that uses them updates for free.

## Navigation

The `BottomAppBar` and FAB in `MainActivity` are replaced by `LumenNavPill`, shown on the same top-level routes plus Settings. Items: Home, Cloud (only when S3 is configured, as today), Tools, Settings. The selected item expands into an accent pill with its label. "New upload" moves to the Upload tile on Home.

## Screens

**Home.** Glow, brand mark, "N uploads today" eyebrow, the "Capture. Upload. Share in a tap." hero, a bento of Upload (accent, large, opens the picker), Search (toggles the search field) and Stats (opens Statistics), a bezel card for the latest upload (preview with capture corners, mono link, host chip, copy button), then recent uploads with host chips. Empty state when there is no history yet.

**Upload.** File header (thumbnail, name, mono metadata), "Upload to" card listing profiles with host dots and a radio, album and tag pill chips, and a `PillCta` to upload. (Per-upload after-upload chips are out of scope; after-upload stays a setting.) Batch mode shows a file count in the header. Progress and conflict handling keep working as today.

**Uploaded.** The success state of the upload screen and the share card opened from Home: accent check badge, "Uploaded. Link copied." headline, a bezel link card with a copy button, Share / Open / QR tiles, a detail list (host, size, time, delete URL saved) and Delete from host when the host supports it.

**Tools.** Title and an accent hero card for batch resize and convert, then a two-column grid: Color picker, QR, Hash. Tool screens get `LumenTopBar` and Lumen cards.

**Editor.** Top bar with back, mono file name, an Undo/Redo pill and an Effects button. Canvas in a bezel frame. A colour swatch row with stroke width, a floating pill toolbar with the active tool in accent, and a `PillCta` to continue.

**Appearance.** Live preview bezel, System / Light / Dark `SegmentedTiles`, the seven preset swatches plus a custom swatch, and toggles for Material You and True black. Accent swatches are disabled, with a note, while Material You is on. The theme editor reuses the swatches and preview.

**Everything else.** History, S3 explorer and stats, Settings and all its sub-screens, destination config, profiles, uploader import, backup, statistics, security, storage, onboarding and app update move to `LumenTopBar`, Lumen cards and pills. Their layout and behaviour stay as they are.

## Out of scope

- Domain, Room, WorkManager and upload pipeline changes.
- New settings, or changes to existing settings keys.
- New features beyond what is listed above.

## Testing

- Unit tests for accent mixing and token derivation: light, dark and true black, every preset, and text contrast of at least 4.5:1.
- Emulator screenshots of the main flow in light, dark, true black, Material You and two accents.
- Release gate: `./gradlew clean testDebugUnitTest lint assembleRelease`, then a startup check of the release build.
