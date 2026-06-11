# TTR Linter, Formatter & Autofix — Design

Status: **proposal** · Owner: editor-tooling · Supersedes the ad-hoc validation in `@modeler/semantics`

This document designs three related capabilities that share one foundation:

1. A **lossless, comment-preserving CST** in `@modeler/parser` — built **first**, because everything
   else depends on it.
2. A **formatter** (`@modeler/format`, `ttr fmt`) — whole-file, always-safe, presentation-only.
3. A first-class **linter** (`@modeler/lint`, `ttr lint`) with per-rule config, trivia-based inline
   suppression, and **autofix** (`ttr lint --fix`) for the subset of rules that carry a safe fix.

The linter is Option **B** from the brainstorming pass (dedicated package), config lives in a
standalone `.ttrlint.toml`, and the rule set runs identically live (LSP) and in batch (CLI).
The formatter and the linter's autofix are **distinct surfaces over a shared CST**: `ttr fmt`
rewrites layout; `ttr lint --fix` resolves specific diagnostics.

This supersedes `docs/v1/design/diagnostics.md`, which describes the machinery being replaced and
stays accurate until the migration phases below land.

---

## 1. Goals and non-goals

**Goals**

- A lossless CST that retains comments and whitespace as trivia, enabling formatting, autofix, and
  suppression to all read/preserve comments from one source of truth.
- A comment-preserving `ttr fmt` that produces canonical layout, is idempotent, and is always safe.
- A rule registry where every check is self-describing (`id`, category, default severity, docs,
  `check`, optional `fix`), not an inline `if` in a 480-line `Validator`.
- Per-rule control (enable/disable + severity) owned by the project via `.ttrlint.toml`.
- One rule set, two surfaces: identical results live in the LSP and in `ttr lint` for CI.
- Inline suppression (`// ttr-disable-next-line <rule>`) read from CST trivia.
- Autofix for the bounded subset of rules whose fix needs no human judgment, applied via the
  existing `@modeler/edit` synthesizer and surfaced as LSP CodeActions and `ttr lint --fix`.
- A migration that preserves today's 26 diagnostics' codes, messages, and severities (no regression).

**Non-goals**

- Autofix for rules that require human judgment (`unresolved-reference`, `ambiguous-reference`,
  `duplicate-definition`, missing types). These stay report-only, optionally surfaced as editor
  *suggestions* the user applies manually — never auto-applied by `--fix`.
- Changing what ai-platform consumes. The grammar change (§4) is parse-equivalent; ai-platform
  keeps its own load-blocking severity policy (`docs/ai-platform-upgrade.md` A5).
- A linter for syntax errors. `ttr/parse-error` / `ttr/parse-recovery-info` stay unconditional in
  `@modeler/parser` and are not lint rules.

---

## 2. Current state (what we are replacing)

Semantic validation is one class, `packages/semantics/src/validator.ts`, with 11 `validate*`
methods that each push `{ code, severity, message, source }`. The 26 codes are an enum in
`packages/parser/src/diagnostics.ts`. **Severity is hardcoded at each call site**, with two dynamic
exceptions: `unresolved-reference` flips error/warning on `lint.strict`, and `requireDescriptions`
gates one warning. Config is two booleans under `[lint]` in `modeler.toml`. The LSP
(`publishDiagnostics`) calls every `validate*`, concatenates, and maps severities. No registry, no
per-rule config, no categories, no suppression, no autofix.

Two facts that shape this design, both verified in the tree:

- **Comments are discarded at lex time.** `packages/grammar/src/TTR.g4` lines 597–599 route
  `LINE_COMMENT` / `BLOCK_COMMENT` / `WS` to `-> skip`. They never reach the parser.
- **There is no trivia mechanism today.** The edit synthesizer
  (`packages/edit/src/{rename-symbol,rename-package,graph-edits}.ts`) works purely off
  `SourceLocation` offsets and does surgical text patches; it never consumes a CST-with-trivia.
  CLAUDE.md's claim that the parser "exposes a CST view with trivia, used by the edit synthesizer"
  is **aspirational** — this design makes it true (see §4 and Appendix B).

---

## 3. Architecture

The shared foundation is a lossless CST in `@modeler/parser`. Three consumers sit above the
existing layers:

```
grammar → parser(+CST) → semantics → ┌─ lint ─┐
                              │       │        ├→ lsp → vscode-ext
                              ├─ edit ┘        │
                              └─ format ───────┘            designer
```

- **`@modeler/parser`** gains a lossless CST: every token retained, comments/whitespace attached as
  leading/trailing **trivia** on AST nodes. The semantic AST shape is unchanged; trivia is additive.
- **`@modeler/format`** (new) — the formatter. Depends only on `parser` (formatting is syntactic, it
  needs no symbol resolution). Exposes `format(source): string`. Ships the `ttr fmt` bin.
- **`@modeler/lint`** (new) — rule registry, runner, config, suppression, autofix. Depends on
  `semantics` (resolver/symbols/manifest/package-graph), `parser` (AST + CST trivia for
  suppression), and `edit` (to synthesize autofix `WorkspaceEdit`s). Ships the `ttr lint` bin.
- **`@modeler/edit`** — unchanged role; gains any new structured-edit ops the autofixes need.
- **`@modeler/lsp`** — depends on `lint` and `format` instead of calling `Validator`. Wires
  `textDocument/formatting` (+ format-on-save) to `format`, and diagnostics + CodeActions to `lint`.

Dependencies stay one-way. `format` deliberately does **not** depend on `semantics` — keeping
presentation free of semantics is what lets `ttr fmt` be unconditionally safe.

`DiagnosticCode` stays in `@modeler/parser` (shared with parse errors). The linter adds a parallel
**rule-id** namespace mapping onto those codes (§5.5); the enum is not duplicated.

### 3.1 Package layout

```
packages/parser/src/cst/      # trivia model, CST builder, attach-to-node logic
packages/format/src/
  index.ts                    # format(source): string
  printer.ts                  # CST walk → canonical layout
  ordering.ts                 # canonical element ordering (owns file-ordering)
  cli.ts                      # ttr fmt
packages/lint/src/
  index.ts                    # lintDocument, lintProject, loadLintConfig, RULES
  rule.ts                     # Rule, RuleContext, RuleCategory, Severity, Fix
  registry.ts                 # RULES map; lookups; category queries
  runner.ts                   # run rules, apply config + suppression, stamp severity
  config.ts                   # .ttrlint.toml schema/discovery/resolution + modeler.toml back-compat
  suppression.ts              # build suppression index from CST trivia
  fix.ts                      # collect/merge fixes → WorkspaceEdit via @modeler/edit
  cli.ts                      # ttr lint [--fix]
  rules/{structure,references,imports,packages,graph,search,project}.ts
```

---

## 4. The lossless CST foundation (built first)

### 4.1 Grammar change

`LINE_COMMENT` and `BLOCK_COMMENT` move from `-> skip` to `-> channel(HIDDEN)` in `TTR.g4`. `WS`
may stay `skip` (the printer re-derives whitespace) or also move to a hidden channel if the
formatter wants to preserve blank-line runs — see §10.1. The **parse tree is unchanged**: the
parser ignores hidden-channel tokens exactly as it ignored skipped ones.

This is a vendored-grammar change. Per CLAUDE.md, after editing `TTR.g4`: regenerate the TS parser
(`packages/parser` prebuild), regenerate the TextMate grammar, and run
`packages/grammar/scripts/sync-to-ai-platform.sh`. Because the change is parse-equivalent,
ai-platform's regenerated Kotlin lexer simply gains hidden-channel comment tokens it continues to
ignore — no behavioural change there (Appendix A).

### 4.2 Trivia model

```ts
interface Trivia {
  kind: 'line-comment' | 'block-comment' | 'whitespace' | 'newline';
  text: string;
  source: SourceLocation;
}
```

The CST builder reads the hidden channel from the `CommonTokenStream` (already constructed in
`walker.ts:162`) and attaches **leading** and **trailing** trivia to each AST node, using token
indices to decide attachment (standard "leading trivia up to the previous significant token,
trailing trivia to end of line"). AST nodes gain optional `leadingTrivia?: Trivia[]` /
`trailingTrivia?: Trivia[]`; existing consumers ignore them. The `SourceLocation` invariants
(1-indexed lines, exclusive `offsetEnd`, the `endColumn = stopToken.column + stopTokenLength`
rule) apply to trivia spans too — re-check `makeSourceLocation` per CLAUDE.md when touching this.

### 4.3 What it provides

- **Formatter**: full-fidelity walk; comments are repositionable, not lost.
- **Autofix**: minimal `WorkspaceEdit`s that preserve surrounding trivia.
- **Suppression**: read `// ttr-disable-*` directives directly from comment trivia, with accurate
  positions — no separate text scan, one source of truth.
- **Round-trip guarantee**: `print(parse(src))` with the identity printer reproduces `src` byte-for-byte
  (the foundational test for the CST before the formatter applies any canonicalization).

---

## 5. The rule model

### 5.1 Types

```ts
export type Severity = 'error' | 'warning' | 'info' | 'off';

export type RuleCategory =
  | 'correctness' | 'references' | 'imports' | 'packages' | 'graph' | 'style';

export type RuleScope = 'document' | 'project';

export interface Fix {
  /** 'safe' fixes are applied by `--fix`; 'suggestion' fixes are CodeAction-only. */
  kind: 'safe' | 'suggestion';
  title: string;                       // shown in the CodeAction menu
  build(ctx: RuleContext, d: Diagnostic): WorkspaceEdit;  // via @modeler/edit
}

export interface Rule {
  id: RuleId;                          // kebab, no `ttr/` prefix, e.g. 'unused-import'
  code: DiagnosticCode;                // emitted code, for back-compat
  category: RuleCategory;
  scope: RuleScope;
  defaultSeverity: Exclude<Severity, 'off'>;
  docs: string;                        // one-liner for `--explain`
  check(ctx: RuleContext): void;       // pure: report via ctx.report
  fix?: Fix;                           // optional; absent = report-only
}
```

A rule never sets severity — it calls `ctx.report({ source, message, data? })` and the runner
stamps effective severity from config. This removes "severity baked in at the call site" at the
root.

### 5.2 RuleContext

Two shapes by `scope`, each built once per run so rules don't re-walk shared state:

```ts
interface BaseContext {
  manifest: ResolvedManifest;
  symbols: ProjectSymbolTable;
  resolver: Resolver;
  report(d: { source: SourceLocation; message: string; data?: unknown }): void;
}
interface DocumentRuleContext extends BaseContext {
  scope: 'document'; uri: string; ast: Document;
  refs: ReadonlyArray<{ ref: Reference; ownerDef: Definition }>;  // collectAllReferences, once
}
interface ProjectRuleContext extends BaseContext {
  scope: 'project'; packageGraph: PackageGraph; documents: ReadonlyMap<string, Document>;
}
```

These are exactly the inputs today's `Validator` already takes — migration moves code, it doesn't
rewrite it. `collectAllReferences(ast)` is computed once and shared via `ctx.refs` (today
`validateReferences` and `validateImports` each recompute it).

### 5.3 Registry

`registry.ts` exports `RULES: ReadonlyMap<RuleId, Rule>`, assembled from `rules/*`. A startup
assertion guarantees one rule per owned `DiagnosticCode` and unique ids, and answers
`rulesByCategory(cat)` and `ruleForCode(code)` (so the wire keeps emitting `code` while config keys
on `id`).

### 5.4 Autofix execution

`fix.build()` returns a `WorkspaceEdit` synthesized through `@modeler/edit`. The runner collects all
`safe` fixes for a document, merges non-overlapping edits (overlaps are dropped and re-reported on
the next pass — `--fix` is iterative to a fixpoint, like eslint), and applies them. `suggestion`
fixes are never auto-applied; they surface only as LSP CodeActions. Autofixes preserve surrounding
trivia because edits are minimal ranges against the CST (§4.3).

### 5.5 Rule → code → severity → fixability migration table

Codes and default severities are preserved exactly; `strict`/`requireDescriptions` become config
(§6.4). The "Fix" column is the autofix plan.

| Rule id | DiagnosticCode | Category | Scope | Default | Fix |
|---|---|---|---|---|---|
| `entity-no-attributes` | `required-property-missing` | correctness | document | error | — |
| `table-no-columns` | `required-property-missing` | correctness | document | error | — |
| `column-missing-type` | `required-property-missing` | correctness | document | error | — |
| `attribute-missing-type` | `required-property-missing` | correctness | document | error | — |
| `graph-missing-schema` | `required-property-missing` | graph | document | error | — |
| `missing-description` | `required-property-missing` | style | document | off | — |
| `entity-attribute-not-found` | `entity-attribute-not-found` | correctness | document | error | — |
| `primary-key-column-not-found` | `primary-key-column-not-found` | correctness | document | error | — |
| `fuzzy-without-searchable` | `fuzzy-without-searchable` | correctness | document | warning | **safe**: insert `searchable: true` |
| `duplicate-search-property` | `duplicate-search-property` | correctness | document | error | suggestion: delete dup |
| `unresolved-reference` | `unresolved-reference` | references | document | warning | — |
| `ambiguous-reference` | `ambiguous-reference` | references | document | error | suggestion: qualify (offer each candidate) |
| `unimported-reference` | `unimported-reference` | references | document | info | **safe**: insert the computed import |
| `unused-import` | `unused-import` | imports | document | warning | **safe**: delete import |
| `duplicate-import` | `duplicate-import` | imports | document | warning | **safe**: delete redundant import |
| `wildcard-with-no-matches` | `wildcard-with-no-matches` | imports | document | warning | **safe**: delete wildcard |
| `circular-package-dependency` | `circular-package-dependency` | packages | project | warning | — |
| `package-declaration-mismatch` | `package-declaration-mismatch` | packages | document | error | suggestion: rewrite to inferred |
| `missing-package-declaration` | `missing-package-declaration` | packages | document | info | **safe**: insert inferred `package` decl |
| `duplicate-definition` | `duplicate-definition` | correctness | project | error | — |
| `duplicate-mapping` | `duplicate-mapping` | correctness | project | error | — |
| `graph-object-not-found` | `graph-object-not-found` | graph | document | warning | — |
| `graph-layout-stale-node` | `graph-layout-stale-node` | graph | document | warning | **safe**: drop stale layout entry |
| `graph-objects-empty` | `graph-objects-empty` | graph | document | warning | — |
| `graph-name-mismatch` | `graph-name-mismatch` | graph | document | warning | suggestion: rename graph ↔ file |
| `file-ordering` | `file-ordering` | style | document | warning | **owned by the formatter** (see §7) |

Codes that are **not** lint rules: `ttr/parse-error`, `ttr/parse-recovery-info` (parser,
unconditional), `ttr/unknown-property`, `ttr/invalid-type`, `ttr/wrong-file-kind` (parse/host
boundary).

`required-property-missing` is reused by six conditions; each gets its own *rule id* sharing the
one *code*, so per-rule config is meaningful (you can disable `missing-description` without
disabling "table has no columns") while external consumers see no change. (Decision §10.2.)

---

## 6. Configuration: `.ttrlint.toml`

### 6.1 Discovery

A standalone `.ttrlint.toml` at the project root, beside `modeler.toml` (same walk-up resolution).
Absent → every rule runs at its `defaultSeverity`. Single file per project for v1; nested overrides
deferred (§10.5).

### 6.2 Schema

```toml
extends = "recommended"          # "recommended" | "strict" | "all" | "none"

[rules]
missing-description   = "warning"
unresolved-reference  = "error"
unimported-reference  = "off"

[categories]
style = "off"                    # individual [rules] entries win over this

[cli]
fail-on = "error"                # severities ≥ this fail CI (exit 1)

[fix]
apply = "safe"                   # "safe" | "none" — which fixes `--fix` applies
```

Precedence, low → high: rule `defaultSeverity` → `extends` preset → `[categories]` → `[rules]`.
Unknown rule id/category is itself reported (`ttrlint/unknown-rule`) on the `.ttrlint.toml`
document, so typos don't silently disable nothing.

### 6.3 Presets

- `recommended` — current default: everything at `defaultSeverity` except `missing-description = off`.
- `strict` — `recommended` + `unresolved-reference = error` + `missing-description = warning`
  (reproduces today's `strict` + `requireDescriptions: true`).
- `all` — every rule at `error`.
- `none` — everything `off` except `correctness` rules (which clamp to `error`, §6.5).

### 6.4 Back-compat with `modeler.toml [lint]`

`[lint].strict = true` → `extends = "strict"` when no `.ttrlint.toml` exists.
`[lint].requireDescriptions = true` → `missing-description = "warning"`. If both files configure
lint, `.ttrlint.toml` wins and we emit an info diagnostic suggesting removal of the `modeler.toml`
knob. `resolveManifest` keeps returning `lint: { strict, requireDescriptions }` so nothing
downstream breaks during migration; the linter reads them only as fallback.

### 6.5 Floor on correctness rules

`correctness` rules describe a model that will not load. Allowing them `off`/`warning` would let the
editor show green while ai-platform rejects the file. Decision: correctness rules may be **raised**
but not lowered below `error`; config requesting otherwise is clamped with a
`ttrlint/clamped-severity` info diagnostic. They also cannot be suppressed (§8). (Decision §10.3.)

---

## 7. Formatter (`@modeler/format`, `ttr fmt`)

Pure presentation over the CST. `format(source): string` parses to the CST and re-emits canonical
layout: indentation, spacing, blank-line policy, and **comment placement** (leading comments stay
with their node; trailing comments stay on their line). It is **semantics-preserving** and
**idempotent** (`format(format(x)) === format(x)` — a core test).

The formatter **owns physical ordering**. `file-ordering` is fundamentally a layout concern, and on
today's order-strict grammar it isn't even an emittable lint diagnostic (out-of-order tokens are
`ttr/parse-error`). So `ttr fmt` canonicalizes element order (`package → imports → schema/graph →
definitions`) and the lint rule stays a registered placeholder that defers its fix to the formatter
rather than synthesizing its own edit.

Surfaces:

- **CLI**: `ttr fmt <path> [--check] [--write]`. `--check` exits non-zero if any file isn't
  formatted (CI gate); `--write` rewrites in place; default prints to stdout.
- **LSP**: `textDocument/formatting` and `textDocument/rangeFormatting`, plus format-on-save support
  (client opt-in). No new custom method needed.

Because `format` has no `semantics` dependency and produces only layout changes, it is always safe
to run unattended — the key distinction from autofix.

---

## 8. Suppression (trivia-based)

With comments on the hidden channel (§4), suppression reads directives directly from comment trivia
— no text scan, one source of truth.

```
// ttr-disable-next-line [rules]      next line
// ttr-disable-line [rules]           same line (trailing comment)
// ttr-disable [rules]  …  // ttr-enable [rules]    range
// ttr-disable-file [rules]           whole file
```

Multiple ids are comma/space-separated; no ids = all rules. `suppression.ts` folds the comment
trivia into a `SuppressionIndex`:

```ts
interface SuppressionIndex {
  isSuppressed(ruleId: RuleId, line: number): boolean;
  unused(): Array<{ line: number; ruleId?: RuleId }>;  // → ttrlint/unused-suppression
}
```

The runner filters each reported diagnostic through `isSuppressed(rule.id, d.source.line)` before
stamping severity. Two guards: a directive that suppresses nothing is reported as
`ttrlint/unused-suppression` (warning); `correctness` rules **cannot** be suppressed (same rationale
as §6.5) — attempting yields `ttrlint/cannot-suppress` (info).

---

## 9. Execution model & LSP integration

### 9.1 Runner

`lintDocument(uri, ast, cst, ctxDeps, config)` and `lintProject(documents, ctxDeps, config)`:

1. Build the shared context once (refs, search blocks, suppression index from CST trivia).
2. Run each enabled rule of the matching scope (`off` rules are skipped, never invoked).
3. Collect reports, apply suppression, clamp/stamp severity from config.
4. Return `{ code, ruleId, severity, message, source, fix? }[]`.

Project-scoped results are bucketed by `uri` (what the LSP does today with
`.filter(d => d.source.file === uri)`), so editing one file still shows project-level diagnostics on
the right file.

### 9.2 LSP changes (`packages/lsp/src/server.ts`)

- `publishDiagnostics` stops calling the eight `validate*` methods; it calls `lintDocument` + cached
  `lintProject` results. `severityToLsp` gains an (unreachable) `off` case.
- New: `textDocument/formatting` / `rangeFormatting` → `format`.
- New: `textDocument/codeAction` → for each diagnostic with a `fix`, return a CodeAction whose edit
  is `fix.build(...)`; `safe` and `suggestion` fixes both appear here (only `safe` is batch-applied
  by `--fix`).
- `.ttrlint.toml` becomes a watched config (like the completion-config invalidation at
  server.ts:405/422); editing it re-lints open documents. Config diagnostics publish on its URI.
- The `Validator` class is deleted once all rules are ported; shared helpers (`searchBlocksOf`,
  `enclosingQnameOf`) move into `rules/*` or stay as `semantics` utilities the rules import.

### 9.3 CLI

Both bins mirror `packages/migrate/src/cli.ts` (commander, explicit `process.exit`):

```
ttr lint <project-root> [--fix] [--format pretty|json] [--fail-on <sev>] [--explain <id>] [--quiet]
ttr fmt  <path>         [--check] [--write]
```

`ttr lint` exit codes: `0` clean (≤ `fail-on`), `1` diagnostics found, `2` operational failure
(matches the migrate CLI pattern review-048 verified). `--fix` applies `safe` fixes to a fixpoint
then re-reports the remainder. `--format json` emits a stable shape
(`{ uri, ruleId, code, severity, message, range }[]`) for CI annotations.

---

## 10. Resolved decisions (approved)

All six confirmed by the owner; the design above reflects them.

1. **Whitespace channel (§4.1).** Keep `WS -> skip` initially; the printer applies a blank-line
   policy. Blank-line preservation revisited later if users want it.
2. **`required-property-missing` split (§5.5).** Distinct rule ids sharing one code.
3. **Correctness floor (§6.5, §8).** Correctness rules cannot drop below `error` and cannot be
   suppressed.
4. **`missing-description` default.** `off` in `recommended`.
5. **Nested config.** Single root `.ttrlint.toml` for v1; per-directory overrides deferred.
6. **Judgment fixes.** `ambiguous-reference` and `package-declaration-mismatch` fixes are
   *suggestions* (CodeAction-only), never batch-applied by `--fix`.

## 11. Locked decisions (from the brainstorming pass)

- **Option B**: dedicated `@modeler/lint` package.
- **Config**: standalone `.ttrlint.toml` (`modeler.toml [lint]` kept for back-compat).
- **Suppression**: in scope, **trivia-based** (reads CST comments).
- **CLI**: in scope; identical rule set runs live (LSP) and batch.
- **Formatter is coming**: build a **lossless comment-preserving CST first**; ship `ttr fmt` as a
  separate always-safe tool.
- **Autofix**: `ttr lint --fix` and LSP CodeActions over the bounded safe-fix subset; formatter and
  autofix are **two tools over the shared CST**, not one.

---

## 12. Phasing

CST is foundational and lands first; the formatter and the lint package can then proceed largely in
parallel; autofix lands last because it needs both the CST and the rule model.

- **P0 — Lossless CST.** Grammar: comments → hidden channel; regenerate + re-sync to ai-platform.
  Parser builds the CST and attaches leading/trailing trivia to AST nodes. Round-trip test
  (`print∘parse` is the identity). Makes CLAUDE.md's trivia claim true. No user-facing change.
- **P1 — Formatter.** `@modeler/format`, `format()`, `ttr fmt` (`--check`/`--write`), LSP
  `textDocument/formatting` + format-on-save. Owns canonical ordering. Idempotency + ordering tests.
- **P2 — Lint package + rule model (no behaviour change).** Port all 26 checks to rules; LSP uses
  the runner; `Validator` deleted; golden test proves byte-identical diagnostics under
  `recommended`. Trivia-based suppression built here. *(Parallelizable with P1 after P0.)*
- **P3 — Config.** `.ttrlint.toml` schema/discovery/precedence/presets, correctness clamp,
  `modeler.toml` back-compat + deprecation diagnostics, LSP config-watch, unknown-rule reporting.
- **P4 — Autofix.** `fix()` on the safe subset (§5.5), merge-to-fixpoint runner, `ttr lint --fix`,
  LSP CodeActions, safe/suggestion split. Routes through `@modeler/edit`.

---

## Appendix A — cross-repo (ai-platform) impact

The grammar is vendored into ai-platform; its Kotlin parser regenerates from our copy
(CLAUDE.md + sync scripts). The only grammar change here is `skip` → `channel(HIDDEN)` for comments,
which is **parse-equivalent**: the parser ignores hidden-channel tokens exactly as skipped ones, so
ai-platform's parse tree and load behaviour are unchanged — its regenerated lexer merely gains
comment tokens on a channel it keeps ignoring. The change still requires the standard flow: edit
`TTR.g4`, regenerate the TS parser and TextMate grammar, run `sync-to-ai-platform.sh`, and have
ai-platform regenerate. ai-platform keeps its own load-blocking severity policy
(`docs/ai-platform-upgrade.md` A5); linter/formatter output is editor-side only and never sent to
or read by ai-platform. The `correctness` floor (§6.5) is the one intentional alignment, achieved by
clamping editor config — not by coupling to ai-platform.

## Appendix B — CLAUDE.md correction

CLAUDE.md states the parser "exposes a CST view with trivia, used by the edit synthesizer." That is
not true today (the edit synthesizer is offset-based; comments are skipped). P0 makes it true. The
CLAUDE.md line should be updated when P0 lands to describe the actual trivia model (§4.2) and note
that the edit synthesizer now preserves trivia during autofix.
