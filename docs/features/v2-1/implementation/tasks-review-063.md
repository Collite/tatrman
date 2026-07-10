# Tasks — review-063 (Section G: ai-platform mirror)

Findings in [`review-063.md`](review-063.md). Section G ships **after B1 and C1 are fixed** — the rest is either coverage-gap polish (T1, T2) or follow-up issues (L1–L6). Branch under review: ai-platform `grammar-2-1` (uncommitted).

> Work is in **`~/Dev/ai-platform`** on branch `grammar-2-1`. The modeler side has no remaining Section G work. Every path below is ai-platform-relative unless prefixed `modeler/`.

> All line numbers are as of the planning snapshot — confirm before editing.

---

## B1 — Thread `schemaCode` / `namespace` into `synthesiseInlineRelationMapping`  *(HIGH; do now — correctness bug)*

The synthesised `Er2DbRelationMapping.relation` field is hardcoded to `qname("er", "entity", def.name)`. Files with a non-default ER namespace (e.g. `schema er namespace foo`) produce a relation reference pointing at a non-existent qname.

The entity-level and attribute-level synthesisers already do this correctly via `entityQn.schemaCode.name.lowercase()` / `entityQn.namespace`. Pattern is established; just propagate it.

- [ ] **Edit `infra/metadata/src/main/kotlin/infra/metadata/source/Source.kt`.** Around line 804, extend `synthesiseInlineRelationMapping`'s signature:

  ```kotlin
  private fun synthesiseInlineRelationMapping(
      def: RelationDef,
      schemaCode: String,
      namespace: String,
      sourceFile: String,
      mappings: MutableList<infra.metadata.model.Mapping>,
  ) {
  ```

- [ ] **Replace the hardcoded `qname("er", "entity", def.name)` (line 824)** with `qname(schemaCode, namespace, def.name)` and **drop the `// best-effort; reconciler resolves` comment** — the new form is no longer best-effort.

- [ ] **Update the call site** in `processDef`'s `RelationDef` branch (around line 510 in the post-format file):

  ```kotlin
  synthesiseInlineRelationMapping(def, schemaCode, namespace, sourceFile, mappings)
  ```

- [ ] **Add a regression test fixture** to `infra/metadata/src/test/kotlin/infra/metadata/source/InlineMappingSynthesisSpec.kt`. Pattern after the existing "relation-level inline mapping (bare-fk) …" case, but use `schema er namespace foo`:

  ```kotlin
  "relation-level inline mapping respects host file's ER namespace" {
      val erTtr =
          """
          schema er namespace foo
          def entity a {}
          def entity b {}
          def relation r {
              from: er.foo.a, to: er.foo.b,
              cardinality: { from: "0..*", to: "1" },
              join: [{ from: er.foo.a.x, to: er.foo.b.x }],
              mapping: db.dbo.fk_a_b
          }
          """.trimIndent()

      val result = loadInline(mapOf("/er.ttr" to erTtr))
      val rel = result.model.mappings.filterIsInstance<Er2DbRelationMapping>().single()
      rel.mappingSource shouldBe MappingSource.Inline("relation")
      rel.relation.namespace shouldBe "foo"
      rel.relation.name shouldBe "r"
  }
  ```

- [ ] **Verify:**
  ```bash
  cd ~/Dev/ai-platform
  ./gradlew :infra:metadata:test --tests "infra.metadata.source.InlineMappingSynthesisSpec"
  ```
  Both the existing 8 cases and the new 9th pass.

---

## C1 — Remove dead `when` branch in `inlineColumnToAttributeTarget`  *(LOW; do now — dead code)*

The third arm of the inner `when` (lines 858–862) is unreachable — when `entries["target"] is PropertyValue.IdValue`, the wrap on lines 845–849 replaces `inner` with `mapOf("column" to it)`, so `inner.containsKey("column")` is true and the first arm catches it before this case is evaluated.

- [ ] **Edit `infra/metadata/src/main/kotlin/infra/metadata/source/Source.kt`.** Delete lines 858–862 inclusive:

  ```kotlin
                  // Bare reference inside { target: <bareId> } — convention: column.
                  entries["target"] is PropertyValue.IdValue ->
                      AttributeMappingTarget.Column(
                          refToQname(entries["target"], "db", "dbo"),
                      )
  ```

  Leave the `else -> null` arm as the final branch. The `// The walker wraps form (b) …` comment a few lines above is still accurate without this branch.

- [ ] **Verify (the existing form-(b) test in `InlineMappingsSpec.kt` exercises this path):**
  ```bash
  cd ~/Dev/ai-platform
  ./gradlew :shared:libs:kotlin:ttr-parser:test :infra:metadata:test
  ```

---

## T1 — Add a relation-level duplicate-mapping test  *(LOW; do in same commit)*

`InlineMappingSynthesisSpec.kt` covers entity-level and attribute-level collisions but not relation-level. Modeler-side has all three. Quick to close.

- [ ] **Edit `infra/metadata/src/test/kotlin/infra/metadata/source/InlineMappingSynthesisSpec.kt`.** Add after the attribute-level duplicate-mapping test:

  ```kotlin
  "ttr/duplicate-mapping fires when inline relation mapping collides with explicit def er2db_relation" {
      val erTtr =
          """
          schema er namespace entity
          def entity a {}
          def entity b {}
          def relation artikl_produkt {
              from: er.entity.a, to: er.entity.b,
              cardinality: { from: "0..*", to: "1" },
              join: [{ from: er.entity.a.x, to: er.entity.b.x }],
              mapping: db.dbo.fk_one
          }
          """.trimIndent()
      val mapTtr =
          """
          schema map
          def er2db_relation artikl_produkt {
              relation: er.entity.artikl_produkt,
              fk: db.dbo.fk_two
          }
          """.trimIndent()

      val result = loadInline(mapOf("/er.ttr" to erTtr, "/map.ttr" to mapTtr))
      val dup = result.errors.filter { it.message.contains("ttr/duplicate-mapping") }
      dup shouldHaveSize 2
      dup[0].message shouldContain "artikl_produkt"
      dup[0].message shouldContain "inline on relation"
      dup[0].message shouldContain "explicit def er2db_*"
  }
  ```

- [ ] **Verify:**
  ```bash
  ./gradlew :infra:metadata:test --tests "infra.metadata.source.InlineMappingSynthesisSpec"
  ```

---

## T2 — Cross-loader round-trip against modeler's `samples/2.1`  *(MEDIUM; can defer to a follow-up commit if shipping G now)*

The cross-loader contract is the point of shipping both repos together. A small integration test that reads the canonical `samples/2.1/{db,er,map}.ttr` from a checked-in copy and asserts the synthesised model is what the modeler side produces catches Sections G.4/G.5 drift faster than anything else.

Two approaches:

1. **Quick (recommended):** copy modeler's `samples/2.1/{db,er,map}.ttr` into `infra/metadata/src/test/resources/v2-1-samples/`. Drive `FileBasedSource` over it with `LocalFsStorage`. Assert (a) zero parse errors, (b) `mappings` contains the expected `Inline("entity")` entries for `artikl`, the expected `Inline("attribute")` entries for `produkt.id_produktu` / `produkt.kód_produktu` / `produkt.název_produktu`, the expected `Inline("relation")` entries for `artikl_produkt` / `artikl_podprodukt`, AND the expected `Explicit` entries for everything in `map.ttr` (`podprodukt`, etc.), (c) zero `ttr/duplicate-mapping` errors.
2. **More work:** read the modeler checkout directly via a relative path. Brittle (depends on repo layout) — skip.

If the goal is to ship G as-is and iterate, **defer T2** to its own commit. The build is green; G.7–G.10 already exercise the per-form synth and validator paths via the `InMemoryStorage` harness. T2 is the highest-value FOLLOW-UP, not a shipping blocker.

- [ ] (Optional, if doing in this commit) Copy `modeler/samples/2.1/{db,er,map}.ttr` to `infra/metadata/src/test/resources/v2-1-samples/`.
- [ ] (Optional) Write `infra/metadata/src/test/kotlin/infra/metadata/source/V21SamplesSpec.kt` that loads the corpus through `FileBasedSource` and asserts the synthesised mapping inventory matches expectations.

---

## L1–L6 — Follow-up issues (not blockers)

Track these as ai-platform GitHub issues after the Section G commit lands:

- [ ] **L1 — `Mapping.sourceLine` / `sourceColumn`.** Add line/column to the `Mapping` sealed interface (or introduce a `SourceLocation` value type) so `ttr/duplicate-mapping` diagnostics can pinpoint the exact `mapping:` site, not just the file. Affects all four `Mapping` implementers + the synthesisers + the validator. Pre-existing ai-platform shape question.
- [ ] **L2 — Canonicalise `Reference.toQname` 4-part interpretation.** The current `package.schema.namespace.name` interpretation of 4-part dotted paths diverges from how the synthesisers construct qnames. Pre-existing quirk; affects more than just v2.1 (any 4-part path resolution).
- [ ] **L3 — Empty `mapping: { }` blocks silently synthesise nothing.** Decide whether the loader should warn. Coordinate with the modeler-side parse-recovery behaviour.
- [ ] **L4 — Cross-loader snake_case vs camelCase.** Already tracked in [modeler#6](https://github.com/BoraPerusic/modeler/issues/6).
- [ ] **L5 — `check-sync.sh` always reports mismatch.** Already tracked in [modeler#5](https://github.com/BoraPerusic/modeler/issues/5).
- [ ] **L6 — No `ttr/duplicate-definition` for pure-explicit er2db_* collisions on ai-platform.** Pre-existing ai-platform gap. File a separate issue at the ai-platform-side equivalent of the modeler issue tracker.

---

## Verification gate after B1 + C1 (+ T1 if folded in)

```bash
cd ~/Dev/ai-platform
./gradlew :shared:libs:kotlin:ttr-parser:test         # InlineMappingsSpec 7 + existing
./gradlew :shared:libs:kotlin:ttr-writer:test         # existing pass
./gradlew :infra:metadata:test                        # InlineMappingSynthesisSpec 9 (8 + T1) + existing
./gradlew build                                       # full tree: 646 tasks, all green
```

Then on the modeler side:

```bash
cd ~/Dev/modeler
packages/grammar/scripts/check-sync.sh ~/Dev/ai-platform   # exits non-zero only because of the header (#5)
diff packages/grammar/src/TTR.g4 ~/Dev/ai-platform/shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated/TTR.g4
# → only the 2-line vendoring header differs
```

Ship Section G when both gates report no surprises.
