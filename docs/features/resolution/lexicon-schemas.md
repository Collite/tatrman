# Lexicon area — schemas and error catalogue

> `ttr-lexicon/v1` · `ttr-skill/v1` — the authored files of a defining repo's **lexicon data
> area**, the sibling of `model/` (RV-36..38, RV-42). Normative shapes: the RV effort's
> `contracts.md` §2. This page documents what the schemas enforce and every error an author
> can hit, with an example of each.
>
> Implementation: `packages/kotlin/ttr-lexicon` (schemas packaged under
> `resources/schemas/`, so a consumer validates against the same bytes this documents).
>
> **The schemas are the contract; the Kotlin validator is the JVM enforcement of it.** They are
> two expressions of one ruleset, and `SchemaEquivalenceSpec` runs every fixture through both —
> a disagreement in either direction fails the build. The split exists so a published toolchain
> artifact, resolved by every TTR-P project, does not ship a JSON-Schema engine (and its Jackson
> stack) to enforce rules it already enforces; networknt stays test-scope here, as it is in
> `ttrp-emit`, `ttrp-cli` and `ttrp-lsp`.

## 1. Where files live

```
<defining-repo>/
├── model/                       # the TTR-M model — unchanged
└── lexicon/                     # this area
    ├── aliases/*.lex.yaml       # ttr-lexicon/v1 — terms → model refs
    ├── values/*.lex.yaml        # ttr-lexicon/v1 — terms → member refs
    ├── grounding/*.lex.yaml     # ttr-lexicon/v1 — terms → `ground:` classes (RV-42)
    └── skills/*.md              # ttr-skill/v1 frontmatter + a Golem-side behavior body
```

Files outside those four directories are ignored, not rejected — notes may live beside a
lexicon. **Entry kind is never authored**: alias vs value is derived from the target class
(RV-38), so a file cannot disagree with the model graph about what it declares.

## 2. `ttr-lexicon/v1` — data files

```yaml
schema: ttr-lexicon/v1
defaults: { lang: cs, method: TYPOS(1) }     # optional; a term's own keys always win
entries:
  - terms:
      - { text: "středisko" }
      - { text: "cost center", lang: en, method: EXACT }
    target: er.CostCenter
```

| Key | Required | Values |
|---|---|---|
| `schema` | yes | exactly `ttr-lexicon/v1` |
| `defaults.lang` / `defaults.method` | no | as per the term keys below |
| `entries[].terms[].text` | yes | non-empty string |
| `entries[].terms[].lang` | no | `cs` · `en` · `cs\|en` |
| `entries[].terms[].method` | no | `EXACT` · `TOKENS` · `TYPOS(1)`…`TYPOS(3)` |
| `entries[].target` | yes | any model-graph ref, member ref, or `ground:` class |

Both schemas are **closed** (`additionalProperties: false`): an unknown key is an error, so a
typo fails the build instead of silently contributing nothing.

**Fallbacks when neither the term nor `defaults` says:** `method` → `EXACT`, `lang` →
`cs|en`. ⚠ Assumed at RV-P1.1, not ruled — see the note in `LexiconValidator.DEFAULT_METHOD`.
`target` refs are **not** resolved here; dangling refs are the compiler's business (RV-P1.2,
drop + build warning per RV-20).

## 3. `ttr-skill/v1` — skill frontmatter

```markdown
---
schema: ttr-skill/v1
op: op:trend                       # the `op:` ref class
triggers:
  - { text: "vývoj", lang: cs, method: TYPOS(1) }
  - { text: "trend", lang: cs|en, method: EXACT }
requires: [ time-grain ]           # applicability, validated at compose time
version: 1
---
Retrieval: group by the finest requested time grain; order chronologically.
Formatting: line chart default; period column first.
```

The **frontmatter compiles into the lexicon** as ordinary vocabulary — operator words get
match methods, aliasing and learning like any other term. The **body is not vocabulary**: it
is a Golem-side behavior artifact, kept verbatim, packaged into the operator-library artifact
by op id. The matcher never reads a body.

Frontmatter must open with `---` as the first non-blank line and close at the next line that
is exactly `---`. A `---` later in the body is a horizontal rule and is left alone. A BOM and
leading blank lines are tolerated; CRLF is normalised so a Windows-authored skill compiles to
the same bytes as a Unix one.

## 4. Error catalogue (`RG-LEX-*`)

Every code is an **ERROR** — each rejects a file at build time, so none has a degraded mode. Ids
follow the `RG-<AREA>-<NNN>` convention the resolution & grounding services use, and every one is
constructed in `LexiconErrors` (whose `ALL` is the catalogue's own index), so the set cannot grow
an undocumented member. Messages quote the authored value and the line it was written on.

| Code | Condition | Example that triggers it |
|---|---|---|
| `RG-LEX-001` | Unknown match method | `{ text: "středisko", method: FUZZY }` |
| `RG-LEX-002` | Required key missing | an entry with `terms:` but no `target:` |
| `RG-LEX-003` | `TYPOS` without a distance | `method: TYPOS` — write `TYPOS(1)` |
| `RG-LEX-004` | Skill `op` is not an `op:` ref | `op: trend` instead of `op: op:trend` |
| `RG-LEX-005` | Skill declares no triggers | `triggers: []` |
| `RG-LEX-006` | Duplicate term in one file | `"středisko"`/`cs` declared under two targets |
| `RG-LEX-007` | Unknown key (closed schema) | `entrys:` instead of `entries:` |
| `RG-LEX-008` | `schema:` id mismatch | `schema: ttr-lexicon/v2` in a v1 file |
| `RG-LEX-009` | Frontmatter missing or unterminated | a skill file that is only prose |
| `RG-LEX-010` | Unsupported `lang` | `lang: czech` — use `cs` |
| `RG-LEX-011` | File does not parse as YAML | a tab-indented block, an unclosed quote |
| `RG-LEX-012` | Unknown grounding kind (RV-42) | `target: ground:weather` — the set is closed: `ground:chrono` \| `ground:money` \| `ground:geo` |

Every row is backed by a fixture under
`packages/kotlin/ttr-lexicon/src/test/resources/lexicon-schema-fixtures/`, and the spec
asserts the **specific** code per fixture — "it failed somehow" is not the contract.

**A whole area reports every bad file, not just the first.** Lexicons are authored in bulk;
failing on file 1 of 40 turns one editing session into forty.

## 5. What this library does not do

Compilation (normalization, the uniform entry table, model-snapshot ref checking, packing) is
RV-P1.2 — `ttr-lexicon-compile`, documented in [`lexicon-compile.md`](lexicon-compile.md).
Layered serving is RV-P1.4. This library stops at *"the authored files are well-formed, and here
they are as typed objects with provenance"* — plus the compiled artifact's **model and codec**
(`Artifact.kt`), which live here rather than in the compiler so a serving consumer reads a
lexicon archive without resolving `ttr-parser`, `ttr-metadata` or the compiler itself.
