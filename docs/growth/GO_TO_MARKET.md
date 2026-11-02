<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Audify user acquisition plan

> Status: initial 30-day experiment plan, created 2026-11-01. This is a marketing plan, not a promise of shipped product capabilities or guaranteed growth.

## 1. Objective

Acquire the first cohort of genuinely active Audify users, learn which user problem and feature story converts, and improve retention before increasing promotion.

**Primary 30-day objective:** recruit 10 relevant Android music enthusiasts to install a stable release, complete a first listening session, and provide at least one useful piece of feedback. This is a target, not a forecast.

Do not optimize for raw APK downloads, stars, or impressions in isolation. The outcome is repeat listening and user-reported value.

## 2. Product positioning

### Primary audience

Android users who want a customizable music experience combining online discovery, personal playlists/library, local audio, playback controls, and synchronized lyrics. Secondary audiences are Android power users interested in open-source apps, and users who want dedicated TV or Wear OS experiences.

### Positioning statement

**Audify is an open-source Android music player and discovery client that brings music search, playlists, local audio, synchronized lyrics, and detailed playback controls into one listening experience.**

Support the positioning with verifiable demonstrations, not adjectives. The strongest first content angles are:

1. One app for music discovery and local listening.
2. A player experience with synchronized lyrics and lyric seeking.
3. Playback control and customization, including crossfade and beta Automix (clearly label beta behavior).
4. Dedicated Android TV and Wear OS builds.

Do not claim Audify is a Spotify replacement, has an unlimited catalogue, is ad-free unless verified across every active provider flow, or is affiliated with Google/YouTube. Explain that it is an independent third-party client and upstream service availability can change.

## 3. Current distribution reality

- Primary distribution is GitHub Releases; there is no Play Store listing to rely on.
- `v1.8.6` is the current stable release, publicly published with nine signed APKs across phone, Android TV, and Wear OS. Use its assets as the distribution baseline: https://github.com/sachit1751-art/Audify/releases/tag/v1.8.6.
- The repository is public but early in its audience-building lifecycle. The observed public baseline on 2026-11-01 was 2 stars, 1 fork, and no open issues. GitHub asset download counters are not unique-user or active-user counts.
- Recommend `arm64-v8a` for most current Android phones and explain the universal fallback. Refer to `docs/DISTRIBUTION.md` and the selected release assets for current package sizes rather than keeping volatile APK-size figures in this plan.
- GitHub Discussions are currently disabled. Use a small, structured tester-feedback path until a maintained community space is deliberately enabled.

For future releases, verify that the selected stable release is installable, signed as expected, has accurate release notes, and has no unresolved release-blocking defects. Never silently direct newcomers to a prerelease.

## 4. Acquisition channels

| Channel | Role | First experiment | Success signal |
|---|---|---|---|
| GitHub README and Releases | Convert interest into safe installation | Clear stable-download CTA and architecture guidance | More stable-release asset downloads and fewer install questions |
| Relevant Reddit communities | Reach Android and open-source app enthusiasts | One transparent developer introduction where self-promotion is allowed | Qualified feedback and tester opt-ins, not upvotes alone |
| Short demo videos | Show product value quickly | 3 short screen recordings, each focused on one job | Meaningful repository visits and tester interest |
| GitHub Issues | Capture reproducible feedback | Structured tester feedback template | Actionable reports with build/device context |
| Developer/open-source communities | Build trust and contributors | Share the project and ask for focused review | Useful issue reports, contributions, and repeat visitors |

Do not mass-post the same message, send unsolicited DMs, buy installs, seed fake reviews, or conceal that the poster is the developer. Follow each community's self-promotion rules.

## 5. First 30 days

### Days 1–3: make the install path trustworthy

- Keep the latest stable release as the default download target.
- Explain stable vs prerelease in the README and on each release.
- Lead with the arm64 APK and explain when to use universal, TV, and Wear OS packages.
- Prepare a short first-run guide: install, grant only necessary permissions, sign in only if a feature requires it, search, play, and report a problem.
- Add a structured feedback template that does not request sensitive data.

### Days 4–10: recruit the first testers

- Publish one honest developer introduction in a community that explicitly permits project sharing.
- Ask for 10 testers who already use Android music apps and are willing to try the stable build.
- Give each tester one bounded task: install, search for a track, play it, try one differentiating feature, and report friction.
- Ask permission before quoting feedback or using screenshots/testimonials.

### Days 11–20: demonstrate, do not hype

- Publish three 20–35 second screen recordings: discovery-to-playback, synchronized lyrics/lyric seeking, and local music plus library organization.
- Each video should show the real app, use captions, and end with the stable GitHub Releases link.
- Track which feature story produces qualified visits and tester responses.

### Days 21–30: improve the funnel

- Fix the top three onboarding or install blockers reported by testers.
- Update the README based on actual questions, not assumptions.
- Repeat the strongest-performing content angle once with a different demonstration.
- Decide whether to enable GitHub Discussions or maintain issue-based feedback based on actual support capacity.

## 6. Measurement

Use a weekly manual scorecard. Do not add telemetry or upload listening behavior as a shortcut to marketing analytics.

| Metric | Source | Interpretation |
|---|---|---|
| Repository stars/forks | GitHub repository | Interest signal only; not active users |
| Stable APK downloads | GitHub release asset counters | Directional distribution signal; may include repeat downloads |
| Tester opt-ins | Voluntary feedback/issues | Qualified interest |
| First-session completion | Voluntary tester confirmation | Activation proxy |
| 7-day return | Voluntary tester follow-up | Retention proxy |
| Reproducible issues | GitHub Issues | Product friction and quality signal |

Record weekly totals with the date and release tag. Do not combine prerelease downloads with stable-release downloads. Do not describe GitHub counters as installs or active users.

## 7. Ready-to-use message

> I've been building Audify, an open-source music player and discovery client for Android. It brings music discovery, playlists, local audio, synchronized lyrics, and detailed playback controls together, with separate Android TV and Wear OS builds. It is an independent third-party client, so availability of connected services can vary. I'm looking for a small number of Android users to try the latest stable build and tell me what is confusing, broken, or genuinely useful. The source, screenshots, release notes, and APKs are on GitHub: https://github.com/sachit1751-art/Audify

Use this only in communities where project promotion is allowed. Keep the developer disclosure; tailor the post to the community rather than cross-posting verbatim.

## 8. Release gate before a wider push

- Stable release is clearly marked and points to tested, correctly signed APKs.
- Release notes distinguish implemented behavior from limitations and beta features.
- README installation path is correct for phone, TV, and Wear OS.
- No open release-blocking crashes, data-loss issues, or misleading capability claims.
- A feedback route exists and can be maintained by the project owner.

## 9. Immediate next actions

1. Confirm the README download path and stable release are still current.
2. Record the baseline GitHub counters on the day the first outreach post goes live.
3. Choose one community whose rules allow a developer introduction; post once and respond to feedback.
4. Record the first five tester questions and fix the highest-friction issue before posting again.

