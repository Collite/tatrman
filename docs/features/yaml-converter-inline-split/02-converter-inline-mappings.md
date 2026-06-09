# Stage 2 — Converter: emit inline mappings (ai-platform)

**Goal:** in `ModelToDefinitions.convert`, stop emitting standalone
`def er2db_entity / er2db_attribute / er2db_relation` and instead attach the
mapping inline to the owning entity/attribute/relation def's `mapping` field.
**Keep file partitioning unchanged in this stage** (still per-schema, `map.ttr`
may transiently shrink) — the db/er/cnc split is Stage 3. Keep `er2cnc_role`
standalone (no inline form).

**Pre-flight:** Stage 1 published; ai-platform consuming a ttr-writer version
that renders inline mappings (Stage 3 does the formal bump, but local
`publishToMavenLocal` is enough to develop/test here).

**File:** `infra/metadata/src/main/kotlin/infra/metadata/export/ModelToDefinitions.kt`.
- `entityToEntityDef` (line 226), `attributeToAttributeDef` (395),
  `relationToRelationDef` (306) — add `mapping`.
- The `model.mappings.forEach { … }` loop (142–152) currently builds er2db defs
  tagged `"map"` — this is what moves inline.
- Existing target helpers `mappingTargetToPropertyValue` (470) and
  `attributeMappingTargetToPropertyValue` (504) produce the `target` objects to
  reuse.

**Mapping-form rules (confirmed):** short where possible, block when needed.

---

- [ ] **2.1 — Tests first: index the mappings.** In the metadata export test
  suite, add fixtures: a `Model` with an `Er2DbEntityMapping` (entity→table),
  its `Er2DbAttributeMapping`s (attribute→column, plus one attribute→expression),
  and an `Er2DbRelationMapping` (relation→fk). Assert (red) that the produced
  `EntityDef.mapping` / `AttributeDef.mapping` / `RelationDef.mapping` are
  populated and that **no** `Er2DbEntityDef`/`Er2DbAttributeDef`/`Er2DbRelationDef`
  appear in the bundle.

- [ ] **2.2 — Tests first: short vs block selection.** Assert: an attribute
  mapped to a plain column on an entity that HAS an entity-level table target →
  appears as a short bare-id entry in the entity's `columns` map
  (`attr: COL`); an attribute mapped to an **expression** → block form
  (`{ target: <expr> }`); an attribute on an entity with **no** entity-level
  target → standalone per-attribute `mapping: COL` short form (the produkt case).
  Relation → `MappingPropertyBareId(fkRef)`.

- [ ] **2.3 — Build a mapping lookup.** Implement a helper that groups
  `model.mappings` by owner qname: entity-target by entity qname, attribute
  targets by attribute qname, relation fk by relation qname. (Keep
  `er2cnc_role` mappings aside — they remain standalone.)

- [ ] **2.4 — Attach entity-level mapping.** In `entityToEntityDef`, when an
  `Er2DbEntityMapping` exists for the entity, build a `MappingPropertyBlock`:
  `target` from `mappingTargetToPropertyValue(entityMapping.target)` wrapped as
  `TargetObjectValue`; `columns` = a `MappingColumnEntry` per attribute mapping
  belonging to this entity — `MappingColumnBareId(colName)` when the
  `AttributeMappingTarget` is a plain `Column` (use the column's last name
  segment), else `MappingColumnObject` from
  `attributeMappingTargetToPropertyValue`. Set `EntityDef.mapping`.

- [ ] **2.5 — Attach attribute / relation mappings for the no-entity-block
  case.** In `attributeToAttributeDef`, when the attribute's entity has **no**
  entity-level mapping but the attribute has a column mapping, set
  `AttributeDef.mapping = MappingPropertyBareId(colName)` (short) or block for
  expression. In `relationToRelationDef`, set
  `RelationDef.mapping = MappingPropertyBareId(fkRef)`. Thread the lookup from
  2.3 into these converters (pass it as a parameter — they're currently `private`
  with no model access).

- [ ] **2.6 — Remove the standalone er2db emission.** Delete the
  `Er2DbEntityMapping`/`…Attribute`/`…Relation` branches from the
  `model.mappings.forEach` loop (142–152) so they no longer become tagged
  `"map"` defs. **Keep** the `Er2CncRoleMapping` branch (it stays standalone).
  Confirm no `er2db_*` def is produced anywhere.

- [ ] **2.7 — Green + semantic equivalence.** `./gradlew :infra:metadata:test`
  green. Add an assertion that loading the new bundle through the resolver
  produces the **same mapping qnames** as the previous standalone-def output
  (inline and standalone must resolve identically). Tick boxes.

### Stage 2 DoD
- [ ] All entity/attribute/relation mappings are inline; no `er2db_*` standalone
      defs; `er2cnc_role` still standalone; resolver sees identical mapping
      qnames as before; metadata suite green.
