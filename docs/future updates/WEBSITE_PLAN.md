# AUDIFY Official Website — Plan

**Design reference:** https://justshivv.github.io/Opentune/

Use OpenTune as inspiration for its polished product storytelling, strong visual hierarchy, interactive demonstrations, device mockups, and smooth motion. Do not copy its branding, copy, layout verbatim, or assets. AUDIFY must retain its own identity.

## Goal
Build a production-quality official website for AUDIFY directly inside this repository, keeping the existing Android app and its build/release workflow intact.

## Design direction
- Premium, music-focused visual identity based on AUDIFY's existing logo, colours, and app UI.
- Bold typography, spacious layouts, strong artwork, subtle gradients, and carefully restrained animation.
- Responsive navigation and layouts for mobile, tablet, and desktop.
- Smooth scrolling, purposeful transitions, hover states, and reduced-motion support.
- Use real app screenshots and artwork; avoid generic templates, fabricated UI, fake testimonials, or invented metrics.

## Site structure
1. **Navigation:** AUDIFY branding, Features, Screenshots, Releases, FAQ, and Download.
2. **Hero:** Clear product positioning, concise value proposition, primary download/GitHub actions, and an animated device/app preview.
3. **Product showcase:** Real AUDIFY screenshots in polished phone/device frames, with an enlarged gallery.
4. **Features:** Explain only verified capabilities, such as playback, library/discovery, playlists, lyrics, statistics, equalizer, and other features confirmed in the current app.
5. **Interactive details:** Small, performant demonstrations of real app interactions and visual design—not fake functional controls.
6. **Releases and downloads:** Latest GitHub release/version, release notes, compatible APK assets, and direct links sourced from GitHub.
7. **Installation guide:** Clear Android download and installation steps, update guidance, and relevant safety/permission notes.
8. **FAQ:** Compatibility, privacy, permissions, updates, troubleshooting, and contribution questions.
9. **Footer:** GitHub repository, issue tracker, contribution information, and project links.

## Implementation plan
1. **Repository audit:** Inspect the existing structure, build system, branding assets, screenshots, README, release workflow, and deployment options before choosing the website stack.
2. **Foundation:** Add an isolated website directory and maintainable frontend setup. Do not disrupt Android modules, Gradle configuration, package identity, or app release automation.
3. **Build:** Implement the design system, responsive navigation, hero, product/features sections, screenshot gallery, releases/downloads, installation guide, FAQ, and footer.
4. **Integrations:** Fetch current release metadata and downloadable assets from the GitHub Releases API; provide reliable repository and issue links. Handle loading, empty, and API-error states.
5. **Polish:** Add accessible interactions, restrained animation, reduced-motion handling, image optimisation, metadata/SEO, and performance improvements.
6. **Verification:** Test production builds, links, release downloads, keyboard/accessibility behaviour, and mobile/tablet/desktop layouts.
7. **Deployment:** Document the chosen hosting setup, build command, deployment configuration, and ongoing maintenance.

## Acceptance criteria
- The website has a distinctive AUDIFY identity and achieves the polish of the reference without copying it.
- All product claims and visuals are grounded in the current app and repository.
- All navigation, GitHub, release, and download links work.
- Release information is not hardcoded to an outdated version.
- Layouts work at common mobile, tablet, and desktop widths.
- Motion is performant and respects reduced-motion preferences.
- Website tooling does not break Android builds, tests, signing, or release workflows.
- No Android version bump, app release, or unrelated app-code change is part of this website task.

## Scope boundary
This document records the plan only. Implementation, deployment, and any app release changes are separate steps.
