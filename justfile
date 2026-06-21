# Tatrman Modeler — task runner
# Run `just` to list recipes.

set shell := ["bash", "-uc"]

# pnpm-aware repo paths
vscode_ext   := "packages/vscode-ext"
vsix_out     := "packages/vscode-ext/ttr-modeler-vsc.vsix"
ghblob       := "https://github.com/Collite/modeler/blob/master/packages/vscode-ext"
ghraw        := "https://raw.githubusercontent.com/Collite/modeler/master/packages/vscode-ext"
# IntelliJ plugin resource root (server bundle + grammars land here; gitignored)
intellij_res := "intellij-plugin/src/main/resources"

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
_bundle-lsp-server server_dir=(vscode_ext / "dist/server"):
    mkdir -p {{server_dir}}/stock
    pnpm --filter @modeler/lsp exec esbuild src/server-stdio.ts \
        --bundle --platform=node --format=esm --target=es2022 \
        --external:vscode \
        --banner:js="import{createRequire as ___cr}from'node:module';const require=___cr(import.meta.url);" \
        --outfile="$PWD/{{server_dir}}/server-stdio.mjs"
    cp packages/semantics/src/stock/*.ttr {{server_dir}}/stock/

# Package the IntelliJ IDEA plugin into a single self-contained .zip.
#
# The plugin is a thin LSP4IJ launcher around the SAME fully-inlined LSP server
# the .vsix ships (server-stdio.mjs + stock/*.ttr). Steps:
#   1. build @modeler/lsp and its workspace deps.
#   2. bundle the inlined server (all deps inlined; only `vscode` external) into
#      the plugin's resources — this is the build input the Gradle build verifies.
#   3. gradle buildPlugin — copyLspBundle pulls in both TextMate grammars, then
#      the server + grammars are shipped UNPACKED in the plugin home (node runs
#      the .mjs from disk; the TextMate engine reads the grammars from a dir).
# Build order matters: the bundle step MUST precede gradle (copyLspBundle fails
# fast if server-stdio.mjs is absent). See docs/features/intellij/.
intellij:
    pnpm --filter @modeler/lsp... build
    just _bundle-lsp-server {{intellij_res}}/server
    cd intellij-plugin && ./gradlew buildPlugin
    @echo "✓ Packaged intellij-plugin/build/distributions/intellij-plugin-*.zip"

# Cut a release of the Kotlin artifacts (org.tatrman:ttr-parser / -writer /
# -semantics) for ai-platform to consume. Publishing is tag-driven: pushing
# `kotlin/v<x.y.z>` triggers .github/workflows/publish.yml, which builds and
# publishes all three modules to GitHub Packages. The version lives entirely in
# the tag (`-Pversion=${TAG##*/v}`) — there is no version file to edit.
#
# ⚠️  Published GitHub Packages versions are PERMANENT (they cannot be deleted),
# so this confirms before pushing and refuses a dirty tree. Make sure the build
# is green first (`just test` and the Kotlin suites).
#
# Usage:
#   just package              # patch bump   (0.5.0 -> 0.5.1)
#   just package minor        # minor bump   (0.5.0 -> 0.6.0)
#   just package major        # major bump   (0.5.0 -> 1.0.0)
#   just package set 0.6.0    # explicit version
#
# Cut a Kotlin release: tag kotlin/v<x.y.z> and push it (CI publishes the jars).
package level="patch" version="":
    #!/usr/bin/env bash
    set -euo pipefail

    LEVEL="{{level}}"
    CUSTOM_VERSION="{{version}}"
    PREFIX="kotlin"   # bundle tag → ttr-parser + ttr-writer + ttr-semantics

    case "$LEVEL" in
        major|minor|patch|set) ;;
        *) echo "❌ Level must be 'major', 'minor', 'patch', or 'set'."; exit 1 ;;
    esac
    if [ "$LEVEL" = "set" ] && [ -z "$CUSTOM_VERSION" ]; then
        echo "❌ 'set' requires a version. E.g. just package set 0.6.0"; exit 1
    fi

    # A release must come from a clean, committed state — CI checks out the tag,
    # and pushing the tag carries its commit to the remote.
    if [ -n "$(git status --porcelain)" ]; then
        echo "❌ Working tree is dirty — commit or stash before cutting a release."; exit 1
    fi

    BRANCH=$(git rev-parse --abbrev-ref HEAD)
    if [ "$BRANCH" != "master" ] && [ "$BRANCH" != "main" ]; then
        read -p "⚠️  On branch '$BRANCH', not master. Tag this commit anyway? [y/N] " -n 1 -r; echo ""
        [[ ${REPLY:-} =~ ^[Yy]$ ]] || { echo "❌ Aborting."; exit 1; }
    fi

    # Latest released version = highest strict X.Y.Z under kotlin/v* (skip pre-releases).
    LATEST=$(git tag -l "${PREFIX}/v*" | sed "s|^${PREFIX}/v||" | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)
    LATEST="${LATEST:-0.0.0}"

    if [ "$LEVEL" = "set" ]; then
        if ! [[ "$CUSTOM_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
            echo "❌ '$CUSTOM_VERSION' is not a valid semver (X.Y.Z)."; exit 1
        fi
        NEW_VERSION="$CUSTOM_VERSION"
    else
        IFS='.' read -r MAJOR MINOR PATCH <<< "$LATEST"
        case "$LEVEL" in
            major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
            minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
            patch) PATCH=$((PATCH + 1)) ;;
        esac
        NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
    fi

    NEW_TAG="${PREFIX}/v${NEW_VERSION}"
    if git rev-parse -q --verify "refs/tags/${NEW_TAG}" >/dev/null; then
        echo "❌ Tag ${NEW_TAG} already exists."; exit 1
    fi

    echo "────────────────────────────────────────────────────────────"
    echo "  Latest released : ${LATEST}"
    echo "  New version      : ${NEW_VERSION}   →  tag ${NEW_TAG}"
    echo "  Commit           : $(git rev-parse --short HEAD) on ${BRANCH}"
    echo "  Publishes        : org.tatrman:{ttr-parser, ttr-writer, ttr-semantics}:${NEW_VERSION}"
    echo "  ⚠️  GitHub Packages versions are PERMANENT — they cannot be deleted."
    echo "────────────────────────────────────────────────────────────"
    read -p "Create and push ${NEW_TAG}? [y/N] " -n 1 -r; echo ""
    [[ ${REPLY:-} =~ ^[Yy]$ ]] || { echo "❌ Aborting."; exit 1; }

    git tag -a "${NEW_TAG}" -m "Release ${NEW_VERSION}"
    git push origin "${NEW_TAG}"
    echo "✅ Pushed ${NEW_TAG} — publish.yml will build & publish to GitHub Packages."
    echo "   Watch it: gh run watch  (or the repo's Actions tab)"
