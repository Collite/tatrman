// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.BindingColumnBareId
import org.tatrman.ttr.parser.model.BindingColumnObject
import org.tatrman.ttr.parser.model.BindingPropertyBareId
import org.tatrman.ttr.parser.model.BindingPropertyBlock
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.Er2DbAttributeDef
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.RelationDef
import org.tatrman.ttr.parser.model.TargetObjectValue
import org.tatrman.ttr.parser.model.TargetReferenceValue

/**
 * v3.0 — inline binding parse tests (was v2.1 `mapping:`).
 *
 * Mirrors `packages/parser/src/__tests__/inline-mappings.test.ts` on the
 * modeler side. Forms exercised:
 *
 *  - entity-level `binding: { target: ..., columns: { ... } }` with all three
 *    column-value shapes (bare-id, `{ target: bareId }`, `{ target: { column: ... } }`).
 *  - attribute-level `binding: <bareId>` and `binding: { target: ... }`.
 *  - relation-level `binding: <fkRef>` and `binding: { fk: <fkRef> }`.
 *  - `targetProperty` bare-id relaxation on explicit `def er2db_attribute`.
 */
class InlineMappingsSpec :
    StringSpec({

        "parses entity with full inline mapping + columns map" {
            val r =
                TtrLoader.parseString(
                    """
                    model er
                    def entity artikl {
                        binding: {
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
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.errors shouldHaveSize 0
            val entity = r.definitions[0]
            entity.shouldBeInstanceOf<EntityDef>()
            val binding = entity.binding
            binding.shouldBeInstanceOf<BindingPropertyBlock>()
            // target carried through as object form
            binding.target.shouldBeInstanceOf<TargetObjectValue>()
            binding.columns shouldHaveSize 3

            // form (a): bare id
            binding.columns[0].name shouldBe "id_artiklu"
            val c0 = binding.columns[0].value
            c0.shouldBeInstanceOf<BindingColumnBareId>()
            c0.id.path shouldBe "IDZBOZI"

            // form (b): { target: bareId } — wrapped in synthetic { target: ... } object
            binding.columns[1].name shouldBe "kód_artiklu"
            val c1 = binding.columns[1].value
            c1.shouldBeInstanceOf<BindingColumnObject>()
            c1.obj.entries.keys shouldBe setOf("target")
            val inner1 = c1.obj.entries["target"]
            inner1.shouldBeInstanceOf<PropertyValue.IdValue>()
            inner1.ref.path shouldBe "KOD_ZBOZI"

            // form (c): { target: { column: bareId } }
            binding.columns[2].name shouldBe "název_artiklu"
            val c2 = binding.columns[2].value
            c2.shouldBeInstanceOf<BindingColumnObject>()
            c2.obj.entries.keys shouldBe setOf("target")
            val inner2 = c2.obj.entries["target"]
            inner2.shouldBeInstanceOf<PropertyValue.ObjectValue>()
            inner2.entries["column"].shouldBeInstanceOf<PropertyValue.IdValue>()
        }

        "parses attribute with bare-id mapping" {
            val r =
                TtrLoader.parseString(
                    """
                    model er
                    def attribute id_produktu { type: int, binding: IDSKUPZBOZI }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val attr = r.definitions[0]
            attr.shouldBeInstanceOf<AttributeDef>()
            val binding = attr.binding
            binding.shouldBeInstanceOf<BindingPropertyBareId>()
            binding.id.path shouldBe "IDSKUPZBOZI"
        }

        "parses attribute with full mapping block" {
            val r =
                TtrLoader.parseString(
                    """
                    model er
                    def attribute název_artiklu {
                        type: text,
                        binding: { target: { column: NAZEV_ZBOZI } }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val attr = r.definitions[0]
            attr.shouldBeInstanceOf<AttributeDef>()
            val binding = attr.binding
            binding.shouldBeInstanceOf<BindingPropertyBlock>()
            binding.target.shouldBeInstanceOf<TargetObjectValue>()
        }

        "parses relation with bare-fk mapping" {
            val r =
                TtrLoader.parseString(
                    """
                    model er
                    def entity a {}
                    def entity b {}
                    def relation r {
                        from: er.entity.a, to: er.entity.b,
                        cardinality: { from: "0..*", to: "1" },
                        join: [{ from: er.entity.a.x, to: er.entity.b.x }],
                        binding: db.dbo.fk_a_b
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val rel = r.definitions[2]
            rel.shouldBeInstanceOf<RelationDef>()
            val binding = rel.binding
            binding.shouldBeInstanceOf<BindingPropertyBareId>()
            binding.id.path shouldBe "db.dbo.fk_a_b"
        }

        "parses relation with fk block" {
            val r =
                TtrLoader.parseString(
                    """
                    model er
                    def entity a {}
                    def entity b {}
                    def relation r {
                        from: er.entity.a, to: er.entity.b,
                        cardinality: { from: "0..*", to: "1" },
                        join: [{ from: er.entity.a.x, to: er.entity.b.x }],
                        binding: { fk: db.dbo.fk_a_b }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val rel = r.definitions[2]
            rel.shouldBeInstanceOf<RelationDef>()
            val binding = rel.binding
            binding.shouldBeInstanceOf<BindingPropertyBlock>()
            binding.fk.shouldNotBeNull()
            binding.fk.path shouldBe "db.dbo.fk_a_b"
        }

        "accepts bare id in target on explicit er2db_attribute" {
            val r =
                TtrLoader.parseString(
                    """
                    model binding
                    def er2db_attribute foo { attribute: er.entity.a.b, target: SOMECOL }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.errors shouldHaveSize 0
            val def = r.definitions[0]
            def.shouldBeInstanceOf<Er2DbAttributeDef>()
            val target = def.target
            target.shouldBeInstanceOf<TargetReferenceValue>()
            target.ref.path shouldBe "SOMECOL"
        }

        "mapping source location points at the value, not the keyword" {
            val r =
                TtrLoader.parseString(
                    """
                    model er
                      def attribute id { type: int, binding: IDX }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val attr = r.definitions[0]
            attr.shouldBeInstanceOf<AttributeDef>()
            val binding = attr.binding
            binding.shouldNotBeNull()
            // Source should be on the same line as the def, not before
            binding.source.line shouldBe 2
        }
    })
