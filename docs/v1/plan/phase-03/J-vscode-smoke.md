# Phase 3.J — VS Code `@vscode/test-electron` smoke test

**Goal:** boot a real VS Code instance via `@vscode/test-electron`, open `samples/v1-metadata/er.ttr`, assert that Phase-1 highlighting + Phase-2 navigation + diagnostics work end-to-end. CI runs the test on Linux under `xvfb-run`; macOS/Windows are out of scope for v1.

**Reads:** `docs/plan/progress-phase-02.md` → §M "Deferred" entry; `packages/vscode-ext/package.json`.
**Blocked by:** Pre-flight only — parallel-safe with §A.
**Blocks:** the Phase-3 acceptance criteria call this smoke green; if J is incomplete, Phase 3 cannot be merged.

## Tests-first

This section *is* the test. The mini-list defines the test cases the smoke runner asserts; the implementation tasks below put the harness together.

Test cases the runner asserts:

- [ ] **TC1 — language detection.** After `vscode.workspace.openTextDocument(<sampleUri>)` + `vscode.window.showTextDocument(doc)`, `vscode.window.activeTextEditor.document.languageId === 'ttr'`.
- [ ] **TC2 — clean diagnostics on a known-good sample.** Wait up to 5 s; `vscode.languages.getDiagnostics(doc.uri).filter(d => d.severity === vscode.DiagnosticSeverity.Error).length === 0`.
- [ ] **TC3 — go-to-definition.** Position cursor inside an attribute reference (e.g. `nameAttribute: id_artiklu` in `er.ttr`); run `await vscode.commands.executeCommand('editor.action.revealDefinition')`; assert `vscode.window.activeTextEditor!.selection.active.line` matches the def's line.
- [ ] **TC4 — unresolved reference produces the right code.** Insert `nameAttribute: nonexistent_attribute` at a known position via a `WorkspaceEdit`; wait for diagnostics; assert ≥1 diagnostic with `code === 'ttr/unresolved-reference'`. Then revert; assert diagnostics clear.
- [ ] **TC5 — workspace symbols.** `await vscode.commands.executeCommand<vscode.SymbolInformation[]>('vscode.executeWorkspaceSymbolProvider', 'art')` returns ≥1 result whose `name` includes `'artikl'`.

## Library reference

Run Context7 before scaffolding:

```
mcp__context7__resolve-library-id { libraryName: "@vscode/test-electron", query: "runTests options, extensionDevelopmentPath, extensionTestsPath, launchArgs, version" }
mcp__context7__query-docs        { libraryId: "<id>", query: "runTests host script Mocha tests inside the VS Code instance with extensionDevelopmentPath and extensionTestsPath" }
```

**Library reference (training-time approximation; verify above):**

```ts
import * as path from 'node:path';
import { runTests } from '@vscode/test-electron';

async function main() {
  const extensionDevelopmentPath = path.resolve(__dirname, '../');
  const extensionTestsPath = path.resolve(__dirname, './suite/index.js');   // bundled Mocha entry
  await runTests({
    version: '1.96.0',                       // pin specific VS Code version
    extensionDevelopmentPath,
    extensionTestsPath,
    launchArgs: [path.resolve(__dirname, '../../../samples/v1-metadata')],
  });
}

main().catch((err) => { console.error(err); process.exit(1); });
```

The Mocha entry (`suite/index.js`) is a small adapter that gathers `*.test.js` files and runs them in the VS Code host's main process. The actual smoke assertions live in `suite/extension.smoke.test.ts`.

## Implementation tasks

- [ ] **J.1 — devDependencies.** `pnpm --filter @modeler/vscode-ext add -D @vscode/test-electron mocha glob @types/mocha @types/glob`. Pin versions explicitly in `package.json`.
- [ ] **J.2 — Test harness scaffold.** Create `packages/vscode-ext/src/test/runTests.ts` (the runner) and `packages/vscode-ext/src/test/suite/index.ts` (the Mocha entry). Build them into `dist/test/` via the existing tsc/esbuild build (or add a small build script for the test entry).
- [ ] **J.3 — Smoke test.** Create `packages/vscode-ext/src/test/suite/extension.smoke.test.ts` asserting TC1–TC5 above. Use `vscode-test`'s `before` hooks to wait for the LSP to fully initialize (poll until diagnostics for the open sample have stabilized).
- [ ] **J.4 — `test:smoke` script.** Add to `packages/vscode-ext/package.json`: `"test:smoke": "pnpm --filter @modeler/vscode-ext build && node ./dist/test/runTests.js"`. The build step is required because the bundled LSP must exist on disk before the test electron host boots.
- [ ] **J.5 — CI job.** Update `.github/workflows/ci.yml` with a new `vscode-smoke` job: Ubuntu runner, `xvfb-run -a pnpm --filter @modeler/vscode-ext test:smoke`. Skip on macOS / Windows for v1. Document the local-run command in `packages/vscode-ext/README.md`.
- [ ] **J.6 — Phase 2 progress doc.** Tick `progress-phase-02.md` §M with `Completed in Phase 3.J` + date.

## Verify by running

```bash
pnpm --filter @modeler/vscode-ext build
pnpm --filter @modeler/vscode-ext test:smoke

# On a Linux CI runner:
xvfb-run -a pnpm --filter @modeler/vscode-ext test:smoke
```

Test output should show all five test cases passing; the VS Code window briefly appears and closes.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] All five smoke test cases pass locally.
- [ ] The CI `vscode-smoke` job runs and passes at least once on a PR.
- [ ] Phase 2 §M line is updated to "completed in Phase 3.J".
