# Tatrman Server — Task Management (SV-P0)

> Generated 2026-07-10 at the SV-P0 `/planning` pass (per the plan's rule: lists per phase, at phase start). Inputs: [`../plan.md`](../plan.md) §SV-P0 · [`../../design/contracts.md`](../../design/contracts.md) · [`../../design/architecture.md`](../../design/architecture.md) · naming ledger [`../../../platform/design/naming-260710.md`](../../../platform/design/naming-260710.md) · extraction inventory (`kantheon/docs/architecture/fork/extraction-inventory-260710.md`) · control room §7 RO-15..21.

## Rules for the executing agent (read before every stage)

1. **Check each checkbox immediately after its task is done.** Never batch checkbox updates.
2. Run the stage's **verify command block** at the end of every stage; a stage is DONE only when it passes.
3. **Rename-before-publish is the invariant**: nothing is published to any registry in SV-P0. If a task seems to require publishing (other than `publishToMavenLocal`), stop and flag.
4. TDD where tests exist: write/extend the named test FIRST, watch it fail, then change code.
5. History discipline: all moves are `git mv` (same-repo) or `git filter-repo` grafts (cross-repo) — never copy-paste-delete.
6. Anything unexpected (missing file, extra importer, failing suite unrelated to your change): stop, record in the stage's "findings" section, flag ⚑ — do not improvise.

## Stage sequence & status

| Stage | List | Repo(s) | Depends on | Status |
|---|---|---|---|---|
| S1 · Server repo bootstrap | [`tasks-sv-p0-s1-bootstrap.md`](./tasks-sv-p0-s1-bootstrap.md) | tatrman-server (new) | — | ✅ done (T1–T7; pushed to `master`, CI running). Admin follow-up: set default branch `master` |
| S2 · tatrman proto amendments | [`tasks-sv-p0-s2-protos.md`](./tasks-sv-p0-s2-protos.md) | tatrman | — (parallel to S1) | ✅ done (T1–T7 green; branch `sv-p0-server-fork`, published `0.0.1-LOCAL`) |
| S3 · The move (kantheon → tatrman-server) | [`tasks-sv-p0-s3-move.md`](./tasks-sv-p0-s3-move.md) | kantheon → tatrman-server | S1 | ☐ |
| S4 · Rename & proto sweep (server-side) | [`tasks-sv-p0-s4-sweep.md`](./tasks-sv-p0-s4-sweep.md) | tatrman-server | S2, S3 | ☐ |
| S5 · Kantheon closure | [`tasks-sv-p0-s5-kantheon-close.md`](./tasks-sv-p0-s5-kantheon-close.md) | kantheon | S4 (`publishToMavenLocal`) | ☐ |
| S6 · Deployment rename + gates | [`tasks-sv-p0-s6-deploy.md`](./tasks-sv-p0-s6-deploy.md) | olymp (+ pilot) | S4, S5 | ☐ |

S3+S4 form **one change window** (the ledger's rename-on-arrival rule): they may land as one PR per repo, but work through the lists in order. S2 can run any time before S4.

## Phase DONE (from plan §SV-P0, restated)

- [ ] `tatrman-server` builds green (`./gradlew build`) with **zero persona strings on any wire surface** (grep gate, S6)
- [ ] `kantheon` builds green without the moved modules (S5)
- [ ] Pilot deployment repointed at renamed charts **or** pinned pre-move with the pin recorded (S6)
- [ ] `_to_delete/` folders removed from tatrman + kantheon (S5)
- [ ] Findings sections of all six lists reviewed by Bora; ⚑ items dispositioned

## Standing facts the tasks rely on (verified 2026-07-10)

- kantheon consumes `org.tatrman:ttr-{parser,writer,semantics}:0.8.4` from GitHub Packages `Collite/tatrman`, and `ttr-metadata(-git):0.0.1-LOCAL` from `mavenLocal()` — the same two mechanisms serve the interim (server artifacts = `0.0.1-LOCAL` via mavenLocal until SV-P1 gate 3).
- `shared/libs/kotlin/query-translator` (73 files) is still kantheon-vendored; it **rides into tatrman-server as an explicitly-temporary module** (contracts §7) and extracts to tatrman at SV-P1 gate 2.
- whois has **no proto** (Ktor service) — S3 renames it; `identity.v1` is only reserved.
- kantheon CI = GitHub Actions (`ci.yml`, `integration-nightly.yml`, `release-image.yml`, `publish-python-images.yml`) — tatrman-server clones this shape.
- Interim GitHub home = the **Collite org** (`Collite/tatrman-server`); migrates to the `tatrman` org after account recovery (checklist item, non-blocking).
