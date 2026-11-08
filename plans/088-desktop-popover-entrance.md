# 088 — Add origin-aware motion to desktop popovers

- **Status**: TODO
- **Commit**: ba5d080d
- **Severity**: LOW
- **Category**: Missed opportunity; physicality & origin; interruptibility
- **Estimated scope**: 2–4 files; shared GPUI popover presentation

## Problem

The desktop UI has a shared GPUI motion system, including `Fading` and `Rising` entrance helpers in `desktop/crates/ui/src/motion.rs:224-249`, but the shared popover currently conditionally inserts/removes its menu without applying an entrance transition.

Current popover body in `desktop/crates/ui/src/popover.rs:192-207`:

```rust
let body = menu.filter(|_| open).map(|menu| {
    let outside = group.clone();
    let chosen = group.clone();

    menu.trigger(trigger)
        .on_dismiss(move |_, _, cx| {
            outside.close();
            cx.refresh_windows();
        })
        .when(commands, |menu| {
            menu.on_action(move |_, _, cx| {
                chosen.close();
                cx.refresh_windows();
            })
        })
});
```

The body appears/disappears as a conditional child. The current `Rising`/`Fading` helpers offer an existing entrance vocabulary, but a popover is trigger-anchored and must not inherit a generic centered origin or break menu dismissal/focus behavior.

## Target

- Opening a shared desktop popover uses a subtle, trigger-origin-aware entrance: opacity 0→1 plus a small 0.97→1 scale, 125–180ms, using the existing quick/ease-out motion preset.
- The transform origin is derived from the actual trigger/menu placement (top/bottom and left/right alignment); do not assume center.
- Closing remains immediate unless the GPUI lifecycle can retain the menu safely through an exit. Never delay dismissal, action dispatch, focus restoration or outside-click behavior to finish an animation.
- Reduced-motion mode preserves a short opacity-only state cue and removes scale/position travel.

## Repo conventions to follow

- Shared motion primitives and OS reduced-motion handling live in `desktop/crates/ui/src/motion.rs`.
- Popover trigger bounds are observed by `head(trigger)` in `desktop/crates/ui/src/popover.rs:215-217`; `Popover` already obtains `trigger` from its `Popovers` group.
- The desktop motion system uses `Motion::Quick` (120ms before pace scaling), `Motion::Base` (200ms), and `ease_out_quint` for quick/slow motion.
- Do not use the `Rising` helper blindly: it applies its own entrance veil/scale/blur and may not provide the trigger-specific origin needed here.

## Steps

1. Trace `Popover`, `Popovers`, `Trigger`, `Menu` placement and the existing GPUI animation lifecycle. Establish how the final menu bounds and trigger bounds are available together.
2. Confirm how `motion::animates(cx)` and the platform reduced-motion flag are exposed to the popover render path.
3. Implement a small, interruptible entrance using existing `Motion::Quick`/`Fading` primitives or a narrowly scoped extension. Derive origin from actual placement and keep scale subtle (0.97→1).
4. Ensure opening another popover in the same group cancels/replaces the previous presentation cleanly; no stale animation or overlay may remain.
5. Keep dismissal, action callbacks, outside-click handling, keyboard navigation and accessibility tree changes immediate. If GPUI cannot animate exit without compromising these semantics, retain immediate exit and document it.
6. Add tests for top/bottom/left/right placement, rapid open-switch-close, reduced motion and focus behavior where the GPUI test harness permits.

## Boundaries

- Do not animate every menu item or add stagger to frequent menu interactions.
- Do not modify panel resizing, layout sizing, z-order, menu contents or command behavior.
- Do not introduce a new animation framework or custom frame loop.
- Do not change `desktop/crates/ui/src/panel.rs` in this plan; its panel sizing/drag behavior is separate.
- If trigger and menu geometry cannot be resolved reliably, stop and report rather than using a guessed transform origin.

## Verification

- **Mechanical**: run the focused desktop UI crate tests and the repository's existing Rust formatting/lint checks; do not create new test harness infrastructure solely for this transition.
- **Feel check**: open menus from toolbar buttons, track rows, picker controls and context menus near all four window edges. The menu should appear to emerge from its trigger without covering it or jumping after placement flips.
- Open a second popover while the first is animating, dismiss with Escape/outside click, and activate a menu item; behavior and focus must remain immediate.
- Toggle OS reduced-motion settings on Windows/macOS/Linux and verify scale travel is removed while brief opacity feedback remains.
- **Done when**: shared popovers have subtle placement-aware entrances, reduced-motion behavior is correct, and all menu interaction semantics remain unchanged.
