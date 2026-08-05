# Lexicon compilation — the declared + metadata layers → one archive

> RV-P1.2 · `ttr-lexicon-compile` · produces the `kind: "lexicon"` snapshot archive.
> Authored-side shapes: [`lexicon-schemas.md`](lexicon-schemas.md). Normative contract: the RV
> effort's `contracts.md` §2.

## 1. What it produces

Two documents in one archive:

| Document | Contents |
|---|---|
| `lexicon.json` | header + the uniform entry table — `{term_normalized, lemma?, lang, target_ref, target_class, method, source_tag, provenance, match_profile?}` |
| `operator-library.json` | `op:` id → `{body, version, checksum, source}` |

They are separate because skill **frontmatter** is vocabulary and a skill **body** is not (RV-35).
The matcher loads the first and never sees the second.

**Entry kind is not stored.** Alias vs value vs operator vs grounding trigger is `target_class`,
derived at compile time (RV-38) — nothing in the artifact can disagree with the model graph.

**The matching profile IS stored, resolved** (RV-44). Every `DECLARED` row carries a
`match_profile` — the one the author wrote, or the expansion of the `method:` they wrote instead
(the sugar table is in [`lexicon-schemas.md` §2.1](lexicon-schemas.md)). Four services read this
artifact; expanding sugar once here is what makes "a `TYPOS(1)` row and its written-out twin behave
identically" a fact rather than a promise. `METADATA` rows carry **none** (⚑M-2), and a reader must
treat absence as *"score this row the way you always did"* — which is what keeps an estate with no
profiles byte-identical to the pre-RV-44 service. The `method` column stays, as the profile's
projection, for readers that predate profiles.

The ⚑M-4 short-term guard is **not** applied at compile time: the artifact records what the estate
authored (the build already warned, `RG-LEX-101`), and the matcher is where the rule fails to fire.
Suppressing it here would leave a reader unable to tell *"the author asked for fuzz and cannot have
it"* from *"the author asked for exact"*.

## 2. Its own archive, not entries in the model's

Ruled 2026-08-02 (option a3). The compiled lexicon is packed by the same `SnapshotWriter` under
the same determinism rules, with its own content id, and a manifest carrying
`resolvedFrom.modelSnapshotId`.

The alternative — entries beside `docs/` inside the model archive — was rejected because it breaks
that archive's invariant that *loading over an archive equals loading over the repo it was packed
from*: a compiled lexicon is derived, not authored. `SnapshotManifest` already says "one archive =
one source kind's content set", so `kind` is the designed extension point and no `formatVersion`
bump is needed.

The consequence worth stating plainly: **reading this artifact costs `ttr-snapshot` +
`ttr-lexicon`, and nothing else.**

```kotlin
val docs = SnapshotReader.read(bytes).contents.docs
val lexicon = CompiledLexicon.fromJson(docs.getValue(LexiconArchive.LEXICON))
```

No `ttr-parser`, no `ttr-metadata`, no compiler. That is the whole reason the artifact model lives
in `ttr-lexicon` while the compiler lives here, and `LexiconPackerSpec` asserts it.

## 3. The layers

`source_tag` says which layer produced a row. Only two of the four contract values are compiled:

| Layer | Source | Compiled here? |
|---|---|---|
| `DECLARED` | the `lexicon/` area **and** `model lexicon` TTR-M units | yes |
| `METADATA` | `displayLabel`, `labelPlural`, `aliases`, `valueLabels` in the model | yes |
| `DATA` | member vocabulary read from the data | no — the lex-matcher index, own refresh cadence |
| `LEARNED` | the estate overlay | no — its own store, never cross-estate |

### The two declared surfaces

An estate may author the same vocabulary two ways, and both are first-class:

```
<repo>/
├── model/…/*.ttrm        `model lexicon locale cs` + `def term X { for: …, forms: [...] }`
└── lexicon/              the RV-36 data area — aliases/ values/ grounding/ skills/
```

They are **two surfaces of one layer, not two areas** — worth saying out loud in a repo like
hartland, which already has a `model/lexicon/` directory holding the first kind. A term authored
on both surfaces compiles to one row; see §4.

Units are selected by their `model` directive, never by directory name — an estate is free to lay
`.ttrm` files out however it likes. `def pattern` and `def example` share the same body as
`def term` but are not vocabulary: a pattern is a regex and an example is a whole question, and
either in the term table would put a sentence in front of the matcher.

**Deferred:** the inline `lexicon { terms: [...] }` sugar on a carrier def. The grammar documents
it as desugaring to canonical `term` entries *in semantics*; doing it here would mean a second,
divergent implementation of package/ref resolution. The extractor consumes desugared output when
`ttr-semantics` surfaces it.

### What the metadata layer takes

Labels — never descriptions. A description is a sentence written for a human reading the model;
admitting it as a term is at best a row that never matches and at worst one that matches
something. `valueLabels` are the only member source, and they are *declared* members
(`"1" → "Aktivní"`); members read out of the data are the other layer.

Labels in languages outside `cs` · `en` · `cs|en` are skipped, because the artifact's `lang`
column is closed by `ttr-lexicon/v1`. Silently — a model richer than the lexicon schema is not an
authoring error.

## 4. Merge rules

Identity is **(normalized term, lang, target_ref)** — deliberately *not* including `method`: one
term pointing at one target with two match methods is contradictory authoring, not two entries.

- **`DECLARED` beats `METADATA`.** An author's file states an intent and carries a line number; a
  model label is a byproduct.
- Then the **widest method wins** (`TYPOS(3)` > … > `TYPOS(1)` > `TOKENS` > `EXACT`), with a
  `RG-LEXC-002` build note. Dropping the wider one would lose matches somebody asked for. Since
  RV-44 that note also fires when two rows agree on `method` but declare **different profiles** —
  otherwise the richer half of a disagreement would pass silently.
- The **same term under two different targets stays two rows.** Homonyms are legitimate; two
  bindings on one mention is what the lattice is for (RV-2). Note that P1.1 rejects a term
  declared twice *within one file* (`RG-LEX-006`) — that rule is about one author's typo, so
  cross-file homonyms are legal and reach the compiler.

Normalization is NFC → trim → collapse internal whitespace → lowercase. **Diacritics are
preserved**: folding them would silently make `vyroba` match `výroba` as `EXACT`, which is a
`TYPOS` decision the author did not make.

## 5. Dangling refs (RV-20)

Every `target_ref` is checked against the model snapshot. Absent ⇒ the row is **dropped** and a
`RG-LEXC-001` warning is emitted with the file and the line the *term* was written on — not the
`target:` line below it; a warning has to point at the word the author will search for.

**Never fatal.** A lexicon with one bad row still compiles: a model refactor should not stop a
build, it should tell you what it broke.

`op:` and `ground:` refs are classified by prefix and never consult the index. They are not model
objects, so checking them against a model snapshot would make every operator and every grounding
trigger dangle against a snapshot that will never contain it.

| Code | Meaning |
|---|---|
| `RG-LEXC-001` | target not in the model snapshot — row dropped |
| `RG-LEXC-002` | one term+target declared with two methods — widest kept |
| `RG-LEXC-003` | an estate skill overrode a stdlib op of the same id |

Schema **violations** (`RG-LEX-*`, P1.1) are reported separately from these warnings: a violation
is a broken file the author must fix, a warning is a good artifact with a row missing.

## 6. Determinism

Same inputs ⇒ same bytes ⇒ same id, proved by `LexiconPackerSpec` and `EstateBuildSpec`. Nothing
in the compiler reads a clock, a locale, a file system order or a hash-map iteration order:

- `builtAt` is a **parameter**. A build passes the model snapshot's stamp or `SOURCE_DATE_EPOCH`.
  `Instant.now()` inside the compiler would make byte-determinism impossible to state.
- Files are walked sorted; the entry table is sorted; the operator map's keys are sorted, because
  a JSON object's key order is part of the artifact's bytes.
- The per-layer `source_hashes` cover the profile too: an edit that changes only *how* a term
  matches is still an edit to the declared layer.
- `CompiledLexicon.contentHash` covers the **entry table only**. It is the RV-39 layer tuple's
  `lexicon_artifact_hash`, and that tuple is asked exactly one question — *did the vocabulary
  change?* A hash that moved because the clock moved would answer it wrongly every build.

## 7. The operator standard library (RV-P1.3)

Six operators, ruled ⚑RV-2, shipped as ordinary `ttr-skill/v1` files under
`ttr-lexicon-compile/src/main/resources/lexicon-stdlib/skills/` and compiled through exactly the
path an estate's own skills take — no special case anywhere.

| Op | cs triggers | en triggers | `requires` |
|---|---|---|---|
| `op:show` | ukaž · zobraz · vypiš | show · display · list | — |
| `op:trend` | vývoj · trend | evolution · trend · over time | `time-grain` |
| `op:compare` | porovnej · srovnej · srovnání | compare · versus · vs | `two-series` |
| `op:drilldown` | rozpad · rozpad podle · detail podle | drill down · breakdown · break down | `parent-context` |
| `op:top-n` | prvních · top · nejlepších · největších | top · first · largest | `order-measure` |
| `op:share-of` | podíl · procento z | share · share of · percentage of | — |

**They live here, not in `tatrman-server`,** which is what the P1.3 list assumed. The stdlib is
*compiler input*: the compile happens in the toolchain, and the toolchain cannot depend on the
server, so a server-side stdlib would leave every estate build with no operators to layer under
its own files. Same reasoning that moved the validator here under (a3).

Files rather than Kotlin constants, because an operator body is prose a non-engineer should be
able to read and revise, and it has to diff as prose.

`LexiconStdlibSpec` gates two things a schema cannot: that the six are all present and nothing
else is, and that **no two operators answer to the same word in an overlapping language**
(`cs|en` overlaps both, so a collision cannot hide behind a lang label). Two operators sharing a
trigger make the lattice ambiguous for every question containing it, and no downstream layer can
undo that.

`LexiconBuild.run(..., includeStdlib = true)` layers them **under** the estate's own skills, which
is the precedence statement the compiler reads: an estate redefining `op:trend` wins, and the
build note names both files.

## 8. Running it

```kotlin
val outcome = LexiconBuild.run(repoRoot, model, modelSnapshotId, builtAt, producedBy = "veles 0.11.2")
outcome.violations   // P1.1 schema rejections — broken files
outcome.result.warnings
outcome.packed.id    // sha256: over the archive bytes
```

**The flag is the files.** A repo with no `lexicon/` directory and no `model lexicon` unit builds
exactly as it did before: the declared layer is empty and no warning is produced. The metadata
layer still compiles — it is a layer of the *model* (RV-39), so the artifact's existence does not
wait on anyone authoring their first alias.

There is deliberately **no snapshot-pipeline registration**: there is no model-snapshot build in
`tatrman-server` to register into, and under (a3) the lexicon is its own archive anyway.

## 9. The CLI (RV-P3.1)

`LexiconBuild.run` above is a library function; the thing an estate actually runs is
`packages/kotlin/ttr-lexicon-cli`:

```
ttr-lexicon build <repoRoot> --out <path> [--check] [--no-stdlib] [--verbose]
                             [--built-at <iso>] [--produced-by <string>]
```

| Exit | Meaning |
|---|---|
| `0` | built (or, with `--check`, up to date). Dangling refs are warnings and land here. |
| `2` | the build failed — `RG-LEX-*` violations, or a model that would not load. **Nothing is written.** |
| `3` | `--check` only: the archive at `--out` is stale, or absent. |

The codes match `resolve-packages`, which is the artifact-check recipe an estate already runs.

**Three inputs the command supplies for you**, all three chosen so the archive id is a pure
function of the sources — otherwise `--check` would report drift on every invocation and mean
nothing:

- **the model** — loaded from `<repoRoot>/model` through `MetadataLoader` over
  `FileBasedSource`/`LocalFsStorage`, the composition `ttr-metadata` already has. A model that
  will not load is **fatal**: `ModelRefIndex` is what tells RV-20 whether a `targetRef` exists, so
  over a half-loaded model every ref looks dangling and the build would drop the whole declared
  layer, warn, and still exit zero. The one tolerated diagnostic is
  `ttr/package-declaration-mismatch` — `QualifiedName.dotted()` drops the package, so it provably
  cannot change a lookup, and hartland declares `package hartland` in all 26 model files on
  purpose (BM-9, `[packages] layout = "off"`).
- **`modelSnapshotId`** — `sha256:` over every `.ttr`/`.ttrm` under `model/`, path and bytes, in
  sorted order. **Not** `LocalFsStorage.fetchVersion()`, which hashes mtimes and therefore differs
  on a fresh clone of an unchanged estate.
- **`builtAt`** — `SOURCE_DATE_EPOCH` if set, else the epoch. Never the clock.

`--check` compares **ids** (`SnapshotId.of` over the file's bytes vs the id of a fresh compile),
never raw bytes against freshly-compressed ones — the latter would test the zstd version rather
than the vocabulary.

### Target refs — the shape, for every schema

Every target ref is **kinded**: `<schema>.<kind>.<name>`, then attribute depth, then the member
code. This is what `QualifiedName.dotted()` renders and what TTR-M's own cross-refs use
(`from: er.entity.store_sales`), so there is one rule, not one per schema.

| depth | er / db | md |
|---|---|---|
| object | `er.entity.customer` | `md.measure.revenue` · `md.dimension.Product` · `md.cubelet.storeSales` |
| attribute | `er.entity.customer.status` | `md.dimension.Customer.state` |
| member | `er.entity.customer.status.1` | `md.dimension.DistributionCentre.dcCode.5` |

Two shapes that look right and are **not**:

- **`er.attribute.status`** — appears in no loaded model. It survived in fixtures only because
  nothing read their `model/` directory until this command existed.
- **`md.revenue`, `md.Customer.state`** — the *unkinded* form TTR-M uses for md cross-refs inside a
  `.ttrm` (`domain: md.Money`). Lexicon targets do not follow it: md is six independent name maps
  with no cross-kind uniqueness, and the two grammars collide at the same arity in opposite
  directions. The kind token is what makes `md.dimension.Product` unambiguous.

Only `measure`, `dimension`, `cubelet` (and attribute/member depth under a dimension) are
addressable. `domain`, `hierarchy` and `map` are **deliberately** not: a domain is a type rather
than something a query selects, a hierarchy is a navigation structure, and a map is binding
plumbing. A ref naming one dangles like any other unknown.

Getting a ref wrong is not an error — RV-20 drops the row with an `RG-LEXC-001` warning and the
build still succeeds. So an authoring pass with the wrong shape "works" and indexes nothing. Read
the warning count.

### Invoking it from an estate

`installDist` lays the command down at
`packages/kotlin/ttr-lexicon-cli/build/install/ttr-lexicon/bin/ttr-lexicon`, so a sibling checkout
runs:

```just
build-lexicon cli="../tatrman/packages/kotlin/ttr-lexicon-cli/build/install/ttr-lexicon/bin/ttr-lexicon":
    {{cli}} build "$(pwd)" --out generated/lexicon.tar.zst
```

⚑ **Note the asymmetry.** An estate's existing model build calls a **TypeScript** CLI
(`node ../tatrman/packages/migrate/dist/cli.js resolve-packages`); this one is **Kotlin**, forced
by the (a3) ruling — the compiler and the packer are Kotlin. So an estate that builds both
artifacts now needs **two toolchains** (Node and a JVM). Recorded rather than solved: collapsing
them would mean either a Kotlin package resolver or a TypeScript lexicon compiler, and neither is
worth it for one command.
