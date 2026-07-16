// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.metadata.model.Er2DbEntityMapping
import org.tatrman.ttr.metadata.model.MappingTarget
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.Query
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import java.nio.file.Files

/**
 * Regression for `entity_query_mapping_unresolved`: a `def query` is a db-schema
 * object (its inner table refs resolve within a database+schema), so it must
 * register under `db.dbo.<name>` — the same schema+namespace as the tables it
 * wraps — so that a fully-qualified entity binding `target: { query: db.dbo.<name> }`
 * resolves to it. Before the fix the loader stranded queries at
 * `UNSPECIFIED.query.<name>` (hardcoded `qname("query","query",…)`), so every
 * row-filtered entity's mapping pointed at a query the translator could not find.
 */
class QueryBindingQnameSpec :
    StringSpec({

        fun loadModel(files: Map<String, String>) =
            Files.createTempDirectory("query-binding-qname").let { root ->
                for ((rel, content) in files) {
                    val p = root.resolve(rel)
                    Files.createDirectories(p.parent)
                    Files.writeString(p, content)
                }
                val storage = LocalFsStorage(id = "qbq", rootPath = root)
                val source = FileBasedSource(sourceId = "qbq", priority = 100, storage = storage)
                MetadataLoader(source, ModelDescriptor(id = "m", name = "m")).load()
            }

        val dbFixture =
            """
            package dbp

            def table FOO_T {
                description: "physical table",
                primaryKey: [ID],
                columns: [
                    def column ID { type: int, isKey: true },
                    def column CODE { type: text, optional: true }
                ]
            }

            def query foo__filter {
                description: "Synthetic row-filter query over FOO_T.",
                language: SQL,
                sourceText: "SELECT * FROM FOO_T WHERE CODE like '5%'",
                tags: ["synthetic", "where-filter"]
            }
            """.trimIndent()

        val erFixture =
            """
            package erp

            def entity foo {
                description: "Foo entity bound to a row-filter query.",
                binding: { target: { query: db.dbo.foo__filter } },
                attributes: [
                    def attribute id { type: int, isKey: true, description: "id", binding: ID },
                    def attribute code { type: text, optional: true, description: "code", binding: CODE }
                ]
            }
            """.trimIndent()

        val dbDbo = QualifiedName(SchemaCode.DB, "dbo", "foo__filter")

        "def query registers under db.dbo, not UNSPECIFIED.query" {
            val model = loadModel(mapOf("erp/er.ttrm" to erFixture, "dbp/db.ttrm" to dbFixture)).model.shouldNotBeNull()

            model.queries.keys shouldContain dbDbo
            model.queries[dbDbo].shouldNotBeNull().qname shouldBe dbDbo
            // The pre-fix stranded key (schema unspecified, namespace "query") must be gone.
            model.queries.keys.any { it.namespace == "query" } shouldBe false
        }

        "entity inline query binding resolves to the registered db.dbo query" {
            val model = loadModel(mapOf("erp/er.ttrm" to erFixture, "dbp/db.ttrm" to dbFixture)).model.shouldNotBeNull()

            val mapping =
                model.mappings
                    .filterIsInstance<Er2DbEntityMapping>()
                    .single { it.entity == QualifiedName(SchemaCode.ER, "entity", "foo") }
            val target = mapping.target.shouldBeInstanceOf<MappingTarget.SqlQuery>()
            target.qname shouldBe dbDbo

            // The end-to-end that was broken: the mapping target resolves to a real Query.
            model.objectByQname()[target.qname].shouldBeInstanceOf<Query>()
        }
    })
