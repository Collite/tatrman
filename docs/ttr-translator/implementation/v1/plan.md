# ttr-translator extraction arc — Implementation plan

> **Status:** planned 2026-07-06. Companions: [`../../architecture/architecture.md`](../../architecture/architecture.md) + [`../../architecture/contracts.md`](../../architecture/contracts.md). Task ≈ ½–1 day; stage ≈ 6–8 tasks, ships something testable. Tracking: [`tasks-overview.md`](./tasks-overview.md).
>
> Two phases, two repos, one direction: **Phase A (tatrman)** extracts + publishes; **Phase B (kantheon)** adopts + deletes. **TTR-P Phase 3 unblocks at the end of Phase A** — Phase B is off TTR-P's critical path (TR-8). Kantheon-side task lists live in `Collite/kantheon` `docs/implementation/v1/ttr-translator/` (mirrored from this plan; that repo's tracker is authoritative for B execution status).

## Phase map

```
A1 toolchain + ttr-plan-proto ─► A2 code move (tests first) ─► A3 publish plumbing + v0.1.0
                                                                    │
                                              TTR-P Phase 3 GATE OPENS
                                                                    │
kantheon: B1 proto adoption ─► B2 translator switch (Proteus+Ariadne) ─► B3 docs, guards, tags
```

## Phase A · tatrman — extract + publish

**Deliverable:** `org.tatrman:ttr-plan-proto` + `org.tatrman:ttr-translator` `v0.1.0` on GitHub Packages (+ `ttr-plan-proto` wheel on PyPI); repo build green; kantheon untouched.

- **Stage A1 · Proto module + toolchain** ([tasks-a1-toolchain-proto.md](./tasks-a1-toolchain-proto.md)) — version-catalog additions (calcite 1.41.0, protobuf 4.33.4 + gradle plugin 0.9.5); `:packages:kotlin:ttr-plan-proto` scaffold; the 5 `.proto` files copied byte-identical from kantheon `f2e2efb`; wire round-trip test (TEST-FIRST); `.proto` files bundled in the jar (import-path contract §4.1); Python wheel package scaffold.
  - *Pre-flight:* clean tatrman tree; kantheon checkout readable at the pinned hash. *DONE:* `./gradlew :packages:kotlin:ttr-plan-proto:build` green; jar contains classes + `.proto` resources; wheel builds and imports.
- **Stage A2 · Code move** ([tasks-a2-code-move.md](./tasks-a2-code-move.md)) — `:packages:kotlin:ttr-translator` scaffold; dependency audit of the source lib (hidden ttr-metadata/ttr-parser leaks); **test sources + resources ported first (red)**; main sources ported; package rewrite `org.tatrman.query.shared.translator` → `org.tatrman.translator` + source-dir normalization; test-fixtures wiring; ktlint; provenance README.
  - *DONE:* full moved suite green (`:packages:kotlin:ttr-translator:test`, 30+ specs); zero behavioral diff vs. kantheon `f2e2efb` (package names + build wiring only); `./gradlew build` repo-green.
- **Stage A3 · Publish plumbing + first release** ([tasks-a3-publish.md](./tasks-a3-publish.md)) — `publish.yml` `kotlin-translator/v*` branch (lockstep pair); `justfile` prefix row; `PUBLISHING.md` rows; `publish-python.yml` extension for `python-plan/v*`; Maven-Local validation; cut `kotlin-translator/v0.1.0` + `python-plan/v0.1.0`; flip the TTR-P Phase 3 gate rows.
  - *DONE:* both coordinates resolve from GitHub Packages; wheel on PyPI; `docs/ttr-p/implementation/v1/plan.md` + `tasks-overview.md` gate rows updated to "artifact published"; kantheon notified (B1 pre-flight satisfiable).

## Phase B · kantheon — adopt + switch + delete

**Deliverable:** kantheon consumes the published artifacts; in-repo lib and protos deleted; all gates green. Executed in kantheon against its own tracker (`docs/implementation/v1/ttr-translator/tasks.md`).

- **Stage B1 · Proto adoption** (`tasks-b1-proto-adoption.md`) — catalog entries; delete the 5 transferred `.proto` files from `shared/proto`; `api(libs.tatrman.ttr.plan.proto)`; `just proto` green via the extracted include path; Kotlin-wide compile+test (same FQCNs — no import churn); Python package/steropes onto the wheel; TS gen verified untouched; duplicate-class guard.
  - *Pre-flight (EXTERNAL GATE):* `kotlin-translator/v0.1.0` resolvable from `https://maven.pkg.github.com/Collite/tatrman`.
- **Stage B2 · Translator switch** (`tasks-b2-translator-switch.md`) — Proteus + Ariadne re-point (`project(...query-translator)` → `libs.tatrman.ttr.translator`); repo-wide import rewrite (contracts §4.2); suites green incl. Calcite golden tests; **delete `shared/libs/kotlin/query-translator`** + settings include; `just test-all && just lint-all`; local K3s smoke (`just deploy-kt proteus`).
- **Stage B3 · Docs, guards, tags** (`tasks-b3-docs-tags.md`) — CLAUDE.md §3 tree + §7.3 standing-deps + governance rule; `kantheon-architecture.md` §Proteus wording ("consumes the Collite/tatrman TTR toolchain" — now realized); `ttr-metadata-adoption.md` QueryParseWorker note resolved; ariadne README pointer; tracker ticks; service tags per kantheon release convention.

## Pre-flight conditions (arc-level)

1. tatrman working tree clean (the `just package` recipe refuses a dirty tree — commit the pending CLAUDE.md/CI fixes first).
2. kantheon checkout available at `f2e2efb` for the copy; re-pin if HEAD moved (A2 pre-flight re-verifies).
3. GitHub Packages publish credentials in place (existing `publish.yml` mechanics — nothing new).
4. PyPI trusted publishing: `ttr-plan-proto` registered as a project with this repo's `publish-python.yml` as trusted publisher **before** `python-plan/v0.1.0` is pushed (same setup ttr-parser went through).

## Definition of DONE (arc)

- [ ] Phase A DONE bar met (contracts §6) — **TTR-P Phase 3 may start here**
- [ ] Phase B DONE bar met (contracts §6)
- [ ] Decision log + contracts changelogs updated in both repos (S25 closure recorded; done at planning time for tatrman)
- [ ] `docs/ttr-p/implementation/v1/tasks-overview.md` cross-cutting row checked

## Explicitly out of scope

- Any behavioral/API change to the translator (preserved-shape improvements, new dialects, Polars) — post-arc, normal releases.
- Ariadne's move to ttr-metadata (separate M4 arc) — B2 only re-points its `QueryParseWorker` dependency.
- TS/JS proto artifact (TR-3 guardrail: no consumer, no artifact).
- `proteus.v1` service proto or any kantheon service proto — they stay home.
