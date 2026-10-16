# Plan 046 — MotionTokens cleanup

**Status: not started.** Verified at commit `61778ab4` — no centralized motion token system exists. Durations 
and easings are scattered as literals across composables and GSAP timelines. The plan is to centralize them 
into a single source of truth (a `MotionTokens` object / design-tokens file) and replace literals with named 
tokens, per the design system's motion section.

## Objective

Centralize all animation durations and easings into a single `MotionTokens` source of truth (one file / object), 
replace hardcoded literals with named tokens (e.g. `MotionTokens.sheetEnter`, `MotionTokens.fadeDefault`), and 
remove orphaned literals. The design system's motion section is the spec — the executor reads it at 
implementation time and maps each token to the right value.

## Why

- Design system §4 (motion) says durations and easings should be centralized. Today they're scattered — 
different screens may use slightly different fade durations, sheet easings, etc. Centralizing makes motion 
consistent and changeable in one place.
- A cleanup pass — not a feature. It makes the codebase easier to maintain and the motion consistent.

## What exists today

- Many composables and GSAP timelines use hardcoded durations (`0.3s`, `0.5s`, `0.6s`) and easings 
(`power2.out`, `power1.inOut`, `linear`, etc.) as literals.
- No single `MotionTokens` object / design-tokens file for motion (the executor checks at implementation time 
whether a partial one exists).
- The design system's motion section (in `plans/AUDIFY_DESIGN_SYSTEM.md` or wherever the design system lives) is 
the spec — the executor reads it and maps tokens to values.

## Scope

**In scope:**
- Create a `MotionTokens` object (or design-tokens file) with named duration tokens (e.g. `fast = 150ms`, 
`normal = 300ms`, `slow = 500ms`, `sheetEnter = 250ms`, `fade = 200ms`, etc.) and easing tokens (e.g. 
`default = ease.out`, `slide = ease.slide`, `spring = spring(...)`, etc.). The exact names and values come 
from the design system's motion section — the executor reads it at implementation time.
- Replace hardcoded literals in composables and GSAP timelines with the named tokens. The executor picks the 
literals to replace at implementation time — start with the common ones (fade, sheet enter/exit, item animate) 
and the ones that are clearly duplicated.
- Remove orphaned literals (literal durations/easings that are no longer used after the token replacement).
- Keep the change mechanical and low-risk: a duration literal → `MotionTokens.xxx` is a safe replacement. Don't 
refactor motion behavior as part of this — just centralize the tokens.

**Out of scope:**
- Changing motion behavior (making things faster/slower, adding new animations) — that's a separate pass. This 
plan centralizes existing motion, it doesn't redesign it.
- GSAP timeline restructure — the plan replaces literals with tokens inside existing timelines, not rewrites 
timelines.
- Design system motion section authoring — if the design system's motion section doesn't exist yet, the executor 
writes it first (or the plan defers to a design-system pass). The plan assumes the motion section exists as the 
spec.
- Non-motion literals (colors, spacing, typography) — those are a different cleanup pass. This plan is motion 
only.

## Affected files

| File | Change |
|------|--------|
| `utils/MotionTokens.kt` (new, or extend existing tokens file) | Central `MotionTokens` object / design-tokens: duration tokens, easing tokens. The spec is the design system's motion section. |
| `ui/` composables (many files) | Replace hardcoded duration/easing literals with `MotionTokens.xxx`. The executor picks files at implementation time — start with common ones. |
| GSAP timelines (in composables or timeline JS) | Replace hardcoded durations/easings with tokens. The executor picks timelines at implementation time. |
| `sachit_strings.xml` | No strings needed for v1 — this is a code cleanup, not a user-facing feature. (Unless motion tokens are exposed to the user somewhere, which they aren't for v1.) |

## Technical approach

1. **Create MotionTokens** — A central object / design-tokens file with named duration and easing tokens. The 
values come from the design system's motion section. Example (the executor fills in the real values from the 
design system): `Duration.Fast = 150.milliseconds`, `Duration.Normal = 300.milliseconds`, `Duration.Slow = 
500.milliseconds`, `Easing.Default = EaseOut`, `Easing.Slide = EaseOutSlide`, etc. The executor reads the 
design system's motion section at implementation time and maps each token to the right value.
2. **Replace literals** — In composables and GSAP timelines, replace hardcoded literals with the tokens. E.g. 
`duration = 300` → `duration = MotionTokens.Duration.Normal`, `ease = "power2.out"` → `ease = MotionTokens.Easing.Default`. 
The executor picks which literals to replace at implementation time — prioritize the common/duped ones.
3. **Remove orphans** — After replacement, scan for leftover literal durations/easings that are no longer used 
and remove them. The executor does this at implementation time.
4. **No behavior change** — The values stay the same (tokens map 1:1 to the existing literals). The motion 
behavior doesn't change — only the source of truth moves to `MotionTokens`. The executor verifies at 
implementation time that the motion looks the same after the change.
5. **Compose + GSAP both** — The plan covers both Compose (duration/easing params) and GSAP timelines 
(duration/easing in the timeline). Both use the same `MotionTokens` — one source of truth for the whole app.

## Testing

- Build: `./gradlew :app:assembleFossDebug` must pass. No behavior change means no new tests are strictly 
required for the cleanup — the existing tests should still pass.
- Manual: visually verify motion is unchanged after the cleanup (fades, sheets, item animations look the same). 
The executor does a quick visual check at implementation time.
- Regression: any existing motion tests still pass. No new motion behavior, so no new motion tests needed for 
v1.

## Performance notes

- No performance impact — tokens are compile-time constants / top-level values. Replacing a literal with a 
token reference is zero-cost at runtime.

## Rollback

- Revert the `MotionTokens` file and the token replacements → literals back, motion unchanged. The rollback is 
mechanical (revert the token file + the replacements).

## Anti-BitChord check

- A motion token cleanup is a standard engineering cleanup — not a copy of a competitor's system. Implement it as 
a mechanical, low-risk pass: one source of truth, replace literals, no behavior change. Don't scope-creep into 
redesigning motion — that's a different pass.
- The design system's motion section is the spec — the executor reads it and maps tokens. If the design system 
section is vague, the executor picks reasonable values and documents them in `MotionTokens` (with a comment 
pointing to the design system section). Don't invent a motion system from scratch.
