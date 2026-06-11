# P1b — Comment-aware printing + `ttr fmt` CLI + LSP rewire

**Package:** `@modeler/format` · **Pre-flight:** P1a DONE · **Contracts:** §2.3, §7.2, §8

Goal: the formatter preserves comments (using P0 trivia), ships a `ttr fmt` CLI, and the LSP
formatting path is confirmed end-to-end.

Tick each box when done; commit as `Section P1b: <task>`.

---

- [ ] **1. (test) Comment-preservation cases.**
  In `packages/format/src/__tests__/comments.test.ts` assert: a leading comment above a `def`
  survives reformatting in the same position; a trailing comment on a property line stays trailing;
  a block comment survives; a comment is never duplicated; and every comment in input appears
  exactly once in output. Use inputs that are already canonically ordered so only comment handling
  is under test. These fail until task 3.

- [ ] **2. (test) Idempotency + semantics-preservation with comments.**
  Extend `formatter-samples.test.ts` corpus with commented samples and assert
  `format(format(x)) === format(x)` and that `parseString(format(x)).ast` matches
  `parseString(x).ast` modulo `source` spans (deep-equal with spans stripped).

- [ ] **3. Emit trivia in the printer.**
  In `printer.ts`, when building the `Doc` for each node, prepend its `leadingTrivia` comments
  (each on its own `hardline`) and append `trailingTrivia` comments after the node text on the same
  line. Respect existing indentation via the `indent` IR combinator. Keep whitespace/blank-line
  policy from the printer (WS is not trivia — §design 10.1). Make tasks 1–2 pass.

- [ ] **4. CLI bin scaffolding.**
  Create `packages/format/src/cli.ts` (`#!/usr/bin/env node`, commander@13, mirror
  `packages/migrate/src/cli.ts`). Add `"bin": { "modeler-fmt": "dist/cli.js" }` to
  `packages/format/package.json`. Command: `modeler-fmt <path>` with `--check` and `--write`
  (contracts §7.2). Resolve a directory path by recursing `*.ttr`/`*.ttrg`.

- [ ] **5. CLI behaviour + exit codes.**
  Default prints formatted output to stdout. `--check`: compare `format(src)` to `src` per file;
  exit `1` if any differ (list them), write nothing. `--write`: rewrite changed files in place,
  report count. Operational failure (bad path, parse error in a file) → exit `2` with a clear
  message; never partially write on parse error.

- [ ] **6. (test) CLI tests.**
  `packages/format/src/__tests__/cli.test.ts`: `execSync` the built bin against a temp fixture dir —
  assert `--check` exits 1 on an unformatted file and 0 after `--write`; assert `--write` is
  idempotent (second run changes nothing, exit 0). Mirror the migrate CLI test pattern.

- [ ] **7. Gates + LSP confirmation.**
  `pnpm -r {build,test,typecheck,lint}` green. Confirm the LSP `onDocumentFormatting` now preserves
  comments via the integration test from P1a (extend it with a commented document). No `any`.
