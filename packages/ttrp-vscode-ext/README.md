# TTR-P (VS Code)

Language support for **TTR-P** (the Tatrman processing language): live diagnostics,
hover (types + er provenance), go-to-definition, SSA-aware rename, format-on-save, and
Build / Run / Explain commands — all served by the single Kotlin LSP
(`packages/kotlin/ttrp-lsp`) over stdio.

## Why a second extension

This is a **new** package, not an extension of `packages/vscode-ext` (the TTR-M shim).
The TTR-M shim launches a *Node* LSP via a module transport; TTR-P launches a *JVM*
process (different lifecycle, settings, packaging). The two languages version and ship
independently (S6). "One LSP across hosts" (architecture §6) is per-family, not
per-editor-extension. A marketplace merge, if ever wanted, is a post-v1 packaging
question.

## Running (dev)

1. Build the server launcher:
   `./gradlew :packages:kotlin:ttrp-lsp:installDist`
2. Open this folder in VS Code and press **F5** (Extension Development Host).
3. Open any `.ttrp` file → live diagnostics; edit → they update; **Format Document**
   reflows chains but leaves `"""sql` interiors byte-identical.

Server resolution order: `ttrp.server.path` setting → the `installDist` launcher under
the monorepo → the bundled server in a packaged `.vsix`. Format-on-save works via the
standard `editor.formatOnSave` (the server returns no edits when already canonical).

## Grammar

`syntaxes/ttrp.tmLanguage.json` is **generated** from `packages/grammar/src/TTRP.g4`:
`pnpm run regen-tmgrammar`. The bare-fragment grammars (`ttr-sql`, `ttr-pandas`) are
hand-written thin wrappers that delegate to `source.sql` / `source.python`.
