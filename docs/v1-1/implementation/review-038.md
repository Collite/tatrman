# Review 038 — Section C2 fourth re-review (after `tasks-review-037`)

**Date:** 2026-05-21
**Scope:** re-review of C2 after the developer reported the `tasks-review-037` work done. Verified against runtime from a clean build, applying the generated edits. Companion: [`tasks-review-038.md`](tasks-review-038.md).
**Verdict:** **Approve pending docs (CC2/CC3).** The implementation is complete, correct, and green from a clean build (`pnpm -r build` then `pnpm -r test`: edit 42, parser 82, semantics 107, lsp 53, designer 61, vscode-ext 7, integration 63 | 1 skipped). Every code-level finding from review-037 is resolved. The **only** outstanding item is the documentation half of G3 — CLAUDE.md and `architecture.md` still describe the `.ttrl` layout sidecar that the code just removed, and the contract version wasn't bumped (CC3). These are doc-only, no code risk, but CLAUDE.md is load-bearing so the false invariant must be corrected before sign-off.

---

## Resolved (verified against runtime)

- **G2 [High] — autoImport fixed.** The handler now resolves the real package via `projectSymbols.get(qname)?.packageName`, and the fallback emits **no** import when the qname's first segment is a schema code (`db|er|map|query|cnc`). For `er.entity.artikl` → no import (correct). No test asserts `import er` anymore.
  ```ts
  const symbol = projectSymbols.get(_params.qname);
  if (symbol?.packageName) packageToImport = symbol.packageName;
  else if (!['db','er','map','query','cnc'].includes(firstSegment)) packageToImport = firstSegment;
  ```
- **G1 [High] — setLayout (carried, still good).** `buildSetLayoutEdit` produces valid TTR on both paths; covered by the edit unit suite (now 42 tests) and the integration round-trip.
- **G4 [Med] — apply-and-reparse tests present and green** (these are what caught the earlier stale build).
- **G5 [Med] — `createGraph` cleaned.** Returns `{ documentChanges: [] }` for an invalid uri; the non-standard `{ … error }` field is gone.
- **G6 [Med] — single return shapes.** `modeler/setLayout` and `modeler/createGraph` both return `Promise<WorkspaceEdit>` (empty `documentChanges` signals "can't build"); the client wrapper union is gone.
- **Build discipline** — the suite is green from a clean `pnpm -r build && pnpm -r test` this time. Thank you.
- **G3 code side — `.ttrl` file I/O removed.** `getLayout`/`setLayout`/`exportLayout` no longer read/write `<root>/.modeler/layout.ttrl` on disk.

---

## Outstanding

### G3-docs [Med] — CC2 not done: CLAUDE.md & `architecture.md` still describe the removed `.ttrl` sidecar

The D4 decision text in `section-C-plan.md` explicitly includes *"the architecture invariant 'text is canonical / layout is sidecar' is updated (CC2)."* That update wasn't made, so the docs now contradict the code:

- **`CLAUDE.md:65`** — *"Node positions are sidecar data, stored in `<project-root>/.modeler/layout.ttrl` and managed by the LSP (hosts never touch this file directly)."* This invariant is now false. CLAUDE.md is loaded as standing instructions, so a wrong invariant here will mislead future work (human and agent).
- **`docs/v1/design/architecture.md`** — multiple stale spots: the D3 row (line 25), the `getLayout`/`setLayout` description (line 168), the whole **§6 "Layout sidecar (`.ttrl`) format"** (lines 259–261…), and the browser-persistence note (line 422).

### G3-contract [Med] — CC3 not done

The layout method surface changed (graphUri-scoped; no on-disk `.ttrl`), but contracts §8 wasn't amended and the version wasn't bumped. If you keep the `projectRoot`/`layoutStore` params (see below), document them as the in-memory test/host seam; if not, remove them from §8. Either way, bump the version with a §12 changelog entry.

### Minor — vestigial `projectRoot`/`layoutStore` branches

`getLayout`/`setLayout` still have `opts.layoutStore && _params.projectRoot` branches (server.ts:419-420, 434-435), the `layoutStore?` option (line 66), and the `uri.endsWith('.ttrl')` guard (line 194). The on-disk sidecar is gone, but these in-memory remnants remain. They're harmless (a host/test injection seam), but the D4 text says "the branches are removed." Either delete them or note in D4 that the in-memory `layoutStore` seam is intentionally retained. Not a blocker.

### G7 [Low] — `buildRemoveObjectText` still matches by `indexOf(qname)`

Unchanged; substring/prefix-collision risk. Fine to defer.

---

## Recommendation

The code is done and correct — I'd sign C2 off as soon as the **CC2** doc update lands (so CLAUDE.md and `architecture.md` stop describing a `.ttrl` sidecar that no longer exists) and **CC3** bumps the contract. These are documentation tasks; no further code review is needed. Decide whether to also drop the vestigial `layoutStore`/`projectRoot` branches or document them, and whether G7 is worth doing now or deferring. `tasks-review-038.md` lists the steps.
