# Stage 2F — Cubelet validator + LSP wiring + integration

Goal: validate cubelet grain/measures against the lattice, then wire MD symbols into the LSP
(hover, go-to-definition, completion incl. calc-name completion) and add integration tests. Closes
Phase 2.

Prereq: Stage 2E merged & green. TDD: 2F1 before 2F2–2F4.

References (verified):
- Cubelet rules: [`../../contracts.md`](../../contracts.md) §3.7, §6.1 (grain must resolve; advisory
  `md/grain-not-leaf`).
- LSP server: `packages/lsp/src/server.ts` (hover/definition/completion handlers + custom methods;
  `sourceLocationToRange`). **Do not** add a new custom method — MD flows through standard methods
  (contracts §9).
- Integration harness: `tests/integration/src/` (e.g. `completion.test.ts`, `rename.test.ts`,
  `areas-lsp.test.ts`) — the `PassThrough`-paired-connection pattern. Put new MD LSP tests here.
- Codes: contracts §7 (`md/grain-ref-unknown`, `md/grain-not-leaf`).

---

- [ ] **2F1 — Tests first (red).**
  - Unit: a cubelet whose `grain` ref doesn't resolve → `md/grain-ref-unknown`; a grain attribute
    strictly coarser than another in the same grain → `md/grain-not-leaf` (warning); a clean cubelet
    validates.
  - Integration (`tests/integration/src/md-*.test.ts`): boot the server, `didOpen` an `.ttrm` MD
    model, assert: (a) diagnostics for a seeded error match the expected `md/*` codes; (b) hover on a
    `domain:`/measure ref returns its def; (c) go-to-definition jumps to the def; (d) completion
    inside `calc:` lists catalog names (`truncToDay`, …); (e) completion of `domain:` lists domains.
  - Confirm red.

- [ ] **2F2 — Cubelet validator.** Resolve each `grain` dotted ref to a real `Dimension.attribute`
  (`md/grain-ref-unknown`); using 2E's grain order, emit advisory `md/grain-not-leaf` when one grain
  attribute is strictly coarser than another. Validate `measures` refs/inline defs resolve.

- [ ] **2F3 — LSP wiring.** Ensure the new MD symbols feed hover, definition, and completion through
  the existing handlers (no new method). Add calc-name completion sourced from `MD_CALC_CATALOG`,
  and context completion for `domain:`/`measure`/grain refs. Confirm `md/*` diagnostics publish via
  `textDocument/publishDiagnostics`.

- [ ] **2F4 — TextMate sanity.** Confirm the 1B TextMate regen highlights the MD keywords in the
  VS Code ext (open an `.ttrm` MD file in the Extension Dev Host, F5). No code change expected;
  re-run the generator if needed.

- [ ] **2F5 — Verify (Phase 2 DONE).**
  - 2F1 unit + integration tests pass.
  - **Every logical `md/*` code** (contracts §7, excluding the binding-only codes deferred to
    Phase 3) has a triggering + a clean fixture.
  - `pnpm --filter @modeler/semantics test && pnpm --filter @modeler/integration-tests test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test`

- [ ] **2F6 — Commit.** `Section MD-2F: cubelet validator + MD LSP features`.
