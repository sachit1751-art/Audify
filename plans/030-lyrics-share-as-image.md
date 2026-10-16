# Plan 030 — Lyrics share-as-image

**Status: not started.** Verified at commit `9e594bde` — the app has lyrics (portrait `LyricsLine`,
`OriginalLyrics`, `ExperimentalLyrics`), and a "Share lyrics" action exists, but it shares the lyrics as
text. There is no "share lyrics as an image" path — no bitmap compose, no card render to image.

## Objective

Let the user share the current lyric line (or the full lyrics card) as a visually clean image — a card
with the song title, artist, and lyrics — via the system share sheet. Viral, low-cost, identity-building.
Complements the existing text share.

## Why

- Matrix #8: SimpMusic ships lyrics share-as-image; no other YTM client does. Audify has the lyric data
  and the card components — closing this gap is a visible, shareable feature with low implementation cost.
- A lyric card is a natural social object: "here's the line I'm listening to." Text shares are invisible;
  an image card is postable.
- Reuses existing lyric components and the share sheet — the work is "render the card to a bitmap and
  share it", not "build a new lyrics UI".

## What exists today

- `ui/component/LyricsComponents.kt` — lyrics rendering with word-by-word sync.
- `ui/component/OriginalLyrics.kt` — original-language lyrics display.
- `ui/component/ExperimentalLyrics.kt` — experimental lyrics UI.
- A "Share lyrics" action exists (text share via `Intent.createChooser`).
- Share-image infra exists elsewhere in the app (recap card share, wrapped share) — the pattern for
  "compose a card to a bitmap and share it" already exists in some form.
- No lyric-card-to-bitmap compose path exists yet.

## Scope

**In scope:**
- A "Share as image" option in the lyrics share menu, alongside the existing "Share as text".
- A lyric card compose: song title, artist, the current lyric line (or a selected line), styled like the
  app's visual identity (parchment background, dark text, the "S" identity). The card is a Compose
  layout rendered to a bitmap.
- The bitmap is shared via `Intent.createChooser` as an image (PNG). The text share remains available as
  a separate option.
- The card uses the current lyric line by default; if a line is long, the card shows the current line and
  the song context. Not the full lyrics — a card, not a document.
- Shown in the lyrics share menu (where "Share lyrics" / "Share as text" already lives).

**Out of scope:**
- Full-lyrics image (all lines) — too tall for a share card; the line + context is the card. A full-lyrics
  image is a different feature (defer).
- Custom share targets, cloud upload, or any server path — pure local bitmap + system share sheet.
- Animated lyric cards — static image only for v1.
- Changing the existing text share — keep it; add the image share as an extra option.

## Affected files

| File | Change |
|------|--------|
| `ui/component/LyricsShareCard.kt` (new) | Compose card layout: song title, artist, current lyric line, app identity. Renders to a bitmap via `ComposeView` / `draw` to `Bitmap`. |
| `ui/component/LyricsComponents.kt` (or the share menu host) | Add "Share as image" to the lyrics share menu alongside "Share as text". |
| `sachit_strings.xml` | "Share lyrics as image", "Sharing lyric card…", empty state if no lyrics. |
| `utils/BitmapShare.kt` (new, or extend existing share utils) | Helper: Compose layout → Bitmap, then share via `Intent.ACTION_SEND` with `image/*`. Reuse the existing share-sheet pattern. |

## Technical approach

1. **Card compose** — A small Compose function that lays out the card: app identity (a subtle "S" mark or
   "Audify" label), song title, artist, the current lyric line, styled with the app's parchment background
   and dark text. This is the same visual language as the app — not a generic image.
2. **Render to bitmap** — Use the existing app pattern for "compose to bitmap" (the recap share path, if
   it exists, is the template). The card Composable is measured and drawn to a `Bitmap` at a fixed
   resolution (e.g. 1080px wide).
3. **Share** — Wrap the bitmap in a `FileProvider` URI (or share the bitmap directly if the share path
   allows it), fire `Intent.ACTION_SEND` with `image/png` via `Intent.createChooser`. Reuse the existing
   "Share lyrics" menu as the host — add "Share as image" as a second option.
4. **Content** — Default to the current lyric line + song title + artist. If there's a selected line or a
   highlighted line, use that. Keep it to one line — a card, not a document. If the line is empty (no
   lyrics), show an empty-state message on the card.
5. **Cancel / failure** — If bitmap render fails, fall back to a toast and no share. Standard failure
   handling; no crash.

## Testing

- Unit test for the card compose layout (or a snapshot test if the project has that infra) — verify the
  card renders with title, artist, line, and identity.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: open lyrics for a song with synced lyrics, open the share menu, verify "Share as image" appears,
  tap it, verify the system share sheet offers the image to image apps (gallery, messaging, etc.). Verify
  the image looks like the app (parchment, dark text, identity). Verify "Share as text" still works.
- Regression: existing lyrics share (text) still works; lyrics UI still works.

## Performance notes

- Bitmap render is a one-time cost on share, not per frame. Cheap.
- Keep the card resolution reasonable (1080px wide) — sharp enough to share, not a multi-MB image.

## Rollback

- Remove "Share as image" from the menu and delete the card + bitmap utils → text share only, as before.
- Zero impact on lyrics playback or sync.

## Anti-BitChord check

- The card uses Audify's visual identity (parchment, dark text, the "S" mark) — not a generic image and
  not copied from SimpMusic's card style.
- Private and local — no upload, no server, no analytics on what's shared. The share is purely a local
  bitmap handed to the system share sheet.
