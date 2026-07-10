# Review 055 — Section H3 (document outline, workspace symbols, completion settings)

**Date:** 2026-05-25
**Scope:** review of Section 1.1.H.3 against [`H3-symbols-settings.md`](../plan/tasks/H3-symbols-settings.md). Verified against runtime:

- `pnpm --filter @modeler/lsp test` → **88 passed** (incl. `document-symbol` 3, `config-completion` 4).
- `pnpm --filter @modeler/integration-tests test` → **88 passed, 1 skipped** (incl. `workspace-symbol-v1.1` 3).
- `pnpm --filter @modeler/vscode-ext test` → **24 passed**.
- `pnpm -r typecheck` → clean (8/8).
- `pnpm --filter @modeler/lsp lint` → **FAILS, 5 errors** (see F2).
- A runtime probe of the completion-config path (see F1).

Companion: [`tasks-review-055.md`](tasks-review-055.md).

**Verdict:** **Not done.** The *symbols* half is solid — document outline (H3.1/H3.2), full-qname workspace symbols (H3.3), per-package query mode (H3.4), and the documented settings (H3.7) all work and are verified. But the *settings* half is **functionally dead**: `loadCompletionConfig` is never called and there's no `didChangeConfiguration` handler, so the cached config is always the default and `modeler.completion.autoImport` is **never read from the client** (proven: the server issues **zero** `workspace/configuration` requests). This is a **regression** — the pre-H3 completion handler honoured `autoImport` via an inline per-request fetch, which H3 removed and replaced with a cache that nothing fills. The `config-completion` test passes only because it exercises the helper in isolation, never a real completion. Plus the package **fails lint**.

---

## High — blockers

### F1 [High] — Completion settings are never read from the client (regression; verified)
`server.ts:54` imports `loadCompletionConfig` but **never calls it**, and there is **no `connection.onDidChangeConfiguration`**. The completion handler reads `getCompletionConfig()` (`server.ts:805`), which returns the module-level `cachedConfig` — only ever written by `loadCompletionConfig`/`invalidateCompletionConfig`, neither of which the server invokes. So `getCompletionConfig()` always returns `{ autoImport: true, preselectFullyQualified: false }`.

Probe — boot the server, register a client `workspace/configuration` responder, `initialize` (with `workspace.configuration: true`), open a reference-completion scenario, request completion:

```
PROBE configRequests = 0
→ server never asked the client for modeler.completion.* — the user setting is unreachable
```

Consequences:
- A user who sets `modeler.completion.autoImport: false` still gets auto-import `additionalTextEdits` (`autoImport` stays `true`). DONE's "both completion settings are honoured" is unmet.
- **Regression:** before H3 the `onCompletion` handler fetched `modeler.completion.autoImport` via `workspace/configuration` per request and honoured it. H3 deleted that and wired a cache that is never loaded.
- H3.5's "invalidated on `workspace/didChangeConfiguration`" is absent entirely.

The fix is to actually load the config (on `initialized`, when the client advertises `workspace.configuration`) and reload/invalidate on `didChangeConfiguration`. Also confirm `preselectFullyQualified` is honoured somewhere in completion ordering — I find no consumer of it, so that half of "both settings honoured" is also unmet.

### F2 [High] — `pnpm --filter @modeler/lsp lint` fails (5 errors)
`@typescript-eslint/no-unused-vars`:
- `loadCompletionConfig` — `server.ts:54` (the dead import from F1; lint flags it directly).
- `Location` — `document-symbol.ts:5`.
- `PackageDecl` — `document-symbol.ts:7`.
- `parseString` — `document-symbol.test.ts:5`.
- `ConfigurationItem` — flagged in the config path; remove if unused after F1's fix or keep if `fetchCompletionConfig` retains it.

`typecheck` passes because `noUnusedLocals` is off, but `lint` is a project gate (see CLAUDE.md). H3 can't be "done" with the package red.

---

## Medium

### F3 [Med] — The `config-completion` test asserts the helper, not the behaviour
`config-completion.test.ts`'s "when user sets `autoImport: false`, subsequent completion results have no `additionalTextEdits`" never issues a completion — it calls `loadCompletionConfig(mockConnection)` directly and asserts the cached flag. So its title claims a behaviour it doesn't test, and (like the F1 bug it should have caught) it can't see that the server never loads the config. Replace it with a server-level test: `initialize` with a client `workspace/configuration` responder returning `false`, drive a reference completion at an unimported symbol, assert the returned items have **no** `additionalTextEdits`. (The probe in F1 is the skeleton.)

---

## Low

- **F4** — `document-symbol.test.ts` case 1 is titled "package → schema → defs → **properties**" but only asserts down to the entity (`Class`); it never checks the attribute (`Field`) child the spec's Tests-first requires ("each with attribute children (kind: Field)"). Add the assertion (the impl does emit them via `nestedDefKinds`).
- **F5** — `onWorkspaceSymbol` package filter uses `qname.startsWith(prefix)` where `prefix` is the query minus its trailing `.` (so `"billing."` → `"billing"`). That also matches a sibling package like `billingsystem.*`. Match `prefix + '.'` (or exact-package) to scope precisely.
- **F6** — `document-symbol.ts` graph branch gives every object child a `{0,0}` range/selectionRange and the graph's own `selectionRange` is approximated from `graph.source`. Fine for v1; revisit if outline navigation needs accurate ranges.

---

## What's good (verified)

- **Document outline (H3.1/H3.2)** — `buildDocumentSymbols` produces the spec tree: `package` (Package) → `schema.ns` (Namespace) → defs (Class/Field/Interface/…) → nested fields; no-package files root at the schema directive; `.ttrg` roots at the graph (File) with object children. Wired via `onDocumentSymbol` + `documentSymbolProvider: true`. 3 unit cases green.
- **Workspace symbols (H3.3/H3.4)** — full package-qualified qnames returned; `query.endsWith('.')` enables package-scoped filtering. Integration test confirms `artikl`, `billing.`, and `billing.invoicing.` queries at runtime. The fuzzy + kind-boost path is intact.
- **Settings documented (H3.7)** — `modeler.completion.autoImport` and `modeler.completion.preselectFullyQualified` are in `vscode-ext/package.json` `contributes.configuration` with types, defaults, and descriptions.

---

## Recommendation

Land the symbols work as-is. To finish H3: (F1) load the completion config on `initialized` when the client supports `workspace.configuration`, and reload/invalidate on `onDidChangeConfiguration`, so `autoImport` (and `preselectFullyQualified`) actually reach completion; (F2) clear the 5 lint errors; (F3) make the config test drive a real completion so the wiring can't silently rot again. Then the Low items. `tasks-review-055.md` has exact steps.
