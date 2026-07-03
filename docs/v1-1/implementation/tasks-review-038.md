# Tasks — review-038 (Section C2 fourth re-review)

Findings in [`review-038.md`](review-038.md). **All C2 code is done and green — G1/G2/G4/G5/G6 are resolved; do not touch them.** The remaining work is **documentation only** (CC2/CC3) plus two optional cleanups. Once CC2/CC3 land, C2 is done.

> Suite is green from a clean build (edit 42, parser 82, semantics 107, lsp 53, designer 61, vscode-ext 7, integration 63 | 1 skipped). No code changes are required for the blockers below.

---

## CC2 [Med, required] — Update the layout invariant in the docs

The `.ttrl` on-disk sidecar was removed; layout now lives in the `.ttrg` `layout` block. Fix every doc that still says otherwise.

- [ ] **CC2.1 — `CLAUDE.md:65`.** Rewrite the "Text is canonical" bullet's last sentence. Replace *"Node positions are sidecar data, stored in `<project-root>/.modeler/layout.ttrl` and managed by the LSP (hosts never touch this file directly)."* with something like: *"Node positions live inside each `.ttrg` file's `layout` block; the LSP reads them via `modeler/getLayout` and writes them by synthesizing a `WorkspaceEdit` (`modeler/setLayout`) that the host applies — there is no separate sidecar file."*
- [ ] **CC2.2 — `docs/v1/design/architecture.md`.** Update the stale spots:
  - Line ~25 (D3 decision row): drop "node positions live in `.ttrl` sidecars"; say layout lives in the `.ttrg` `layout` block.
  - Line ~168 (`getLayout`/`setLayout` description): they read/write the `.ttrg` `layout` block, not a `.ttrl` sidecar.
  - **§6 "Layout sidecar (`.ttrl`) format"** (lines ~259+): either delete the section or replace it with a short "Layout is stored inside the `.ttrg` `layout` block (see v1.1 contracts §7.1); the standalone `.ttrl` sidecar from the original v1 design was removed in v1.1 — see `docs/v1-1/` D4." Cross-reference rather than leaving a format spec for a file that no longer exists.
  - Line ~422 (browser persistence note): update to the `.ttrg` layout block.
  - Lines ~182 / ~221 (`.ttrl` as a registered file extension): remove `.ttrl` from the registered-extensions lists **only if** the extension is truly unused now (confirm against `server.ts:194` — see the optional cleanup below); otherwise leave and note why.
- [ ] **CC2.3** Since `architecture.md` is the v1 design doc, prefer a brief "superseded in v1.1 (see D4)" note + pointer over silently rewriting v1 history, so the v1 record stays coherent. Use your judgment, but don't leave a flatly false invariant.

## CC3 [Med, required] — Bump the contract for the layout-method change

- [ ] **CC3.1** In `docs/v1-1/design/v1-1-contracts.md` §8, update the `getLayout`/`setLayout`/`exportLayout` entries to the graphUri-scoped, `.ttrg`-backed shape actually shipped (no on-disk `.ttrl`). If you retain the `projectRoot`/`layoutStore` params, document them explicitly as the in-memory host/test seam; otherwise remove them from §8.
- [ ] **CC3.2** Bump the contract version (header + a new §12 changelog entry, e.g. `v6, 2026-05-21 — §8 layout methods are graphUri-scoped and read/write the .ttrg layout block; the .modeler/layout.ttrl sidecar is removed (D4)`).

## Optional cleanups (not blockers)

- [ ] **OPT-1 — Vestigial layout branches.** Either delete the `opts.layoutStore && _params.projectRoot` branches (server.ts:419-420, 434-435), the `layoutStore?` option (line 66), and the `uri.endsWith('.ttrl')` guard (line 194); **or** add a one-line comment that `layoutStore` is intentionally kept as an in-memory test/host seam, and reconcile the D4 text ("the branches are removed") with whichever you choose.
- [ ] **OPT-2 — G7.** In `packages/edit/src/graph-edits.ts`, change `buildRemoveObjectText`'s `inner.indexOf(qname)` to a comma/bracket-boundary match so removing `er.entity.a` can't hit a substring of `er.entity.ab`; add a unit test with a near-prefix sibling. Defer if time-boxed.

---

## Done when

- [ ] CLAUDE.md and `architecture.md` no longer describe a `.ttrl` layout sidecar; the invariant matches the code (CC2).
- [ ] Contracts §8 reflects the shipped layout methods and the version is bumped (CC3).
- [ ] Vestigial `layoutStore`/`projectRoot` paths are either removed or documented, and the D4 text agrees with the code (OPT-1).
- [ ] `pnpm -r build && pnpm -r typecheck && pnpm -r lint && pnpm -r test` still green (no code change expected, but confirm).
