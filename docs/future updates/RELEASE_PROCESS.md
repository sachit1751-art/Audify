<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Production release process

**Last reviewed:** 2026-11-04
**Workflow:** [Audify Release](../../.github/workflows/android.yml)

This document is the operational source of truth for production APK validation. GitHub Actions and the published GitHub Release are authoritative for run and publication status; this file records the latest known snapshot and must be refreshed when those facts change.

## Current release snapshot

- Repository branch: `main`
- Latest release-preparation commit: `a135811c` (`Increase release build memory for R8`)
- Candidate: Audify `1.8.9`, versionCode `37`, tag `v1.8.9`
- Latest published stable release checked on 2026-11-04: [`v1.8.7`](https://github.com/sachit1751-art/Audify/releases/tag/v1.8.7)
- Candidate dry-run: [workflow run 41835700383](https://github.com/sachit1751-art/Audify/actions/runs/41835700383)
- Last observed state: `in_progress`; unit-test gates were running. Production lint, APK assembly, staging, signature verification, native-payload verification, and artifact upload had not yet completed.
- No `v1.8.9` GitHub Release has been published. Do not call the candidate released until the workflow succeeds and publication is separately confirmed.

## APK contract

The release workflow must stage exactly four production APKs, in this order:

1. `Audify-1.8.9.apk` — phone universal.
2. `Audify-1.8.9_arm64-v8a.apk` — phone ARM64.
3. `AudifyTV-1.8.9.apk` — Android TV universal.
4. `AudifyWear-1.8.9.apk` — Wear OS universal.

The phone and TV universal APKs must contain the required native libraries for `armeabi-v7a`, `arm64-v8a`, and `x86_64`. TV and Wear OS are separate packages and must not be presented as phone APKs.

## CI gates

The workflow checks the release tag against the version in `build.gradle.kts`, configures JDK 21 and private signing credentials, then runs:

- Unit tests: `:app:testDevDebugUnitTest`, `:app:testProdDebugUnitTest`, `:tv:testProdDebugUnitTest`, `:shared:jvmTest`, and `:sharedUi:jvmTest`.
- Production lint for phone, TV, and Wear OS.
- Production APK assembly for all three applications.
- Exact artifact count/names, package IDs, version name/code, APK signature validity, and signing-certificate equality with the published `v1.8.7` reference APK.
- Required native-library presence in both universal phone and TV APKs.
- Artifact upload only after all checks pass.

The non-dry-run publish job is separate and approval-gated through the GitHub `release` environment. A successful dry run does not publish a release.

## R8 memory failure and mitigation

The previous candidate run failed during Android TV R8 shrinking with `java.lang.OutOfMemoryError: Java heap space`. This was a build-runner memory failure, not evidence of a Kotlin source compilation error. The corrective commit `a135811c` makes two targeted changes:

- Gradle daemon heap: `-Xmx6g` (previously `-Xmx4096m`).
- Production APK assembly: `--max-workers=1` to serialize memory-heavy shrink/package work.

Minification and resource shrinking remain enabled. The mitigation is not considered proven until the new workflow completes the phone, TV, and Wear OS production APK builds successfully. If R8 fails again, inspect the exact failing module/task and heap/worker logs before increasing memory further or changing shrinker rules. Do not disable R8 or resource shrinking as a workaround without an explicit product decision.

## Signing and publication safeguards

- CI requires `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` secrets.
- The generated APK signatures must match the signing certificate extracted from the published `v1.8.7` phone APK.
- Never print, commit, or document secret values or keystore contents.
- Do not push a release tag or publish a GitHub Release without explicit authorization.
- Keep unrelated working-tree changes out of release commits. In particular, guest-created on-device playlist work is deferred to a later version and is not part of `1.8.9`.

## Updating this document

After every release attempt, refresh the commit, run URL, exact failing/successful step, artifact status, and published-release status. Distinguish `in_progress`, `failed`, `dry-run succeeded`, `draft`, and `published`; do not infer one state from another. Update `PRODUCT.md`, `plans/README.md`, `docs/future updates/DISTRIBUTION.md`, and the version-specific release notes when the stable release changes.
