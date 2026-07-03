# Review 034 — Section C1 re-review (after `tasks-review-033`)

**Date:** 2026-05-21
**Scope:** re-review of `1.1.C.1` after the developer reported the `tasks-review-033` fixes done (and mentioned a `parseCardinality("many")` fix). Verified every review-033 item against runtime, then re-checked the new fixture. Companion: [`tasks-review-034.md`](tasks-review-034.md).
**Verdict:** **Approve with one required fix.** All six review-033 findings (F1–F6) are properly resolved — F3 is actually better than the task asked. But the new positive fixture (F4) was authored with **non-canonical cardinality syntax**, and to make it work the developer changed `parseCardinality`/`extractCardinality` in a way that **contradicts the cardinality contract** (`docs/v1/design/phase-03-contracts.md` §8). That one item must be fixed; everything else is signed off.

---

## review-033 items — all verified resolved

Ran the full suite: parser **82**, semantics **107**, lsp **53**, designer **61**, vscode-ext **7**, integration **50 passed | 1 skipped**. `grep -rn "TODO(C1)" packages/` returns nothing.

- **F1 (.ttrg-without-graph WrongFileKind) — DONE, correct.** `walker.ts` now emits `WrongFileKind` for `file.endsWith('.ttrg') && !graphCtx` (mutually exclusive with the graph+defs branch); the `TODO(C1)` is gone. `ttrg-parse.test.ts` flipped to assert the Error for both the schema-directive-only and defs-only cases, and keeps a positive "graph block, no defs → no error" case. New broken fixture `samples/broken/v1.1/graph-missing.ttrg` (`schema er namespace entity`) + B7 row `['graph-missing.ttrg', ['ttr/wrong-file-kind']]` (`integration.test.ts:155`). Solid.
- **F2 (de-dup edges + bare-id FK) — DONE, correct.** `buildEdgeForDef(def, schemaCode, namespace, knownQnames)` extracted; **both** `buildProjectModelGraph` and `computeGraphEdges` now call it. FK endpoints go through `extractFkRef` (handles bare `id` *and* `list`). `computeGraphEdges` uses `objectSet.has(defQname)` and the redundant endpoint guard is gone. New test `graph-resolve.test.ts:110` proves the bare-id FK form (`from: a.id, to: b.a_id`) produces an edge. Exactly what was asked.
- **F3 (contract version + changelog) — DONE, better than asked.** My task said bump to `v4`; the developer correctly noticed the changelog *already* had both a `v3` and a `v4` entry (2026-05-19) and bumped to **`v5, 2026-05-21`** instead — header and a new §12 changelog entry now agree, no collision. Good catch; my review-033 missed the existing `v4`.
- **F4 (positive fixture) — created** (`samples/v1.1-mini/graphs/artikl_overview.ttrg` + `entities/artikl.ttr`), parses clean via the `ttrg-parse` sweep, uses unquoted dotted-id layout keys, partial layout (no stale node). See **G1** below for the one problem with it.
- **F5 (stale-node README) — DONE.** README row now lists exactly the two codes the B7 test asserts (`graph-layout-stale-node`, `graph-name-mismatch`); the bogus `graph-object-not-found` claim is removed.
- **F6 (resolution-assumption comment) — DONE.** `computeGraphEdges` carries the fully-qualified-objects / `resolveRef`-not-`Resolver` comment.

---

## New finding

### G1 [Medium] — Cardinality changes violate the §8 contract; the fixture uses non-canonical syntax

The contract (`docs/v1/design/phase-03-contracts.md` §8) is explicit and even ships the reference implementation:

- `extractCardinality` accepts **string-valued entries only** — `if (!entry || entry.value.kind !== 'string') return null;` (line 534), and line 552: *"If a user writes the cardinality as a non-string value … `lookup` returns `null`."*
- `parseCardinality` accepts these strings for `'many'`: **`"n"` and `"*"`** (line 523). `"many"` is **not** an accepted input — `many` is the *output* enum value (`Cardinality = … | 'many' | …`).

The corpus agrees: every cardinality in `samples/` uses quoted strings (`cardinality: { from: "0..*", to: "0..1" }`, ×112). The **only** exception is the new fixture:

```
// samples/v1.1-mini/entities/artikl.ttr
def relation artikl_dobropis { from: er.entity.artikl, to: er.entity.dobropis, cardinality: { from: 1, to: many } }
```

`from: 1` is a `number`, `to: many` is a bare `id` — neither is contract-legal input. To make this fixture render an edge glyph, the developer changed `model-graph.ts`:

1. `parseCardinality` — added `case 'many': return 'many';` (conflates the output enum with an input token; not in the §8 accepted set).
2. `extractCardinality` — now also accepts `entry.value.kind === 'id'` and `'number'`, directly reversing the contract's "string-only / non-string → null" rule.

(The `id`/`number` widening was present in the first C1 submission and `graph-resolve.test.ts:96` also leans on it with `{ from: 1, to: n }`; review-033 mis-classified it as "verified/correct." This re-review corrects that — it's the same contract issue.)

**Why it matters (not just style):** §8 is a versioned contract with its own amendment discipline; the parser is supposed to mirror ai-platform's mapping. Quietly broadening accepted cardinality inputs to satisfy a self-authored, non-canonical fixture is exactly the kind of contract drift the amendment process exists to prevent — and risks divergence from ai-platform, which (per the same contract) accepts string forms only. A fixture destined for the migrated 1.1.G samples should also *model* canonical syntax.

**Resolution (preferred — makes the problem disappear with no contract change):**
- Rewrite the fixture relation to canonical string cardinality, e.g. `cardinality: { from: "1", to: "0..*" }` (or `"n"`/`"*"` for many).
- Rewrite `graph-resolve.test.ts:96`'s cardinality case to string form (`{ from: "1", to: "n" }`), expecting `one`/`many` as before.
- Revert the two `model-graph.ts` changes: drop the `case 'many'` line in `parseCardinality`, and restore `extractCardinality`'s `entry.value.kind !== 'string'` guard.

**Alternative (only if v1.1 deliberately wants to accept unquoted/numeric/`many` cardinality):** that is a real contract change — amend §8 (or add a v1.1 override in `v1-1-contracts.md`), enumerate the newly-accepted forms, bump the contract version, and coordinate with ai-platform's parser. Don't leave the parser broader than the written contract.

---

## Recommendation

C1 is essentially done and the review-033 work is high quality. Resolve **G1** (the canonical-fixture path is ~10 minutes and needs no contract change), keep the suite green, and C1 can be marked complete. No further re-review needed for the F-items.
