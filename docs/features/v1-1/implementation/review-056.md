# Review 056 — Section H3 re-review (fixes for review-055)

**Date:** 2026-05-25
**Scope:** re-review of Section 1.1.H.3 after the developer addressed [`tasks-review-055.md`](tasks-review-055.md). Verified against runtime:

- `pnpm --filter @modeler/lsp lint` → **clean (exit 0)** (was 5 errors).
- `pnpm --filter @modeler/lsp test` → **89 passed**.
- `pnpm --filter @modeler/integration-tests test` → **88 passed, 1 skipped**.
- `pnpm --filter @modeler/vscode-ext test` → **24 passed**.
- `pnpm -r typecheck` → clean (8/8).
- End-to-end probe of the completion-config path (see below).

Companion: [`tasks-review-056.md`](tasks-review-056.md).

**Verdict:** **Blockers fixed; two loose ends.** The two High blockers from 055 are genuinely resolved and verified at runtime — `autoImport` is now honoured end-to-end, and lint is clean. F4/F5 are done too. What remains are the two items the developer skipped: the `preselectFullyQualified` setting is still not honoured (F1.5), and the config wiring still has **no end-to-end regression test** (F3) — the exact gap that let this regress the first time.

---

## Verified fixed (runtime evidence)

### F1 — completion config is loaded and honoured ✅ (verified end-to-end)
`server.ts` now captures `supportsConfiguration` from the client capabilities (`:314`), calls `loadCompletionConfig(connection)` in `onInitialized` (`:358`), and reloads / `invalidateCompletionConfig()` in `onDidChangeConfiguration` (`:364`). Probe — replay the auto-import scenario with a client `workspace/configuration` responder:

```
autoImport=true  → { configRequests: 2, withImports: 1 }   // server queried config; import edit present
autoImport=false → { configRequests: 2, withImports: 0 }   // import edit suppressed — setting honoured
```

The regression is gone: the server reads the setting and `modeler.completion.autoImport: false` removes the auto-import `additionalTextEdits`.

### F2 — lint clean ✅
`pnpm --filter @modeler/lsp lint` exits 0. The unused imports (`loadCompletionConfig` now used; `Location`, `PackageDecl`, `parseString` removed) are gone.

### F4 — outline test asserts the attribute level ✅
`document-symbol.test.ts` now asserts `artikl` has a child `kod` with `kind === SymbolKind.Field` (`:99-102`), matching the spec's Tests-first.

### F5 — workspace-symbol package filter is precise ✅
`onWorkspaceSymbol` now matches `qnameLower.startsWith(prefix + '.')` (`server.ts:712`), so `"billing."` no longer also matches a sibling package like `billingsystem.*`.

---

## Remaining

### F1.5 [Med] — `preselectFullyQualified` is still neither honoured nor deferred
The setting is documented (H3.7) and loaded into the config, but **nothing consumes it** — no `preselect` logic anywhere in `packages/lsp/src`. H3's DONE says "Both completion settings are honoured." `autoImport` is; `preselectFullyQualified` is not. 055-F1.5 offered two ways out (wire it, or defer-and-document); neither was taken. Pick one.

### F3 [Med] — no end-to-end test for the config wiring
`config-completion.test.ts`'s "autoImport: false → no `additionalTextEdits`" test is **unchanged** — it still calls `loadCompletionConfig` directly and asserts the cached flag, never issuing a completion. So the behaviour I verified by hand has **no automated guard**. Given the wiring already regressed once (and the green suite hid it), this is worth closing: add a server-level test that drives a real completion with the config flipped (the probe above is the skeleton; ~30 lines).

---

## Recommendation

The substance of H3 is done and verified — outline, workspace symbols, package-scoped queries, and `autoImport` all work, and the package is lint-clean. Two small things stand between this and a clean "done": honour-or-defer `preselectFullyQualified` (F1.5) and add the end-to-end config test (F3). Both are quick. `tasks-review-056.md` has the steps. If you'd rather ship and track these as follow-ups, that's defensible — they don't block the I sub-phase functionally (rename only needs workspace symbols, which work), but DONE isn't fully met until F1.5 is resolved.

---

## Resolution (2026-05-25)

Both remaining items closed directly. **H3 is now done.**

- **F3** ✅ Added `packages/lsp/src/__tests__/completion-config.test.ts` — a server-level end-to-end test that drives a real cross-package reference completion with a client `workspace/configuration` responder: `autoImport=true` ⇒ the server queries config and an item carries an auto-import edit; `autoImport=false` ⇒ no `additionalTextEdits`. Also retitled the misleading `config-completion.test.ts` case to "caches autoImport: false from workspace/configuration" (it only asserts the cache) and pointed it at the new e2e test.
- **F1.5** ⏭️ Deferred explicitly (Option B). `preselectFullyQualified` doesn't fit the current single-item-per-symbol completion model (no FQN/bare pair to disambiguate); honouring it needs a dual-item model. Documented in `H3-symbols-settings.md` DONE and as a comment on `CompletionConfig.preselectFullyQualified`. The setting stays documented/loaded, reserved for that future model.

Gate: lsp **91** · integration **88 (+1 skip)** · vscode-ext **24** · lint clean · typecheck 8/8.
