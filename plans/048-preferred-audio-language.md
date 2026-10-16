# Plan 048 — Preferred audio language

**Status: not started.** Verified at commit `61778ab4` — no preferred-audio-language selection exists. 
Videos with multiple audio tracks don't offer a per-video or per-user audio language choice today.

## Objective

Let the user pick a preferred audio language for videos that have multiple audio tracks. When playing a video 
song with multiple tracks, Audify chooses the track matching the user's preferred language (if available), 
falling back to the default track if the preferred language isn't available. Per-video override optional (v1 may 
be just a global preference — the executor decides at implementation time whether to add per-video override).

## Why

- Matrix #13: SimpMusic has preferred audio language for multi-track videos. Audify plays video songs but 
doesn't offer an audio language choice — it picks the default track.
- A global preferred-audio-language setting is a small, focused feature: the user sets a language, and videos 
with that language track play it. Low cost, clear value for multilingual users.
- It's a playback behavior, not a content feature — small scope, clear implementation.

## What exists today

- Video song playback exists (`MusicService`, video mediaIds, the stream resolution picks audio formats).
- The executor checks at implementation time whether the stream resolution / format selection already has access 
to the available audio tracks' languages (many video formats include language tags on audio streams). If the 
stream data includes language tags, the feature is a small selection tweak. If not, the executor may need to 
fetch track info — check at implementation time.
- No global audio language preference, no per-track language selection logic.

## Scope

**In scope:**
- A global preferred audio language setting (a language selector, e.g. a list of common languages + "Default 
/ auto"). The user picks a language; videos with an audio track in that language play that track.
- Stream selection logic: when selecting an audio stream for a video song, if the video has an audio track in 
the user's preferred language, pick that track. If not, fall back to the default track (the executor's existing 
default selection logic). No change to the fallback behavior.
- Per-video override (optional, v1 may be global-only): the executor decides at implementation time whether to 
add a per-video override (e.g. a small audio track selector on the video player). v1 can be global-only if the 
executor prefers simplicity. Document the decision.
- Applies to video songs only — audio-only songs have one track, no language choice. The feature is gated on 
the video having multiple audio tracks (if it has one track, there's nothing to choose).

**Out of scope:**
- Audio language per playlist / per queue — defer; v1 is a global preference (or per-video if the executor 
adds that).
- Voice/silent track selection — defer; v1 is main audio language.
- Downloading a specific audio track on download — defer; v1 is playback-time selection. The downloaded file 
is what it is; playback picks the right track from the available streams.
- Language sync / lyric sync tied to audio language — defer; v1 is audio track selection only.

## Affected files

| File | Change |
|------|--------|
| `constants/PreferenceKeys.kt` | Add `PreferredAudioLanguageKey` (the user's preferred language code, e.g. "en", "es", "hi", or "default"). |
| `ui/screens/settings/AudioSettings.kt` (or the existing audio/playback settings) | Add a preferred audio language selector (list of common languages + default). |
| `playback/MusicService.kt` or the stream selection logic (the executor checks at implementation time where stream/audio-track selection lives) | When selecting an audio stream for a video song, if the preferred language is set and a matching track exists, pick it; else fall back to default. |
| `sachit_strings.xml` | Language selector labels, "Preferred audio language", language names, "Default" option. |

## Technical approach

1. **Preference** — A `PreferredAudioLanguageKey` storing the user's preferred language code (e.g. "en", "es", 
"fr", "hi", "de", "ja", "zh", … — the executor picks the language list at implementation time, common languages 
+ a "Default / auto" option that falls back to the default track).
2. **Stream selection** — At playback time, when selecting an audio stream for a video song: if the preferred 
language is set (not "default") and the video has an audio track with that language code, select that track. If 
no matching track, fall back to the existing default selection logic. The executor implements this at the 
stream-selection point (the exact file is checked at implementation time — it may be in `MusicService`, the 
stream resolver, or the format selection logic).
3. **Per-video override (optional)** — The executor decides at implementation time whether to add a per-video 
audio track selector on the video player (e.g. a small audio track chooser). v1 can be global-only if the 
executor prefers simplicity. Document the decision at implementation time.
4. **Fallback** — If the preferred language is "default" or not set, use the existing default track selection 
unchanged. No behavior change when the preference is default.
5. **Downloads** — Downloads are not affected (v1 plays the right track from the available streams; the downloaded 
file is what it is). If the user wants a specific audio track downloaded, that's a different feature (defer).

## Testing

- Unit test for the stream selection logic: given a video with multiple audio tracks (with language codes) + a 
preferred language, assert the matching track is selected; given no matching track, assert the default track is 
selected; given "default" preference, assert the default track is selected.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: set preferred language to a specific language, play a video song that has a track in that language, 
verify that track plays. Play a video song without that language, verify the default track plays. Set preference 
to "default", verify default track always plays. Test on a video song with one track (no choice) — works as 
before.
- Regression: existing playback for audio-only songs unchanged; existing default track selection unchanged when 
preference is default.

## Performance notes

- Stream selection with a language check is a small lookup — cheap. No new work per frame.
- No new network / download cost — playback-time selection only.

## Rollback

- Remove the preference + stream-selection logic → video songs play the default track, as before. Zero impact on 
audio-only songs or existing playback.

## Anti-BitChord check

- Preferred audio language is a standard feature — SimpMusic has it. Implement as Audify: a global preference + 
playback-time track selection + fallback. Don't scope-creep into per-video override or download-track selection 
for v1 unless the executor chooses to. Keep it a focused playback setting.
