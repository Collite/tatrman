# Embedded-SQL — Phase 0 spike report

**Repo:** modeler (scratch `packages/sql-spike`). **Date:** 2026-06-11.
**Scope:** de-risk the ANTLR-SQL approach (DESIGN §12) before any production
wiring. Three gates (S0.1–S0.3); all must be green to open Phases 2+.

> **Note (close-out):** the scratch `packages/sql-spike` was **deleted** after the
> gate (S0.3.6); the `src/*` / `scripts/*` / `corpus/*` paths referenced below are
> historical. To reproduce: re-vendor `grammars-v4 @923a1a9` (see `PINNED.md`),
> regenerate with `antlr-ng`, and re-run the harnesses described here.

| Gate | Verdict |
|---|---|
| S0.1 — antlr-ng generates tsql + postgresql | ✅ **PASS** |
| S0.2 — real-query parse + span fidelity | ✅ **PASS** (with the documented `{param}` pre-pass) |
| S0.3 — bundle-size + host split | ✅ **PASS** (lexer-only-in-browser confirmed) |

**Go/no-go for ANTLR (E3): 🟢 GO.** All three gates green. The ANTLR
(grammars-v4, `antlr4ng`, vendored-pinned + lazily patched) approach is viable
for embedded SQL; the `node-sql-parser` (E8) fallback is **not** triggered.
Proceed to Phase 2 (lexer-first highlighting), gated on Phase 1 merge.

**Owner decision points carried out of Phase 0:**
1. Confirm the browser-Worker headroom: both lexers add **+155 KB gz** (~+49%
   over today's 316 KB gz). Recommended **accept** (see S0.3).
2. The `{param}` placeholder pre-pass (`maskPlaceholders`) is a **required Phase
   2 carrier component**, not a SQL-grammar patch (see S0.2 backlog).

---

## S0.1 — antlr-ng generates tsql + postgresql — ✅ PASS

**Gate:** both grammars generate TS that compiles and instantiates under
`antlr4ng`, **and** case-insensitive keyword matching works (or a costed
fallback is chosen).

### Provenance

- Upstream: `antlr/grammars-v4`, pinned commit
  **`923a1a90f3b9e28c8ac08e170e582ceb15d6f0a9`** (see `vendor/PINNED.md`).
- Vendored verbatim by `scripts/vendor-grammars.sh`; no `.g4` edits.
- Generator: `antlr-ng@1.0.10` (the repo's existing TTR generator), command:
  `npx antlr-ng -l -v -Dlanguage=TypeScript -o <out> --lib <dir> -- <Lexer.g4> <Parser.g4>`.

### Generation result

Both dialects generated cleanly. Warnings (verbatim) — **PostgreSQL only**:

```
warning(79): PostgreSQLLexer.g4:1441:0: non-fragment lexer rule AfterEscapeStringConstantMode_NotContinued can match the empty string
warning(79): PostgreSQLLexer.g4:1456:0: non-fragment lexer rule AfterEscapeStringConstantWithNewlineMode_NotContinued can match the empty string
```

These are benign upstream warnings about empty-string-capable lexer rules in the
Postgres escape-string state machine; they do **not** block generation and the
lexer tokenises correctly (verified below). T-SQL generated with **zero**
warnings.

`tsc --noEmit` over `generated/**` + `src/**`: **exit 0** (no type errors). The
generated antlr4ng TypeScript is strict-clean under `tsconfig.base.json`.

### PostgreSQL base-class handling (the one non-trivial bit)

`PostgreSQLParser.g4` declares `superClass = PostgreSQLParserBase` (and the lexer
`= PostgreSQLLexerBase`) with semantic predicates implemented in those base
classes. grammars-v4 ships an **`Antlr4ng/`** target directory containing base
classes written for the **`antlr4ng` runtime** — the exact runtime this repo
uses — plus a `transformGrammar.py` that injects an `@header` import of them.
`scripts/generate.sh` replicates that injection with `sed` against a throwaway
copy (vendor/ stays pristine) and copies the base `.ts` files beside the
generated output. **No hand-forking required.** T-SQL has no base-class
dependency.

### Smoke + instantiate (S0.1.4)

`src/__tests__/generate-smoke.test.ts`, `pnpm --filter @modeler/sql-spike test`
— **6/6 pass**:

- T-SQL & Postgres lexers: non-empty token stream for `SELECT 1`.
- T-SQL `tsql_file()` and Postgres `root()` parsers: build a non-empty tree for
  `SELECT 1;`.

### Case-insensitivity (S0.1.5) — solved upstream, **no fallback needed**

Both lexers declare `options { caseInsensitive = true; }` and `antlr-ng`
**honours it**. Lexing `select` / `SELECT` / `SeLeCt` all yield the `SELECT`
keyword token (type, not `ID`/`Identifier`) for both dialects — asserted in the
test. **Chosen approach: rely on the native `caseInsensitive` lexer option.**
The DESIGN §12.6 fallbacks (case-folding `CharStream`, fragment `[Ss][Ee]…`
keywords) are **not** required — effort saved.

### Raw generated source sizes (informational; bundle sizes measured in S0.3)

| File | Size |
|---|---|
| `tsql/TSqlLexer.ts` | 532 KB |
| `tsql/TSqlParser.ts` | 8.3 MB |
| `postgresql/PostgreSQLLexer.ts` | 222 KB |
| `postgresql/PostgreSQLParser.ts` | 3.9 MB |

The parser sources are ~15–16× the lexer sources — early evidence for the
lexer-only-in-browser split (confirmed with real bundle numbers in S0.3).

### Verdict

**PASS.** Both grammars generate, compile, and instantiate under `antlr4ng`;
case-insensitivity works natively. No E8 fallback triggered. Proceed to S0.2.

---

## S0.2 — real-query parse + span fidelity — ✅ PASS

**Gate:** corpus **lexes 100%** and **parses ≥ threshold** (proposed ≥ 85% to a
tree with no fatal error) on **both** dialects; identifier tokens carry tight
`line/column/start/stop`; failures catalogued as the lazy-patch backlog.

### Corpus (S0.2.1)

| Dialect | Files | Source |
|---|---|---|
| tsql | 97 | 94 **real** project queries auto-extracted from `samples/**` + conformance fixtures by `src/extract-corpus.ts` (deduped), plus 3 hand-written covering `[brackets]`, `@params`, `#temp`, `N'…'`, CTE, cross-db 4-part names |
| postgres | 12 | hand-written (the project SQL is **all** MS-SQL): plain/joins/CTE/window/`$1`+`:name` params/`"quoted"` ids/`$$`-dollar-quoting/subquery+`::`cast/aggregate+`HAVING`/array+jsonb/`CASE`+`UNION` |

Real T-SQL breadth (across the 90 source queries): window functions
(`OVER … PARTITION BY`), `LEFT JOIN`, `CASE WHEN`, `GROUP BY`/`HAVING`, `UNION`,
`TOP`, `COLLATE`, `CONCAT`. No CTEs appear in the project corpus (hand-written
ones added).

### Coverage (S0.2.2 / S0.2.3) — `src/coverage.ts`

Parsers use the **default error strategy** (NOT `BailErrorStrategy`), so a broken
query yields a partial tree (DESIGN §12.3).

| Dialect | Lex (raw) | Lex (masked) | Parse (raw) | Parse (masked) |
|---|---|---|---|---|
| **tsql** | **41.2%** | **100%** | 100% | 100% |
| **postgres** | **100%** | **100%** | 100% | 100% |

**The entire T-SQL raw-lex gap is one cause: TTR `{param}` placeholders.** A
per-token tally (`distinct error tokens`) shows **334/334** lex errors are `{`
or `}` (167 each) — **zero** genuine SQL lex errors. The T-SQL lexer recovers by
skipping the braces, leaving the inner name as a bare identifier, which is why
*parsing* still reads 100% (on a silently-corrupted token stream — not something
to rely on).

This is a **TTR-carrier artifact, not a SQL-grammar deficiency.** The fix is a
**span-preserving pre-pass** (`src/mask.ts`, `maskPlaceholders`): blank the two
brace characters to spaces so `{nazev_produktu}` → `·nazev_produktu·` (identical
length → the §8 uniform additive source map is untouched), and return the masked
placeholder spans for later re-colouring as parameters + the `parameters`
cross-check. With that pre-pass, **T-SQL lexes 100% and parses 100%.** Postgres
needs no pre-pass (no placeholders in its corpus).

### Span fidelity (S0.2.4) — `src/__tests__/span-fidelity.test.ts` (4/4 pass)

Identifier tokens carry exact spans (`start`/`stop` 0-based inclusive offsets,
`column` 0-based, `line` 1-based); each sample also re-slices the source by
`[start, stop]` and gets the identifier back:

| Dialect | Query | Target | line | col | start | stop |
|---|---|---|---|---|---|---|
| tsql | `SELECT u.email FROM users u` | `email` | 1 | 9 | 9 | 13 |
| postgres | `SELECT u.email FROM users u` | `email` | 1 | 9 | 9 | 13 |
| tsql | `SELECT id,⏎ name⏎FROM customers` | `name` (line 2) | 2 | 7 | 18 | 21 |
| postgres | `SELECT u.id⏎FROM public.users u` | `users` (line 2) | 2 | 12 | 24 | 28 |

The §8 source map and go-to-definition are feasible.

### S0.2 backlog (lazy-patch list — DESIGN §12.7)

This is a deliverable, not a defect log. On this corpus there is **exactly one**
item, and it is a carrier concern rather than a grammar gap:

1. **TTR `{param}` placeholders break raw lexing of T-SQL** (167 occurrences
   across 57/97 files). **Resolution: implement the span-preserving
   `maskPlaceholders` pre-pass before SQL lexing** (prototyped in `src/mask.ts`,
   proven to restore 100% lex/parse). Productionise it in Phase 2 (lexer
   service) and surface masked spans as `parameter` semantic tokens; feed them
   to the Phase 3 `parameters` cross-check. _Not a grammar patch._

No genuine T-SQL or Postgres grammar failures were observed in this corpus — the
lazy-patch grammar backlog (DESIGN §12.7) is currently **empty**; it will grow
only as real failing constructs appear.

### Verdict

**PASS on both dialects.** Lex 100% (tsql via the documented `{param}` pre-pass;
postgres natively), parse 100% — comfortably above the ≥85% threshold. Span
fidelity exact. The threshold was not stressed because the corpus parsed cleanly;
**owner note:** the headline finding to confirm is the `{param}` pre-pass as a
required Phase 2 carrier component (it is small and span-safe).

---

## S0.3 — bundle-size + host split — ✅ PASS

**Gate:** the generated **lexer** bundle fits the browser-Worker budget;
**parser** sizes measured so the lexer-only-in-browser split (E6 / DESIGN §12.5)
is confirmed or revised with real numbers.

### Today's Worker baseline (S0.3.1)

`pnpm --filter @modeler/lsp build` → `dist/server-browser.js`:
**1,973 KB raw / 316 KB gzipped.** (Built unminified by the current esbuild step;
the gz figure is the like-for-like number to budget against, since the SQL
measurements below are gzipped too.) The browser bundle's esbuild step does
**not** mark `antlr4ng` external, so the runtime is **already inlined** in the
Worker today.

### SQL bundle measurements (S0.3.2 / S0.3.3) — `src/bundle-eval.ts`

esbuild, `platform=browser, format=esm, target=es2022, minify`, gzipped. The
"marginal-gz" column is the cost **on top of** the `antlr4ng` runtime baseline
(46 KB gz), i.e. the real added weight of the SQL classes:

| Entry | minified | gz | marginal-gz vs runtime |
|---|---|---|---|
| runtime-only (`antlr4ng`) | 170 KB | 46 KB | — (baseline) |
| tsql **lexer** | 613 KB | 153 KB | **+107 KB** |
| postgres **lexer** | 352 KB | 94 KB | **+48 KB** |
| **both lexers** | 795 KB | 201 KB | **+155 KB** |
| tsql parser (lexer+parser) | 4,143 KB | 600 KB | +554 KB |
| postgres parser (lexer+parser) | 2,014 KB | 330 KB | +284 KB |
| **both parsers (full)** | 5,987 KB | 884 KB | **+839 KB** |

**Parsers are ~5.4× the lexers** (839 vs 155 KB gz marginal) — the concrete
rationale for keeping parsers out of the Worker.

### Shared-runtime check (S0.3.4)

`antlr4ng` is already in the browser bundle (the TTR parser runs there). The
marginal figures above are measured against the runtime-only baseline, so they
**do not double-count the runtime** — adding the SQL lexers reuses the existing
`antlr4ng` classes and contributes only the generated lexer classes. Confirmed.

### Projected Worker sizes & verdict (S0.3.5)

| Scenario | projected Worker gz | vs today |
|---|---|---|
| today (no SQL) | 316 KB | — |
| **+ both lexers** (proposed) | **~471 KB** | **+49%** |
| + both parsers (rejected for browser) | ~1,155 KB | +265% |

**Verdict: lexer-only-in-browser CONFIRMED** (E6 / architecture §5) with real
numbers. Adding both dialect lexers grows the Worker by **+155 KB gz (~+49%)** —
acceptable for the highlighting payoff, and it scales sub-linearly per added
dialect (DuckDB reuses the Postgres lexer). Bundling the parsers instead would
nearly quadruple the Worker (+839 KB gz, +265%), which is correctly rejected;
parsers + semantics stay desktop-only (`server-stdio`, Node).

**Owner sign-off pending on one number:** is **+155 KB gz / +49%** within the
acceptable Worker headroom? If not, the fallbacks are (a) lazy-load the lexers in
the Worker on first SQL block, or (b) ship a single default dialect's lexer to
the browser and gate the others. Recommended: **accept as-is** (highlighting is
the browser's whole value-add here, and lazy-loading is available later if
needed).
