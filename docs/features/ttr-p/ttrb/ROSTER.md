# TTR-B — sentence roster

> Inventory of every TTR-B sentence shape and the canonical TTR-P it lowers to, read off
> `packages/grammar/src/TTRB.g4` and `ttrp-frontend`'s `dialect/b/TtrbDecomposer.kt` as of the
> `feat/ag-display-schema` base (grammar 0.14). Companion to
> [`language-design.md` §7.3](../language-design.md#73-ttr-b--controlled-english) and
> [`contracts.md` §8](../architecture/contracts.md#8-diagnostics-convention).

## 1. The roster today (English only)

Every sentence ends with `.`. Keywords are case-insensitive; identifiers are ASCII
(`[a-z_][a-z0-9_]*`). `that` / `this` / `it` and the implicit subject are the previous
sentence's value (anaphora). `as <name>` binds an SSA name.

| # | Sentence shape | Lowers to (canonical TTR-P) |
|---|---|---|
| 1 | `Load [from] file "<path>" [with schema <ref>] [as <n>].` | `n = load("<path>"[, schema: ref])` |
| 2 | `Load [from] <qname> [with schema <ref>] [as <n>].` | `n = load(qname[, schema: ref])`; without a schema the name is a derived in-port |
| 3 | `Keep\|Take\|Select [only] [the] columns a [as b], c.` | `-> project(a, c)` |
| 4 | `Keep\|Take\|Select [all] [the] [columns] except\|but\|excluding a, b.` | `-> project(a, b)` |
| 5 | `Keep\|Take\|Select [only] [the] [rows] [where\|which\|that\|with] <pred>.` · `Filter [for] [the] [rows] [where…] <pred>.` | `-> filter(pred)` |
| 6 | `Remove\|Delete [the] [rows] [where…] <pred>.` | `-> filter(not(pred))` |
| 7 | `Rename [the] [columns] a to\|as b[, c as d].` | `-> calc { b = a  d = c }` |
| 8 | `Convert\|Retype [the] [type] [of] a to\|as <type>.` | `-> calc { a = cast(a as <type>) }` |
| 9 | `Create\|Compute\|Calculate [new] [column] <expr> as <n>.` | `-> calc { n = expr }` |
| 10 | `Summarize f of a [as n][, …] by\|grouped by\|group by k[, …].` | `-> aggregate { group by k  n = f(a) }` |
| 11 | `Join that\|this\|it\|<name> with <name> on <pred> [as <n>].` | `n = join(left: …, right: …, on: pred, type: inner)` |
| 12 | `Sort [that] [the rows] by a [asc\|ascending\|desc\|descending][, …].` | `-> sort(a, …)` |
| 13 | `[Keep] [only] [the] first <n> rows.` | `-> limit(n)` |
| 14 | `Combine\|Append\|Union that\|<name> with <name>.` | `-> union(name)` |
| 15 | `Store that\|[the] result\|<name> to file "<path>" \| <qname>.` | `<chain> -> store(target)` |
| 16 | `Show\|Display [me] [the] [result] [that] [as <n>].` | `n = <chain> -> display(n)` (default name `result`) |

A pipeline not ended by `Show` / `Store` becomes the container's first OUT port.

**Predicates / expressions** (the verbose skin over the one expression IR,
`ttr-b.synonyms.toml`): `is more|bigger|larger|higher than` / `comes after` (`>`), `is
less|fewer|lower|smaller than` / `comes before` (`<`), `is not more than` (`<=`), `is not less
than` (`>=`), `is` / `is equal to` / `equals` / `is the same as` (`=`), `is not` / `is not
equal to` / `does not equal` (`<>`), `is [not] empty` (`is [not] null`), `is one of (…)`
(`in`), `is [not] between a and b`; the canonical operators; `and` / `or` / `not`; `+ - * /`;
function calls; dotted column refs; numbers, `"…"` / `'…'` strings, `true` / `false`.

**Markers.** Embedded: `"""ttrb`. Bare: `*.ttrb`, or a first-line `# ttr: dialect=ttrb`
override (the docs say `dialect=b`; the code only accepted `ttrb`).

**Rejects** (`ttr-b.rejects.toml`, English): `TTRP-B-001` update · `-002` insert · `-003`
DDL · `-004` off-roster verb (catch-all) · `-005` `//` / `/*` comments · `-006` non-ASCII
letter ("English-only") · `-007` unknown verbose comparison · `-008` pivot · `TTRP-EQ-001`
`==`.

## 2. Gap list (scope of B2–B6)

| Gap | Today | Needed | Part |
|---|---|---|---|
| Keyword skins | keywords are lexer literals in `TTRB.g4`; English only | one grammar, keyword tables `roster.en.yaml` + `roster.cs.yaml`; keywords match case- and diacritic-insensitively against the active skin | B2 |
| Czech identifiers | `IDENT` is ASCII; a non-ASCII letter is `TTRP-B-006` | identifiers take the Latin-extended range `TTRP.g4` uses, matched exactly (never folded) | B2 |
| Markers | `"""ttrb`, `.ttrb`, `dialect=ttrb` | `"""ttrb-cs`, `*.ttrb-cs` with `# ttr: dialect=b lang=cs`; `lang=en` for `*.ttrb` | B2 |
| Blocks | whitespace is insignificant; no `:`; a fragment has one main line | `If <pred>:` / `Když <pred>:` + an indented block; one `filter` per block, blocks overlap; a nested block is `TTRP-B-110` | B3 |
| Actions | none | e-mail, set-field and manual-task sentences, each `calc { <schema columns> } -> select(…)` feeding `display(<kind>)` (`send_email`, `update_field`, `manual_task`) | B4 |
| Recipients | none | a column; `department "x"` → `"oddělení:x"`; `<column>, otherwise department "x"` → `coalesce(…)` | B4 |
| Count / attach | none (`count` only inside `Summarize`) | `Count the rows of T as x.` (row count, cross-joined); `Attach T to the result.` (cross join) | B4 |
| Routing to program-level displays | a fragment's `Show` is a display inside the container; host-executed (`sql-text`) bundles read action displays only from container OUT ports | an action sentence is a sink: a container OUT port + program-level `display(<kind>)` | B4 |
| File-backed fragments | interiors are always inline | `container x(…) target e from "rules/x.ttrb-cs"`; the LSP opens the file | B5 |
| Diagnostics as help | one English TOML; a malformed sentence is the generic `TTRP-B-004` | `rejects.en.yaml` / `rejects.cs.yaml` (code, message in that language, a suggested correct sentence); shape-specific help for the new sentences; every row has a triggering fixture | B6 |
| Tooling | the LSP never analyses `.ttrb`; VS Code knows `.ttrb` only | the LSP publishes TTR-B diagnostics for `ttrb` / `ttrb-cs` documents; VS Code registers `.ttrb-cs` | B6 |

Recorded, not in scope (pre-existing behaviour, unchanged here): row 3's `as` rename is parsed
but dropped; row 4 lowers to a positive `project` (schema expansion deferred, C2-b-iii); row 7
copies (`calc`) rather than renames; row 12 drops the sort direction.
