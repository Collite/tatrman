# PD4 — `modeler resolve-packages` CLI + resolved-packages artifact

**Goal:** ship a CLI that emits the deterministic `resolved-packages.json` artifact (contracts §13.4) describing the project's packages, entities, and resolved domains, so non-TS consumers (`ai-models` CI) validate references without reimplementing model structure.

**Reads:** [contracts §13.4](../../v1-1/design/v1-1-contracts.md#134-resolved-packages-artifact), [design §14.6](../../v1-1/design/v1.1-packages-and-graphs.md#146-resolved-packages-artifact-b22), [architecture §4](architecture.md). The shipped [`F-migration-cli.md`](../../v1-1/plan/tasks/F-migration-cli.md) is the CLI-packaging pattern to copy (`packages/migrate`, arg parsing, exit codes).
**Blocked by:** PD1 (canonical names), PD3 (`DomainTable`).
**Blocks:** PD5.
**Estimated time:** 2–3 days.

## Tests-first

- [x] `packages/migrate/src/__tests__/resolve-packages.test.ts` (10 cases): §13.4 shape; packages sorted by `canonicalName` with `nested`/`directory`; entities sorted by `qname`; domains carry the recursive closure `['a','a.b','a.b.c']`; **determinism** (serialise twice + reversed file order → identical); `root="cz.dfpartner"` → prefixed `canonicalName`, bare `declaredName`; undeclared-under-root derive prefixed names; empty project / no-domains → empty arrays present.
- [x] `packages/migrate/src/__tests__/resolve-packages-cli.test.ts` (6 cases): `--out` writes there; default `<root>/.modeler/resolved-packages.json`; exit `2` on IO error; `--check` exits `0` in sync, non-zero on drift, non-zero when no artifact exists.

## Library reference

No new external library — Node `fs`/`path` + the existing semantics build. For deterministic JSON, sort keys/arrays explicitly and `JSON.stringify(obj, null, 2) + "\n"`; do **not** rely on insertion order. Reuse `ProjectSymbolTable` + `DomainTableBuilder` from PD1/PD3. Find the project-walk helper the migrate CLI already uses (`grep -rn "rglob\|readdir\|walk" packages/migrate/src`).

## Implementation tasks

- [x] **PD4.1 — Subcommand wiring.** `packages/migrate` CLI restructured into subcommands; `resolve-packages <project-root> [--out] [--check] [--verbose]` added alongside `migrate-to-packages`. Exit codes: `0` ok, `2` IO/parse, `3` `--check` drift/missing. (The migration E2E test was updated to call the `migrate-to-packages` subcommand.)
- [x] **PD4.2 — Build the model.** `resolve-packages.ts` loads `modeler.toml` (`PackagesConfig`), walks `.ttr`/`.ttrg`/`.ttrd`, builds `ProjectSymbolTable` + `DomainTable` via the shared semantics path (`effectivePackage`, `DomainTableBuilder`).
- [x] **PD4.3 — Serialise the artifact.** `ResolvedPackagesArtifact` per §13.4; arrays sorted; 2-space JSON + trailing newline. `canonicalName`/`qname` re-prefixed to the canonical root form; `generatedFrom` = root basename for cross-machine determinism.
- [x] **PD4.4 — Write to disk.** Default `<root>/.modeler/resolved-packages.json` (creates `.modeler/`; verified gitignored). `--out` honoured.
- [x] **PD4.5 — `--check` drift mode.** Generates in-memory, byte-compares to the on-disk artifact, exits `3` on drift or missing snapshot with a stderr hint.
- [ ] **PD4.6 — Committed-snapshot flow (cross-repo → lands in PD5).** Modeler side is ready: `modeler resolve-packages <model-root> --out model-ttr/resolved-packages.json` produces the committed snapshot; `… --check --out …` is the drift gate. The `model-ttr`/`ai-models` CI step + README live in the `ai-models` repo and are wired in **PD5** (the dedicated ai-models phase). Verified the producer command against `~/Dev/ai-models/model-ttr` (17 packages, 66 entities, byte-deterministic).

## Verify by running

```bash
pnpm --filter @modeler/migrate test
pnpm -r build && pnpm -r typecheck && pnpm -r lint
# smoke against the real model:
node packages/migrate/dist/cli.js resolve-packages ~/Dev/ai-models/model-ttr --out /tmp/rp.json && head -40 /tmp/rp.json
```

All exit 0. The real-model smoke produces a well-formed artifact listing the `ai-models` packages.

## DONE when

- [x] Every checkbox ticked (PD4.6 modeler-side ready; ai-models CI wiring lands in PD5).
- [x] Artifact matches contracts §13.4 and is byte-deterministic (re-run = no-op diff; verified on the real model).
- [x] `--check` exits non-zero on drift, zero when in sync.
- [x] Smoke run against `~/Dev/ai-models/model-ttr` succeeds and lists its packages (17 pkgs, 66 entities).
- [ ] Committed-snapshot flow: producer/`--check` commands ready and verified; the `model-ttr` CI step + committed `model-ttr/resolved-packages.json` are wired in PD5 (ai-models repo).
