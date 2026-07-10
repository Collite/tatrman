# Tasks — Review 025 (Section A follow-ups)

Companion task list to [`review-025.md`](review-025.md). Work through these in order; each box is intentionally small and explicit. Verification commands are listed inline so you can confirm each step before moving on.

The whole list is finishable in well under a day.

---

## Block 1 — Fix the broken TextMate generator test (Finding 1)

The vscode-ext test suite currently fails on module load. We need to (a) restore robust script-path resolution, and (b) prevent the script body from running when the module is imported.

- [ ] **1.1 — Restore `import.meta.url`-based path resolution in `packages/vscode-ext/scripts/generate-tm-grammar.ts`.**

  Replace lines 5–10 (currently `process.argv[1]`-based) with:

  ```ts
  import { fileURLToPath } from 'url';

  const __filename   = fileURLToPath(import.meta.url);
  const __dirname    = path.dirname(__filename);
  const monorepoRoot = path.resolve(__dirname, '..', '..', '..');

  const GRAMMAR_PATH = path.join(monorepoRoot, 'packages', 'grammar', 'src', 'TTR.g4');
  const OUTPUT_PATH  = path.join(__dirname, '..', 'syntaxes', 'ttr.tmLanguage.json');
  ```

  Add the missing `import { fileURLToPath } from 'url';` at the top of the file.

- [ ] **1.2 — Wrap the module-level side effect in a guarded `main()` in the same file.**

  Lines 289–~end currently read the grammar, build the JSON, and write the output file at top level. Move that block into a function:

  ```ts
  function main(): void {
    const g4Content = fs.readFileSync(GRAMMAR_PATH, 'utf-8');
    const tokens    = parseGrammar(g4Content);
    const grammar   = buildGrammar(tokens);
    fs.writeFileSync(OUTPUT_PATH, JSON.stringify(grammar, null, 2));
    console.log(`Wrote ${OUTPUT_PATH}`);   // keep whatever log line existed before, if any
  }

  if (process.argv[1] === __filename) {
    main();
  }
  ```

  This makes the file safe to `import` from a test without firing any I/O.

- [ ] **1.3 — Regenerate the committed `.js` artifact.**

  Run:

  ```bash
  pnpm --filter @modeler/vscode-ext run build-generator
  ```

  This invokes `tsc scripts/generate-tm-grammar.ts --outDir scripts --module nodenext --target es2022 --moduleResolution nodenext`. Verify the new `.js` contains a `function main()` and an `if (process.argv[1] === __filename) { main(); }` block, and that it no longer reads `TTR.g4` at top level.

  > Note: because the package is `"type": "commonjs"`, the emitted file should use `require`. That's fine — both `require.main === module` and `process.argv[1] === __filename` are valid guards in CJS; the tsc output of the ESM source will compile the `import.meta.url` line into a CJS equivalent. Sanity-check the emitted file before committing.

- [ ] **1.4 — Run the regen end-to-end and confirm the JSON output is byte-identical.**

  ```bash
  pnpm --filter @modeler/vscode-ext run regen-tmgrammar
  git diff packages/vscode-ext/syntaxes/ttr.tmLanguage.json
  ```

  Expect an empty diff (the rewrite changed *how* paths are derived, not *what* gets emitted).

- [ ] **1.5 — Run the vscode-ext test suite. It must now pass.**

  ```bash
  pnpm --filter @modeler/vscode-ext test
  ```

  Expected: ≥1 test file, all cases green, no `ENOENT`. Do not move on until this is the case.

---

## Block 2 — Add the missing parser test file (Finding 2)

Task A's "Tests-first" demands `packages/parser/src/__tests__/grammar-v2.test.ts` with four specific cases. Create it now.

- [ ] **2.1 — Create `packages/parser/src/__tests__/grammar-v2.test.ts`.**

  Use exactly the four cases from `docs/v1-1/plan/tasks/A-grammar.md` ("Tests-first" section). Skeleton:

  ```ts
  import { describe, it, expect } from 'vitest';
  import { parseString } from '../index.js';

  describe('grammar v1.1 — package / import / graph', () => {
    it('parses a package declaration', () => {
      const result = parseString(
        'package billing.invoicing\n' +
        'schema er namespace entity\n' +
        'def entity X {}\n'
      );
      expect(result.errors).toEqual([]);
      // The AST walker doesn't know packageDecl yet (B1), so probe the parse tree:
      // result.parseTree should contain a packageDecl rule node with name "billing.invoicing".
    });

    it('parses named and wildcard imports', () => {
      const result = parseString(
        'package a.b\n' +
        'import x.y.*\n' +
        'import p.q.r.S\n' +
        'schema er namespace entity\n'
      );
      expect(result.errors).toEqual([]);
      // Probe parse tree for two importDecl nodes; first has DOT STAR, second does not.
    });

    it('parses a graph block', () => {
      const result = parseString(
        'package a.b\n' +
        'graph my_view { schema: er, objects: [a.b.er.entity.X] }\n'
      );
      expect(result.errors).toEqual([]);
      // Probe parse tree for graphBlock node with id "my_view", graphSchemaProperty "er",
      // and graphObjectsProperty containing one id "a.b.er.entity.X".
    });

    it('every existing v1 sample still parses without errors', async () => {
      const fg = await import('fast-glob');
      const fs = await import('fs');
      const path = await import('path');
      const samples = await fg.default([
        'samples/v1-metadata/*.ttr',
        'samples/v1-mini/*.ttr',
        'samples/builtin/*.ttr',
      ], { cwd: path.resolve(__dirname, '../../../..'), absolute: true });
      expect(samples.length).toBeGreaterThan(0);
      for (const f of samples) {
        const src = fs.readFileSync(f, 'utf-8');
        const r = parseString(src);
        expect(r.errors, `${f} should parse cleanly`).toEqual([]);
      }
    });
  });
  ```

  > **Notes for the implementer.**
  > - The exact `parseString` import path may differ — copy whatever `parser.test.ts` already uses; do not invent.
  > - The "probe parse tree" comments above are pseudocode. Look at how `parser.test.ts` walks `result.parseTree` (or whichever field exists) and use the same idiom. Acceptable minimum: assert `result.errors.length === 0` for all four cases and that the source text round-trips through the parser without rejection. The richer parse-tree assertions are nice-to-have for v1.1.B1's sake but not strictly required by Task A.
  > - If your project doesn't have `fast-glob`, swap to `fs.readdirSync` over the three sample directories. The point is: every existing v1 `.ttr` sample must parse with `errors === []`.

- [ ] **2.2 — Run the new test file in isolation, then the whole parser suite.**

  ```bash
  pnpm --filter @modeler/parser test -- grammar-v2
  pnpm --filter @modeler/parser test
  ```

  Both must exit 0. If a v1 sample fails the re-parse case, that is a real regression — stop and investigate before touching anything else.

---

## Block 3 — Extend the TextMate generator test (Finding 3)

After Block 1 the test file loads cleanly. Now make it actually assert the v1.1 scopes.

- [ ] **3.1 — Add the three new scopes to `EXPECTED_SCOPES` in `packages/vscode-ext/scripts/__tests__/generate-tm-grammar.test.ts`.**

  Edit lines 11–25, adding three entries:

  ```ts
  const EXPECTED_SCOPES = [
    'keyword.control.def.ttr',
    'keyword.control.package.ttr',       // NEW (v1.1)
    'keyword.control.import.ttr',        // NEW (v1.1)
    'keyword.declaration.graph.ttr',     // NEW (v1.1)
    'keyword.other.schema.ttr',
    'keyword.other.kind.ttr',
    'keyword.other.property.ttr',
    'support.type.primitive.ttr',
    'constant.language.ttr',
    'constant.language.indextype.ttr',
    'constant.language.constrainttype.ttr',
    'constant.language.querylang.ttr',
    'punctuation.separator.ttr',
    'punctuation.section.braces.ttr',
    'punctuation.section.brackets.ttr',
    'punctuation.section.parens.ttr',
  ];
  ```

  The existing assertion `expect(byScope.get(scope) ?? 0).toBeGreaterThan(0)` already enforces that each of these scopes has at least one token mapping; you don't need to add a separate test.

- [ ] **3.2 — (Optional but recommended) add a small targeted assertion.**

  Append to the same `describe` block:

  ```ts
  it('v1.1 keywords map to dedicated scopes', () => {
    expect(tokenToScope('PACKAGE', 'package')).toBe('keyword.control.package.ttr');
    expect(tokenToScope('IMPORT',  'import')).toBe('keyword.control.import.ttr');
    expect(tokenToScope('GRAPH',   'graph')).toBe('keyword.declaration.graph.ttr');
    expect(tokenToScope('OBJECTS', 'objects')).toBe('keyword.other.property.ttr');
    expect(tokenToScope('LAYOUT',  'layout')).toBe('keyword.other.property.ttr');
  });
  ```

  This makes future regressions in `tokenToScope` fail loudly instead of being silently swallowed by the broader scope-count check.

- [ ] **3.3 — Run the vscode-ext suite.**

  ```bash
  pnpm --filter @modeler/vscode-ext test
  ```

  All green.

---

## Block 4 — Hygiene (Finding 4)

- [ ] **4.1 — Add a top-of-file comment to `packages/vscode-ext/scripts/generate-tm-grammar.ts` noting that the `.js` next to it is tsc-emitted and should not be hand-edited.**

  One line at the very top, after the shebang:

  ```ts
  // The sibling .js is emitted by `pnpm run build-generator`. Do not edit the .js by hand.
  ```

- [ ] **4.2 — When you commit the work for Section A, include this restructure explicitly in the commit message.**

  Suggested message body (use whatever phrasing you prefer, but make the restructure visible):

  ```
  Section A: v1.1 grammar additions (review-025 follow-ups)

  - Add package / import / graph parser rules + lexer tokens (per contracts §1).
  - Add grammar-v2.test.ts covering the new productions and v1-sample re-parse.
  - TextMate generator: switch .js to tsc-emitted CommonJS via build-generator;
    fix script-path resolution to use import.meta.url; guard main() so the file
    is safe to import from tests.
  - Extend generate-tm-grammar.test.ts to cover the three new v1.1 scopes.
  ```

---

## Block 5 — Final verification

Before flipping STATUS.md, run the full ladder.

- [ ] **5.1 — Per-package.**

  ```bash
  pnpm --filter @modeler/grammar test          # if the package has tests; currently it does not
  pnpm --filter @modeler/parser test           # expect 38+ tests, all green
  pnpm --filter @modeler/vscode-ext test       # expect all green incl. the three new scope assertions
  ```

- [ ] **5.2 — Whole workspace.**

  ```bash
  pnpm -r typecheck
  pnpm -r build
  pnpm -r test
  ```

  All four must exit 0.

- [ ] **5.3 — Integration tests (sanity).**

  ```bash
  pnpm --filter @modeler/integration-tests test
  ```

  Still 29/29. Section A doesn't touch the LSP, but a green run here is the cheapest confirmation that the parser's behaviour on real fixtures didn't shift.

- [ ] **5.4 — Update STATUS.md.**

  Change `[x] A grammar — under review` to `[x] A grammar`. Do **not** start B1 until this is committed and pushed.

---

## Out of scope

- AST additions for `packageDecl` / `importDecl` / `graphBlock` — that is task B1 (`docs/v1-1/plan/tasks/B1-ast-extension.md`). Leave the AST walker untouched.
- Validator diagnostics (`ttr/wrong-file-kind`, `ttr/file-ordering`, etc.) — those land with B4.
- `.ttrg` separate language registration in `vscode-ext/package.json` — that is task D.

If you find yourself editing `packages/parser/src/walker.ts`, `packages/semantics/`, or `packages/vscode-ext/package.json`'s `contributes.languages`, stop and re-read the task scope.
