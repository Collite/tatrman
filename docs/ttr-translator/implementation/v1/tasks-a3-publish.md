# Tasks · Extraction arc · Stage A3 — Publish plumbing + v0.8.0

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Contracts: [`../../architecture/contracts.md`](../../architecture/contracts.md) §5.
> **Coder rules:** top-to-bottom; check `[x]` immediately after verification; blocked ⇒ STOP + §Blockers.

## Stage deliverable

`kotlin-translator/v0.8.0` published (lockstep `ttr-plan-proto` + `ttr-translator` on GitHub Packages), `python-plan/v0.8.0` on PyPI; all plumbing (`publish.yml`, `justfile`, `PUBLISHING.md`, `publish-python.yml`) wired; **TTR-P Phase 3 gate rows flipped**.

## Pre-flight

- [x] Stage A2 DONE + `/review` findings addressed (review-064: F1/F2/F3 all fixed).
- [ ] Working tree clean (`git status --porcelain` → empty; `just package` refuses otherwise). — checked at release time; the A3 plumbing commit must land first.
- [x] PyPI trusted publisher registered for `ttr-plan-proto` (Collite/tatrman · publish-python.yml · environment pypi) — **done 2026-07-06** (pending publisher; the first `python-plan/v0.8.0` push 403'd until it was registered, then a tag re-push published green).

## Tasks

### T-A3.1 · Maven-Local dry run (the consumer gate, TEST-FIRST)

- [x] Publish locally and prove resolvability exactly the way `tasks-p3-s3.1` pre-flight will check it: **(done — both jars+poms in `~/.m2`; ttr-translator POM lists `ttr-plan-proto:0.0.1-LOCAL` + `calcite-core:1.41.0`; test-fixtures jar present.)**
  ```bash
  ./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttr-plan-proto:publishToMavenLocal :packages:kotlin:ttr-translator:publishToMavenLocal
  ls ~/.m2/repository/org/tatrman/ttr-translator/0.0.1-LOCAL/ ~/.m2/repository/org/tatrman/ttr-plan-proto/0.0.1-LOCAL/
  ```
  → both jars + poms present; ttr-translator's pom lists `ttr-plan-proto` and `calcite-core` deps.
  - **Verify:** commands above; pom inspected (`grep -A2 "ttr-plan-proto\|calcite" ~/.m2/repository/org/tatrman/ttr-translator/0.0.1-LOCAL/*.pom`).

### T-A3.2 · `publish.yml` — `kotlin-translator/v*` branch

- [x] Add to `.github/workflows/publish.yml`: trigger `'kotlin-translator/v*'`; ladder branch
  ```bash
  elif [[ "$TAG" == kotlin-translator/v* ]]; then
    MODULES=":packages:kotlin:ttr-plan-proto:publish :packages:kotlin:ttr-translator:publish"
  ```
  (lockstep pair, kotlin-metadata pattern); update the header comment's tag→modules table.
  - **Verify:** `yq '.on.push.tags' .github/workflows/publish.yml` lists the new prefix; ladder handles it before the `else`.

### T-A3.3 · `justfile` — package recipe prefix row

- [x] Extend the `package` recipe's prefix→modules mapping with `kotlin-translator` (mirrors the workflow ladder exactly — the recipe's guard rejects unknown prefixes, so a missing row = dead tag protection working as designed). Update the usage examples comment (`just package kotlin-translator set 0.8.0`).
  - **Verify:** `just package kotlin-translator set 0.8.0 --dry-run` if the recipe supports it, else `just --evaluate` + code inspection; the guard no longer rejects the prefix.

### T-A3.4 · `PUBLISHING.md`

- [x] "What is published": rows for both modules (coordinate, phase = "extraction arc", why — translator: Proteus core consumed by ttrp-emit + kantheon Proteus/Ariadne; plan-proto: canonical wire formats, `.proto`-in-jar contract). Tag table: `kotlin-translator/v<x.y.z>` row (lockstep pair, first tag v0.8.0) + `python-plan/v<x.y.z>` in the Python section. Semver note: proto wire changes follow contracts §2 (append-only in v1). Consumer-setup section: add the two coordinates to the example.
  - **Verify:** rendered tables consistent with `publish.yml` ladder (same modules, same prefixes).

### T-A3.5 · `publish-python.yml` — `python-plan/v*`

- [x] Extend the workflow (or clone a sibling job) to trigger on `python-plan/v*`, building `packages/python/ttr-plan-proto` (no JVM step needed — protoc via grpcio-tools; adjust the ttr-parser-specific steps). Version injection into its pyproject, same sed pattern. **(done — added `build-plan`/`publish-plan` jobs gated on `python-plan/v`; existing ttr-parser jobs gated on `python/v`; both YAMLs parse.)**
  - **Verify:** workflow YAML valid (`yq` parse); paths exist.

### T-A3.6 · Cut the release

- [x] `just package kotlin-translator set 0.8.0` → pushed `kotlin-translator/v0.8.0`; **"Publish Kotlin artifacts" run 28820113344 green** (ttr-plan-proto + ttr-translator on GitHub Packages). Then `python-plan/v0.8.0` — first run 403'd (PyPI publisher unregistered); after registration a tag re-push published green (**"Publish Python (PyPI)" run 28821187530**).
- [x] Post-publish resolution check:
  - **PyPI:** verified live via the JSON API — `ttr-plan-proto` `0.8.0`, `ttr_plan_proto-0.8.0-py3-none-any.whl`.
  - **GitHub Packages (Kotlin pair):** publish job green + Maven-Local resolvability proven in T-A3.1 (POM lists `ttr-plan-proto` + `calcite-core:1.41.0`). A credentialed scratch-consumer resolve (`read:packages` PAT) is the kantheon **B1 pre-flight** — not repeated here.
  - **Verify:** ✅ PyPI wheel live; ✅ both publish runs green.

### T-A3.7 · Flip the TTR-P gate rows + notify

- [x] `docs/ttr-p/implementation/v1/plan.md` — Phase 3 pre-flight line + §Cross-cutting row + phase-map diagram: marked **published (kotlin-translator/v0.8.0, 2026-07-06)**; kantheon Phase B noted pending-but-non-blocking.
- [x] `docs/ttr-p/implementation/v1/tasks-overview.md` — Phase 3 EXTERNAL GATE checkbox + cross-cutting Proteus-extraction checkbox + gate diagram flipped to published.
- [x] `docs/ttr-translator/implementation/v1/tasks-overview.md` — A3 ticked (gate open). B1 pre-flight (`kotlin-translator/v0.8.0` resolvable) is now satisfiable — kantheon notified out-of-band.
  - **Verify:** `grep -n "kotlin-translator/v0.8.0" docs/ttr-p/implementation/v1/plan.md docs/ttr-p/implementation/v1/tasks-overview.md` → both hit.

## Definition of DONE (stage)

- [x] Both Maven coordinates published at 0.8.0 (GitHub Packages, run 28820113344); wheel live on PyPI (run 28821187530). Full external-consumer resolve = kantheon B1 pre-flight.
- [x] Plumbing consistent across `publish.yml` / `justfile` / `PUBLISHING.md` (same prefixes, same module lists) + `publish-python.yml` (`python-plan/v*`). Both workflow YAMLs parse; ladder routing verified.
- [x] TTR-P Phase 3 gate rows flipped. **TTR-P Phase 3 (Stage 3.1) may start.**
- [ ] `/review` of the plumbing diff — optional (docs/CI-only change, validated inline; committed in `7448a5f`).

## Blockers

_(empty)_
