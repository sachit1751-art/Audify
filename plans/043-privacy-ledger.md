# Plan 043 — Privacy ledger

**Status: not started.** Verified at commit `61778ab4` — no privacy ledger exists. The app has outbound calls 
(AI providers, lyrics services, recognition) but no local log + kill switches that let the user see and control 
what goes out.

## Objective

A local privacy ledger: a log of outbound calls (lyrics fetches, recognition API calls, AI provider calls, 
any network requests the app makes on the user's behalf) with timestamps, what was sent, what was received, 
and which provider. Plus kill switches per provider/category — the user can disable an outbound call type and 
the app stops making it. Exportable — the user can export the ledger as a file. Local-only — no server, no 
analytics on the ledger itself.

## Why

- Innovation #9: local log + kill switches for outbound AI/lyrics/recognition calls, exportable.
- A privacy ledger is a trust feature: the user can see exactly what the app sent out and when, and disable 
anything they don't want. It's a transparency tool, not a marketing feature.
- The app already has several outbound call paths (lyrics, recognition, AI providers) — a ledger makes the 
invisible visible.

## What exists today

- Outbound call paths exist: lyrics (Better Lyrics / LRCLIB / etc.), recognition (Shazam-style, PoToken), AI 
providers (the executor checks at implementation time what AI provider infra exists). Each has its own network 
call path.
- No central log of outbound calls, no kill switches per call type, no export.
- The app may already have some permission / setting for each (e.g. AI settings, lyrics settings) — the ledger 
adds a log + kill switch layer on top, not a replacement for settings.

## Scope

**In scope:**
- A local ledger: a log entry per outbound call — timestamp, call type (lyrics / recognition / AI / other), 
what was sent (summary — e.g. "song ID X to lyrics service", "audio snippet hash to recognition"), what was 
received (summary — e.g. "lyric line", "song match", "AI response size"), provider, success/failure. The 
ledger is local, stored in DB or a local file, append-only (the user can clear it).
- Kill switches per call type / provider: the user can disable "lyrics network calls", "recognition network 
calls", "AI provider calls", etc. When disabled, the app stops making that call type. The kill switch is a 
setting the user controls — not a hidden default.
- A privacy ledger UI: shows the log (recent entries, filterable by type/provider), the kill switches, an 
export action (export the log as a file — CSV or JSON — local, shareable). The UI is a settings section (or a 
nav destination) the user opens to see + control outbound calls.
- Export: the user can export the ledger to a file (CSV or JSON) and share/save it locally. The export is the 
user's data — no server, no analytics on the ledger.

**Out of scope:**
- A server-side privacy dashboard — defer; v1 is local-only.
- Real-time monitoring / notification on every outbound call — defer; v1 is a log the user looks at, not a 
live ticker.
- Blocking individual calls at runtime (beyond the kill switch) — the kill switch is the control; v1 disables 
the call type, not per-call flags.
- Call content capture beyond summary — the ledger logs summaries (what was sent / received), not full payloads 
by default. The executor may add payload capture as an opt-in debug feature, but v1 logs summaries.

## Affected files

| File | Change |
|------|--------|
| `utils/PrivacyLedger.kt` (new) | Core ledger logic: log an outbound call (timestamp, type, provider, summary-sent, summary-received, success), query recent entries, clear, export. Local, append-only. |
| `db/entities/PrivacyLedgerEntry.kt` (new) | Entity for a ledger entry: id, timestamp, callType, provider, summarySent, summaryReceived, success. |
| `db/MusicDatabase.kt` | DAO for ledger entries: insert, query recent (with filters), clear, count by type. |
| `utils/KillSwitch.kt` (new) | Kill-switch state: per call type / provider, enabled/disabled. Check before making an outbound call — if disabled, skip. |
| `constants/PreferenceKeys.kt` | Kill-switch toggles per call type (lyrics, recognition, AI, other). |
| `ui/screens/settings/PrivacyLedgerScreen.kt` (new) | Privacy ledger UI: kill switches, recent log entries (filterable), export action. Settings section or nav destination. |
| `ui/screens/settings/integrations/` (or the settings host) | Add privacy ledger section / entry. |
| `sachit_strings.xml` | Ledger labels, call type labels, kill switch labels, export labels, empty states. |

## Technical approach

1. **Ledger entry** — A `PrivacyLedgerEntry`: id, timestamp (system time, local), callType (enum: lyrics / 
recognition / ai / other), provider (string — the service name), summarySent (short string — e.g. "songId 
abc123 → lyrics service"), summaryReceived (short string — e.g. "lyric line for X", "song match Y", "AI 
response: N chars"), success (boolean). The summaries are short, human-readable — not full payloads. The 
executor picks the summary format at implementation time.
2. **Logging** — At each outbound call site (lyrics fetch, recognition call, AI provider call), after the call 
completes, log an entry. The log is append-only. The executor adds logging at each outbound call site at 
implementation time — the ledger is a cross-cutting concern, added at the call sites.
3. **Kill switches** — `KillSwitch` state: per call type / provider, enabled/disabled. Before making an 
outbound call, check the kill switch for that type/provider. If disabled, skip the call (and optionally log a 
"skipped" entry). The kill switches are settings the user controls — DataStore-backed, with UI toggles.
4. **Privacy ledger UI** — A screen/section showing: kill switches (with toggles), recent log entries (list, 
filterable by type/provider, possibly limited to the last N entries), export action. The log is the user's 
data — export as CSV or JSON, save locally, share. The UI is clear and readable — summaries, not raw payloads.
5. **Export** — Export the ledger to a CSV or JSON file (the user's choice), save to a shared location, open in 
file manager / share. The export is local and shareable — the user owns their data.
6. **Clear** — The user can clear the ledger (delete all entries). Clear is explicit — not automatic. After 
clear, the ledger is empty; new calls log new entries.

## Testing

- Unit test for `PrivacyLedger`: log an entry, query recent, assert the entry is returned; clear, assert empty; 
export, assert the exported file has the right entries.
- Unit test for `KillSwitch`: set a type disabled, assert the check returns disabled; the call site (mocked) 
skips the call when disabled.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: open privacy ledger, verify kill switches are visible/toggleable, verify recent entries appear (trigger 
some outbound calls — lyrics, recognition, AI — and check the log), test export (export to file, open it, verify 
entries), test clear (clear, verify empty). Verify kill switch actually stops the call (disable lyrics calls, 
fetch lyrics, verify skipped + logged as skipped).
- Regression: existing outbound calls (lyrics, recognition, AI) still work when their kill switch is enabled; 
disabling one doesn't affect others.

## Performance notes

- Logging an entry is a small DB insert per outbound call — cheap.
- The ledger is append-only and local — no network, no analytics. Cheap.
- The log could grow over time — the executor may cap it (e.g. last 1000 entries) at implementation time, or 
offer a clear. A cap is fine for v1 if needed.

## Rollback

- Remove the ledger, kill switches, and privacy ledger UI → no log, no kill switches, outbound calls work as 
before (no central log, no kill switch). Zero impact on the actual call paths.

## Anti-BitChord check

- A privacy ledger is a trust/transparency feature — implement it as Audify: local, append-only, summaries, 
kill switches, exportable. Don't copy a competitor's privacy dashboard — this is Audify's take on outbound-call 
visibility.
- The ledger is the user's data — no analytics on it, no server, no sharing unless the user exports it. Local-
first, user-owned.
