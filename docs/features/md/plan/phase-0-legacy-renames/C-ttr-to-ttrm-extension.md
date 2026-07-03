# Stage C — `.ttr` → `.ttrm` extension

Goal: rename the model file extension from `.ttr` to `.ttrm` ("Tatrman Model") everywhere — file
detection, host wiring, syntax highlighting, fixtures, and docs. `.ttrg` (graph) is **unchanged**.

This is the widest stage (many files), but mechanical. Do it **after** A & B so the mass rename
happens once over already-correct content. TDD: C1 before the rest.

References (verified file pointers — all the `.ttr` extension literals):
- `packages/vscode-ext/package.json` — `languages[0]`: id `"ttr"`, `extensions: [".ttr"]`,
  scopeName `source.ttr`, grammar path `./syntaxes/ttr.tmLanguage.json`, icons `./icons/ttr.svg`
  (≈ lines 48–87).
- `packages/vscode-ext/src/extension.ts` — file watcher `**/*.ttr` (≈ line 47) and
  `findFiles('**/*.ttr')` (≈ line 64).
- `packages/vscode-ext/scripts/generate-tm-grammar.ts` and `syntaxes/ttr.tmLanguage.json`
  (`scopeName: source.ttr`).
- `packages/format/src/cli.ts` — `.ttr`/`.ttrg` accept logic (≈ lines 12, 25, 79–90).
- `packages/migrate/src/index.ts` — `endsWith('.ttr')` (≈ line 282); `resolve-packages.ts`
  `isModelExt` (≈ line 49, already touched in B — re-confirm).
- `packages/designer/src/fs/file-system.ts` (≈ lines 13, 37), `App.tsx` (≈ lines 28, 164),
  `components/Header.tsx` (≈ lines 108–116), `CreateGraphWizard.tsx`.

---

- [x] **C1 — Tests first (red).**
  - Update unit tests that hardcode `.ttr` paths/globs (format CLI discovery, designer
    `isModelFile`, migrate file walk) to expect `.ttrm`.
  - Add a `vscode-ext` smoke assertion that the language `ttr` activates on a `.ttrm` document
    (`extension.smoke.test.ts` already references the language).
  - Decide the conformance-runner glob change (`*.ttr` → `*.ttrm`) and update it; tests should fail
    until fixtures are renamed in C5.

- [x] **C2 — VS Code extension contributions.** In `packages/vscode-ext/package.json`:
  - Change `languages[0].extensions` to `[".ttrm"]`. Keep the language **id** `"ttr"` (internal id;
    changing it churns more than needed) **or** rename to `"ttrm"` — pick one and apply consistently
    to `grammars[].language` and the LSP client `documentSelector`. Recommended: keep id `ttr`,
    change only the extension + scope.
  - Rename `scopeName` `source.ttr` → `source.ttrm`; rename `syntaxes/ttr.tmLanguage.json` →
    `ttrm.tmLanguage.json` and its `path`; rename `icons/ttr.svg` → `icons/ttrm.svg` (and the dark
    entry). Update `generate-tm-grammar.ts` output filename + `scopeName`, then re-run it.

- [x] **C3 — Host wiring.**
  - `extension.ts`: change both globs to `**/*.ttrm`; confirm the LSP `documentSelector` matches the
    chosen language id.
  - Check the LSP server (`packages/lsp/src/server*.ts`) for any `.ttr` extension assumptions in
    project-root/file-kind logic and update to `.ttrm`.

- [x] **C4 — Designer + format + migrate.**
  - `designer`: update `file-system.ts`, `App.tsx` `isModelFile`, `Header.tsx` `accept`, and any
    `.ttr` literal to `.ttrm` (leave `.ttrg` alone). Note: `.ttrl` literals here are dead (CLAUDE.md
    D4) — leave for Stage D cleanup.
  - `format/src/cli.ts`: `.ttr` → `.ttrm` in the accept + discovery + error messages.
  - `migrate/src/index.ts`: `endsWith('.ttr')` → `.ttrm`; confirm `isModelExt` (from B) lists
    `.ttrm`, `.ttrg`.

- [x] **C5 — Mass-rename fixtures + sample files.**
  - `git mv` every `*.ttr` fixture/sample to `*.ttrm`:
    `for f in $(rg --files --glob='*.ttr' --glob='!**/node_modules/**'); do git mv "$f" "${f%.ttr}.ttrm"; done`
  - Update any in-fixture or in-test string that references a sibling file by `.ttr` name (imports,
    expected paths, golden outputs).

- [x] **C6 — Docs.** Update `CLAUDE.md`, `docs/**`, and READMEs: every `.ttr` model-file reference
  → `.ttrm` (keep `.ttrg`). Update the "Testing the VS Code extension" instructions ("open any
  `.ttrm` file").

- [x] **C7 — Verify.**
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test`
  - `pnpm --filter @modeler/vscode-ext test` (smoke) and manual F5 sanity on a `.ttrm` file.
  - `rg -n "\.ttr\b" --glob='!**/node_modules/**' --glob='!**/.vscode-test/**' --glob='!CHANGELOG.md'`
    returns nothing (only `.ttrm`/`.ttrg` remain).

- [x] **C8 — Commit.** `Section Phase0-C: rename .ttr model extension → .ttrm`.
