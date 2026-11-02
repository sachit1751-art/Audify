<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 13 — Casting protocol expansion

**Status: research proposal — no implementation until architecture and maintenance gates pass.**

## Objective

Determine whether FCast and/or DLNA support can be added as a reliable, supportable output option alongside Audify's current output-selection architecture.

## Why this is a research-first plan

The competitor audit lists multiple casting protocols, but Audify's current support must be verified. Protocol breadth is not valuable if discovery, authentication, playback control, queue state, or device compatibility is unreliable.

## Scope

**In scope:** current Cast/output architecture inventory; protocol and library evaluation; device discovery/security review; compatibility matrix; proof-of-concept recommendation; explicit go/no-go decision.

**Out of scope:** implementing both protocols by default, undocumented device control, LAN-wide scanning beyond user intent, insecure unauthenticated control surfaces, or claiming support based on a successful demo with one receiver.

## Research steps

1. Verify existing casting support and identify the canonical output/session abstractions.
2. Evaluate FCast and DLNA protocol maturity, Android library maintenance, license, security model, receiver ecosystem, metadata/artwork support, seek/pause/queue semantics, and testing burden.
3. Define a narrow compatibility matrix and threat model for local-network discovery and control.
4. Build a disposable proof of concept only after dependency/license review; do not merge it into production during research.
5. Test representative receivers and network conditions, including unavailable devices, reconnects, and malformed responses.
6. Produce a written go/no-go recommendation with maintenance cost and supported-device claims. If neither protocol meets the bar, close the proposal without implementation.

## Go/no-go criteria

- A maintained, appropriately licensed Android implementation exists or a bounded in-house implementation is justified.
- Discovery and control can be limited to the user's explicit local-network action and platform permissions.
- Playback controls and state can map honestly to Audify's existing output abstraction.
- A practical receiver test matrix and regression strategy are available.
- Ongoing maintenance cost is proportionate to demonstrated user demand.

## Rollback

Do not ship if the research gate fails. If a later implementation is approved, protocol adapters must be removable without affecting existing output routes.

## Mandatory pre-implementation audit

Inspect current output selection, Cast integration, network permissions, Android platform requirements, dependency licenses, and any older casting plans. No production code changes before the go/no-go report is reviewed.

## Project constraints and handoff

Follow `plans/README.md`, `PRODUCT.md`, and `DESIGN.md`. No broad network permission, schema/migration, version bump, translated-string edits, or unauthorized commits/pushes. Clearly distinguish tested receivers from theoretical protocol support.
