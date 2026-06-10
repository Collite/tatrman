# Linter / Formatter / Autofix — Implementation Plan

Companion to [`architecture.md`](architecture.md) and [`contracts.md`](contracts.md). This is the
phased plan: overall shape, per-phase deliverables, pre-flight conditions, and definitions of DONE.
Task lists live in [`tasks/`](tasks/README.md) and implement these phases.

Convention reminder (CLAUDE.md): commits use `Section <X>: <description>`; `pnpm -r build`,
`pnpm -r test`, `pnpm -r typecheck`, `pnpm -r lint` must all be green at every phase gate; new LSP
feature tests go in `tests/integration/`.

---

## Overall

Five phases. **P0 is foundational and blocks everything.** After P0, P1 (formatter) and P2 (lint
package) can proceed in parallel. P3 (config) follows P2. P4 (autofix) needs P0 + P2 (+P3 for the
`[fix]` knob). Each phase is independently shippable and leaves the repo green.

```
P0 CST/trivia ──┬──► P1 Formatter ───────────────► (ship ttr fmt)
                └──► P2 Lint package ──► P3 Config ──► P4 Autofix ──► (ship ttr lint --fix)
```

TDD throughout: each stage writes tests first (defined in the task lists), then code to green.
Unit tests (single function/class) and component tests (inter-module) only; E2E/integration is a
separate flow except the few LSP-wiring tests that belong in `tests/integration/`.

---

## P0 — Lossless CST & trivia (parser)

**Goal.** Comments become first-class trivia on AST nodes; the parse round-trips byte-for-byte.

**Pre-flight.**
- Working tree green (`pnpm -r {build,test,typecheck,lint}`).
- Confirm antlr4ng hidden-channel API names against `node_modules/antlr4ng` (`BufferedTokenStream.getHiddenTokensToLeft/Right`, `Token.HIDDEN_CHANNEL`).
- Read CLAUDE.md "Grammar regeneration" and `SourceLocation` invariant.

**Deliverables.**
- `TTR.g4`: `LINE_COMMENT`/`BLOCK_COMMENT` → `channel(HIDDEN)` (`WS` stays `skip`). Regenerated TS
  parser + TextMate grammar; `sync-to-ai-platform.sh` run and committed.
- `parser/src/cst/trivia.ts` (types per contracts §1.1), `cst/attach.ts` (`attachTrivia`, §1.4).
- AST nodes carry optional `leadingTrivia`/`trailingTrivia`; `parseString`/`parseFile` populate them.
- Round-trip identity test helper + corpus.

**DONE.**
- Round-trip identity holds for the sample corpus (`print∘parse === src`).
- `check-sync.sh <ai-platform>` passes; ai-platform parser regen verified to still parse the corpus.
- All four gates green. No `any`. `SourceLocation` spans on trivia verified by exact-range tests.
- No change to any existing diagnostic or formatter output (comments still dropped by the *old*
  formatter at this point — that's P1).

---

## P1 — Formatter package (`@modeler/format`, `ttr fmt`)

**Goal.** Extract the existing formatter into its own package, make it comment-preserving, ship the
CLI, and re-wire the LSP.

**Pre-flight.** P0 DONE. Existing formatter tests green in `lsp`.

**Deliverables.**
- New `@modeler/format` with `format`, `formatDocument`, `FormatConfig`, `DEFAULT_FORMAT_CONFIG`
  (contracts §2). `printer.ts`/`ir.ts`/`render.ts` moved from `lsp/src/formatter/`.
- Printer emits `leadingTrivia`/`trailingTrivia` (comment preservation).
- `modeler-fmt` CLI with `--check`/`--write` (contracts §7.2).
- `lsp` depends on `@modeler/format`; `onDocumentFormatting` and the extract-def refactor call it;
  `lsp/src/formatter/` deleted.

**DONE.**
- Idempotency, semantics-preserving, and comment-preservation tests pass (contracts §2.3).
- Moved formatter tests pass unchanged in the new package; new comment cases added.
- `ttr fmt --check` exits 1 on an unformatted fixture, 0 after `--write`.
- LSP formatting still works (integration test). All gates green.

---

## P2 — Lint package & rule model (`@modeler/lint`)

**Goal.** Replace the `Validator` with a rule registry + runner producing byte-identical
diagnostics; add trivia-based suppression; re-wire the LSP; delete `Validator`.

**Pre-flight.** P0 DONE. Inventory `validator.ts` checks against the design §5.5 table.

**Deliverables.**
- New `@modeler/lint`: `rule.ts`, `registry.ts`, `runner.ts` (contracts §3), `suppression.ts` (§4).
- All 26 checks ported to `rules/*` with ids/codes/categories/severities from design §5.5.
- `lintDocument`/`lintProject` (contracts §3.5); suppression wired into the runner.
- `lsp.publishDiagnostics` calls the runner; `Validator` deleted from `semantics`; shared helpers
  re-homed.
- Golden corpus test: lint output == old `Validator` output under `recommended`.

**DONE.**
- Per-rule unit tests (ported from `validator.test.ts` et al.) pass; golden test passes.
- Suppression unit tests pass (every directive form, unused, cannot-suppress on correctness).
- LSP diagnostics integration test passes. `Validator` gone; no references remain. All gates green.

---

## P3 — Configuration (`.ttrlint.toml`)

**Goal.** Per-rule/category config with presets, precedence, correctness clamp, `modeler.toml`
back-compat, and LSP config-watch.

**Pre-flight.** P2 DONE.

**Deliverables.**
- `config.ts` (contracts §5): discovery, `smol-toml` parse, precedence resolution, presets, clamp.
- Back-compat mapping from `modeler.toml [lint]` + deprecation diagnostic.
- Config-level diagnostics (§5.6) published on `.ttrlint.toml`.
- LSP watches `.ttrlint.toml` and re-lints open docs (extend completion-config invalidation).

**DONE.**
- Precedence, preset, clamp, back-compat, and unknown-rule unit tests pass.
- Editing `.ttrlint.toml` live re-lints open documents (integration test). All gates green.

---

## P4 — Autofix (`ttr lint --fix`, CodeActions)

**Goal.** Rules carry `fix`; safe fixes batch-apply to a fixpoint; suggestions are CodeAction-only;
re-home the existing quick-fixes; CLI `--fix`.

**Pre-flight.** P0 + P2 DONE; P3 DONE for the `[fix]` knob.

**Deliverables.**
- `fix.ts` (contracts §6): `collectSafeFixes`, non-overlap merge, fixpoint loop.
- `Rule.fix` populated for the safe subset + suggestion subset (design §5.5 Fix column).
- Existing `quickFix*` edit logic moved into `@modeler/edit`; `onCodeAction` re-wired to rule fixes
  (drops the hard-coded `switch`).
- `modeler-lint --fix` (contracts §7.1).

**DONE.**
- Each safe fix has a unit test asserting resulting text; overlap/fixpoint behaviour tested.
- `--fix` on a fixture removes unused imports / inserts missing imports etc., is idempotent, and
  never touches suggestion-only diagnostics.
- CodeActions surface in the editor (integration test); suggestion fixes are not batch-applied.
- All gates green.

---

## Sequencing & parallelism

- **Critical path:** P0 → P2 → P3 → P4.
- **Parallel:** P1 may run alongside P2/P3 once P0 lands (independent package, parser-only dep).
- **Shared touch-point:** both P1 and P2/P4 modify `server.ts`; sequence those edits to avoid
  conflicts (formatter wiring in P1; diagnostics wiring in P2; codeaction wiring in P4).

## Global definition of DONE (every phase)

`pnpm -r build` · `pnpm -r test` · `pnpm -r typecheck` · `pnpm -r lint` all green; no `any` outside
`generated/**`; new behaviour covered by tests written before the code; design/contract docs updated
if the implementation revealed a needed change; CLAUDE.md updated when P0 lands (trivia claim, §
Appendix B of design).
