# Review 059 — Section B (grammar: MAPPING token + inline-mapping rules)

**Date:** 2026-05-27
**Release:** v2.1 (inline mappings)
**Scope:** review of Section 2.1.B against [`tasks/section-B-grammar.md`](../plan/tasks/section-B-grammar.md), the contract doc [`grammar-v2-1-changes.md`](../design/grammar-v2-1-changes.md) §3, and the design doc [`v2.1-inline-mappings.md`](../design/v2.1-inline-mappings.md) §3. Commit under review: `d93529d` "Section B: grammar — add MAPPING token and inline-mapping rules".

Verified against runtime (not just by reading the diff):

- `pnpm --filter @modeler/grammar build` clean → `version.ts` exports `'2.1'`, `property-map.ts` has a `mapping` entry on entity/attribute/relation.
- `pnpm --filter @modeler/parser build` clean (antlr regen + tsc).
- `pnpm -r typecheck` green (8/8).
- v1.1 backward-compat intact: `samples-v1.1-parse` (27) green; integration `v1.1-samples` green.
- **Probed every surface form with a built parser** (the check the developer skipped): 5 of the documented forms parse, but the entity-level `columns:` forms (b) and (c) — the headline example in design §3.1 — **fail to parse**. See B1.
- `pnpm -r test` is **red** in `@modeler/parser` and `@modeler/integration-tests`. See B2.

Companion: [`tasks-review-059.md`](tasks-review-059.md).

**Verdict: NOT done.** The hand-written `TTR.g4` edits faithfully match the Section B task list, the changelog/property-map/TextMate regen are all present and correct, and the package builds + typecheck are green. But two issues block "done": a **grammar bug** that makes the design's canonical entity example unparseable (B1), and a **red test suite** (B2). Both are fixable inside Section B with small, well-scoped changes — neither requires touching Sections C+.

---

## What's good (verified)

- **`.g4` edits are faithful to the task list.** Version marker → `2.1`; `MAPPING : 'mapping' ;` added in the v1.1 keyword block (before `IDENT`); `MAPPING` added to `idPart`; `mappingProperty` wired into `entityProperty`/`attributeProperty`/`relationProperty`; `targetProperty` relaxed to `( object_ | id )`. All sub-rules (`mappingValue`, `mappingBlock`, `mappingBlockProperty`, `mappingColumnsProperty`, `mappingColumnMap`, `mappingColumnEntry`, `mappingColumnValue`) present, reusing `fkProperty_` and `COLUMNS` as designed.
- **`targetProperty` relaxation works** — confirmed `target: KOD_ZBOZI` shorthand parses in an explicit `def er2db_attribute` (and in entity-level `mapping: { target: db.dbo.T }`).
- **Five forms parse cleanly** (probed against the built parser): attribute bare-id (§3.2), attribute full (§3.3), relation bare-fk (§3.4), relation full (§3.5), entity-level `target:` (object + bare-id shorthand), and entity `columns:` form (a) bare-id.
- **Regen + ancillary edits correct.** `CHANGELOG.md` 2.1 entry; `extract-property-map.ts` typeMap `mapping` entry; `generate-tm-grammar.ts` `MAPPING` scope + regenerated `ttr.tmLanguage.json` (`mapping` now in the keyword alternation).
- **No `def er2db_*` rules were touched** — explicit declarations are unchanged.
- **Backward compatibility holds** — every v1.1 sample parses unchanged.
- **The broken `samples/2.1/er.ttr` recovers gracefully** (the task's "ParseError diagnostics, not crashes" bar): `parseFile` returns 11 diagnostics and does not throw.

---

## Blocker

### B1 — Entity-level `columns:` forms (b) and (c) do not parse

The canonical design example (design §3.1, and the contract doc's headline) fails:

```ttr
def entity artikl {
    mapping: {
        target: { table: db.dbo.QZBOZI_DF },
        columns: {
            id_artiklu:    IDZBOZI,                            // form (a) — parses ✓
            kód_artiklu:   { target: KOD_ZBOZI },              // form (b) — FAILS ✗
            název_artiklu: { target: { column: NAZEV_ZBOZI } } // form (c) — FAILS ✗
        }
    }
}
```

Parse error: `mismatched input 'target' expecting {'package','import','graph',...}`.

**Root cause.** The grammar routes column-entry values through `mappingColumnValue : id | object_` (TTR.g4 ~lines 240–243). Forms (b) and (c) are objects whose **key is `target`**. But a generic `object_` takes its keys from `propertyEntry → key → id → idPart`, and **`idPart` does not list `TARGET`** (it is a dedicated token used only by `targetProperty`). So `{ target: ... }` can never be parsed as a generic `object_` — only form (a) (a bare id) works for column entries.

**Where the mistake entered.** The contract doc [`grammar-v2-1-changes.md`](../design/grammar-v2-1-changes.md) §3.2 had this **right** — it used a dedicated alternative plus a helper rule:

```antlr
mappingColumnValue
  : id
  | LBRACE TARGET propSep? mappingTargetValue RBRACE   // forms (b) AND (c)
  | object_
  ;
mappingTargetValue : id | object_ ;
```

The Section B **task file** (`tasks/section-B-grammar.md`, lines 100–104 and the note at line 108) collapsed this to `id | object_`, asserting "the semantic layer disambiguates by inspecting keys." That reasoning is wrong: the semantic layer never runs because the **parser** rejects `{ target: ... }` first. The developer implemented the task-file version verbatim and never tested a valid inline mapping (the task's only sample check was that the *broken* `er.ttr` errors — which it does, for unrelated reasons), so the regression went unnoticed.

**Fix (proven).** Restore the contract-doc rules. I applied exactly this change, regenerated the parser, and confirmed **all four surface forms then parse** (including the full §3.1 entity example), then reverted so the tree is back to the committed state. The dedicated `LBRACE TARGET … RBRACE` alternative covers both form (b) (`mappingTargetValue = id`) and form (c) (`mappingTargetValue = object_`), exactly as the contract doc's note intends. This is grammar-only and additive; it does not affect Sections C+ beyond giving them parseable input.

---

## High

### B2 — The full test suite is red (`pnpm -r test` fails)

`@modeler/parser` and `@modeler/integration-tests` both fail. Each has a whole-`samples/` glob that asserts every non-broken sample parses with zero errors:

- `packages/parser/src/__tests__/parser.test.ts:93` — "parses all sample files without errors", `getAllTtrFiles(samplesDir, ['broken'])`.
- `tests/integration/src/integration.test.ts:195` — "parses all sample files (non-broken) without errors", `getAllTtrFiles(samplesDir, ['broken'])` (call site line 126).

Both pick up `samples/2.1/er.ttr`, which is the user's **raw sketch** — it uses `attributes:` instead of `columns:` (rejected by decision C2), the unsupported bareword `target { … }` form (design §3.5 "not supported"), a standalone `def mapping { type: er2db }` (deferred to v3.0 per C4), and has unbalanced braces. It was committed in the planning commit (`f6cfdf7`), not in Section B.

The Section B task file itself anticipated this ("Confirm the sketched `samples/2.1/er.ttr` does NOT yet parse cleanly … Section F rewrites it") — but Section B added **no guard**, so the WIP fixture now fails the suite. The fix is to exclude `samples/2.1` from those two globs until Section F re-includes it (change `['broken']` → `['broken', '2.1']`; the helper matches by directory name). Section F's task list must then revert that exclusion when it rewrites `er.ttr`.

**Doc contradiction to resolve.** The plan ([`implementation-plan-v2.1.md`](../plan/implementation-plan-v2.1.md), Section B Acceptance) says `samples/2.1/er.ttr` "parses without grammar errors," while the task file says "expect parse errors." These are mutually exclusive. The task file's reading is the correct one (the sketch is genuinely pre-design); the plan's acceptance bullet should be corrected.

---

## Low

- **L1 — Swallowed null-deref diagnostic on hard parse failures.** The broken `er.ttr` yields, alongside 10 proper `ttr/parse-error` diagnostics, one **uncoded** error `Cannot read properties of null (reading 'tableProperty')` (with `ast: null`). `parseFile` does not throw (good), but a code-less captured exception is a smell. This is pre-existing walker fragility on severely malformed input, **not** introduced by Section B — track it for whoever hardens error recovery; do not fix it here.
- **L2 — Generated files are gitignored; the task/CLAUDE docs say otherwise.** `packages/grammar/src/generated/` and `packages/parser/src/generated/` are matched by `generated/` in `.gitignore` and were never committed (project convention since Phase 00). The Section B task file's verification step ("`git status` shows regenerated files … stage them … in one commit") and CLAUDE.md's grammar-regen step ("Commit the generated files alongside the grammar change") both contradict the actual `.gitignore`. The developer correctly did **not** commit them; the **docs** are wrong and mislead a reviewer. The TextMate output (`packages/vscode-ext/syntaxes/ttr.tmLanguage.json`) *is* tracked and *was* committed — correct. Recommend fixing the two docs.

---

## Recommendation

Section B is **one grammar fix away** from correct, plus a one-line test guard. Do **B1** (restore the contract-doc `mappingColumnValue`/`mappingTargetValue`, regenerate, add a parser test per form) — without it, Sections C–F build on a grammar that can't parse the design's primary example. Do **B2** (exclude `samples/2.1` from the two globs until Section F; fix the plan's acceptance wording). The Low items are tracking/doc fixes — L2 (reconcile the "commit generated files" docs) is worth doing now since it actively misleads; L1 is a follow-up ticket. `tasks-review-059.md` has the concrete, ordered steps.

---

## Resolution (2026-05-27, commit `2b673b5`)

**Verdict now: DONE.** Re-verified against runtime — all blockers cleared, full workspace green.

- **B1 ✅** `mappingColumnValue` restored to the contract-doc form (`id | LBRACE TARGET propSep? mappingTargetValue RBRACE | object_`) plus the `mappingTargetValue` helper (`TTR.g4:240–249`). Parser regenerated (no ANTLR ambiguity warnings between the dedicated `TARGET` alternative and the `object_` fallback). New `packages/parser/src/__tests__/inline-mapping-grammar.test.ts` asserts **zero errors** on all six forms — including the previously-failing entity full a+b+c example. The Section B task file's `mappingColumnValue` spec and both explanatory notes (lines ~108, ~188) were corrected to match.
- **B2 ✅** `pnpm -r test` is green. Both integration call sites were guarded (`integration.test.ts:126` *and* `:225` — the developer guarded the second `beforeAll` too, which I'd only flagged via grep), plus `parser.test.ts:94`, with `['broken', '2.1']` and an explaining comment. The plan's Section B acceptance bullet was rewritten to state the sketch is expected to error until Section F. A `F.WIP` reminder to remove the guard was added to `section-F-integration-tests.md` (lists all three locations).
- **L2 ✅** `CLAUDE.md` grammar-regen step 3 and the Section B task file's verification bullet both now state that `src/generated/` is gitignored and regenerated at build time; only `TTR.g4`, the scripts, and `ttr.tmLanguage.json` are committed.
- **L1** — left as a tracking item; not separately ticketed outside this review. Pre-existing, non-blocking (the uncoded `tableProperty` null-deref on hard parse failures). Carry forward to whoever does error-recovery hardening.

**Gate after fix:** parser 115 · semantics 108 · edit 60 · migrate 23 · lsp 130 · vscode-ext 24 · designer 129 · integration 92 (+1 skip) · `pnpm -r typecheck` 8/8 · `pnpm -r lint` clean · `TTR_GRAMMAR_VERSION === '2.1'`. Section B is ready; Section C (parser AST + walker) can proceed.
