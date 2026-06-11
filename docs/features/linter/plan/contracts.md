# Linter / Formatter / Autofix — Contracts

Companion to [`architecture.md`](architecture.md). This is the authoritative spec for every public
and internal API, data structure/DTO, config schema, CLI, and LSP method the feature introduces or
changes. Task lists reference these contracts by section number; implement to these signatures.

All code is TypeScript ESM, `.js` extensions on relative imports (Node16), strict, no `any`.

---

## 1. `@modeler/parser` — CST / trivia (P0)

### 1.1 Trivia model — `packages/parser/src/cst/trivia.ts`

```ts
export type TriviaKind = 'line-comment' | 'block-comment' | 'whitespace' | 'newline';

export interface Trivia {
  kind: TriviaKind;
  /** Raw token text, e.g. "// ttr-disable-next-line foo" or "/* ... *​/". */
  text: string;
  /** ANTLR-style span (1-indexed line, 0-indexed col, exclusive offsetEnd). */
  source: SourceLocation;
}
```

### 1.2 AST node additions (additive, optional)

Every node that already carries `source: SourceLocation` MAY carry:

```ts
leadingTrivia?: Trivia[];   // trivia between the previous significant token and this node
trailingTrivia?: Trivia[];  // trivia on the same line after this node, up to newline
```

Existing consumers ignore these. The semantic AST shape is otherwise unchanged. `ParseResult` is
unchanged in type; nodes are enriched in place.

### 1.3 Token-stream access

The walker already builds `new CommonTokenStream(lexer)` (`walker.ts:162`). After the parse, fill
and read hidden-channel tokens.

```ts
// antlr4ng (verify exact names against node_modules/antlr4ng at build):
tokenStream.fill();                         // ensure all tokens buffered
const all: Token[] = tokenStream.getTokens();
// comment tokens now carry channel === Token.HIDDEN_CHANNEL (lexer `-> channel(HIDDEN)`)
// adjacency helpers on BufferedTokenStream:
tokenStream.getHiddenTokensToLeft(tokenIndex, channel?);
tokenStream.getHiddenTokensToRight(tokenIndex, channel?);
```

Map a `Token` → `Trivia` by `TTRLexer.LINE_COMMENT`/`BLOCK_COMMENT` token type → kind, and build
`source` from `token.line`/`token.column`/`token.start`/`token.stop` using the existing
`makeSourceLocation` conventions (exclusive `offsetEnd = token.stop + 1`).

### 1.4 Attachment function — `packages/parser/src/cst/attach.ts`

```ts
/** Attaches leading/trailing trivia to AST nodes in-place. Pure w.r.t. semantics. */
export function attachTrivia(ast: Document, tokenStream: CommonTokenStream): void;
```

Rule: leading trivia = hidden tokens to the left of a node's start token, up to and including the
newline that ends the previous significant line; trailing trivia = hidden tokens to the right of a
node's stop token on the same line, before the next newline.

### 1.5 Round-trip guarantee (P0 gate)

An identity printer (test helper, not shipped) that emits significant tokens + attached trivia in
source order MUST reproduce the input byte-for-byte for the sample corpus. This proves no trivia is
dropped or duplicated before the formatter applies canonicalization.

---

## 2. `@modeler/format` — formatter (P1)

### 2.1 Public API — `packages/format/src/index.ts`

```ts
export interface FormatConfig {
  separator: 'newline' | 'comma' | 'preserve';
  alignKeys: boolean;
  indentSpaces: number;   // default 4
  width: number;          // default 100
}
export const DEFAULT_FORMAT_CONFIG: FormatConfig;

/** Parse + print. Returns canonical text. Throws on unparseable input. */
export function format(source: string, uri: string, config?: FormatConfig): string;

/** Lower-level: print an already-parsed document (used by LSP to avoid double-parse). */
export function formatDocument(ast: Document, source: string, config?: FormatConfig): string;
```

`FormatConfig`, `DEFAULT_FORMAT_CONFIG`, `formatDocument` are the existing signatures from
`lsp/src/formatter/format.ts` — preserved on extraction so the move is mechanical.

### 2.2 Internal (extracted as-is, then extended)

- `printer.ts` (was `format.ts`): AST→Doc IR. **Extended** to emit `leadingTrivia`/`trailingTrivia`.
- `ir.ts`: `Doc`, `text`, `verbatim`, `concat`, `line`, `hardline`, `indent`, `group`, `join`.
- `render.ts`: `render(doc, width): string`.

### 2.3 Invariants

- **Idempotent:** `format(format(x)) === format(x)`.
- **Semantics-preserving:** `parse(format(x))` has the same AST (modulo source spans) as `parse(x)`.
- **Comment-preserving:** every comment in input appears exactly once in output, attached to the
  same logical node (leading stays leading, trailing stays trailing).
- **Owns ordering:** canonicalizes `package → imports → schema/graph → definitions` and the fixed
  per-kind property order already encoded in `propsOf`.
- **No `semantics` import.**

---

## 3. `@modeler/lint` — rule model (P2)

### 3.1 Core types — `packages/lint/src/rule.ts`

```ts
export type Severity = 'error' | 'warning' | 'info' | 'off';
export type RuleCategory = 'correctness' | 'references' | 'imports' | 'packages' | 'graph' | 'style';
export type RuleScope = 'document' | 'project';
export type RuleId = string;  // kebab, no `ttr/` prefix

export interface LintDiagnostic {
  ruleId: RuleId;
  code: DiagnosticCode;       // from @modeler/parser
  severity: Exclude<Severity, 'off'>;
  message: string;
  source: SourceLocation;
  data?: unknown;             // carried to fix.build
}

export interface Fix {
  kind: 'safe' | 'suggestion';
  title: string;
  build(ctx: RuleContext, d: LintDiagnostic): WorkspaceEdit;  // WorkspaceEdit from @modeler/edit
}

export interface Rule {
  id: RuleId;
  code: DiagnosticCode;
  category: RuleCategory;
  scope: RuleScope;
  defaultSeverity: Exclude<Severity, 'off'>;
  docs: string;
  check(ctx: RuleContext): void;
  fix?: Fix;
}
```

A rule reports via `ctx.report({ source, message, data? })`; it never sets severity. The runner
stamps effective severity from config (§5).

### 3.2 RuleContext — `packages/lint/src/rule.ts`

```ts
interface BaseContext {
  manifest: ResolvedManifest;        // from @modeler/semantics
  symbols: ProjectSymbolTable;
  resolver: Resolver;
  report(d: { source: SourceLocation; message: string; data?: unknown }): void;
}
export interface DocumentRuleContext extends BaseContext {
  scope: 'document';
  uri: string;
  ast: Document;
  refs: ReadonlyArray<{ ref: Reference; ownerDef: Definition }>;  // collectAllReferences, computed once
}
export interface ProjectRuleContext extends BaseContext {
  scope: 'project';
  packageGraph: PackageGraph;
  documents: ReadonlyMap<string, Document>;
}
export type RuleContext = DocumentRuleContext | ProjectRuleContext;
```

### 3.3 Registry — `packages/lint/src/registry.ts`

```ts
export const RULES: ReadonlyMap<RuleId, Rule>;
export function ruleForCode(code: DiagnosticCode): Rule | undefined;
export function rulesByCategory(cat: RuleCategory): Rule[];
// throws at module load if: duplicate id, >1 rule per owned code, or category/severity invalid
```

### 3.4 Rule inventory (id → code → category → scope → defaultSeverity → fix)

The 26-row table is authoritative in [`../design.md` §5.5](../design.md). Implementers copy ids,
codes, categories, scopes, default severities, and the Fix column from that table verbatim.

### 3.5 Runner — `packages/lint/src/runner.ts`

```ts
export interface LintDeps {
  manifest: ResolvedManifest;
  symbols: ProjectSymbolTable;
  resolver: Resolver;
}
export function lintDocument(
  uri: string, ast: Document, tokenStream: CommonTokenStream,
  deps: LintDeps, config: ResolvedLintConfig,
): LintDiagnostic[];

export function lintProject(
  documents: ReadonlyMap<string, Document>, packageGraph: PackageGraph,
  deps: LintDeps, config: ResolvedLintConfig,
): Map<string /*uri*/, LintDiagnostic[]>;
```

Runner steps: build context once (incl. `refs` and the suppression index from `tokenStream`
trivia) → for each enabled rule of matching scope call `check` → collect reports → drop suppressed
→ clamp/stamp severity from config → return. `off` rules are never invoked.

---

## 4. Suppression — `packages/lint/src/suppression.ts` (P2)

### 4.1 Directives (read from comment trivia)

```
// ttr-disable-next-line [ids]      next significant line
// ttr-disable-line [ids]           same line (trailing comment)
// ttr-disable [ids] … // ttr-enable [ids]   range
// ttr-disable-file [ids]           whole file
```

Ids: comma- and/or space-separated rule ids; empty ⇒ all rules.

### 4.2 API

```ts
export interface SuppressionIndex {
  isSuppressed(ruleId: RuleId, line: number): boolean;  // line is 1-indexed (ANTLR-style)
  unused(): Array<{ line: number; ruleId?: RuleId }>;
}
export function buildSuppressionIndex(ast: Document): SuppressionIndex;
```

`buildSuppressionIndex` reads `leadingTrivia`/`trailingTrivia` comment tokens across the document.
Guards: `correctness`-category rules are never suppressible (runner emits `ttrlint/cannot-suppress`
info if attempted); directives matching nothing surface via `unused()` →
`ttrlint/unused-suppression` warning.

---

## 5. Config — `packages/lint/src/config.ts` (P3)

### 5.1 `.ttrlint.toml` schema

```toml
extends = "recommended"          # "recommended" | "strict" | "all" | "none"
[rules]
<rule-id> = "error" | "warning" | "info" | "off"
[categories]
<category> = "error" | "warning" | "info" | "off"
[cli]
fail-on = "error"                # "error" | "warning" | "info" | "none"
[fix]
apply = "safe"                   # "safe" | "none"
```

### 5.2 Types

```ts
export interface RawLintConfig {
  extends?: 'recommended' | 'strict' | 'all' | 'none';
  rules?: Record<string, Severity>;
  categories?: Partial<Record<RuleCategory, Severity>>;
  cli?: { 'fail-on'?: Exclude<Severity,'off'> | 'none' };
  fix?: { apply?: 'safe' | 'none' };
}
export interface ResolvedLintConfig {
  severityOf(ruleId: RuleId): Severity;          // after precedence + clamp
  failOn: 'error' | 'warning' | 'info' | 'none';
  applyFixes: 'safe' | 'none';
  diagnostics: LintDiagnostic[];                 // ttrlint/unknown-rule, clamped-severity, deprecation
}
export function loadLintConfig(projectRoot: string): ResolvedLintConfig;  // discovers + parses + resolves
```

### 5.3 Precedence (low → high)

1. rule `defaultSeverity` → 2. `extends` preset → 3. `[categories]` → 4. `[rules]`.

### 5.4 Presets

- `recommended`: defaults, except `missing-description = off`.
- `strict`: `recommended` + `unresolved-reference = error` + `missing-description = warning`.
- `all`: every rule `error`.
- `none`: every rule `off` **except** `correctness` (clamped to `error`).

### 5.5 Clamp & back-compat

- `correctness` rules clamp to a floor of `error`; a request below it yields a
  `ttrlint/clamped-severity` info diagnostic (does not change behaviour).
- No `.ttrlint.toml` + `modeler.toml [lint].strict=true` ⇒ behave as `extends="strict"`.
- `[lint].requireDescriptions=true` ⇒ `missing-description="warning"`.
- Both present ⇒ `.ttrlint.toml` wins + info diagnostic suggesting removal of `[lint]`.

### 5.6 New config-level diagnostic codes (reported on `.ttrlint.toml`)

`ttrlint/unknown-rule`, `ttrlint/unknown-category`, `ttrlint/clamped-severity`,
`ttrlint/cannot-suppress`, `ttrlint/unused-suppression`, `ttrlint/deprecated-lint-config`. These are
NOT in the `ttr/*` `DiagnosticCode` enum; they are lint-tool diagnostics with a `ttrlint/` prefix.

---

## 6. Autofix — `packages/lint/src/fix.ts` (P4)

```ts
export interface FixResult {
  edit: WorkspaceEdit;            // merged, non-overlapping
  applied: LintDiagnostic[];      // diagnostics whose safe fix was included
  deferred: LintDiagnostic[];     // overlapping fixes left for the next pass
}
/** Collect safe fixes for a single document and merge non-overlapping edits. */
export function collectSafeFixes(diags: LintDiagnostic[], ctx: RuleContext): FixResult;
```

CLI `--fix` applies `collectSafeFixes` to a fixpoint (re-parse + re-lint until no further safe fix
or a max of N passes), then reports the remainder. `suggestion` fixes are never auto-applied;
they surface only as LSP CodeActions.

The four existing quick-fixes (`quickFixUnusedImport`, `quickFixMissingPackageDeclaration`,
`quickFixUnimportedReference` as **safe**; `quickFixPackageDeclarationMismatch` as **suggestion**)
are re-expressed as `Rule.fix` and reuse the same edit logic, moved into `@modeler/edit`.

---

## 7. CLI contracts

### 7.1 `ttr lint` — `packages/lint/src/cli.ts` (bin: `modeler-lint`)

```
modeler-lint <project-root> [options]
  --fix                 apply safe fixes (per [fix].apply), then report remainder
  --format <fmt>        pretty | json            (default pretty)
  --fail-on <sev>       error|warning|info|none  (overrides [cli].fail-on)
  --rule <id>           run only this rule (repeatable; debugging)
  --explain <id>        print rule docs and exit 0
  --quiet               print only error-severity
```

JSON shape: `{ uri: string; ruleId: string; code: string; severity: string; message: string; range: {start:{line,character}, end:{...}} }[]`.
Exit codes: `0` no diagnostics ≥ `fail-on`; `1` diagnostics ≥ `fail-on`; `2` operational failure.

### 7.2 `ttr fmt` — `packages/format/src/cli.ts` (bin: `modeler-fmt`)

```
modeler-fmt <path> [options]      # path = file or dir (recurses *.ttr, *.ttrg)
  --check               exit 1 if any file is not already formatted; write nothing
  --write               rewrite files in place
  (default)             print formatted result to stdout
```

Exit codes: `0` ok / all formatted; `1` `--check` found unformatted files; `2` operational failure.

---

## 8. LSP contracts (changes in `packages/lsp/src/server.ts`, P1/P2/P4)

No capability changes (`documentFormattingProvider` and `codeActionProvider` already advertised at
`server.ts:366-367`). Handler bodies change:

- `publishDiagnostics(uri, content)` — replace the eight `validator.validate*` calls with
  `lint.lintDocument(uri, ast, tokenStream, deps, config)` + cached
  `lint.lintProject(...)` filtered to `uri`. Keep emitting parser errors first. `severityToLsp`
  gains an (unreachable) `off` case.
- `onDocumentFormatting` — call `format.formatDocument(ast, content, await loadFormatConfig())`;
  keep the "don't format if parse errors" guard (`server.ts:937-940`) and the full-document
  `TextEdit` return shape (`:946-951`).
- `onCodeAction` — for each `params.context.diagnostics` with a matching `Rule.fix`, return a
  `CodeAction` whose `edit` is `fix.build(ctx, diag)`; `safe`→`CodeActionKind.QuickFix`,
  `suggestion`→`CodeActionKind.RefactorRewrite`. Replaces the hard-coded `switch (diag.code)`.
- Config watch — extend the existing completion-config invalidation pattern (`server.ts:405,422`)
  to also invalidate the lint config on `.ttrlint.toml` change and re-`publishDiagnostics` open docs.

---

## 9. Workspace wiring (P1/P2)

Each new package: `packages/<name>/package.json` named `@modeler/<name>`, `"type":"module"`,
extends `tsconfig.base.json`, `workspace:*` deps per §2, `bin` entry for the CLI, Vitest configured
like sibling packages. `pnpm-workspace.yaml` already globs `packages/*` (no change). Build via
`pnpm -r build`; `@modeler/lint`/`@modeler/format` are `tsc` libraries; CLIs are esbuild-bundled if
they need a single-file bin (mirror `@modeler/lsp` bundling or `@modeler/migrate` bin setup).
