# Section C — Parser AST + walker (+ parser tests)

Extend the parser AST with `MappingProperty` and friends; wire `walkEntityDef` / `walkAttributeDef` / `walkRelationDef` to populate them. Add accurate source locations so the Designer's Cmd-click lands on the inline `mapping:` value.

**Depends on:** Section B (regenerated parser contexts must exist).

**TDD:** write parser tests first, watch them fail, then implement.

**Files:**
- `packages/parser/src/ast.ts` — new node types.
- `packages/parser/src/walker.ts` — walker extensions.
- New test: `packages/parser/src/__tests__/inline-mappings.test.ts`.

All line numbers below are as of the planning snapshot — confirm by reading the surrounding code before editing.

---

## Tasks

### C.0 — Write the failing parser tests first

- [ ] **Create the test file** at `packages/parser/src/__tests__/inline-mappings.test.ts`. Use `parseString` from the package entry (`import { parseString } from '../index.js'`; match the style in existing tests). Cases:

  ```ts
  import { describe, it, expect } from 'vitest';
  import { parseString } from '../index.js';

  describe('inline mappings — entity level', () => {
    it('parses entity with full inline mapping + columns map', () => {
      const result = parseString(`
        schema er
        def entity artikl {
          mapping: {
            target: { table: db.dbo.QZBOZI_DF },
            columns: {
              id_artiklu: IDZBOZI,
              kód_artiklu: { target: KOD_ZBOZI },
              název_artiklu: { target: { column: NAZEV_ZBOZI } }
            }
          },
          attributes: [
            def attribute id_artiklu { type: int, isKey: true },
            def attribute kód_artiklu { type: text },
            def attribute název_artiklu { type: text }
          ]
        }
      `);
      expect(result.errors).toEqual([]);
      const entity = result.ast!.definitions[0] as any;
      expect(entity.kind).toBe('entity');
      expect(entity.mapping).toBeDefined();
      expect(entity.mapping.kind).toBe('block');
      expect(entity.mapping.target).toBeDefined();
      expect(entity.mapping.columns).toHaveLength(3);
      expect(entity.mapping.columns[0].name).toBe('id_artiklu');
      expect(entity.mapping.columns[0].value.kind).toBe('bareId');
    });
  });

  describe('inline mappings — attribute level', () => {
    it('parses attribute with bare-id mapping', () => {
      const result = parseString(`
        schema er
        def attribute id_produktu { type: int, mapping: IDSKUPZBOZI }
      `);
      expect(result.errors).toEqual([]);
      const attr = result.ast!.definitions[0] as any;
      expect(attr.mapping).toBeDefined();
      expect(attr.mapping.kind).toBe('bareId');
      expect(attr.mapping.id.path).toBe('IDSKUPZBOZI');
    });

    it('parses attribute with full mapping block', () => {
      const result = parseString(`
        schema er
        def attribute název_artiklu {
          type: text,
          mapping: { target: { column: NAZEV_ZBOZI } }
        }
      `);
      expect(result.errors).toEqual([]);
      const attr = result.ast!.definitions[0] as any;
      expect(attr.mapping.kind).toBe('block');
      expect(attr.mapping.target).toBeDefined();
    });
  });

  describe('inline mappings — relation level', () => {
    it('parses relation with bare-fk mapping', () => {
      const result = parseString(`
        schema er
        def relation r {
          from: er.entity.a, to: er.entity.b,
          cardinality: { from: "0..*", to: "1" },
          join: [{ from: er.entity.a.x, to: er.entity.b.x }],
          mapping: db.dbo.fk_a_b
        }
      `);
      expect(result.errors).toEqual([]);
      const rel = result.ast!.definitions[0] as any;
      expect(rel.mapping.kind).toBe('bareId');
      expect(rel.mapping.id.path).toBe('db.dbo.fk_a_b');
    });

    it('parses relation with fk block', () => {
      const result = parseString(`
        schema er
        def relation r {
          from: er.entity.a, to: er.entity.b,
          cardinality: { from: "0..*", to: "1" },
          join: [{ from: er.entity.a.x, to: er.entity.b.x }],
          mapping: { fk: db.dbo.fk_a_b }
        }
      `);
      expect(result.errors).toEqual([]);
      const rel = result.ast!.definitions[0] as any;
      expect(rel.mapping.kind).toBe('block');
      expect(rel.mapping.fk).toBeDefined();
    });
  });

  describe('targetProperty bare-id relaxation', () => {
    it('accepts bare id in target on explicit er2db_attribute', () => {
      const result = parseString(`
        schema map
        def er2db_attribute foo { attribute: er.entity.x.y, target: SOMECOL }
      `);
      expect(result.errors).toEqual([]);
    });
  });

  describe('source locations', () => {
    it('mapping source location points at the value, not the keyword', () => {
      const result = parseString(`schema er
  def attribute id { type: int, mapping: IDX }`);
      expect(result.errors).toEqual([]);
      const attr = result.ast!.definitions[0] as any;
      // Source location should cover "IDX", not "mapping: IDX".
      expect(attr.mapping.source.line).toBe(2);
      // column is 0-indexed; "mapping: " is 9 chars after the start of `, mapping:`
      // so the value starts somewhere after column 30. Exact column depends on
      // the surrounding text — assert that it points past the `mapping` keyword.
      const fileText = `schema er\n  def attribute id { type: int, mapping: IDX }`;
      const offset = attr.mapping.source.offsetStart;
      expect(fileText.slice(offset, offset + 3)).toBe('IDX');
    });
  });
  ```

- [ ] **Run the tests; confirm failure.**
  ```bash
  pnpm --filter @modeler/parser test -- inline-mappings
  ```
  Every case should fail with `entity.mapping is undefined` or similar — the AST has no `mapping` field yet.

### C.1 — Extend AST node types

- [ ] **Add the discriminated-union types** in `packages/parser/src/ast.ts`. Insert near the existing `SearchBlock` interface (~line 137), or in a dedicated v2.1 block at the bottom:

  ```ts
  // ----- v2.1: inline mappings -----

  export interface MappingPropertyBareId {
    kind: 'bareId';
    id: Reference;
    source: SourceLocation;       // covers the bare id span only
  }

  export interface MappingPropertyBlock {
    kind: 'block';
    target?: ObjectValue | Reference;     // target: { table: ... } or target: <bareId>
    columns?: MappingColumnEntry[];       // entity-level only
    fk?: Reference;                       // relation-level only
    source: SourceLocation;       // covers the `{ ... }` span
  }

  export type MappingProperty = MappingPropertyBareId | MappingPropertyBlock;

  export interface MappingColumnEntry {
    name: string;                 // attribute name (the key)
    value: MappingColumnValue;
    source: SourceLocation;       // covers `<name>: <value>`
  }

  export type MappingColumnValue =
    | { kind: 'bareId'; id: Reference; source: SourceLocation }
    | { kind: 'object'; object: ObjectValue; source: SourceLocation };
  ```

- [ ] **Add `mapping?: MappingProperty;` to `EntityDef`, `AttributeDef`, `RelationDef`.** ~lines 268, 278, 294:
  ```diff
   export interface EntityDef {
     kind: 'entity';
     ...
     search?: SearchBlock;
  +  mapping?: MappingProperty;
   }

   export interface AttributeDef {
     ...
     search?: SearchBlock;
  +  mapping?: MappingProperty;
   }

   export interface RelationDef {
     ...
     search?: SearchBlock;
  +  mapping?: MappingProperty;
   }
  ```

- [ ] **Note for `targetProperty` relaxation in walkers.** `Er2dbEntityDef.target` and `Er2dbAttributeDef.target` are currently typed `ObjectValue | undefined` (~lines 312/322). Widen to `ObjectValue | Reference | undefined` to accommodate the new `target: <bareId>` form:
  ```diff
  -  target?: ObjectValue;
  +  target?: ObjectValue | Reference;
  ```
  Apply to both `Er2dbEntityDef` and `Er2dbAttributeDef`.

### C.2 — Walker construction

- [ ] **Import the new context types.** Top of `packages/parser/src/walker.ts` (~line 30, in the antlr-context imports):
  ```diff
   import {
     ...
     Er2dbRelationDefContext,
  +  MappingPropertyContext,
  +  MappingValueContext,
  +  MappingBlockContext,
  +  MappingBlockPropertyContext,
  +  MappingColumnsPropertyContext,
  +  MappingColumnMapContext,
  +  MappingColumnEntryContext,
  +  MappingColumnValueContext,
     ...
   } from './generated/TTRParser.js';
  ```
  (Exact accessor names come from the regenerated parser — adjust if antlr-ng pluralizes differently. Check with `grep -n 'Mapping' packages/parser/src/generated/TTRParser.ts`.)

- [ ] **Export the new AST types** from `packages/parser/src/index.ts` (re-export block at the top, alongside `EntityDef`, etc.):
  ```ts
  export type {
    ...
    MappingProperty,
    MappingPropertyBareId,
    MappingPropertyBlock,
    MappingColumnEntry,
    MappingColumnValue,
  } from './ast.js';
  ```

- [ ] **Add the `walkMappingProperty` helper.** Near the other property walkers (after `walkSearchBlock` ~line 1447 is a good neighbour):

  ```ts
  function walkMappingProperty(
    ctx: MappingPropertyContext,
    file: string
  ): MappingProperty {
    const valueCtx = ctx.mappingValue();

    if (valueCtx.id()) {
      const idCtx = valueCtx.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      const ref: Reference = {
        path: parts.join('.'),
        parts,
        source: makeSourceLocation(idCtx, file),
      };
      return {
        kind: 'bareId',
        id: ref,
        source: makeSourceLocation(valueCtx, file),
      };
    }

    // Otherwise: mappingBlock.
    const blockCtx = valueCtx.mappingBlock()!;
    let target: ObjectValue | Reference | undefined;
    let columns: MappingColumnEntry[] | undefined;
    let fk: Reference | undefined;

    for (const p of blockCtx.mappingBlockProperty()) {
      if (p.targetProperty()) {
        target = walkTargetValue(p.targetProperty()!, file);
      }
      if (p.mappingColumnsProperty()) {
        columns = walkMappingColumnMap(
          p.mappingColumnsProperty()!.mappingColumnMap()!,
          file
        );
      }
      if (p.fkProperty_()?.id()) {
        const idCtx = p.fkProperty_()!.id()!;
        const parts = idCtx.idPart().map((pt) => pt.getText());
        fk = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
      }
    }

    return {
      kind: 'block',
      target,
      columns,
      fk,
      source: makeSourceLocation(blockCtx, file),
    };
  }

  function walkTargetValue(
    ctx: TargetPropertyContext,
    file: string
  ): ObjectValue | Reference {
    if (ctx.id()) {
      const idCtx = ctx.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      return { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
    return walkObject(ctx.object_()!, file);
  }

  function walkMappingColumnMap(
    ctx: MappingColumnMapContext,
    file: string
  ): MappingColumnEntry[] {
    const entries: MappingColumnEntry[] = [];
    for (const e of ctx.mappingColumnEntry()) {
      const name = e.id().idPart().map((pt) => pt.getText()).join('.');
      const v = e.mappingColumnValue();
      let value: MappingColumnValue;
      if (v.id()) {
        const idCtx = v.id()!;
        const parts = idCtx.idPart().map((pt) => pt.getText());
        value = {
          kind: 'bareId',
          id: { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) },
          source: makeSourceLocation(v, file),
        };
      } else if (v.mappingTargetValue()) {
        // Post review-059 B1: grammar is `id | LBRACE TARGET ... mappingTargetValue RBRACE | object_`
        // Forms (b) and (c) arrive via mappingTargetValue(); rebuild a consistent { target: <inner> } wrapper.
        const mtv = v.mappingTargetValue()!;
        if (mtv.id()) {
          const idCtx = mtv.id()!;
          const parts = idCtx.idPart().map((pt) => pt.getText());
          value = {
            kind: 'object',
            object: { kind: 'object', entries: [{ key: 'target', value: { kind: 'id', path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) }, source: makeSourceLocation(mtv, file) }], source: makeSourceLocation(mtv, file) },
            source: makeSourceLocation(v, file),
          };
        } else {
          const inner = walkObject(mtv.object_()!, file);
          value = {
            kind: 'object',
            object: { kind: 'object', entries: [{ key: 'target', value: inner, source: makeSourceLocation(mtv, file) }], source: makeSourceLocation(v, file) },
            source: makeSourceLocation(v, file),
          };
        }
      } else {
        value = {
          kind: 'object',
          object: walkObject(v.object_()!, file),
          source: makeSourceLocation(v, file),
        };
      }
      entries.push({ name, value, source: makeSourceLocation(e, file) });
    }
    return entries;
  }
  ```

- [ ] **Hook `walkMappingProperty` into `walkEntityDef`.** ~line 848, in the `for (const p of ctx.entityProperty())` loop, add at the end:
  ```ts
  if (p.mappingProperty()) {
    mapping = walkMappingProperty(p.mappingProperty()!, file);
  }
  ```
  Declare the local at the top of the function (alongside `let search`):
  ```ts
  let mapping: MappingProperty | undefined;
  ```
  And include it in the return:
  ```diff
  -return { kind: 'entity', name, source, description, tags, labelPlural, nameAttribute, codeAttribute, aliases, attributes, roles, displayLabel, search };
  +return { kind: 'entity', name, source, description, tags, labelPlural, nameAttribute, codeAttribute, aliases, attributes, roles, displayLabel, search, mapping };
  ```

- [ ] **Hook `walkMappingProperty` into `walkAttributeDef`.** ~line 888, mirror the entity wiring. Apply to BOTH:
  - The block-form walker (`walkAttributeDef`).
  - The inline-list walker inside `walkAttributeDefList` (~grep for `walkAttributeDefList` — same loop, attributes inside `attributes: [ ... ]`). The walker reuses the same `AttributeProperty` access pattern.

- [ ] **Hook `walkMappingProperty` into `walkRelationDef`.** ~line 933. Same pattern.

- [ ] **Update the explicit `walkEr2dbEntityDef` / `walkEr2dbAttributeDef` target handling.** ~lines 1002–1006 (entity) and ~1036–1040 (attribute). The current code reads only `p.targetProperty()?.object_()`; replace with `walkTargetValue`:
  ```diff
  -if (p.targetProperty()?.object_()) {
  -  target = walkObject(p.targetProperty()!.object_()!, file);
  -}
  +if (p.targetProperty()) {
  +  target = walkTargetValue(p.targetProperty()!, file);
  +}
  ```
  This makes the bare-id `target: <ref>` form work in explicit declarations too.

### C.3 — Run tests to green

- [ ] **Re-run.**
  ```bash
  pnpm --filter @modeler/parser test -- inline-mappings
  pnpm --filter @modeler/parser typecheck
  ```
  Every case from C.0 should pass.

- [ ] **Run the full parser suite.**
  ```bash
  pnpm --filter @modeler/parser test
  ```
  No regressions.

---

## Verification

- [ ] `inline-mappings.test.ts` passes.
- [ ] `pnpm --filter @modeler/parser typecheck` clean.
- [ ] No downstream type errors from the widened `Er2dbEntityDef.target` / `Er2dbAttributeDef.target` shape — search for `\.target\.` references in `@modeler/semantics` and `@modeler/lsp`:
  ```bash
  grep -rn "\.target\." packages/{semantics,lsp}/src --include=*.ts
  ```
  Any place that does `def.target.<key>` may need a discriminating check (`if (def.target && 'kind' in def.target && def.target.kind === ...)`). Most likely the LSP's `model-graph.ts` is unaffected because it builds a synthesized `targetDescription` string.
- [ ] Source-location assertion in C.0 passes — the `offsetStart` of `attr.mapping.source` points at the value text, not the `mapping` keyword.

## Notes / gotchas

- **`SourceLocation` invariant.** Per CLAUDE.md, `endColumn = stopToken.column + stopTokenLength`, not `startColumn + spanLength`. `makeSourceLocation` already does this correctly — don't reach for shortcuts in the new helpers.
- **`walkObject` already exists** — reuse it for `mappingColumnValue` / `target` object forms. Don't duplicate the property-walk logic.
- **Attribute inline-list walker** (`walkAttributeDefList`) and the block-form `walkAttributeDef` share the same `AttributeProperty` accessor pattern. If you forget one, the entity-with-inline-attributes case fails — the C.0 test for entity full form covers this.
- **`mappingColumnValue` has three alternatives** (post review-059 B1 grammar change): `id | LBRACE TARGET propSep? mappingTargetValue RBRACE | object_`. Forms (b) and (c) arrive via `v.mappingTargetValue()` (not `v.object_()`). The walker rebuilds a `{ target: <inner> }` ObjectValue for both so form (b) and form (c) have an identical top-level `target` shape at the AST level (review-060 C1). The `object_` fallback is for future extensions.
- The Designer's "find references" path goes through `references.ts` in semantics. It walks `Reference` nodes inside the AST. Section D's synthesizer will produce additional `Reference` instances for inline-mapping targets; they'll be picked up automatically. C just makes the AST shape available.
