# TTR Grammar — v1 → v1.1 Changes

**Status:** Draft v1, 2026-05-18. Coordination document for the ai-platform Kotlin parser maintainer. Pairs with the Modeler v1.1 release; design rationale in [`v1.1-packages-and-graphs.md`](v1.1-packages-and-graphs.md).

## 1. Audience and purpose

This document is the contract between the Modeler v1.1 release and the ai-platform metadata service. It describes every change Modeler is making to the TTR grammar and the resolution rules so that ai-platform's Kotlin parser can be regenerated and the metadata loader can be updated to match.

The change introduces three new top-level constructs (`package`, `import`, `graph`) and a new resolution chain. It is a **breaking change** to the grammar — files that use the new constructs will not parse against v1, and the resolver's behaviour for cross-references changes. Old files (no `package` declaration, no `import`s) continue to parse and resolve under v1.1 via the "default package" rule (§3.6 below).

Modeler ships v1.1 independently of ai-platform's grammar adoption (decision B12 in the design doc). There is a coordination window during which Modeler emits v2-grammar files and ai-platform still parses v1-grammar files. Bora coordinates ai-platform's adoption timeline; this document is the spec ai-platform implements against.

## 2. Grammar version bump

- **Old version:** `1.x.x` (current TTR grammar shipped with Modeler v1)
- **New version:** `2.0.0`
- **Bump reason:** new reserved keywords (`package`, `import`, `graph`), new top-level rules, new file kind (`.ttrg`). Major bump per semver.

The version string lives in two places that need to update in lockstep:

1. `packages/grammar/package.json` — `"version": "2.0.0"`
2. The `// TTR (Tatrman) vN grammar — ...` header comment at the top of `TTR.g4`

ai-platform's vendored copy should update both files when it adopts.

## 3. Grammar changes

### 3.1 New lexer tokens

Add these tokens before `IDENT` so the keyword-vs-identifier disambiguation works (ANTLR4's longest-match-then-first-listed rule):

```
PACKAGE : 'package' ;
IMPORT  : 'import' ;
GRAPH   : 'graph' ;
STAR    : '*' ;        // wildcard for `import x.y.*`
```

`STAR` is genuinely new punctuation — no production in v1 uses `*`. Place it with the other punctuation tokens (near `DOT`, `COMMA`, etc.).

### 3.2 Top-level rule change

The `document` rule changes from:

```
document
  : schemaDirective? definition* EOF
  ;
```

to:

```
document
  : packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF
  ;
```

A document is either:
- A v1-style document (no `package`, no `import`s, optional `schemaDirective`, definitions) — still legal
- A v1.1 `.ttr` document: optional `packageDecl`, zero or more `importDecl`s, optional `schemaDirective`, definitions
- A v1.1 `.ttrg` document: optional `packageDecl`, zero or more `importDecl`s, **one** `graphBlock`, no definitions

The grammar itself does not enforce the `.ttr` vs `.ttrg` distinction (i.e., a file with both a `graphBlock` and definitions is grammatically valid). The semantic layer enforces "one or the other" via the `ttr/wrong-file-kind` diagnostic. Open question for ai-platform: do you want this enforced at parse time instead? Either is fine on our side; if you'd prefer a grammar-level disambiguation, we can split into separate parser entry points (e.g. `ttrDocument` and `ttrgDocument`) — flag it during review.

### 3.3 New parser rules

```
packageDecl
  : PACKAGE qualifiedName
  ;

importDecl
  : IMPORT qualifiedName (DOT STAR)?
  ;

graphBlock
  : GRAPH id LBRACE (graphProperty (COMMA? graphProperty)* COMMA?)? RBRACE
  ;

graphProperty
  : graphSchemaProperty
  | descriptionProperty
  | tagsProperty
  | graphObjectsProperty
  | graphLayoutProperty
  ;

graphSchemaProperty
  : SCHEMA propSep? schemaCode
  ;

graphObjectsProperty
  : OBJECTS propSep? LBRACK ( id (COMMA id)* )? COMMA? RBRACK
  ;

graphLayoutProperty
  : LAYOUT propSep? object_
  ;

qualifiedName
  : id              // reuses the existing dotted-identifier rule
  ;
```

The new keyword lexer tokens needed by these productions:

```
OBJECTS : 'objects' ;
LAYOUT  : 'layout' ;
```

(`SCHEMA` already exists; reused.)

### 3.4 `idPart` extension

The existing `idPart` rule accepts schema-code and kind keywords as identifier components (so cross-references like `er.entity.X` parse with `ER` and `ENTITY` tokens treated as ID-ish). Extend it to include the new keywords for consistency:

```
idPart
  : IDENT
  | DB | ER | MAP | QUERY | CNC
  | ROLE | ER2CNC_ROLE
  | TABLE | VIEW | COLUMN | INDEX | CONSTRAINT
  | FK | PROCEDURE | ENTITY | ATTRIBUTE | RELATION
  | ER2DB_ENTITY | ER2DB_ATTRIBUTE | ER2DB_RELATION
  | MODEL
  | NAME | LABEL | DIRECTION
  | FROM | TO
  | PACKAGE | IMPORT | GRAPH        // <-- NEW
  | OBJECTS | LAYOUT                // <-- NEW
  ;
```

This means a user can still write `def attribute package { ... }` if they really want to — unusual, but consistent with how `name` and `label` are handled today.

### 3.5 Diff against current `TTR.g4`

Concise unified-diff sketch:

```diff
--- a/TTR.g4 (v1)
+++ b/TTR.g4 (v1.1)
@@ -16,11 +16,33 @@
 // ----- Top level -----

 document
-  : schemaDirective? definition* EOF
+  : packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF
   ;

+packageDecl
+  : PACKAGE qualifiedName
+  ;
+
+importDecl
+  : IMPORT qualifiedName (DOT STAR)?
+  ;
+
+graphBlock
+  : GRAPH id LBRACE (graphProperty (COMMA? graphProperty)* COMMA?)? RBRACE
+  ;
+
+graphProperty
+  : graphSchemaProperty | descriptionProperty | tagsProperty
+  | graphObjectsProperty | graphLayoutProperty ;
+graphSchemaProperty  : SCHEMA  propSep? schemaCode ;
+graphObjectsProperty : OBJECTS propSep? LBRACK (id (COMMA id)*)? COMMA? RBRACK ;
+graphLayoutProperty  : LAYOUT  propSep? object_ ;
+
+qualifiedName : id ;
+
 schemaDirective
   : SCHEMA schemaCode ( NAMESPACE id )?
   ;
@@ -325,6 +347,8 @@ idPart
   | MODEL
   | NAME | LABEL | DIRECTION
   | FROM | TO
+  | PACKAGE | IMPORT | GRAPH
+  | OBJECTS | LAYOUT
   ;

@@ -340,6 +364,12 @@ DEF        : 'def' ;
 SCHEMA     : 'schema' ;
 NAMESPACE  : 'namespace' ;

+PACKAGE    : 'package' ;
+IMPORT     : 'import' ;
+GRAPH      : 'graph' ;
+OBJECTS    : 'objects' ;
+LAYOUT     : 'layout' ;
+
 DB    : 'db' ;
 ER    : 'er' ;
@@ -456,4 +486,5 @@ LPAREN : '(' ;
 RPAREN : ')' ;
 DOT    : '.' ;
+STAR   : '*' ;
```

(Line numbers are approximate against the current `TTR.g4`; treat the diff as illustrative — the canonical change lands when Modeler's `pnpm --filter @modeler/parser run prebuild` regenerates cleanly.)

### 3.6 Backward compatibility

- A v1 `.ttr` file with no `package` declaration parses cleanly under v1.1 — `packageDecl?` is optional.
- A v1 `.ttr` file with no `import`s parses cleanly under v1.1 — `importDecl*` is optional.
- Files in the "default (empty) package" continue to behave like v1 for cross-reference resolution within the same project, because every other file is also in the default package and same-package refs need no import (resolution rule §4.2 step 2).

The semantic layer flags missing `package` declarations as info-level (`ttr/missing-package-declaration`), not error — projects that haven't migrated still work.

## 4. Resolution rule changes

This is the part with the most subtlety for ai-platform's loader. The v1 resolver was:

```
1. Lexical scope (attribute-within-entity, etc.)
2. Project symbol table (any def, anywhere in the project)
```

The v1.1 resolver is:

```
1. Lexical scope                          (unchanged from v1)
2. Same-package symbols                   (NEW — "you don't import your siblings")
3. Named imports                          (NEW)
4. Wildcard imports (non-recursive)       (NEW)
5. Auto-imports (cnc.*)                   (NEW — see §4.3)
6. Fully-qualified name                   (NEW — always works if symbol exists)
```

A bare reference (e.g. `er.entity.artikl`) tries steps 1–5 in order; a fully-qualified reference (e.g. `billing.invoicing.er.entity.artikl`) tries step 1 then jumps to step 6.

### 4.1 Qname shape change

v1 qname: `<schema>.<namespace-or-kind>.<defName>[.<subDef>]`
v1.1 qname: `<package>.<schema>.<namespace-or-kind>.<defName>[.<subDef>]`

Examples:

| v1                          | v1.1                                          |
| --------------------------- | --------------------------------------------- |
| `er.entity.artikl`          | `billing.invoicing.er.entity.artikl`          |
| `db.dbo.QZBOZI_DF.IDZBOZI`  | `billing.invoicing.db.dbo.QZBOZI_DF.IDZBOZI`  |
| `cnc.role.fact`             | See §4.3 — special-cased                      |

For files in the default (empty) package, the v1.1 qname omits the leading dot — `er.entity.artikl` parses and resolves to itself, matching v1 behaviour exactly. This is what keeps unmigrated files working.

### 4.2 Same-package and import rules in detail

**Same package (step 2):** any `def` in the same package as the reference site resolves by bare name without any `import`. Two files in package `billing.invoicing` can refer to each other's defs as if they were in one file.

**Named imports (step 3):** `import billing.products.er.entity.produkt` makes `er.entity.produkt` (the suffix after the package) resolvable bare. The named-import form must include the full `<schema>.<namespace-or-kind>.<defName>` suffix — bare-defName imports (`import billing.products.produkt`) are not legal.

**Wildcard imports (step 4):** `import billing.products.*` makes every top-level def in package `billing.products` resolvable bare. **Non-recursive**: defs in `billing.products.subordinates` are *not* in scope from `import billing.products.*`.

**Ambiguity (step 4 conflicts):** if two wildcard imports both expose a def with the same bare name, the reference is ambiguous; emit `ttr/ambiguous-reference` and require the user to either disambiguate with a fully-qualified name (step 6) or replace one wildcard with a named import.

### 4.3 Stock vocabulary

The six built-in CNC roles (`fact`, `dimension`, `structural`, `master`, `transaction`, `bridge`) are implicitly auto-imported in every file, as if `import cnc.*` appeared at the top.

The qname these resolve to depends on the open question in Modeler's design doc §13.10 (decision pending during Modeler's sub-phase 1.1.B). The three options under consideration:

| Option | Stock qname  | Pro                                                         | Con                                       |
| ------ | ------------ | ----------------------------------------------------------- | ----------------------------------------- |
| a      | `cnc.cnc.role.fact` | Strictly follows the `<package>.<schema>.<ns-or-kind>.<defName>` rule | Doubled `cnc.` is ugly                    |
| b      | `cnc.role.fact`     | No duplication; matches v1                                  | Adds a special-case to the qname rule (when package name == schema code, omit package) |
| c      | `stock.cnc.role.fact` (or similar) | Cleaner naming with no special case          | Migration of every existing reference                       |

Modeler is leaning toward **option (b)** as the cleanest user-facing form. ai-platform's input is welcome — this is one of the few changes that affects every existing TTR file's references to stock vocab.

**Action item for ai-platform:** confirm preference; the final answer goes into both this doc and the design doc §13.10 before Modeler v1.1 ships.

### 4.4 Package = directory rule

The directory containing `modeler.toml` is the single classpath root. A `.ttr` file at `<root>/foo/bar/baz.ttr` is in package `foo.bar`. The file's declared `package` must match this inferred package or a `ttr/package-declaration-mismatch` error fires.

ai-platform's loader: if you walk the directory tree to discover `.ttr` files (which I believe you do), you can compute the package the same way. If you read files in some other order (e.g. flat from a config), the package is whatever the `package` declaration says.

### 4.5 New diagnostics

These are the new diagnostic codes Modeler's LSP emits. ai-platform's metadata loader may or may not need to emit them depending on how strict its loading is. Recommended: emit at least the error-severity ones during loading.

| Code                                   | Severity | When                                                              |
| -------------------------------------- | -------- | ----------------------------------------------------------------- |
| `ttr/unimported-reference`             | Error    | Bare ref to a def in a non-imported package                       |
| `ttr/ambiguous-reference`              | Error    | Bare ref matches defs in 2+ wildcard-imported packages            |
| `ttr/package-declaration-mismatch`     | Error    | Declared `package X.Y` doesn't match directory `X/Z/`             |
| `ttr/wrong-file-kind`                  | Error    | `.ttrg` with no `graph` block, or `.ttr` with one                 |
| `ttr/unused-import`                    | Warning  | `import` whose targets are never referenced                       |
| `ttr/wildcard-with-no-matches`         | Warning  | `import x.y.*` where `x.y` has no defs                            |
| `ttr/duplicate-import`                 | Warning  | Same package imported twice, or named shadows wildcard            |
| `ttr/circular-package-dependency`      | Warning  | Package A imports B, B imports A                                  |
| `ttr/missing-package-declaration`      | Info     | File has no `package` keyword (in the default/empty package)      |
| `ttr/graph-object-not-found`           | Warning  | `.ttrg` `objects` lists a qname that doesn't resolve              |
| `ttr/graph-layout-stale-node`          | Warning  | `.ttrg` `layout.nodes` references a qname not in `objects`        |

## 5. `.ttrg` file kind

`.ttrg` files use the same grammar as `.ttr` (same parser, same lexer). The only structural difference is that a `.ttrg` file contains exactly one `graphBlock` and no `definition`s. Modeler treats `.ttrg` as a separate language for the purposes of editor registration (file icon, file-extension binding) but shares all parsing and semantic infrastructure.

ai-platform: you do **not** need to load `.ttrg` files. They are editor-side artefacts only — they describe how a slice of the model is rendered, not what the model contains. ai-platform's metadata loader can ignore them entirely (treat `.ttrg` as a non-loadable extension, alongside whatever else it filters out).

If you do want to load them for some reason (e.g. validation that a graph references real entities), the AST structure is straightforward — `graphBlock` is a sibling of `definition` in the document tree.

## 6. Testing recommendations

Test cases that should pass on the new grammar and resolver:

1. **v1 backward-compatibility:** every file in Modeler's `samples/v1-metadata/` and `samples/v1-mini/` (which have no `package` declarations) parses and resolves cleanly under v1.1.
2. **Default-package cross-references:** two files both with no `package` declaration referring to each other resolve correctly.
3. **Same-package cross-references:** two files both declaring `package billing.invoicing` referring to each other without `import` resolve correctly.
4. **Named import:** file A in `billing.invoicing` with `import billing.products.er.entity.produkt` can reference `er.entity.produkt` bare.
5. **Wildcard import:** file A in `billing.invoicing` with `import billing.products.*` can reference any top-level def in `billing.products`.
6. **Wildcard non-recursion:** `import billing.products.*` does *not* expose defs in `billing.products.subordinates`.
7. **Fully-qualified reference always works:** `billing.products.er.entity.produkt` resolves from any file regardless of imports.
8. **Auto-imported cnc:** `cnc.role.fact` (or whatever §4.3 settles on) resolves with no explicit import.
9. **Diagnostics:** one fixture per new diagnostic code, asserting the code fires.

Test cases that should fail:

1. **Bare unimported reference:** file in `billing.invoicing` referring bare to a def in `billing.products` without an import → `ttr/unimported-reference`.
2. **Wildcard ambiguity:** two wildcards expose conflicting bare names → `ttr/ambiguous-reference`.
3. **Package mismatch:** file at `<root>/foo/bar/baz.ttr` declaring `package foo.qux` → `ttr/package-declaration-mismatch`.

Modeler will share its full fixture set under `samples/v1.1-*/` and `samples/broken/v1.1/` once sub-phases 1.1.B and 1.1.G land. ai-platform's existing 17-case parser suite should also be re-run against the v2 grammar to confirm no regressions.

## 7. Coordination plan

1. **Modeler lands the grammar on a feature branch.** CI verifies the TS parser regenerates cleanly and all v1 samples still parse. Modeler internal review.
2. **This document is finalised** based on what shipped in 1.1.A. Modeler updates `grammar-v1-1-changes.md` with any deviations from this draft.
3. **ai-platform's parser maintainer reviews** this doc. Open questions resolved (especially §4.3 — stock-vocab qname). Sign-off.
4. **Modeler merges 1.1.A** and continues with subsequent sub-phases. Bora coordinates ai-platform's grammar bump on their own schedule.
5. **ai-platform vendors `TTR.g4` v2.0.0**, regenerates the Kotlin parser, updates the metadata loader's resolution chain per §4 above, ships their release.
6. **Modeler's grammar-sync CI**, which was set to "warn on drift" during the coordination window, returns to "block on drift" once ai-platform is on v2.0.0.
7. **Modeler v1.1.0 ships independently** of step 5 (per decision B12). The coordination window may overlap with Modeler v1.1's release — that's fine. During the window, ai-platform consumes v1-grammar TTR files; once it's on v2 it can consume both.

## 8. Open items

~~1. **§4.3 — stock-vocab qname.** Awaiting ai-platform's preference among options (a)/(b)/(c).~~ **RESOLVED (corrected 2026-06-19, design B23):** option **(a)** retained for now — the **doubled** internal qname `cnc.cnc.role.<defName>` is canonical, with auto-import of `cnc.*` so files reference `cnc.role.<defName>` bare. This matches live `ai-models/model-ttr` data, which already writes `role: cnc.cnc.role.structural`, and matches contracts §3.1 ("accept the doubled form for v1.1"). The earlier "option (b)" note was inconsistent with shipped data and is withdrawn; option-(b) de-duplication is deferred to a separately-tracked cleanup with its own migration (design §14.7 open question 4). **No action needed from ai-platform now** beyond continuing to accept the doubled form.

~~2. **§3.2 — grammar-level vs semantic-level enforcement** of "one or the other" (graph block vs definitions).~~ **RESOLVED:** semantic-level enforcement. The grammar accepts both a `graphBlock` and `definition`s in the same document; Modeler's validator enforces the mutual exclusivity and emits `ttr/wrong-file-kind` for violations.

~~3. **§4.5 — diagnostic emission** during ai-platform's metadata loading.~~ **RESOLVED:** guidance given in §4.5 table. At minimum, emit `ttr/unimported-reference` (Error) and `ttr/wrong-file-kind` (Error) during load. Others are LSP-editor signals that don't need to block loading.

4. **Coordination window duration.** Bora to confirm when ai-platform is ready to consume v2.0.0; once known, Modeler updates the sync CI's "warn vs block" mode accordingly.

These items are non-blocking for Modeler 1.1.A merging but need resolution before Modeler v1.1.0 ships.

## 9. Addendum (2026-06-19) — nested packages, declaration authority, and the `.ttrd` domain file

A second design pass (design doc §14) finalised the package model against the live `ai-models` consumer. Three things in here affect ai-platform's loader; the rest is editor-only and ai-platform can ignore it.

### 9.1 Nested packages are confirmed (affects loader)

Multi-segment dotted packages (`prodeje.regional`) are now an explicit requirement, not just a grammatical possibility. The grammar is unchanged — `qualifiedName : id` already accepts dotted names — but the loader must not assume a single path segment. Any code that maps "package ⇄ directory" must handle `a/b/c.ttr → a.b` (already the rule in §4.4), and any code that parses an entity reference by position (e.g. splitting `pkg.entity` on the first dot) is now wrong: under nesting, the package portion can be multiple segments. Resolve entity/qname references through the symbol table, not by string position.

### 9.2 Declaration is authoritative; mismatch severity is configurable (affects loader strictness)

Revises §4.4. The in-file `package` declaration is now the source of truth; the directory is a *checked convention*. A declared package that doesn't match the directory is no longer an unconditional `ttr/package-declaration-mismatch` **Error** — its severity is set by a new `modeler.toml` knob, `[packages].layout` (`"flexible"`=Warning default, `"strict"`=Error, `"off"`=silent). There is also an optional `[packages].root` prefix prepended to *directory-derived* names, elidable in references (so unprefixed references keep resolving). For ai-platform's loader: read the declaration as truth; if you also derive from path, treat divergence per the project's `[packages].layout` setting rather than hard-failing. A new Warning, `ttr/package-prefix-divergence`, flags the dangerous case where a declaration's non-leaf segments differ from its path.

### 9.3 New `.ttrd` domain file (editor-only — ai-platform does NOT load it)

A new file kind, `.ttrd`, declares **domains** — named, recursive groupings of packages (+ individual entities) used to scope downstream consumers (Golem agents). It shares the parser/lexer with `.ttr`/`.ttrg`. New tokens/rule (final spelling settled in Modeler PD2):

```
DOMAIN   : 'domain' ;
PACKAGES : 'packages' ;
ENTITIES : 'entities' ;

document
    : packageDecl? importDecl* (schemaDirective | graphBlock | domainBlock)? definition* EOF
    ;

domainBlock
    : DOMAIN id LBRACE (domainProperty (COMMA? domainProperty)* COMMA?)? RBRACE
    ;

domainProperty
    : descriptionProperty
    | tagsProperty
    | domainPackagesProperty
    | domainEntitiesProperty
    ;

domainPackagesProperty : PACKAGES propSep? LBRACK ( id (COMMA id)* )? COMMA? RBRACK ;
domainEntitiesProperty : ENTITIES propSep? LBRACK ( id (COMMA id)* )? COMMA? RBRACK ;
```

Like `.ttrg`, the grammar does not enforce the file-kind distinction; the semantic layer does (`ttr/wrong-file-kind` extended to `.ttrd`). **ai-platform's metadata loader does not need to load `.ttrd` files** — they are consumed by the *agent registry* and the *resolved-packages artifact* (§9.4), not by the model loader. Treat `.ttrd` as a non-loadable extension alongside `.ttrg`. The `domain.packages` membership is **recursive** (pulls `X` and `X.*`), in contrast to non-recursive `import X.*` — keep the two semantics distinct in any tooling you build on this.

### 9.4 Resolved-packages artifact (affects the `ai-models` CI, not the model loader)

Modeler will emit a deterministic JSON artifact (`modeler resolve-packages`) describing the resolved package/domain structure, so non-TS consumers (the `ai-models` agent-registry CI) can validate references without reimplementing nesting/`root`-elision logic. Shape in contracts §13. This does not affect ai-platform's Kotlin loader; it is called out here only because it is part of the same grammar/resolution change set and the same coordination window.
