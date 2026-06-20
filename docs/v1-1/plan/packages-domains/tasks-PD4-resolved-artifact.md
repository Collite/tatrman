# PD4 — `modeler resolve-packages` CLI + resolved-packages artifact

**Goal:** ship a CLI that emits the deterministic `resolved-packages.json` artifact (contracts §13.4) describing the project's packages, entities, and resolved domains, so non-TS consumers (`ai-models` CI) validate references without reimplementing model structure.

**Reads:** [contracts §13.4](../../design/v1-1-contracts.md#134-resolved-packages-artifact), [design §14.6](../../design/v1.1-packages-and-graphs.md#146-resolved-packages-artifact-b22), [architecture §4](architecture.md). The shipped [`F-migration-cli.md`](../tasks/F-migration-cli.md) is the CLI-packaging pattern to copy (`packages/migrate`, arg parsing, exit codes).
**Blocked by:** PD1 (canonical names), PD3 (`DomainTable`).
**Blocks:** PD5.
**Estimated time:** 2–3 days.

## Tests-first

- [ ] `packages/migrate/src/__tests__/resolve-packages.test.ts`:
  - On a fixture project (packages `a`, `a.b`, `a.b.c`, an entity in each, one `.ttrd` `domain D { packages: [a] }`): assert the produced object matches contracts §13.4 — `formatVersion: 1`, `root` echoed, `packages[]` sorted by `canonicalName` with correct `nested` flags and `directory`, `entities[]` sorted by `qname`, `domains[]` with `resolvedPackages` = the recursive closure `['a','a.b','a.b.c']`.
  - **Determinism:** serialise twice → byte-identical strings. Serialise a structurally-identical project built in a different file order → identical output (sorting defeats input order).
  - `root="cz.dfpartner"`: `canonicalName`s are prefixed; `declaredName`s are the bare written form.
  - empty project / project with no domains → valid artifact with empty arrays (not missing keys).
- [ ] `packages/migrate/src/__tests__/resolve-packages-cli.test.ts`:
  - `--out <path>` writes to that path; default writes `<root>/.modeler/resolved-packages.json`.
  - exit code `0` on success; `2` on IO/parse error. (No `1` case — this command does not "fail on ambiguity"; it reports structure.)
  - `--check` mode (see PD4.5) exits non-zero if the on-disk artifact differs from freshly-generated.

## Library reference

No new external library — Node `fs`/`path` + the existing semantics build. For deterministic JSON, sort keys/arrays explicitly and `JSON.stringify(obj, null, 2) + "\n"`; do **not** rely on insertion order. Reuse `ProjectSymbolTable` + `DomainTableBuilder` from PD1/PD3. Find the project-walk helper the migrate CLI already uses (`grep -rn "rglob\|readdir\|walk" packages/migrate/src`).

## Implementation tasks

- [ ] **PD4.1 — Subcommand wiring.** Add `resolve-packages` to the `modeler` CLI (same entry as `migrate-to-packages`). Args: `<project-root>`, `--out <file>`, `--check`, `--verbose`. Exit codes: `0` ok, `2` IO/parse error, (`--check`) non-zero on drift.
- [ ] **PD4.2 — Build the model.** Load `modeler.toml` (`PackagesConfig`), walk `.ttr`/`.ttrg`/`.ttrd`, build the `ProjectSymbolTable` and `DomainTable`. Reuse the LSP/semantics build path — do not re-parse independently.
- [ ] **PD4.3 — Serialise the artifact.** Produce `ResolvedPackagesArtifact` exactly per contracts §13.4: `packages` (canonical+declared+nested+directory), `entities` (qname+package+schema for entity-kind objects), `domains` (name+resolvedPackages+resolvedEntities). Apply the determinism contract (sort everything; 2-space; trailing newline).
- [ ] **PD4.4 — Write to disk.** Default `<root>/.modeler/resolved-packages.json`; create `.modeler/` if absent. Confirm `.modeler/` is gitignored (it is per CLAUDE.md — verify). Honour `--out`.
- [ ] **PD4.5 — `--check` drift mode.** Generate in-memory, compare byte-for-byte to the on-disk artifact, exit non-zero with a diff summary if they differ. This is what a `ai-models` (or `model-ttr`) CI drift gate calls.
- [ ] **PD4.6 — Wire the committed-snapshot flow (Q1 = committed snapshot, decided 2026-06-19).** The artifact is committed into the model repo at a **non-ignored** path — `model-ttr/resolved-packages.json` (NOT under the gitignored `.modeler/`). The producer command is `modeler resolve-packages <model-root> --out model-ttr/resolved-packages.json`. Add a `model-ttr`-side CI step that runs `modeler resolve-packages --check --out model-ttr/resolved-packages.json` and fails on drift, so the committed snapshot can't go stale. `ai-models` agent CI reads this committed file directly and never invokes Modeler/Node. Document the regen command in `model-ttr`'s README (PD5 mirrors it).

## Verify by running

```bash
pnpm --filter @modeler/migrate test
pnpm -r build && pnpm -r typecheck && pnpm -r lint
# smoke against the real model:
node packages/migrate/dist/cli.js resolve-packages ~/Dev/ai-models/model-ttr --out /tmp/rp.json && head -40 /tmp/rp.json
```

All exit 0. The real-model smoke produces a well-formed artifact listing the `ai-models` packages.

## DONE when

- [ ] Every checkbox ticked.
- [ ] Artifact matches contracts §13.4 and is byte-deterministic (re-run = no-op diff).
- [ ] `--check` exits non-zero on drift, zero when in sync.
- [ ] Smoke run against `~/Dev/ai-models/model-ttr` succeeds and lists its packages/domains.
- [ ] Committed-snapshot flow wired: artifact lives at `model-ttr/resolved-packages.json` (committed, non-ignored); `model-ttr` CI runs `resolve-packages --check`; consumer command documented for PD5.
