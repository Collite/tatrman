# Review 037 — Section C2 third re-review (after `tasks-review-036`)

**Date:** 2026-05-21
**Scope:** re-review of C2 after the developer reported the `tasks-review-036` work done. Verified against runtime, including a clean rebuild and applying the generated edits. Companion: [`tasks-review-037.md`](tasks-review-037.md).
**Verdict:** **Changes requested.** `buildSetLayoutEdit` (G1) is genuinely fixed — it now writes valid TTR. But of the two remaining High items, **G2 (autoImport still emits `import er`) and G3 (`.ttrl` removal) are not actually resolved**, and one was the *same* "decided but not implemented" contradiction flagged last round. Separately, **the suite was red as delivered** — `pnpm -r test` fails on a stale build because the developer didn't rebuild before claiming "done."

---

## Process issue — "done" was claimed on a red suite

Running `pnpm -r test` as delivered gives **2 failing integration tests** (`addObjectToGraph` apply-and-reparse), with `newText` = `'import false'` / `'import true'`. That's a **stale-build** symptom: the integration tests import `@modeler/lsp` from its built `dist/`, and `@modeler/lsp` wasn't rebuilt after the source change, so an old handler ran. After `pnpm -r build`, the suite is green (integration 64 | 1 skipped).

The task list's verify step is `pnpm -r build && pnpm -r typecheck && pnpm -r lint && pnpm -r test` — the `build` is not optional, because the integration suite runs against compiled artifacts. **Please run the full sequence (build first) before reporting done.** This is the third C2 round; a red suite at handoff costs a full review cycle.

---

## G1 — `buildSetLayoutEdit` — FIXED (verified)

Applying the edit to a freshly-built `@modeler/edit`:

- **No existing layout** → inserts `, layout: { nodes: { er.entity.a: { x: 10, y: 20 } } }` before the graph's closing brace. Parses.
- **Replacing a layout** → single `layout: { nodes: { … } }`, no doubled keyword. Parses.

Both paths now produce valid TTR with unquoted node keys (D1). Minor cosmetic only: the no-layout insertion yields `] ,` (space before the comma) and the inner `nodes` indentation is slightly ragged — not worth blocking, tidy if convenient.

---

## G2 [High] — `autoImport` still emits `import er` (not fixed; just relocated)

The builder signature was cleaned up (`buildAddObjectEdit(..., packageToImport: string | null)`) — good. But the **handler computes the package with the same naive first-segment slice** that was the bug:

```ts
// server.ts:479–481
const dotIdx = _params.qname.indexOf('.');
const packageToImport = _params.autoImport && dotIdx !== -1 ? _params.qname.slice(0, dotIdx) : null;
return buildAddObjectEdit(content, _params.uri, _params.qname, packageToImport);
```

For `er.entity.artikl`, `slice(0, dotIdx)` = `'er'` → it still emits `import er`. `er` is the **schema code**, not an importable package; unpackaged objects need no import at all. And the tests still **assert** the wrong output:
- `graph-edits.test.ts:88` → `expect(...).toBe('import er\n')`
- `lsp-v1.1-graph-methods.test.ts:226` → `toContain('import er')`

So G2.1 ("resolve the real package via the symbol table; emit nothing for unpackaged objects") and G2.3 ("no test asserts `import er`") were **not** done — the naive logic just moved from the builder to the handler, and the tests bless it. This is the same correctness bug as review-036, unchanged in effect.

**Minimum correct behaviour:** the first segment of a qname is a package **only if it isn't one of the schema codes** (`db|er|map|query|cnc`). For `er.entity.*` the package is `null` → emit no import. The robust version asks the resolver/symbol table for the symbol's declaring package (which also handles multi-segment packages like `billing.invoicing`). Either way, `import er` must never be produced.

## G3 [High] — `.ttrl` removal (D4) still not implemented; doc and code still contradict

`section-C-plan.md` D4 reaffirms, in writing: *"remove the `.ttrl` sidecar … the `getLayout`/`setLayout`/`exportLayout` `.ttrl` branches are removed … the architecture invariant … is updated (CC2)."* Yet:

- `server.ts` still contains **5** `.ttrl` references — the read/write branches are intact.
- `CLAUDE.md` and `docs/v1/design/architecture.md` are untouched (CC2 not done).
- The contract version is unchanged (CC3 not done).

This is the **second** round with the D4 decision recorded as "remove" while the code keeps it. Resolve it for real this time: either delete the `.ttrl` paths + plumbing and do CC2/CC3, **or** change the D4 text to "retain `.ttrl` as a v1 fallback (reason …)". Right now the plan and the code disagree, which is exactly what a decision record is supposed to prevent.

## G4 [Med] — apply-and-reparse tests: partially done

The developer did add apply-style tests (they're what surfaced the stale build — good, that's the point of them). But the `autoImport` apply test asserts `import er` (G2), so it validates the wrong result. Once G2 is fixed, this test must assert the corrected output.

## G5 [Med] — `createGraph`: dead code removed, error shape remains

The `const hasWorkspaceFolder = false` dead block is gone (good). But the handler still returns `{ documentChanges: [], error: 'uri must end with .ttrg' }` (server.ts:492) — a non-standard `error` field bolted onto a `WorkspaceEdit`. Tie this to G6.

## G6 [Med] — union return types still present

`modeler/setLayout` still returns `{ ok: boolean; reason?: string } | WorkspaceEdit` (server.ts:437), and the client types it as `{ ok: boolean } | WorkspaceEdit`. `createGraph` returns a `WorkspaceEdit`-or-`{…error}`. Pick one shape per method (return a `WorkspaceEdit` on the graphUri/create path; signal "can't" via empty `documentChanges` or an LSP error) and reflect it in contract §8 + the client types.

## G7 [Low] — `buildRemoveObjectText` still matches by `indexOf(qname)`

Unchanged (server-side `graph-edits.ts:151`). Substring/prefix-collision risk remains. Low priority; fix when touching this file for G2.

---

## Status summary

| Item | Severity | Status |
|------|----------|--------|
| G1 setLayout malformed | High | ✅ Fixed |
| G2 autoImport `import er` | High | ❌ Not fixed (logic relocated, same bug; tests still assert it) |
| G3 `.ttrl` removal + CC2/CC3 | High | ❌ Not done (decision says remove; code keeps it) |
| G4 apply-and-reparse tests | Med | ◐ Added, but autoImport test asserts wrong output |
| G5 createGraph dead code / error shape | Med | ◐ Dead code gone; error shape remains |
| G6 union return types | Med | ❌ Not done |
| G7 indexOf match | Low | ❌ Not done (defer ok) |

## Recommendation

Two High items (G2, G3) plus the build-discipline issue stand between C2 and done. They're small:
1. **G2:** make the handler emit no import for schema-code-prefixed (unpackaged) qnames — ideally via the resolver — and fix the three tests that assert `import er`.
2. **G3:** actually delete the `.ttrl` branches + do CC2/CC3, or revise the D4 decision text to match the code.
3. Run `pnpm -r build && … test` and confirm green **before** claiming done.

G5/G6 are quick; G7 can wait. `tasks-review-037.md` has the concrete steps.
