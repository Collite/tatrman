# T2 — TS parser AST + walker

Pre-flight: T1 merged into the feature branch; generated parser exposes `semanticsBlockProperty` contexts.

- [ ] **T2.1 — Tests first.** `packages/parser/src/__tests__/semantics-block.test.ts` (mirror `search-block.test.ts` structure): (a) block parses on entity/attribute/table/column and lands on the AST node; (b) entries preserved as raw key→value pairs — `kind: period_table` (id value), `role: period_code` + `code_format: "yyyyMM"` (string value), `period: AccountingPeriod` (id value, kept as **opaque string** — resolution is T3's job, parser stays mechanical); (c) duplicate key `role` twice → `duplicateProperties: ['role']`; (d) trivia attaches (leading comment on the block survives); (e) source locations present and ANTLR-convention-correct on the block node. All red.
- [ ] **T2.2 — AST type.** In `packages/parser/src/ast.ts` add, beside `SearchBlock` (~196):
  ```ts
  export interface SemanticsBlock {
    entries: Record<string, SemanticsValue>;   // raw, unvalidated
    duplicateProperties?: string[];
    location: SourceLocation;
  }
  export type SemanticsValue = string | number | boolean | null;  // ids arrive as string
  ```
  Add `semantics?: SemanticsBlock` to the definition-node interfaces for entity, attribute, table, column (exactly the four attachment kinds).
- [ ] **T2.3 — Walker.** In the walker (same module that builds `SearchBlock`), handle `semanticsBlockProperty`: fold `object_` propertyEntries into `entries` (last-wins), record repeats in `duplicateProperties` (search-block bookkeeping pattern), attach trivia, build `location` via `makeSourceLocation` (mind the multi-token `endColumn = stopToken.column + stopTokenLength` invariant from CLAUDE.md).
- [ ] **T2.4 — Value fidelity.** Ensure id values (`period_table`, `AccountingPeriod`) are captured as their identifier text, string literals unquoted, booleans/numbers as JS primitives; nested `object_`/lists inside a semantics block are **rejected at walk time** into a parser diagnostic (`TTR-PARSE` family, "semantics entries must be scalar") — keeps T3's input shape flat. Cover with a test in T2.1's file (add case (f)).
- [ ] **T2.5 — Green + lint.** `pnpm --filter @tatrman/parser test && pnpm --filter @tatrman/parser lint && pnpm -r typecheck`. No `any` (ESLint enforces outside `generated/**`).
- [ ] **T2.6 — Writer round-trip guard.** If `@tatrman/format` or writer-adjacent TS emits definitions, add `semantics` emission + one round-trip test (parse `59-semantics.ttrm` → emit → reparse → deep-equal AST modulo trivia). If TS-side has no writer, note "Kotlin ttr-writer covers round-trip (T4)" here and tick.
