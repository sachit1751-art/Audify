# 084 — Preserve useful feedback under reduced motion

- **Status**: TODO
- **Commit**: ba5d080d
- **Severity**: HIGH
- **Category**: Accessibility
- **Estimated scope**: 2–4 files; focused CSS/accessibility change

## Problem

The global reduced-motion rule in `web/app/src/styles/global.css:398-407` collapses every animation and transition to 0.01ms. This removes useful opacity and colour state feedback as well as nonessential movement.

Current code:

```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
```

The audit playbook explicitly says reduced motion means fewer and gentler animations, not zero: remove position changes and retain transitions that aid comprehension. Its reference example keeps a 0.2s opacity fade.

## Target

- Remove nonessential position, scale, rotation, parallax and large-blur movement in reduced-motion mode.
- Preserve short opacity and colour feedback where it communicates state; use 100–160ms for small controls and up to 200ms for simple opacity feedback.
- Disable smooth scrolling and continuous decorative keyframes where they are not essential.
- Do not globally force every transition to 0.01ms. Scope overrides to motion-bearing properties and component classes.
- Ensure any JS-driven animation also checks `prefers-reduced-motion` before applying transforms.

## Repo conventions to follow

- Global CSS and motion tokens live in `web/app/src/styles/global.css` and `web/app/src/styles/tokens.css`.
- Use the existing CSS custom-property and Tailwind conventions; do not add a motion dependency.
- The desktop motion system already exposes a platform reduced-motion preference in `desktop/crates/ui/src/motion.rs`; do not change that subsystem in this plan.

## Steps

1. Inventory web `@keyframes`, transitions, smooth-scroll rules and JS-driven transform animations; classify each as essential state feedback or nonessential movement.
2. Replace the universal 0.01ms blanket rule with targeted reduced-motion rules for movement/continuous effects while preserving opacity and colour transitions.
3. Add reduced-motion variants to any component whose transform is driven outside CSS and is not covered by the media query.
4. Keep `scroll-behavior: auto` in reduced-motion mode.
5. Add a browser-level regression test or deterministic stylesheet assertion proving that opacity feedback remains nonzero-duration while translation/scale motion is suppressed.

## Boundaries

- Do not change normal-motion styling or the visual design of controls.
- Do not remove semantic state changes or focus feedback.
- Do not change Android, TV, Wear OS or Desktop motion policy here.
- Do not add dependencies.
- If an animation cannot be classified safely, preserve it and report the uncertainty rather than disabling it globally.

## Verification

- **Mechanical**: run the web app's existing typecheck, lint and test scripts after inspecting `web/app/package.json`; add and run the focused reduced-motion regression test.
- **Feel check**: in browser DevTools emulate `prefers-reduced-motion: reduce`; toggle buttons, open menus, load artwork, change routes and scroll. State changes must remain perceivable without spatial travel or continuous decorative movement.
- Compare normal and reduced modes at 100% and 10% animation playback. Confirm no component becomes visually stuck or loses focus/state indication.
- **Done when**: reduced-motion mode suppresses nonessential movement, retains useful opacity/colour feedback, and automated checks cover the global policy.
