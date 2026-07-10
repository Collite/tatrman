# TTR Grammar — v2.0 → v2.1 Changes

**Status:** Draft v1, 2026-05-27. Coordination document for the ai-platform Kotlin parser maintainer. Pairs with the Modeler v2.1 release; design rationale in [`v2.1-inline-mappings.md`](v2.1-inline-mappings.md).

## 1. Audience and purpose

This document is the contract between the Modeler v2.1 release and the ai-platform metadata service. It describes every change Modeler is making to the TTR grammar and the semantic-table representation so that ai-platform's Kotlin parser can be regenerated and the metadata loader can be updated to match.

Unlike v1.1, **v2.1 is additive** — every v2.0 `.ttr` file parses unchanged under v2.1, and the resolver chain is not touched. The change introduces an `inline mapping` shape that is *equivalent* to writing an explicit `def er2db_*` declaration; the semantic layer normalizes both into the same symbol-table entries.

Unlike v1.1's "modeler ships independently" approach (B12), this PR aims to land both sides together — one deployment unit. ai-platform's adoption is on the critical path for the modeler v2.1 release.

## 2. Grammar version bump

- **Old version:** `2.0` (current, since v1.1 promoted to 2.0)
- **New version:** `2.1`
- **Bump reason:** new reserved keyword `mapping`, new optional property on entity/attribute/relation, small relaxation of `targetProperty`. Minor bump per the version scheme in `packages/grammar/CHANGELOG.md`.

The version lives in the `// @grammar-version: 2.1` marker at the top of `TTR.g4`. The `packages/grammar` package `package.json` `version` is a separate concern and may or may not bump alongside.

ai-platform's vendored copy picks up the new marker via `sync-to-ai-platform.sh`; the existing `check-sync.sh` continues to verify hashes match.

## 3. Grammar changes

### 3.1 New lexer token

```
MAPPING : 'mapping' ;
```

Place near the other property-name tokens (after `LAYOUT`, in the v1.1 keyword block). Tokens must appear before `IDENT` so the keyword wins on longest-match.

`columns` already exists as a token (`COLUMNS`), reused as the inner-map key inside an inline entity-level mapping.

### 3.2 New property rule

```
mappingProperty : MAPPING propSep? mappingValue ;

mappingValue
  : id                               // form: bare reference (attribute or relation form)
  | mappingBlock                     // form: { ... }
  ;

mappingBlock
  : LBRACE ( mappingBlockProperty ( COMMA? mappingBlockProperty )* COMMA? )? RBRACE
  ;

mappingBlockProperty
  : targetProperty                   // existing rule, used by both entity & attribute mappings
  | mappingColumnsProperty           // entity mapping only — semantic layer rejects on attribute/relation
  | fkProperty                       // relation mapping only — semantic layer rejects on entity/attribute
  ;

mappingColumnsProperty
  : COLUMNS propSep? mappingColumnMap
  ;

mappingColumnMap
  : LBRACE ( mappingColumnEntry ( COMMA? mappingColumnEntry )* COMMA? )? RBRACE
  ;

mappingColumnEntry
  : id propSep? mappingColumnValue
  ;

mappingColumnValue
  : id                                                   // form (a): bare id
  | LBRACE TARGET propSep? mappingTargetValue RBRACE     // form (b): { target: <bareId | object> }
  | object_                                              // form (c): fully-nested { target: { column: <ref> } }
  ;

mappingTargetValue
  : id                                                   // bare column reference
  | object_                                              // { column: <ref> } form
  ;
```

Note: form (b) and form (c) overlap grammatically when the wrapper object happens to contain only a `target:` key — that's fine, both reduce to the same AST. The semantic layer enforces "exactly one of `target` is present" on form (c).

The existing `fkProperty` rule (`FK propSep? id`) is reused inside `mappingBlock` for the relation-mapping form.

### 3.3 Relaxation of `targetProperty`

Today:

```
targetProperty : TARGET propSep? object_ ;
```

Becomes:

```
targetProperty : TARGET propSep? ( object_ | id ) ;
```

This allows `target: KOD_ZBOZI` shorthand inside both inline mappings and explicit `def er2db_*` blocks. The semantic layer treats a bare id as `{ <defaultKey>: <id> }` where `<defaultKey>` is `column` for attribute targets, `table` for entity targets, `fk` for relation targets.

This is the only existing-rule change. It is itself backward compatible (every `object_` form remains valid).

### 3.4 Per-kind property extensions

Add `mappingProperty` to:

```
entityProperty       : ... | mappingProperty ;
attributeProperty    : ... | mappingProperty ;
relationProperty     : ... | mappingProperty ;
```

No removals, no other property changes.

### 3.5 `idPart` extension

```
idPart
  : IDENT
  | ...                                        // existing alternatives unchanged
  | MAPPING                                    // <-- NEW
  ;
```

So `def attribute mapping { ... }` remains writable (consistent with how `name`, `label`, `package`, etc. are handled).

### 3.6 Diff against current `TTR.g4`

Concise unified-diff sketch (line numbers approximate):

```diff
--- a/TTR.g4 (v2.0)
+++ b/TTR.g4 (v2.1)
@@ entityProperty
-entityProperty : descriptionProperty | ... | searchBlockProperty ;
+entityProperty : descriptionProperty | ... | searchBlockProperty | mappingProperty ;

@@ attributeProperty
-attributeProperty : descriptionProperty | ... | searchBlockProperty ;
+attributeProperty : descriptionProperty | ... | searchBlockProperty | mappingProperty ;

@@ relationProperty
-relationProperty : descriptionProperty | ... | searchBlockProperty ;
+relationProperty : descriptionProperty | ... | searchBlockProperty | mappingProperty ;

@@ targetProperty
-targetProperty : TARGET propSep? object_ ;
+targetProperty : TARGET propSep? ( object_ | id ) ;

@@ (new rules added after relationProperty)
+mappingProperty       : MAPPING propSep? mappingValue ;
+mappingValue          : id | mappingBlock ;
+mappingBlock          : LBRACE ( mappingBlockProperty ( COMMA? mappingBlockProperty )* COMMA? )? RBRACE ;
+mappingBlockProperty  : targetProperty | mappingColumnsProperty | fkProperty ;
+mappingColumnsProperty: COLUMNS propSep? mappingColumnMap ;
+mappingColumnMap      : LBRACE ( mappingColumnEntry ( COMMA? mappingColumnEntry )* COMMA? )? RBRACE ;
+mappingColumnEntry    : id propSep? mappingColumnValue ;
+mappingColumnValue    : id | LBRACE TARGET propSep? mappingTargetValue RBRACE | object_ ;
+mappingTargetValue    : id | object_ ;

@@ idPart
   | OBJECTS | LAYOUT
+  | MAPPING
   ;

@@ (new lexer token)
+MAPPING : 'mapping' ;
```

(The canonical change lands when `pnpm --filter @modeler/parser run prebuild` regenerates cleanly.)

### 3.7 Backward compatibility

Every v2.0 `.ttr` file parses unchanged under v2.1. The new `mappingProperty` is optional in `entityProperty`, `attributeProperty`, and `relationProperty`. The `targetProperty` relaxation accepts every previously-valid input. The new `MAPPING` keyword is added to `idPart` so files using `mapping` as an identifier (none in practice, but allowed) continue to work.

## 4. Semantic-table representation

This is the part with substance for ai-platform's loader. Grammar accepts the new shape; the semantic layer is what makes it equivalent to explicit `def er2db_*`.

### 4.1 Synthesis rule

For each `mappingProperty` AST node encountered during the load pass:

- **On an entity** with a `mappingBlock` containing `target:` and optionally `columns:` — synthesize:
  - one `er2db_entity` symbol named `<entityName>`, target taken from `target:` (`{ table: <ref> }` or `{ view: <ref> }`)
  - one `er2db_attribute` symbol per entry in `columns:`, named `<entityName>.<columnsKey>`, target taken from the entry's value (per the three forms (a)/(b)/(c) in the design doc §3.1)
- **On an attribute** with a `mappingValue` that is a bare id — synthesize one `er2db_attribute` symbol named `<enclosingEntityName>.<attributeName>`, target `{ column: <id> }`
- **On an attribute** with a `mappingBlock` containing `target:` — synthesize one `er2db_attribute` symbol with the explicit target
- **On a relation** with a `mappingValue` that is a bare id — synthesize one `er2db_relation` symbol named `<relationName>`, with `fk: <id>` and `relation: <relationName>`
- **On a relation** with a `mappingBlock` containing `fk:` — synthesize one `er2db_relation` symbol with the explicit FK

### 4.2 Qname shape

Synthesized symbols use the host file's package, not the `map` schema's package. A file `billing/products/er.ttr` declaring `package billing.products`:

```
def entity artikl { ..., mapping: { target: { table: db.dbo.QZBOZI_DF }, columns: { id_artiklu: IDZBOZI } } }
```

Synthesizes:

- `billing.products.map.er2dbEntity.artikl` → `{ table: billing.products.db.dbo.QZBOZI_DF }`
- `billing.products.map.er2dbAttribute.artikl.id_artiklu` → `{ column: billing.products.db.dbo.QZBOZI_DF.IDZBOZI }`

The `map` schema prefix is part of the qname (matching how explicit `def er2db_*` symbols are named today) but the symbols themselves are *not* attached to the `map` per-file symbol table — they live in the project-level semantic table only. See §4.4.

### 4.3 Source attribution

Synthesized symbols carry a discriminator distinguishing them from explicitly-declared ones:

```
sealed class MappingSource {
  object Explicit : MappingSource()                  // from `def er2db_*` declaration
  data class Inline(val hostKind: String) : MappingSource()  // hostKind ∈ {"entity", "attribute", "relation"}
}
```

The Kotlin equivalent of modeler's TypeScript discriminator. Used by:
- The conflict validator (§5)
- The Designer's inspector (potential v2.2 UI: small icon distinguishing inline vs. explicit)

### 4.4 The `map` schema is not modified

Synthesized symbols are added only to the project-level symbol table, **not** to any `DocumentSymbolTable` for a `map`-schema file. Consequences:

- `.ttrg` files with `schema: map` do not list synthesized symbols in their `objects:` list (the user can't add a synthesized `er2db_entity` to a graph by qname — it's not a member of the `map` schema's file-symbol-table).
- `map`-schema-only validators do not run against synthesized symbols.
- `workspace/symbol` results for `er2db_entity.*` include synthesized symbols (they're in the project table).
- `textDocument/references` on a db table includes synthesized references (they're in the project table).

ai-platform's loader: synthesize into the same project-level metadata index that holds explicit mappings. Do not add to per-file maps of the `map` schema.

### 4.5 Source locations

Every synthesized symbol carries a `SourceLocation` pointing back at the *value* of the inline `mapping:` property (the bare id or the object literal). Not the `mapping` keyword, not the enclosing `def entity` — the value itself, so that Cmd-click in the editor lands on the authored text and not on whitespace.

For entity-level mappings with a `columns:` sub-block, each synthesized `er2db_attribute` points at *its own* entry within the `columns:` map (the `<id>: <value>` line), not at the whole `columns:` block.

## 5. New diagnostic code

| Code                       | Severity | When                                                                                            |
| -------------------------- | -------- | ----------------------------------------------------------------------------------------------- |
| `ttr/duplicate-mapping`    | Error    | Inline `mapping:` and explicit `def er2db_*` both target the same entity/attribute/relation.    |

The diagnostic fires on **both** participating locations (both the `mapping:` value and the explicit `def er2db_*` header). The message includes the qname of the synthesized symbol and both source locations.

This is the only new diagnostic in v2.1. Existing diagnostic codes (`ttr/duplicate-definition`, `ttr/unresolved-reference`, etc.) continue to apply normally to the symbols involved — inline-synthesized symbols are first-class for those purposes.

## 6. Testing recommendations

Test cases that should pass on the new grammar and semantics:

1. **Entity full form** parses and synthesizes one `er2db_entity` + N `er2db_attribute`.
2. **Attribute bare-id form** parses and synthesizes one `er2db_attribute` with target defaulted to `column`.
3. **Attribute full form** parses and synthesizes one `er2db_attribute` with explicit target.
4. **Relation bare-id form** parses and synthesizes one `er2db_relation` with `fk:` set to the bare id.
5. **Relation full form** parses and synthesizes one `er2db_relation` with explicit `fk:`.
6. **`target:` shorthand** — `target: KOD_ZBOZI` parses and is treated as `{ column: KOD_ZBOZI }` in attribute contexts, `{ table: ... }` in entity contexts.
7. **Mixed forms inside one `columns:`** — forms (a), (b), and (c) coexist and all synthesize correctly.
8. **Cross-package qname** — inline mapping in `billing.products` synthesizes a symbol under `billing.products.map.er2db_entity.*`, distinct from the same name in `billing.invoicing`.
9. **All v2.0 samples parse unchanged.**

Test cases that should fail:

1. **Conflict: inline + explicit on same entity** → `ttr/duplicate-mapping` fires on both locations.
2. **Conflict: inline-attribute + explicit-attribute** → `ttr/duplicate-mapping` fires on both.
3. **Conflict: entity-level inline `columns.X` + explicit attribute mapping for X** → `ttr/duplicate-mapping` fires.
4. **Conflict: inline relation + explicit relation** → `ttr/duplicate-mapping` fires.

Modeler will share its full fixture set under `samples/2.1/` and `samples/broken/v2.1/` once Section F lands.

## 7. Coordination plan

This release is a **single deployment unit**: modeler and ai-platform land together in one feature, on coordinated branches in both repos.

1. Modeler's grammar change lands on `feat/v2.1-inline-mappings` (modeler).
2. `sync-to-ai-platform.sh ~/Dev/ai-platform` runs from the modeler branch; ai-platform's Kotlin parser regenerates from the vendored copy.
3. ai-platform's Kotlin AST + synthesizer + validator land on `feat/v2.1-inline-mappings` (ai-platform).
4. Tests on both sides cover the same shapes.
5. Both branches are reviewed together; merging is coordinated so neither side ships in isolation.
6. After both merge, `check-sync.sh` returns to "block on drift" mode.

## 8. Open items

None blocking Section A. Items deferred to follow-on releases:

1. **`def mapping { type: er2db, ... }` standalone form.** Deferred to v3.0+. Distinct design — would replace `def er2db_*` rather than complement it.
2. **`whereFilter:` in inline form.** Not supported in v2.1 per author's direction (legacy feature being removed).
3. **Designer "inline vs explicit" UI hint.** Deferred to v2.2 polish.
