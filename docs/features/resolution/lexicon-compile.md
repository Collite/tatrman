# Lexicon compilation — the declared + metadata layers → one archive

> RV-P1.2 · `ttr-lexicon-compile` · produces the `kind: "lexicon"` snapshot archive.
> Authored-side shapes: [`lexicon-schemas.md`](lexicon-schemas.md). Normative contract: the RV
> effort's `contracts.md` §2.

## 1. What it produces

Two documents in one archive:

| Document | Contents |
|---|---|
| `lexicon.json` | header + the uniform entry table — `{term_normalized, lemma?, lang, target_ref, target_class, method, source_tag, provenance}` |
| `operator-library.json` | `op:` id → `{body, version, checksum, source}` |

They are separate because skill **frontmatter** is vocabulary and a skill **body** is not (RV-35).
The matcher loads the first and never sees the second.

**Entry kind is not stored.** Alias vs value vs operator vs grounding trigger is `target_class`,
derived at compile time (RV-38) — nothing in the artifact can disagree with the model graph.

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
  `RG-LEXC-002` build note. Dropping the wider one would lose matches somebody asked for.
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
- `CompiledLexicon.contentHash` covers the **entry table only**. It is the RV-39 layer tuple's
  `lexicon_artifact_hash`, and that tuple is asked exactly one question — *did the vocabulary
  change?* A hash that moved because the clock moved would answer it wrongly every build.

## 7. Running it

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
`tatrman-server` to register into, and under (a3) the lexicon is its own archive anyway. The
callers are the toolchain CLI and, for estates, the Modeler CLI path that already emits
`generated/`.
