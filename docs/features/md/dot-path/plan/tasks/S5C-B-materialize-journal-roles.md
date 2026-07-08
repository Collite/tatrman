# S5C-B — materialization, generated model, journaling roles

Goal: `:=` end to end (R26/R27 + MDS7 generated `.ttrm`), statement lowering (§8 rows), and the
journaling role family (R30/R31 + MDS8) with technical-column filling.

Prereq: S5C-A; **semantics-block feature (grammar 4.2) merged & published** (arc pre-flight —
verify before starting, note the version here). TDD: S5C-B1–B3 (red) before S5C-B4–B6.

## Tasks

- [ ] **S5C-B1 — red MaterializeSpec** (ttrp-frontend): existing target — `plan := e` legal,
  truncate+overwrite IR; `with` present and matching → legal; any mismatched/unknown key →
  MD-015; RHS grain/measures ≠ plan's → MD-023. Fresh target — `C := e` without `with` → MD-015
  (shape required); `with { shape: long }` → legal, inferred grain/measures from e's shape
  asserted on the IR; `table:` defaulted by project convention (assert the derived name);
  `journal:` defaulted to overwrite.
- [ ] **S5C-B2 — red GeneratedTtrmSpec**: materializing fresh `C` emits
  `<project>/generated/md/c.ttrm` via ttr-writer — content: cubelet def + `md2db_cubelet`
  binding (+ long-shape measure-code/value columns when `shape: long`), do-not-edit header;
  **deterministic**: emit twice → byte-identical; idempotent re-run of the same script → no
  diff; changed `with` values → updated file; round-trip: the generated file parses, resolves,
  and the S1 `MdModel` sees C as a regular cubelet in the next compile.
- [ ] **S5C-B3 — red JournalRolesSpec** (ttr-semantics + frontend): fixture backing tables gain
  `semantics { role: … }` tags — `valid_flag`, `valid_from`/`valid_to`, `version`,
  `authored_by`, `written_at`; validation: invalidate-journaled binding whose table lacks a
  valid role → MD-018; role spellings asserted against the grounding contracts' conventions
  (**check ai-platform `feature-grounding-contracts.md` role list first**; record alignment
  here); the latest-valid default agg (S1-B1's MAX case) now **derives** from
  `valid_from`/`valid_to` presence (D26) — migrate that fixture + spec, behavior unchanged.
- [ ] **S5C-B4 — implement frontend side**: `with` key validation, definition inference,
  generated-`.ttrm` emission (ttr-writer; file-replace semantics, MDS7), journal-role validation
  wiring.
- [ ] **S5C-B5 — implement translator side** (§8 statement rows): `:=` → (create-table for fresh
  targets, from the generated binding) + truncate + Store; `+=` → MERGE-shaped plan on grain
  keys with the per-mode collision arm; `-=` → key anti-join → DELETE (overwrite) /
  valid-flip UPDATE (invalidate); technical-column filling on every write path (version = max+1
  per key, authored_by = run identity from the manifest, written_at = run clock) — extend the
  S5-B write goldens; migrate S4-A4b/S5-B fixtures from binding-declared `valid` column to the
  role-based declaration, **goldens unchanged**.
- [ ] **S5C-B6 — E2E script on PG** (extends the S4-B/S5-B harness): one `.ttrp` script —
  `V = sales.2025.month.*` (virtual) → `C := V with { shape: long, journal: invalidate }` →
  `C += sales.2026.month.*` (merge) → `C -= C.2025.{january}` (delete → valid-flip) → read
  `c.2025.net` back through the dot-path pipeline (journaling view applied); assert generated
  `.ttrm` exists; repeat under overwrite mode; diff mode: `+=` appends deltas and read
  SUM-reconstructs, `-=` errors (MD-017).
- [ ] **S5C-B7 — gates.** Un-skip remaining `pending: "S5C"` goldens; both domains + conformance
  green. Commit `md-sugar S5CB: materialization + generated model + journal roles`.

## Coder notes

_(empty — semantics-block version, grounding role-spelling alignment, project table-name
convention land here)_

## References

- Contracts §11 (R26/R27), §12 (R30/R31), §8 statement + journaling-view rows, §6 · design note
  D22, D25, D26 · architecture MDS7/MDS8.
- Semantics-block feature: `docs/features/semantics-block/README.md` (role mechanics, free-form
  object, validation home) — journal roles are a new family in ITS vocabulary registry; add them
  where T3 put the grounding roles.
- ai-platform grounding contracts (repo root `feature-grounding-contracts.md`) — role-spelling
  authority; if unreachable from the working environment, get the role list from Bora before B3.
