# Plan 004 — Scope or document the cleartext network config

Written against commit **`7ca7a9653`**. Security hardening pass on the network security
config.

## Why this matters

`app/src/main/res/xml/network_security_config.xml` currently:

```xml
<network-security-config>
    <!-- Allow cleartext traffic for Listen Together local servers -->
    <!-- We're already using WSS for the prod server -->
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

The `base-config` applies to **every** host the app talks to (YouTube API hosts, LastFM,
Discord gateway, the custom Listen Together server) — not just the local LAN servers the
comment claims to justify. Every dependency and all first-party endpoints use HTTPS today,
so exposure is latent, not active: the risk is that any future HTTP URL silently works, and
that a network attacker can already downgrade anything the app ever sends in cleartext.
Android's network-security-config cannot scope cleartext to private IP ranges, and the
Listen Together feature explicitly lets users type a custom server URL
(`ListenTogetherSettings.kt` `serverUrl`, default `wss://metroserverx.meowery.eu/ws` from
`ListenTogetherServers.kt`) that may be plain `ws://` on a LAN. So a blanket ban is not
possible without breaking that feature.

## Files in scope

- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/kotlin/com/sachit/music/ui/screens/settings/integrations/ListenTogetherSettings.kt`
  (only if the chosen option adds a warning; see step 3)
- `app/src/main/res/values/sachit_strings.xml` (English, only if a warning string is added)

## Options — pick after step 1

- **A. Tighten what's tighten-able**: keep `cleartextTrafficPermitted="true"` at base level
  (required for LAN `ws://` custom servers), but add an explicit `domain-config` for the
  well-known public hosts (YouTube/LastFM/Discord are all HTTPS-only) is NOT possible —
  base-config already permits, domain-config can only add per-host rules when base is false.
  Realistic tightening: none available for arbitrary LAN IPs.
- **B. Keep config, fix the lie, add a user warning**: update the misleading comment,
  assert in the config file that all first-party traffic is HTTPS, and — where a user pastes
  a custom Listen Together server URL — show a one-time warning when the URL is not `wss://`
  ("this server connection is unencrypted; only use on trusted networks").
- **C. ADR-style decision record**: since no config can express "cleartext for LAN IPs
  only", record the decision + rationale in an XML comment (markdown ADRs are off-limits per
  AGENTS.md) so future contributors don't "fix" it into a regression.

Recommended: **B + C together** (small, honest, user-protective); A is a no-op on Android.

## Steps

1. **Confirm current usage**: grep the codebase for every `http://` and `ws://` literal to
   verify nothing first-party is currently cleartext to a public host, and that cleartext is
   genuinely only needed for user-supplied LT server URLs:
   ```bash
   grep -rn "http://" app/src/main/kotlin --include='*.kt' | grep -v "schemas.android"
   grep -rn "ws://" app/src/main/kotlin --include='*.kt'
   ```
   (Expect: no first-party `http://` endpoints; `ws://` only in LT custom-server paths.)
   Record the evidence in the completion report.

2. **Option B edits**: in `network_security_config.xml`, replace the two comment lines with
   a precise comment stating: cleartext is enabled globally ONLY because Listen Together
   permits user-supplied `ws://` custom servers on LANs; all first-party traffic is HTTPS;
   before disabling, the LT custom-server feature must restrict URLs to `wss://` or
   localhost/LAN only. Add the non-`wss://` warning in `ListenTogetherSettings.kt` where the
   custom URL is saved (follow existing `rememberPreference`/dialog patterns in that file),
   with one English string in `sachit_strings.xml` (e.g. `lt_unencrypted_server_warning`).
   Use `stringResource` and keep the copy consistent with the file's existing tone.

3. **Option C**: leave the config functional; the comment added in step 2 IS the decision
   record (XML files are not markdown, so this respects AGENTS.md).

4. **Verify** the feature still works end-to-end for the default WSS server and that the
   warning appears for a `ws://` custom URL (manual QA by a human; automated verification is
   the build + existing tests only).

## Verification

```bash
export JAVA_HOME="C:\\Users\\HP\\.jdks\\jbr-21.0.11"
./gradlew :app:assembleFossDebug --console=plain
./gradlew :app:testFossDebugUnitTest --console=plain
```

Both exit 0. New strings exist only in the default `sachit_strings.xml`.

## Done criteria

- Config comment truthfully explains why cleartext is global and what must change before it
  can be scoped.
- Custom LT server URLs that are not `wss://` surface a visible in-app warning.
- Build + tests pass; no behavior change to default (WSS) flows.

## Escape hatches

- If step 1 reveals any first-party `http://` endpoint in production code paths, STOP and
  report it as a separate HIGH-severity finding — do not fold it into this plan.
- If adding the warning proves structurally awkward in `ListenTogetherSettings.kt` (large
  file, ~400 lines), leave the config comment only (Option C) and report the warning as a
  follow-up.

## Maintenance note

Any future feature that adds a network call must default to HTTPS; the config comment is the
tripwire. If Listen Together ever drops custom-server support, this config should be flipped
to `cleartextTrafficPermitted="false"` — put that in the comment so the future removal is
obvious.
