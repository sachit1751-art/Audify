# 086 — Make artwork loading fades responsive and consistent

- **Status**: TODO
- **Commit**: ba5d080d
- **Severity**: MEDIUM
- **Category**: Easing & duration; cohesion; performance
- **Estimated scope**: 3–5 files; artwork presentation cleanup

## Problem

Artwork appears throughout the application, but loading fades use inconsistent and slow ease-in timing.

Current shared artwork class at `web/app/src/components/music/Artwork.tsx:38-40`:

```tsx
<img
  key={`${url}#${attempt}`}
  className="absolute inset-0 size-full object-cover opacity-0 transition-opacity duration-250 ease-in data-loaded:opacity-100"
```

Artist hero replacement at `web/app/src/pages/ArtistPage.tsx:86-94`:

```tsx
{apple && (
  <img
    key={apple.heroUrl}
    className="absolute inset-0 size-full object-cover opacity-0 transition-opacity duration-400 ease-in data-loaded:opacity-100"
    src={apple.heroUrl}
```

The hero replacement is a 400ms ease-in fade; the shared artwork fade is 250ms ease-in. Both delay the visible response.

## Target

- Shared artwork image load: opacity-only 180ms `var(--ease-out)` transition.
- Artist hero replacement: opacity-only 220ms `var(--ease-out)` transition.
- Both remain below the 300ms UI budget.
- Preserve image identity keys, loading/retry behavior, eager/lazy policy, crop, masking and existing fallback semantics.
- In reduced-motion mode, use the short opacity feedback policy defined in plan 084; do not reintroduce positional movement.

## Repo conventions to follow

- Artwork presentation is centralized in `web/app/src/components/music/Artwork.tsx`.
- Hero replacement is owned by `web/app/src/pages/ArtistPage.tsx`.
- Easing tokens are defined in `web/app/src/styles/tokens.css`; use the shared ease-out token from plan 085 once it exists, otherwise use the exact CSS curve `cubic-bezier(0.23, 1, 0.32, 1)`.

## Steps

1. Confirm the current branch still has the quoted image classes and inspect the component's `loaded`/`attempt` state transitions.
2. Change the shared artwork opacity transition to 180ms ease-out.
3. Change the artist hero replacement opacity transition to 220ms ease-out.
4. Verify that image key changes, retries, errors and `onLoad` handling do not leave stale `data-loaded` state or flash a previous image.
5. Review other artwork load fades only when they share the same loading semantics; do not globally change editorial motion artwork in this plan.

## Boundaries

- Do not change artwork URL sizing, network retry, cache, `referrerPolicy`, decoding or loading priority.
- Do not animate layout, crop, radius or image position.
- Do not change the full-screen player's motion artwork; that belongs to plan 087.
- Do not add dependencies.

## Verification

- **Mechanical**: run the existing web lint, typecheck and component tests; add a test for load state resetting when the keyed source changes if the current test setup supports it.
- **Feel check**: test cached, cold, slow, failed and retried artwork on Artist, Album, Playlist and track rows. At 10% playback, opacity should rise promptly with no visible double image or stale frame.
- Toggle reduced motion and verify the image still becomes visible and state remains understandable.
- **Done when**: all in-scope artwork load transitions use the specified responsive opacity fades and source/retry behavior is unchanged.
