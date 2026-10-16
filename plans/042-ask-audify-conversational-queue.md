# Plan 042 — "Ask Audify" conversational queue

**Status: not started.** Verified at commit `61778ab4` — no conversational queue feature exists. The app 
has `AiSettings` (AI providers) and Shazam/recognition, but no local prompt→queue path. The user can ask 
(in natural language) to queue songs and Audify builds the queue locally from existing content + AI 
providers, privacy-safe, no server.

## Objective

A "Ask Audify" feature: the user types a natural-language prompt (e.g. "queue some lo-fi for studying", 
"play songs like the last one", "make a workout queue") and Audify builds a queue from the prompt, using 
existing content (library, auto-playlists, YT search) + the existing AI providers (if enabled), locally, 
with no server. Privacy-safe: the prompt never leaves the device unless an AI provider that requires a server 
is used — and even then, the default is local-first.

## Why

- Matrix #21: YTM has "Ask Music" (conversational). Audify has `AiSettings` + recognition — the pieces are 
there to build a local conversational queue.
- A conversational queue is a natural extension of the existing AI settings + recognition infra. It's a 
privacy-positive feature: the user asks, Audify builds a queue locally.
- It distinguishes Audify from plain YTM clients — a conversational queue is a power-user discovery tool.

## What exists today

- `AiSettings` / AI provider infra exists (the executor checks at implementation time what providers are 
available — e.g. a local LLM, an API-based provider, etc.). The plan deliberately doesn't hard-code the 
provider — the executor implements the prompt→queue path using the existing AI infra.
- Music recognition (Shazam-style) exists — "recognize this" → song. The conversational queue is a different 
path: "ask" → queue.
- Existing content: library, auto-playlists, YT search (the executor uses these as the content sources for 
the queue).
- No prompt→queue UI, no prompt parsing, no queue-building from a prompt.

## Scope

**In scope:**
- A "Ask Audify" UI: a text input (the prompt) + a "Build queue" action. The user types a prompt, taps 
build, Audify builds a queue and plays it / adds it to the queue.
- Prompt→queue pipeline: parse the prompt (intent: "queue similar to X", "queue genre Y", "queue my top 
songs", "queue a mood"), resolve to content sources (library, auto-playlists, YT search, recents), build a 
queue, present it (preview → play / add to queue).
- AI provider usage (optional): if an AI provider is enabled and the prompt benefits from it (e.g. "make a 
queue like this" needs a similarity model; "lo-fi for studying" may need semantic understanding), the prompt 
is processed by the provider. If no provider is enabled, Audify uses rule-based heuristics (keyword match: 
"lo-fi" → genre, "top" → top songs, "similar to X" → song radio). The executor implements the rule-based 
fallback so the feature works without a provider.
- Privacy-safe: the prompt is local by default. If a server-based AI provider is used, the user has configured 
it in `AiSettings` and knows it goes to a server. The feature itself doesn't introduce a new server — it uses 
the existing AI infra. Local-first: rule-based fallback works with no provider.
- Queue preview: before playing, show the built queue (the songs Audify picked) so the user can confirm / edit 
/ cancel. The queue is built, shown, then played (or added to the existing queue).

**Out of scope:**
- A full AI assistant / chat — this is a prompt→queue feature, not a general chat. Don't scope-creep into 
"talk to Audify".
- Server-side queue building — defer; v1 is local-first, using existing AI providers only if the user has 
configured them. The rule-based fallback ensures it works offline.
- Voice input — defer; v1 is text prompt. Voice can be added later (the recognition infra may help).
- Prompt history / memory — defer; v1 is one-off prompts, no memory.
- Complex intent parsing — v1 handles a few clear intents (similar to X, genre, top, mood) + keyword 
heuristics. Don't over-build the parser for v1.

## Affected files

| File | Change |
|------|--------|
| `ui/screens/AskAudifyScreen.kt` (new) | "Ask Audify" UI: prompt input, Build queue action, queue preview. Entry point from a menu / nav / player action. |
| `utils/PromptToQueue.kt` (new) | The prompt→queue pipeline: parse intent, resolve to content sources, build queue. Rule-based heuristics + optional AI provider. The core logic — testable, pure where possible. |
| `viewmodels/AskAudifyViewModel.kt` (new) | Bridge: prompt → `PromptToQueue` → queue preview → play/add action. State: prompt, building, preview queue, error. |
| `constants/PreferenceKeys.kt` | Optional: "Ask Audify" enable toggle, provider preference if the feature gates on a provider. |
| `sachit_strings.xml` | "Ask Audify", "Build queue", prompt hints ("Queue lo-fi for studying", "Play songs like…", "Make a workout queue"), preview labels, error strings. |
| `ui/menu/` or nav | Add "Ask Audify" entry (in the player menu, home, or a nav destination) — the executor picks the entry point at implementation time. |

## Technical approach

1. **Prompt→queue pipeline** — The core is `PromptToQueue`: given a prompt string, return a queue (list of 
songs) + a label explaining what was built. The pipeline has two layers: (a) rule-based heuristics (keyword 
match: "lo-fi" → genre filter, "similar to X" → song radio from a song the user names, "top" → top songs, 
"mood" → genre/playlist heuristics), (b) optional AI provider (if enabled, the prompt is sent to the provider 
for a richer interpretation / similarity). The rule-based layer ensures it works offline with no provider.
2. **Content sources** — The queue is built from existing content: library songs, auto-playlists, YT search 
(results for a query derived from the prompt), recents. The executor picks which sources to use at 
implementation time; the key is that the queue is built from Audify's existing content, not a new content 
source.
3. **Queue preview** — Before playing, show the built queue (the songs + a label). The user can: Play (start 
from the first), Add to queue (append to the existing queue), Cancel. The preview is the trust step — the user 
sees what was built before it plays.
4. **AI provider usage** — If a provider is enabled and the prompt benefits from it, the pipeline uses it 
(e.g. for "make a queue like this song" the provider may give similarity scores; for "lo-fi for studying" the 
provider may interpret the mood). If no provider, the rule-based layer handles it. The feature doesn't 
introduce a new provider — it uses the existing `AiSettings` infra. The executor implements the provider 
integration at implementation time using the existing AI infra.
5. **Entry point** — The executor picks the entry point at implementation time: a menu entry in the player, a 
home action, a nav destination. "Ask Audify" should be easy to find — not buried. The executor picks the most 
natural spot.
6. **Privacy** — The prompt is processed locally by default. If a server-based provider is used, it's because 
the user configured it in `AiSettings` — the feature doesn't introduce a new server. Document the privacy 
posture in the feature UI (e.g. "Processed locally" or "Uses your configured AI provider").

## Testing

- Unit test for `PromptToQueue` rule-based layer: given prompts like "lo-fi for studying", "songs like X", 
"top songs", "make a workout queue", assert the pipeline returns a sensible queue (correct content source, 
correct songs for the heuristic). Test the rule-based fallback works with no provider.
- Unit test for the AI provider path (mock provider): given a prompt that benefits from the provider, assert 
the pipeline uses the provider and builds a queue. Don't hit a real API in unit tests.
- Unit test for queue preview state: prompt → building → preview queue → play/add/cancel transitions.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: open "Ask Audify", type a prompt (e.g. "lo-fi for studying"), verify a queue is built and shown, 
verify play/add/cancel. Test prompts that use the rule-based fallback (no provider configured). Test with a 
provider enabled (if the executor configures one). Verify the queue plays correctly.
- Regression: existing AI settings unchanged; existing recognition unchanged; existing queue behavior unchanged.

## Performance notes

- Prompt→queue is a one-time operation on user action — not per-frame. Cheap.
- AI provider call (if used) is a one-time network call (if server-based) — the executor handles it on the 
right dispatcher with loading state. Rule-based fallback is instant.

## Rollback

- Remove the "Ask Audify" screen, `PromptToQueue`, the VM, and the menu entry → no conversational queue, 
existing AI settings + recognition unchanged. Zero impact on the rest of the app.

## Anti-BitChord check

- "Ask Audify" is a local-first conversational queue — not a server-dependent AI feature. The rule-based 
fallback ensures it works offline. Don't scope-creep into a server-dependent AI assistant — keep it a 
prompt→queue feature with local-first operation.
- Use Audify's existing AI infra (if the user has configured a provider) — don't introduce a new AI provider 
or server. The feature is opt-in in the sense that it uses what the user already configured; the rule-based 
fallback means it works for everyone.
