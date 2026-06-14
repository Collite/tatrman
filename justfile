# Tatrman Modeler — task runner
# Run `just` to list recipes.

set shell := ["bash", "-uc"]

# pnpm-aware repo paths
vscode_ext := "packages/vscode-ext"
vsix_out   := "packages/vscode-ext/ttr-modeler-vsc.vsix"
ghblob     := "https://github.com/Collite/modeler/blob/master/packages/vscode-ext"
ghraw      := "https://raw.githubusercontent.com/Collite/modeler/master/packages/vscode-ext"

# List available recipes
default:
    @just --list

# Package the VS Code extension into a single self-contained .vsix.
#
# vsce can't follow pnpm's symlinked node_modules, so instead of shipping the
# dependency tree we inline everything into self-contained bundles and package
# with `--no-dependencies`. Steps:
#   1. build @modeler/lsp and its workspace deps (parser/semantics/edit/...).
#   2. compile the extension's own TypeScript (typecheck + .d.ts).
#   3. bundle the extension entry (inlines vscode-languageclient) → dist/extension.js.
#   4. bundle a fully self-contained LSP server (all deps inlined) to
#      dist/server/server-stdio.mjs — what extension.ts loads when packaged.
#   5. vsce package --no-dependencies (tree is self-contained; skip pnpm).
vscode:
    pnpm --filter @modeler/lsp... build
    pnpm --filter ttr-modeler-vsc run build
    just _bundle-extension
    just _bundle-lsp-server
    cd {{vscode_ext}} && vsce package --no-dependencies \
        --baseContentUrl {{ghblob}} \
        --baseImagesUrl {{ghraw}} \
        --out ttr-modeler-vsc.vsix
    @echo "✓ Packaged {{vsix_out}}"

# Bundle the extension entry into a self-contained CJS file. The `tsc` build
# above emits `dist/extension.js` with a bare `require("vscode-languageclient")`,
# but `vsce package --no-dependencies` ships no node_modules — so that require
# would throw at activation and the language client (and its output channel)
# would never start. Re-emit `dist/extension.js` with the client inlined; only
# `vscode` stays external (the editor provides it at runtime).
_bundle-extension:
    pnpm --filter @modeler/lsp exec esbuild "$PWD/{{vscode_ext}}/src/extension.ts" \
        --bundle --platform=node --format=cjs --target=es2022 \
        --external:vscode \
        --outfile="$PWD/{{vscode_ext}}/dist/extension.js"

# Inline the LSP server (all @modeler/* + antlr4ng + vscode-languageserver) into
# one ESM file the packaged extension loads directly — no node_modules needed.
# Output stays ESM (the server uses import.meta.url); the createRequire banner
# lets the bundled CJS deps require() Node builtins.
#
# The server reads stock vocabulary (.ttr) from disk at runtime via
# @modeler/semantics' stock-loader, whose first search path is `<dir>/stock/`
# relative to the server file. esbuild can't inline those data files, so copy
# them next to the bundle — without them, all `cnc.role.*` references go
# unresolved in the packaged extension.
_bundle-lsp-server:
    mkdir -p {{vscode_ext}}/dist/server/stock
    pnpm --filter @modeler/lsp exec esbuild src/server-stdio.ts \
        --bundle --platform=node --format=esm --target=es2022 \
        --external:vscode \
        --banner:js="import{createRequire as ___cr}from'node:module';const require=___cr(import.meta.url);" \
        --outfile="$PWD/{{vscode_ext}}/dist/server/server-stdio.mjs"
    cp packages/semantics/src/stock/*.ttr {{vscode_ext}}/dist/server/stock/
