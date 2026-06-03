# Contributing to Tatrman Modeler

## Workspace Structure

This is a pnpm monorepo with the following structure:

```
modeler/
├── packages/
│   ├── grammar/      # TTR.g4 grammar (canonical source)
│   ├── parser/      # TypeScript parser generated from grammar
│   ├── semantics/   # Symbol table, resolver, validator (Phase 2+)
│   ├── edit/        # WorkspaceEdit synthesizer (v1.1+)
│   ├── lsp/         # LSP server (stdio + browser transports)
│   ├── vscode-ext/ # VS Code extension
│   └── designer/   # React-based graphical designer
├── tests/
│   └── integration/ # Cross-package integration tests
├── samples/         # Sample .ttr files
└── docs/           # Design and plan documents
```

## Adding a New Package

1. Create the package under `packages/<name>/`
2. Add to `pnpm-workspace.yaml` if needed (packages/* is already listed)
3. Use `@modeler/<name>` as the package name
4. Extend `../../tsconfig.base.json` in its `tsconfig.json`
5. Add workspace dependencies using `workspace:*`

## Package Conventions

### package.json

```json
{
  "name": "@modeler/<name>",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "build": "tsc",
    "typecheck": "tsc --noEmit",
    "test": "vitest run"
  }
}
```

### tsconfig.json

```json
{
  "extends": "../../tsconfig.base.json",
  "compilerOptions": {
    "outDir": "./dist",
    "rootDir": "./src"
  },
  "include": ["src/**/*"]
}
```

## Test Conventions

- Use Vitest for all test files
- Test files: `src/__tests__/*.test.ts`
- Run tests: `pnpm test` in package dir, or `pnpm -r test` for all

## Building

All packages are built via `pnpm -r build` from the root. Each package's build script should compile TypeScript to `dist/`.

## Grammar Changes

If you modify `packages/grammar/src/TTR.g4`:

1. Regenerate the TypeScript parser:
   ```bash
   cd packages/parser
   pnpm run prebuild
   ```

2. Regenerate TextMate grammar for VS Code:
   ```bash
   cd packages/vscode-ext
   node scripts/generate-tm-grammar.ts
   ```

3. Commit the generated files along with the grammar changes

## TypeScript Configuration

The base `tsconfig.base.json` enforces:
- Strict mode
- ES2022 target
- Node16 module resolution
- ES module interop

## Commit Style

Use clear commit messages that reference the section:
```
Section X: <description>

- <change 1>
- <change 2>
```