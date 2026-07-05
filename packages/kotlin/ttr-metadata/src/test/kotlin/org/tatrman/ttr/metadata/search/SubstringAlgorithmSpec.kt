package org.tatrman.ttr.metadata.search

import org.tatrman.ttr.metadata.search.SearchQuery
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.model.parseSchemaCode
import org.tatrman.ttr.metadata.graph.ModelGraph
import org.tatrman.ttr.metadata.model.CncSchema
import org.tatrman.ttr.metadata.model.LocalizedText
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.model.ModelVersion
import org.tatrman.ttr.metadata.model.Query
import org.tatrman.ttr.metadata.model.Role
import org.tatrman.ttr.metadata.model.SearchHints
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import org.tatrman.ttr.metadata.search.substring.SubstringAlgorithm
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class SubstringAlgorithmSpec :
    StringSpec({

        "matches a query name with score 0.3" {
            val algo = SubstringAlgorithm()
            val snap = snapshotWith(query("customersList"))
            val out = algo.rebuild(snap, "cs")
            val hits = algo.search(req("customersList"), out.index)
            hits.size shouldBe 1
            hits[0].matchedField shouldBe "name"
            hits[0].score shouldBe 0.3f
            hits[0].algorithm shouldBe "substring"
        }

        "matches an alias with score 0.6" {
            val algo = SubstringAlgorithm()
            val q = query("Q1", search = SearchHints(aliases = listOf("customer-list")))
            val snap = snapshotWith(q)
            val out = algo.rebuild(snap, "cs")
            val hits = algo.search(req("customer-list"), out.index)
            hits.size shouldBe 1
            hits[0].matchedField shouldBe "alias"
            hits[0].score shouldBe 0.6f
        }

        "highest-scoring match wins when multiple fields match on the same object" {
            val algo = SubstringAlgorithm()
            val q =
                query(
                    "report",
                    description = "report on customers",
                    search = SearchHints(examples = listOf("report")),
                )
            val snap = snapshotWith(q)
            val out = algo.rebuild(snap, "cs")
            val hits = algo.search(req("report"), out.index)
            hits.size shouldBe 1
            hits[0].matchedField shouldBe "example"
            hits[0].score shouldBe 0.7f
        }

        "lowercases the user query for case-insensitive match" {
            val algo = SubstringAlgorithm()
            val q = query("Q1", search = SearchHints(aliases = listOf("zákazník")))
            val snap = snapshotWith(q)
            val out = algo.rebuild(snap, "cs")
            val hits = algo.search(req("ZÁKAZNÍK"), out.index)
            hits.size shouldBe 1
            hits[0].matchedField shouldBe "alias"
        }

        "indexes localised display_label only for the requested language" {
            val algo = SubstringAlgorithm()
            val role =
                Role(
                    internalId = "id",
                    qname = qn("cnc", "role", "neutral"),
                    label = LocalizedText(mapOf("cs" to "Faktová", "en" to "Dimensional")),
                    search = SearchHints(),
                )
            val snap = snapshotWithRole(role)
            val outCs = algo.rebuild(snap, "cs")
            algo.search(req("Faktová"), outCs.index).size shouldBe 1
            algo.search(req("Dimensional"), outCs.index).size shouldBe 0

            val outEn = algo.rebuild(snap, "en")
            algo.search(req("Faktová"), outEn.index).size shouldBe 0
            algo.search(req("Dimensional"), outEn.index).size shouldBe 1
        }
    })

private fun req(query: String): SearchQuery = SearchQuery(query = query)

private fun query(
    name: String,
    description: String = "",
    search: SearchHints = SearchHints(),
): Query =
    Query(
        internalId = "id-$name",
        qname = qn("query", "query", name),
        description = description,
        sourceLanguage = "SQL",
        sourceText = "select 1",
        search = search,
    )

private fun snapshotWith(q: Query): RegistrySnapshot {
    val model =
        Model(
            descriptor = ModelDescriptor("test-id", "test"),
            version = ModelVersion("0", Instant.EPOCH),
            schemas = emptyMap(),
            mappings = emptyList(),
            queries = mapOf(q.qname to q),
        )
    return RegistrySnapshot(model, ModelGraph.build(model), Instant.EPOCH, emptyList())
}

private fun snapshotWithRole(r: Role): RegistrySnapshot {
    val model =
        Model(
            descriptor = ModelDescriptor("test-id", "test"),
            version = ModelVersion("0", Instant.EPOCH),
            schemas = mapOf("cnc" to CncSchema(roles = mapOf(r.qname to r))),
            mappings = emptyList(),
            queries = emptyMap(),
        )
    return RegistrySnapshot(model, ModelGraph.build(model), Instant.EPOCH, emptyList())
}

private fun qn(
    schema: String,
    namespace: String,
    name: String,
): QualifiedName =
    QualifiedName(
        schemaCode = parseSchemaCode(schema) ?: SchemaCode.UNSPECIFIED,
        namespace = namespace,
        name = name,
    )
