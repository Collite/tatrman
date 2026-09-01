// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.graph.ModelGraph
import org.tatrman.ttr.metadata.model.Attribute
import org.tatrman.ttr.metadata.model.DbColumn
import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttr.metadata.model.Entity
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.model.ModelObject
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.query.MetadataQuery
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * T5.1 — the grounding typed-model surface (grammar 4.2 `semantics { … }`). Loads the
 * golden fixtures (`59-semantics.ttrm` er + `60-semantics-db.ttrm` db) via the real
 * `LocalFsStorage → FileBasedSource → MetadataLoader` pipeline and asserts:
 *  (a) `Attribute` / `DbColumn` expose the resolved `semantics`;
 *  (b) `Entity` / `DbTable` expose `semanticsKind`;
 *  (c) `MetadataQuery` answers the five grounding accessors;
 *  (d) a model whose `semantics` blocks carry diagnostics loads with those as
 *      `LoadIssue`s of category `SEMANTICS_INVALID`, and WITHOUT the offending
 *      `ResolvedSemantics` (degrade, don't fail).
 */
class SemanticsSurfaceSpec :
    StringSpec({

        // The golden fixtures live at repo-root/tests/conformance/fixtures; the test
        // working dir is the module dir (see ModelTtrLoadSpec), so climb three levels.
        val fixturesDir = Path.of("../../../tests/conformance/fixtures")

        fun loadModel(files: Map<String, String>): LoadResult {
            val root = Files.createTempDirectory("semantics-surface")
            for ((rel, content) in files) {
                val p = root.resolve(rel)
                Files.createDirectories(p.parent)
                Files.writeString(p, content)
            }
            val storage = LocalFsStorage(id = "sem", rootPath = root)
            val source = FileBasedSource(sourceId = "sem", priority = 100, storage = storage)
            return MetadataLoader(source, ModelDescriptor(id = "m", name = "m")).load()
        }

        fun queryOf(model: Model): MetadataQuery =
            MetadataQuery(RegistrySnapshot(model, ModelGraph.build(model), Instant.EPOCH, emptyList()))

        // Kind reader that doesn't need the private query helper.
        fun kindOf(o: ModelObject?): String? =
            when (o) {
                is Entity -> o.semanticsKind
                is DbTable -> o.semanticsKind
                else -> null
            }

        // ---- clean golden model (er in package `erp`, db in package `dbp`) ----
        val erFixture = Files.readString(fixturesDir.resolve("59-semantics.ttrm"))
        val dbFixture = Files.readString(fixturesDir.resolve("60-semantics-db.ttrm"))
        val clean = loadModel(mapOf("erp/er.ttrm" to erFixture, "dbp/db.ttrm" to dbFixture))
        val model = clean.model.shouldNotBeNull()
        val query = queryOf(model)

        fun erAttr(name: String): Attribute =
            model.objectByQname()[QualifiedName(SchemaCode.ER, "entity", name)] as Attribute

        fun dbCol(name: String): DbColumn = model.objectByQname()[QualifiedName(SchemaCode.DB, "dbo", name)] as DbColumn

        "clean golden fixtures load with no errors" {
            clean.errors.shouldHaveSize(0)
        }

        "(a) Attribute exposes the resolved semantics (role + refs + code_format)" {
            erAttr("Transaction.txn_date").semantics.shouldNotBeNull().let {
                it.role shouldBe "event_date"
                it.period?.path shouldBe "AccountingPeriod"
            }
            erAttr("Transaction.amount").semantics.shouldNotBeNull().let {
                it.role shouldBe "amount"
                it.currency?.path shouldBe "currency_code"
            }
            erAttr("AccountingPeriod.period").semantics.shouldNotBeNull().let {
                it.role shouldBe "period_code"
                it.codeFormat shouldBe "yyyyMM"
            }
            // An attribute without a semantics block degrades to null.
            erAttr("Transaction.currency_code").semantics?.role shouldBe "currency_code"
        }

        "(a) DbColumn exposes the resolved semantics" {
            dbCol("accounting_period.start_date").semantics?.role shouldBe "period_start"
            dbCol("accounting_period.period").semantics.shouldNotBeNull().let {
                it.role shouldBe "period_code"
                it.codeFormat shouldBe "yyyyMM"
            }
        }

        "(b) Entity / DbTable expose semanticsKind" {
            kindOf(model.objectByQname()[QualifiedName(SchemaCode.ER, "entity", "AccountingPeriod")]) shouldBe
                "period_table"
            kindOf(model.objectByQname()[QualifiedName(SchemaCode.ER, "entity", "FxRate")]) shouldBe "fx_rate"
            kindOf(model.objectByQname()[QualifiedName(SchemaCode.ER, "entity", "PoiLatLon")]) shouldBe "poi"
            kindOf(model.objectByQname()[QualifiedName(SchemaCode.ER, "entity", "CalendarDim")]) shouldBe "calendar"
            // An entity without a semantics block has a null kind.
            kindOf(model.objectByQname()[QualifiedName(SchemaCode.ER, "entity", "Transaction")]).shouldBeNull()
            kindOf(model.objectByQname()[QualifiedName(SchemaCode.DB, "dbo", "accounting_period")]) shouldBe
                "period_table"
            kindOf(model.objectByQname()[QualifiedName(SchemaCode.DB, "dbo", "poi")]) shouldBe "poi"
        }

        "(c) periodTableFor resolves the fiscal source per package (null ⇒ calendar-aligned)" {
            query.periodTableFor("erp")?.qname?.name shouldBe "AccountingPeriod"
            query.periodTableFor("dbp")?.qname?.name shouldBe "accounting_period"
            query.periodTableFor("nonexistent").shouldBeNull()
        }

        "(c) semanticRole reads the role off an attribute/column qname" {
            query.semanticRole(QualifiedName(SchemaCode.ER, "entity", "Transaction.doc_date")) shouldBe "document_date"
            query.semanticRole(QualifiedName(SchemaCode.DB, "dbo", "poi.point")) shouldBe "geo_point"
            query.semanticRole(QualifiedName(SchemaCode.ER, "entity", "Transaction")).shouldBeNull()
        }

        "(c) attributesByRole gathers every member with a role in the package" {
            query
                .attributesByRole("erp", "event_date")
                .map { it.qname.name }
                .shouldContainExactly("Transaction.txn_date")
            query
                .attributesByRole("erp", "currency_code")
                .map { it.qname.name }
                .shouldContainExactly("Transaction.currency_code")
        }

        "(c) poiEntities / fxRateTableFor filter by kind" {
            query.poiEntities("erp").map { it.qname.name }.shouldContainExactly("PoiLatLon", "PoiPoint")
            query.poiEntities("dbp").map { it.qname.name }.shouldContainExactly("poi")
            query.fxRateTableFor("erp")?.qname?.name shouldBe "FxRate"
            query.fxRateTableFor("dbp").shouldBeNull()
        }

        "(d) a model with semantics diagnostics loads, categorizes them, and drops the resolved" {
            val bad =
                """
                model er
                def entity Bad {
                    attributes: [
                        def attribute x { type: date, semantics { role: not_a_role } }
                    ]
                }
                """.trimIndent()
            val result = loadModel(mapOf("badpkg/er.ttrm" to bad))

            // Load succeeds (never throws / null-models on model errors).
            val m = result.model.shouldNotBeNull()

            // The TTR-SEM-2xx diagnostic surfaces as a SEMANTICS_INVALID LoadIssue.
            val semIssues = result.issues.filter { it.category == LoadIssue.Category.SEMANTICS_INVALID }
            semIssues.shouldHaveSize(1)
            semIssues.single().severity shouldBe LoadIssue.Severity.ERROR

            // The offending attribute degrades: no ResolvedSemantics on the typed model.
            val x = m.objectByQname()[QualifiedName(SchemaCode.ER, "entity", "Bad.x")] as Attribute
            x.semantics.shouldBeNull()
        }

        // -------------------------------------------------------------------
        // MS-P1·S2 — the typed model carries the mention facet. This is where every
        // downstream consumer (veles, the lexicon compiler) reads it from.
        // -------------------------------------------------------------------

        fun erEntity(name: String): Entity =
            model.objectByQname()[QualifiedName(SchemaCode.ER, "entity", name)] as Entity

        "Entity.mentionSemantics carries name, code and ordered measures" {
            erEntity("SalesOrderLine").mentionSemantics.shouldNotBeNull().let {
                it.kind.shouldBeNull()
                it.name?.path shouldBe "line_label"
                it.code?.path shouldBe "line_no"
                // Declared order is the contract — the FIRST measure is the default measure.
                it.measures.map { m -> m.attribute.path to m.aggregation } shouldBe
                    listOf("amount_czk" to "sum", "quantity" to "avg")
            }
        }

        "the semantics-declared name/code feed the legacy string fields (D2)" {
            // contracts §1.2: the semantics block is the new spelling of nameAttribute:/
            // codeAttribute:, so consumers reading the old fields keep working on a model
            // that declares only the new ones.
            erEntity("SalesOrderLine").nameAttribute shouldBe "line_label"
            erEntity("SalesOrderLine").codeAttribute shouldBe "line_no"
            erEntity("Customer").nameAttribute shouldBe "customer_name"
            erEntity("Customer").codeAttribute shouldBe "customer_no"
        }

        "both facets coexist on one block — kind is untouched by the mention keys" {
            erEntity("AccountingPeriod").let {
                it.semanticsKind shouldBe "period_table"
                it.mentionSemantics.shouldNotBeNull().kind shouldBe "period_table"
                it.mentionSemantics?.code?.path shouldBe "period"
                it.codeAttribute shouldBe "period"
                // Declaring a code did not invent a name.
                it.nameAttribute shouldBe ""
            }
        }

        "an entity with no semantics block has no mentionSemantics" {
            erEntity("Transaction").mentionSemantics.shouldBeNull()
        }

        "DbTable carries the mention facet against its columns" {
            val r =
                loadModel(
                    mapOf(
                        "dbm/db.ttrm" to
                            """
                            model db
                            def table sales {
                                semantics { name: customer_name, code: doc_no, measures: [amount_czk] },
                                columns: [
                                    def column customer_name { type: text },
                                    def column doc_no { type: text },
                                    def column amount_czk { type: decimal }
                                ]
                            }
                            """.trimIndent(),
                    ),
                )
            r.errors.shouldHaveSize(0)
            val t =
                r.model.shouldNotBeNull().objectByQname()[QualifiedName(SchemaCode.DB, "dbo", "sales")] as DbTable
            t.mentionSemantics.shouldNotBeNull().let {
                it.name?.path shouldBe "customer_name"
                it.measures.map { m -> m.attribute.path } shouldBe listOf("amount_czk")
            }
        }

        // contracts §1.2 rows 1 and 3 — legacy keeps working, and says so with a warning.
        "legacy-only keeps today's path, and the deprecation is a WARNING not an error" {
            val r =
                loadModel(
                    mapOf(
                        "legacy/er.ttrm" to
                            """
                            model er
                            def entity E {
                                nameAttribute: n,
                                attributes: [ def attribute n { type: text } ]
                            }
                            """.trimIndent(),
                    ),
                )
            // ⛑ NOT in `errors`: routed there, a deprecation would fail the load of every
            // estate still writing the legacy property (contracts §4 — 218 is a warning).
            r.errors.shouldHaveSize(0)
            r.warnings.filter { "TTR-SEM-218" in it.message }.shouldHaveSize(1)
            val e = r.model.shouldNotBeNull().objectByQname()[QualifiedName(SchemaCode.ER, "entity", "E")] as Entity
            e.nameAttribute shouldBe "n"
            e.mentionSemantics.shouldBeNull()
        }

        // contracts §1.2 row 4 / MS-D2 — "a disagreement is always a bug": degrade, and do
        // NOT pick a winner. The block is dropped; the legacy field keeps the legacy value.
        "a legacy/semantics disagreement degrades the block and keeps the legacy value" {
            val r =
                loadModel(
                    mapOf(
                        "clash/er.ttrm" to
                            """
                            model er
                            def entity E {
                                nameAttribute: b,
                                semantics { name: a },
                                attributes: [
                                    def attribute a { type: text },
                                    def attribute b { type: text }
                                ]
                            }
                            """.trimIndent(),
                    ),
                )
            r.errors.filter { "TTR-SEM-217" in it.message }.shouldHaveSize(1)
            val e = r.model.shouldNotBeNull().objectByQname()[QualifiedName(SchemaCode.ER, "entity", "E")] as Entity
            e.mentionSemantics.shouldBeNull()
            e.nameAttribute shouldBe "b"
        }

        // T3 — the two facets are orthogonal: being listed as a measure changes nothing
        // about the attribute's own grounding semantics.
        "an attribute listed as a measure keeps its grounding semantics untouched" {
            val r =
                loadModel(
                    mapOf(
                        "both/er.ttrm" to
                            """
                            model er
                            def entity E {
                                semantics { measures: [amount] },
                                attributes: [
                                    def attribute amount { type: decimal, semantics { role: amount, currency: ccy } },
                                    def attribute ccy { type: text, semantics { role: currency_code } }
                                ]
                            }
                            """.trimIndent(),
                    ),
                )
            r.errors.shouldHaveSize(0)
            val m2 = r.model.shouldNotBeNull()
            val amount = m2.objectByQname()[QualifiedName(SchemaCode.ER, "entity", "E.amount")] as Attribute
            amount.semantics.shouldNotBeNull().let {
                it.role shouldBe "amount"
                it.currency?.path shouldBe "ccy"
            }
            val e = m2.objectByQname()[QualifiedName(SchemaCode.ER, "entity", "E")] as Entity
            e.mentionSemantics?.measures?.map { it.attribute.path } shouldBe listOf("amount")
        }

        // T4 — the kind accessors still find their entities when the block ALSO declares
        // mention keys. `AccountingPeriod` in the golden fixture is exactly that mixed block.
        "MetadataQuery kind lookups are unaffected by mention keys on the same block" {
            query.periodTableFor("erp")?.qname?.name shouldBe "AccountingPeriod"
            query.fxRateTableFor("erp")?.qname?.name shouldBe "FxRate"
            query.poiEntities("erp").map { it.qname.name }.shouldContainExactly("PoiLatLon", "PoiPoint")
        }
    })
