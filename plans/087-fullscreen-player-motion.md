# 087 — Refine full-screen player artwork motion

- **Status**: TODO
- **Commit**: ba5d080d
- **Severity**: MEDIUM
- **Category**: Easing & duration; physicality & origin; accessibility
- **Estimated scope**: 1–3 files; focused player motion change

## Problem

`web/app/src/shell/FullScreenPlayer.tsx:293-297` scales artwork between playing and paused states over 450ms with an overshooting curve. The transition is long for a frequently visited player surface.

Current code:

```tsx
<div
  className={`relative w-(--art) rounded-[10px] transition-[scale] duration-450 ease-[cubic-bezier(0.34,1.3,0.64,1)] motion-reduce:transition-none${
    video ? ' aspect-video overflow-hidden bg-black' : ' aspect-square'
  }${playing ? '' : ' scale-92'}${column ? ' max-md:hidden' : ''}${theater ? ' hidden' : ''}`}
>
```

The same file has 600ms ease-in motion-artwork opacity at `:327`, 120ms ease-in seek-bar hover/progress feedback at `:57-63`, and 100ms ease-in button feedback at `:79` and `:121`.

## Target

- Playing/paused artwork scale: 220ms using the exact strong ease-in-out curve `cubic-bezier(0.77, 0, 0.175, 1)`; preserve the existing 0.92 resting scale unless a visual/device check demonstrates it is too strong.
- Reduced motion: do not spatially scale the artwork; retain an opacity/state cue of 120ms ease-out rather than disabling all feedback.
- Motion artwork fade: maximum 250ms ease-out, provided the media remains legible and the transition does not compete with track changes.
- Seek-bar hover and button hover/press colour feedback: use `ease` at their existing short 100–120ms timing; do not animate actual playback progress with easing (progress remains continuous/linear).

## Repo conventions to follow

- Shared web curves belong in `web/app/src/styles/tokens.css`; see plan 085 for the exact token values.
- `FullScreenPlayer.tsx` already uses Tailwind arbitrary property and easing classes and includes a reduced-motion variant.
- Keep motion artwork as an overlay; preserve `MotionArtwork` readiness and playback gating.

## Steps

1. Confirm the quoted classes and inspect all uses of `artworkLayer`, `MotionArtwork`, `playing`, `theater` and `column` before editing.
2. Replace the 450ms overshooting scale transition with 220ms ease-in-out; ensure the transition is interruptible when play/pause changes rapidly.
3. Replace `motion-reduce:transition-none` with a policy that suppresses scale movement while retaining short opacity/state feedback.
4. Shorten the motion-artwork opacity fade to at most 250ms ease-out; verify it never masks the static artwork for an excessive interval.
5. Classify the seek-bar and button transitions as hover/press feedback and use the shared hover curve without changing progress timing or transport response.

## Boundaries

- Do not change playback state, video behavior, audio transport, seek math or media selection.
- Do not change artwork sizing, crop, glow, blur, shadow or player layout.
- Do not add continuous scale animation while playing.
- Do not add dependencies.

## Verification

- **Mechanical**: run the existing web lint, typecheck and tests after inspecting `web/app/package.json`.
- **Feel check**: toggle play/pause rapidly, switch tracks, enter/exit theater mode, enable lyrics column, and test video and static artwork. The latest state must win without overshoot or delayed transport response.
- At 10% playback, confirm the scale settles in 220ms and motion artwork fades in no slower than 250ms.
- Emulate reduced motion: no scale travel, but state remains perceivable through opacity/colour.
- **Done when**: player motion is responsive, interruptible, reduced-motion compliant and verified across player layouts.
