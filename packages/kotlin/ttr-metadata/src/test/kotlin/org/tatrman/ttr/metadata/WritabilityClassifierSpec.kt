// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import org.tatrman.ttr.metadata.writability.EntityWritability
import org.tatrman.ttr.metadata.writability.WhyNotCode
import org.tatrman.ttr.metadata.writability.WritabilityClassifier
import java.nio.file.Files

/**
 * EN-P1.2 — the writability classifier (FO §7). One assertion per md-demand Acceptance case: a
 * table-backed identity binding is writable-v1 with its lowering; an aggregation attribute, a
 * view-backed (join) entity, a computed-column attribute, and an unbound entity are each non-writable
 * with the right `whyNot.code`. Plus the T4 modelVersion + T6 determinism gates.
 */
class WritabilityClassifierSpec :
    StringSpec({

        val db =
            """
            model db
            def table CUSTOMER { columns: [ def column ID { type: int }, def column NAME { type: text } ], primaryKey: [ID] }
            def view SALES_V { columns: [ def column ID { type: int } ], definitionSql: "select 1" }
            """.trimIndent()

        val er =
            """
            model er schema entity
            def entity customer {
                binding: { target: { table: db.dbo.CUSTOMER }, columns: { id: ID, name: NAME } },
                attributes: [ def attribute id { type: int, isKey: true }, def attribute name { type: text } ]
            }
            def entity sales_summary {
                attributes: [ def attribute total { type: int, aggregation: sum } ]
            }
            def entity report {
                binding: { target: { table: db.dbo.CUSTOMER }, columns: { id: ID } },
                attributes: [
                    def attribute id { type: int, isKey: true },
                    def attribute label { type: text, binding: { target: { expression: "ID" } } }
                ]
            }
            def entity joined {
                binding: { target: { view: db.dbo.SALES_V } },
                attributes: [ def attribute id { type: int, isKey: true } ]
            }
            def entity orphan {
                attributes: [ def attribute id { type: int, isKey: true } ]
            }
            """.trimIndent()

        fun model(): Model {
            val root = Files.createTempDirectory("writability")
            for ((rel, content) in mapOf("dbp/db.ttrm" to db, "erp/er.ttrm" to er)) {
                val p = root.resolve(rel)
                Files.createDirectories(p.parent)
                Files.writeString(p, content)
            }
            val source = FileBasedSource("w", 100, LocalFsStorage(id = "w", rootPath = root))
            return MetadataLoader(source, ModelDescriptor(id = "m", name = "m")).load().model.shouldNotBeNull()
        }

        val loaded = model()
        val payload = WritabilityClassifier.classify(loaded)

        fun verdict(dotted: String): EntityWritability = payload.entities.first { it.qname == dotted }

        "a table-backed identity binding is writable-v1 with its lowering" {
            val v = verdict("er.entity.customer")
            v.shouldNotBeNull()
            (v as EntityWritability.Writable).let {
                it.rung shouldBe "v1"
                it.lowering.baseTable shouldBe "CUSTOMER"
                it.lowering.binding shouldBe mapOf("id" to "ID", "name" to "NAME")
            }
        }

        "an aggregation-derived attribute ⇒ AGGREGATION" {
            (verdict("er.entity.sales_summary") as EntityWritability.NotWritable).whyNot.code shouldBe
                WhyNotCode.AGGREGATION
        }

        "a computed-column attribute ⇒ COMPUTED_COLUMN" {
            (verdict("er.entity.report") as EntityWritability.NotWritable).whyNot.code shouldBe
                WhyNotCode.COMPUTED_COLUMN
        }

        "a view-backed (non-base-table) entity ⇒ NON_KEY_PRESERVED_JOIN" {
            (verdict("er.entity.joined") as EntityWritability.NotWritable).whyNot.code shouldBe
                WhyNotCode.NON_KEY_PRESERVED_JOIN
        }

        "an entity with no db binding ⇒ NO_DECLARED_WRITEBACK with unlockedBy" {
            (verdict("er.entity.orphan") as EntityWritability.NotWritable).whyNot.let {
                it.code shouldBe WhyNotCode.NO_DECLARED_WRITEBACK
                it.unlockedBy shouldBe "rung-v3"
            }
        }

        "T4 — payload.modelVersion equals what md-metadata serving stamps (model.version.value)" {
            payload.modelVersion shouldBe loaded.version.value
            payload.modelVersion.isNotEmpty() shouldBe true
        }

        "T6 — classifying the same model twice yields a byte-identical payload" {
            val m = model()
            WritabilityClassifier.classify(m).toJson() shouldBe WritabilityClassifier.classify(m).toJson()
        }
    })
