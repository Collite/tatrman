# Tasks · Extraction arc · Stage A3 — Publish plumbing + v0.1.0

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Contracts: [`../../architecture/contracts.md`](../../architecture/contracts.md) §5.
> **Coder rules:** top-to-bottom; check `[x]` immediately after verification; blocked ⇒ STOP + §Blockers.

## Stage deliverable

`kotlin-translator/v0.8.0` published (lockstep `ttr-plan-proto` + `ttr-translator` on GitHub Packages), `python-plan/v0.8.0` on PyPI; all plumbing (`publish.yml`, `justfile`, `PUBLISHING.md`, `publish-python.yml`) wired; **TTR-P Phase 3 gate rows flipped**.

## Pre-flight

- [ ] Stage A2 DONE + `/review` findings addressed.
- [ ] Working tree clean (`git status --porcelain` → empty; `just package` refuses otherwise).
- [ ] PyPI trusted publisher registered for `ttr-plan-proto` (Collite/tatrman · publish-python.yml · environment pypi) — do this in the PyPI UI BEFORE pushing the tag; record the date here.

## Tasks

### T-A3.1 · Maven-Local dry run (the consumer gate, TEST-FIRST)

- [ ] Publish locally and prove resolvability exactly the way `tasks-p3-s3.1` pre-flight will check it:
  ```bash
  ./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttr-plan-proto:publishToMavenLocal :packages:kotlin:ttr-translator:publishToMavenLocal
  ls ~/.m2/repository/org/tatrman/ttr-translator/0.0.1-LOCAL/ ~/.m2/repository/org/tatrman/ttr-plan-proto/0.0.1-LOCAL/
  ```
  → both jars + poms present; ttr-translator's pom lists `ttr-plan-proto` and `calcite-core` deps.
  - **Verify:** commands above; pom inspected (`grep -A2 "ttr-plan-proto\|calcite" ~/.m2/repository/org/tatrman/ttr-translator/0.0.1-LOCAL/*.pom`).

### T-A3.2 · `publish.yml` — `kotlin-translator/v*` branch

- [ ] Add to `.github/workflows/publish.yml`: trigger `'kotlin-translator/v*'`; ladder branch
  ```bash
  elif [[ "$TAG" == kotlin-translator/v* ]]; then
    MODULES=":packages:kotlin:ttr-plan-proto:publish :packages:kotlin:ttr-translator:publish"
  ```
  (lockstep pair, kotlin-metadata pattern); update the header comment's tag→modules table.
  - **Verify:** `yq '.on.push.tags' .github/workflows/publish.yml` lists the new prefix; ladder handles it before the `else`.

### T-A3.3 · `justfile` — package recipe prefix row

- [ ] Extend the `package` recipe's prefix→modules mapping with `kotlin-translator` (mirrors the workflow ladder exactly — the recipe's guard rejects unknown prefixes, so a missing row = dead tag protection working as designed). Update the usage examples comment (`just package kotlin-translator set 0.8.0`).
  - **Verify:** `just package kotlin-translator set 0.8.0 --dry-run` if the recipe supports it, else `just --evaluate` + code inspection; the guard no longer rejects the prefix.

### T-A3.4 · `PUBLISHING.md`

- [ ] "What is published": rows for both modules (coordinate, phase = "extraction arc", why — translator: Proteus core consumed by ttrp-emit + kantheon Proteus/Ariadne; plan-proto: canonical wire formats, `.proto`-in-jar contract). Tag table: `kotlin-translator/v<x.y.z>` row (lockstep pair, first tag v0.1.0) + `python-plan/v<x.y.z>` in the Python section. Semver note: proto wire changes follow contracts §2 (append-only in v1). Consumer-setup section: add the two coordinates to the example.
  - **Verify:** rendered tables consistent with `publish.yml` ladder (same modules, same prefixes).

### T-A3.5 · `publish-python.yml` — `python-plan/v*`

- [ ] Extend the workflow (or clone a sibling job) to trigger on `python-plan/v*`, building `packages/python/ttr-plan-proto` (no JVM step needed — protoc via grpcio-tools; adjust the ttr-parser-specific steps). Version injection into its pyproject, same sed pattern.
  - **Verify:** workflow YAML valid (`yq` parse); paths exist.

### T-A3.6 · Cut the release

- [ ] `just package kotlin-translator set 0.8.0` → pushes `kotlin-translator/v0.8.0`; watch the run; then `git tag python-plan/v0.8.0 && git push origin python-plan/v0.8.0`; watch PyPI publish.
- [ ] Post-publish resolution check from a scratch dir (consumer credentials path, PUBLISHING.md §consumer setup):
  ```bash
  # scratch build.gradle.kts with the GitHub Packages repo + both coordinates at 0.1.0
  gradle dependencies --configuration compileClasspath | grep "org.tatrman:ttr-\(translator\|plan-proto\):0.8.0"
  ```
  → both resolve, no FAILED.
  - **Verify:** GitHub Packages UI shows both artifacts at 0.8.0; `pip download ttr-plan-proto==0.8.0` succeeds.

### T-A3.7 · Flip the TTR-P gate rows + notify

- [ ] `docs/ttr-p/implementation/v1/plan.md` — Phase 3 pre-flight line + §Cross-cutting row: mark the artifact as **published (kotlin-translator/v0.8.0, 2026-07-XX)**; kantheon-side switch (Phase B) noted as pending-but-non-blocking.
- [ ] `docs/ttr-p/implementation/v1/tasks-overview.md` — check the cross-cutting Proteus-extraction checkbox with the same note.
- [ ] `docs/ttr-translator/implementation/v1/tasks-overview.md` — A-stages ticked; ping kantheon-side execution (B1 pre-flight is now satisfiable).
  - **Verify:** `grep -n "kotlin-translator/v0.8.0" docs/ttr-p/implementation/v1/plan.md docs/ttr-p/implementation/v1/tasks-overview.md` → both hit.

## Definition of DONE (stage)

- [ ] Both Maven coordinates resolve from GitHub Packages at 0.1.0; wheel on PyPI.
- [ ] Plumbing consistent across `publish.yml` / `justfile` / `PUBLISHING.md` (same prefixes, same module lists).
- [ ] TTR-P Phase 3 gate rows flipped. **TTR-P Phase 3 (Stage 3.1) may start.**
- [ ] `/review` requested for the plumbing diff.

## Blockers

_(empty)_
