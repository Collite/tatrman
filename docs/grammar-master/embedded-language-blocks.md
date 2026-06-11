# Embedded-language blocks (tagged triple-strings)

Status: **design / proposal** — implements "Option C" from the SQL-analysis brainstorm.
Owner-facing contract change: **yes** (the value contract is shared with the
`ai-platform` Kotlin parser via the vendored `TTR.g4`). Extends `contracts.md`
§2.9 (textwrap-dedent value extraction) rather than replacing it.

## 1. Decision

Embedded foreign-language source (SQL today; `transform` / `dataframe` /
`relnode` later) is carried in a **tagged triple-quoted block**, markdown-fence
style, where the language/dialect tag sits on the opener line:

```ttr
query getActiveUsers {
  sourceText:
    """sql
    SELECT u.id, u.email
    FROM   users u
    WHERE  u.active = :since
    """
}
```

Rules we are committing to:

- **`"""` is the fence, never backticks.** SQL uses single backticks for
  identifier quoting (MySQL/BigQuery); a `` ``` `` fence would collide. `"""`
  never appears in SQL, so termination is unambiguous.
- **Tag ⇒ parsed; no tag ⇒ raw.** A triple-string with a valid tag on its
  opener line is parsed/analysed as that language. An *untagged* triple-string
  is just a multi-line string (descriptions, prose) and is never parsed. This
  decouples "multi-line" from "is-SQL" — the delimiter no longer implies SQL,
  the tag does.
- **Single-quoted strings are never parsed.** Unchanged.
- **The tag is a typed, located AST construct** (Option C), not characters
  buried in the value. The value handed to `ai-platform` never contains the tag.
  Modeled as a **new sealed `PropertyValue` variant** `TaggedBlockValue` —
  **decided: this is an accepted breaking change** (it forces a new branch in
  ai-platform's exhaustive `when`; we are not softening it into optional fields
  on `TripleStringValue`).
- **`language:` becomes derivable.** When a tagged block is present the query's
  `language` is inferred from the tag; an explicit `language:` that disagrees is
  a diagnostic. `language:` is deprecated for `query` (see §6).

## 2. Grammar change (`packages/grammar/src/TTR.g4`)

### 2.1 Lexer — two token shapes, tagged declared first

```antlr
// Tagged block: """<tag>\n ... """   (tag = letter, then letters/digits/-)
// Optional trailing horizontal whitespace after the tag is tolerated; the
// formatter normalises it away. The newline after the tag terminates the
// opener line and is NOT part of the value.
TAGGED_BLOCK_LITERAL
  : '"""' [a-zA-Z] [a-zA-Z0-9-]* [ \t]* '\r'? '\n' .*? '"""'
  ;

// Untagged multi-line string (existing behaviour, now "just a string").
TRIPLE_STRING_LITERAL
  : '"""' .*? '"""'
  ;

STRING_LITERAL
  : '"' ( ~["\\\r\n] | '\\' . )* '"'
  ;
```

`TAGGED_BLOCK_LITERAL` **must be declared before** `TRIPLE_STRING_LITERAL`.
Both can match the same span (`.*?` reaches the same closing `"""`); ANTLR
breaks equal-length ties by declaration order, so the tagged rule wins when a
tag+newline is present. Disambiguation cases:

| Input | Matches | Why |
|---|---|---|
| `"""sql\nSELECT 1\n"""` | `TAGGED_BLOCK_LITERAL` | tag + newline present, declared first |
| `"""\nplain\n"""` | `TRIPLE_STRING_LITERAL` | char after `"""` is `\n`, tagged rule needs a letter |
| `"""a long note"""` | `TRIPLE_STRING_LITERAL` | `note` not followed by newline before `"""` |
| `"""sql"""` (one line) | `TRIPLE_STRING_LITERAL` | no newline after tag → not a block; value is the literal text `sql` |

The last row is intentional: a one-line `"""sql"""` is **not** an embedded
block, it is a plain string whose value is `sql`. The formatter/linter flags it
if it looks like an intended SQL block (see §7).

### 2.2 Parser — restrict tagged blocks to where they belong

`stringLiteralForm` keeps its current meaning ("a string, possibly
multi-line"). A new `embeddedBlock` production is used **only** by the
properties that can hold foreign source:

```antlr
stringLiteralForm
  : STRING_LITERAL
  | TRIPLE_STRING_LITERAL
  ;

embeddedBlock
  : TAGGED_BLOCK_LITERAL    // parsed as the tagged language
  | TRIPLE_STRING_LITERAL   // untagged → raw text, no analysis
  | STRING_LITERAL          // single-line literal (back-compat)
  ;

sourceTextProperty    : SOURCE_TEXT    propSep? embeddedBlock ;
definitionSqlProperty : DEFINITION_SQL propSep? embeddedBlock ;
```

Keeping `TRIPLE_STRING_LITERAL` and `STRING_LITERAL` in `embeddedBlock`
preserves every existing `.ttr` file. A tagged block appearing anywhere other
than these productions is a **parse error** (the token simply isn't in those
rules), which is the behaviour we want — no need for a semantic check.

## 3. AST shape

`walkStringLiteralForm` gains a sibling that the embedded-source properties call.
The node is typed and carries **two** source locations so the tag and the value
can be highlighted / navigated independently:

```ts
interface TaggedBlockValue {
  kind: 'taggedBlock';
  tag: string;              // 'sql', 'ms-sql', 'tsql', 'postgres', ...
  language: LanguageKind;   // resolved enum (SQL | TRANSFORMATION_DSL | ...)
  dialect: string | null;   // sub-dialect if the tag carries one, else null
  value: string;            // SQL text only — tag and fence stripped, dedented
  tagSource: SourceLocation;    // span of the tag token text
  valueSource: SourceLocation;  // span of `value` inside the file (post-dedent)
  source: SourceLocation;       // whole literal
  indentWidth: number;      // columns removed by dedent (for the SQL source map)
}
```

`indentWidth` is what lets the embedded-SQL position mapper add the stripped
indentation back per line (see §8). `tagSource` enables a diagnostic-precise
underline on an unknown/mismatched tag.

## 4. Value-extraction contract (extends `contracts.md` §2.9)

Given the raw `TAGGED_BLOCK_LITERAL` token text `T`:

1. Strip the opening `"""` (3 chars) and the closing `"""` (3 chars) → `inner`.
2. From the start of `inner`, consume `tag = [A-Za-z][A-Za-z0-9-]*`.
3. Consume `[ \t]*` then the required `\r?\n`. This whole prefix (tag +
   trailing hspace + newline) is the **opener line** and is discarded.
4. The remainder is `body`.
5. `dedented = applyTextwrapDedent(body)` — **the exact existing §2.9 algorithm**
   (drop a single leading newline, strip the longest common space/tab prefix
   across non-blank lines, normalise blank lines). `indentWidth` = length of
   that common prefix.
6. `value = dedented` with **exactly one** trailing `\r?\n` removed (the
   close-fence delimiter newline). This is the one place tagged blocks diverge
   from plain triple-strings: the newline that puts the closing `"""` on its own
   line is treated as part of the fence, not the SQL (markdown semantics), so a
   well-formatted block yields clean SQL with no trailing newline. Intentional
   internal blank lines survive — only the single delimiter newline is stripped.

Steps 1 and 5 are already implemented and conformance-tested
(`walker.ts::dedent`, Kotlin `Dedent.applyTextwrapDedent`). Steps 2–4 (tag-peel)
and step 6 (single trailing-newline strip) are new, and both are pure
string-prefix/suffix work with no escaping — the lowest-risk possible extension.
**The tag is removed before dedent, so it can never reach the executed SQL.**

## 5. Tag registry (tag → language + dialect)

A single shared table, owned by `@modeler/grammar` and mirrored in Kotlin:

| Tag(s) | language | dialect |
|---|---|---|
| `sql` | `SQL` | *(project default from `modeler.toml`)* |
| `ms-sql`, `tsql`, `mssql` | `SQL` | `tsql` |
| `postgres`, `postgresql`, `pg` | `SQL` | `postgres` |
| `mysql` | `SQL` | `mysql` |
| `bigquery`, `bq` | `SQL` | `bigquery` |
| `transform` | `TRANSFORMATION_DSL` | — |
| `dataframe` | `DATAFRAME_DSL` | — |
| `relnode` | `REL_NODE` | — |

`dialect` selects the embedded SQL parser/validator. A bare `sql` defers to the
project-level dialect in `modeler.toml`; a specific tag overrides it per block.
An **unknown tag** is a diagnostic anchored on `tagSource` (§3) — the block is
still stored as raw text so the rest of the file stays analysable.

## 6. `language:` inference and deprecation

- If a `query`/`view` has a tagged block, `language`/dialect is taken from the
  tag.
- If `language:` is *also* present and disagrees with the tag → **diagnostic**
  (`language` says X, block tag says Y).
- `language:` on `query` is **soft-deprecated**: still parsed, emits a
  deprecation diagnostic recommending the tag, slated for removal in the next
  grammar-master major. This is the cross-repo coordination point — `ai-platform`
  must read `language` from the tagged block, falling back to the property only
  while it still exists.

## 7. Formatter / linter rules (style, not grammar)

The grammar accepts the tag trailing the property (`sourceText: """sql\n…`).
The formatter normalises and the linter enforces:

- **Opener on its own line.** `sourceText:` ends the line; `"""sql` starts the
  next line. (Per the agreed convention.)
- **Uniform body indent.** Every body line and the closing `"""` are indented to
  the same column. Because §4 step 5 strips the *common* prefix, uniform indent
  means `indentWidth` is a single constant for the block — which keeps the SQL
  source map a uniform per-line column shift (§8) instead of a ragged map.
- **Lint: suspicious one-line `"""sql"""`.** A triple-string whose entire
  content is a known tag (no newline/body) is almost certainly a mistaken block;
  warn with an autofix that expands it to the multi-line form.
- **No trailing space after the tag**; normalise `"""sql   \n` → `"""sql\n`.

## 8. Embedded-SQL position mapping

Once `value` is parsed by the dialect's SQL parser, every SQL token at
`(line, col)` maps back into the `.ttr` file as:

```
fileLine = valueSource.startLine + sqlLine
fileCol  = (sqlLine == 0 ? valueSource.startColumn : 0) + indentWidth + sqlCol
```

Because the formatter guarantees uniform indent, `indentWidth` is one constant
per block, so the column shift is uniform and additive — no ragged segment map,
which is the off-by-one trap the `SourceLocation` invariant warns about. Drive
LSP semantic tokens, diagnostics, hover, and go-to-definition through this single
mapping. (If a future un-formatted file has ragged indent, fall back to a
per-line `indentWidth[]` captured during dedent.)

## 9. Conformance golden cases

For `tests/conformance` (TS ⇄ Kotlin must agree on `tag`, `language`, `dialect`,
and `value` byte-for-byte). `␊` = newline, `·` = significant space.

Values reflect the full pipeline: tag-peel → `applyTextwrapDedent` → strip one
trailing newline (step 6). **Tagged blocks strip the trailing delimiter newline;
untagged triple-strings (C5) do NOT** — they follow the unchanged §2.9 path and
keep it. That asymmetry is deliberate and must be encoded in both harnesses.

| # | Source (between the `sourceText:` and close) | tag | language | dialect | value |
|---|---|---|---|---|---|
| C1 | `"""sql␊SELECT 1␊"""` | `sql` | SQL | *(default)* | `SELECT 1` |
| C2 | `"""ms-sql␊SELECT 1␊"""` | `ms-sql` | SQL | `tsql` | `SELECT 1` |
| C3 | `"""sql··␊··SELECT 1␊··"""` (uniform 2-indent + trailing hspace on opener) | `sql` | SQL | default | `SELECT 1` |
| C4 | `"""sql␊··SELECT a,␊······b␊··"""` (ragged: 2 vs 6) | `sql` | SQL | default | `SELECT a,␊····b` (common 2 stripped) |
| C5 | `"""␊plain␊"""` (untagged) | — | n/a | — | `plain␊` (kind=`tripleString`, **not** parsed, trailing `␊` kept) |
| C6 | `"""a note"""` (untagged, one line) | — | n/a | — | `a note` |
| C7 | `"""sql"""` (tag, no newline) | — | n/a | — | `sql` (plain string; lint warns) |
| C8 | `"""nope␊x␊"""` (unknown tag) | `nope` | — | — | raw `x`; **diagnostic** on tag |
| C9 | empty body `"""sql␊"""` | `sql` | SQL | default | `` (empty) |
| C10 | tag with backtick-quoted id `"""mysql␊SELECT \`id\`␊"""` | `mysql` | SQL | `mysql` | ``SELECT `id` `` (backtick survives) |
| C11 | internal blank line `"""sql␊SELECT 1␊␊FROM t␊"""` | `sql` | SQL | default | `SELECT 1␊␊FROM t` (internal blank kept, only close newline stripped) |

C10 proves the `"""` fence tolerates SQL backticks a triple-backtick fence could
not. C11 proves step 6 strips only the single close-fence newline, not
intentional internal blank lines.

## 10. Cross-repo (`ai-platform`) impact

**Consumption model (current).** The vendored-grammar / regenerate-in-ai-platform
model is **retired** (grammar-master phase-1 stage 1.8: ai-platform's vendored
`.g4` and Kotlin sources were deleted; it now consumes the published
`org.tatrman:ttr-parser` / `ttr-writer` Maven artifacts). The Kotlin parser
lives **in this repo** (`packages/kotlin/ttr-parser`), reads `TTR.g4` directly,
and is published. So the lexer/parser/value work is all *modeler* work — it does
**not** propagate into ai-platform by regeneration. (Stage 1.9 — deleting
`sync-to-ai-platform.sh` and scrubbing the stale "vendored into ai-platform"
line from `CLAUDE.md` / architecture — is still open; that stale line is not the
real model.)

**Work in THIS repo (the bulk):**
1. Grammar (§2) + TS walker (`TaggedBlockValue`, tag-peel before `dedent`).
2. Kotlin `ttr-parser`: the `TaggedBlockValue` model type + the same steps 2–4
   tag-peel in front of the existing `applyTextwrapDedent` (§2.9 is **textwrap,
   not** `trimIndent()` — keep the shared impl).
3. `ttr-writer`: round-trip the tagged block back to `"""<tag>\n…\n"""`.
4. Conformance cases (§9). Then publish a new `org.tatrman:*` version.

**Work in ai-platform (smaller, compile-forced):**
1. Bump `tatrman-modeler` in `gradle/libs.versions.toml`.
2. **Handle the new sealed-class variant.** `sourceText`'s value is the sealed
   `PropertyValue` (`StringValue | TripleStringValue`); we add `TaggedBlockValue`.
   Any exhaustive `when` that reads `query.sourceText` to execute SQL stops
   compiling until it adds a branch — and that branch reads `language`/`dialect`
   off the node. Mechanical, but real.
3. **Later, separately:** switch language reads from the deprecated `language:`
   property to the tag, then absorb its removal in the following major (§6).

**Semver (§7 / PUBLISHING.md):** additive node + variant → **minor**; but <1.0.0
and adding a sealed-class variant is source-breaking for exhaustive `when`, so it
behaves like a breaking minor. Removing `language:` later is a **major**.

Sequence: land grammar + both walkers + writer + conformance here → publish →
ai-platform bumps version and adds the `TaggedBlockValue` branch → drop
`language:` in a later major.

## 11. Resolved decisions

- **Per-block dialect — YES, supported and expected.** Multiple SQL dialects may
  coexist in one project (rare within a single file, but valid across a project).
  The tag's dialect is authoritative per block; a bare `sql` resolves to the
  `modeler.toml` project default. The specific dialect tags in §5 are first-class,
  not discouraged.
- **`relation.join` — does NOT hold SQL.** It stays on its current `list` form;
  only `sourceText` and `definitionSql` use `embeddedBlock`. No change there.
- **Indented-close newline — RESOLVED:** the single newline before the closing
  `"""` is a fence delimiter and is stripped (§4 step 6), so tagged-block values
  carry no trailing newline. Untagged triple-strings keep theirs (existing §2.9).
  Pinned by C1/C3/C5/C9/C11.
