// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.search

import org.tatrman.ttr.metadata.search.SearchQuery
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.model.parseSchemaCode
import org.tatrman.ttr.metadata.graph.ModelGraph
import org.tatrman.ttr.metadata.model.LocalizedTextList
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.model.ModelVersion
import org.tatrman.ttr.metadata.model.Query
import org.tatrman.ttr.metadata.model.SearchHints
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import org.tatrman.ttr.metadata.search.keyword.KeywordAlgorithm
import org.tatrman.ttr.metadata.search.keyword.StopWords
import org.tatrman.ttr.metadata.search.keyword.Tokenizer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class KeywordAlgorithmSpec :
    StringSpec({

        "stop-word resource files load and known stops are present" {
            val sw = StopWords(listOf("cs", "en"))
            sw.isStop("cs", "ten") shouldBe true
            sw.isStop("en", "the") shouldBe true
            sw.isStop("cs", "zakaznik") shouldBe false
        }

        "tokenizer NFD-folds diacritics" {
            val sw = StopWords(listOf("cs"))
            val t = Tokenizer(sw)
            t.tokenize("Zákazníci", "cs") shouldBe listOf("zakaznici")
        }

        "tokenizer drops stop-words" {
            val sw = StopWords(listOf("cs"))
            val t = Tokenizer(sw)
            t.tokenize("seznam ten zákazníků", "cs") shouldBe listOf("seznam", "zakazniku")
        }

        "single-token keyword match scores 1.0" {
            val algo = KeywordAlgorithm(StopWords(listOf("cs")))
            val q = query("Q", search = SearchHints(keywords = LocalizedTextList(mapOf("cs" to listOf("zákazníci")))))
            val out = algo.rebuild(snapshotOf(q), "cs")
            val hits = algo.search(req("zákazníci", "cs"), out.index)
            hits.size shouldBe 1
            hits[0].matchedField shouldBe "keyword"
            hits[0].score shouldBe 1.0f
        }

        "multi-token query aggregates token weights" {
            val algo = KeywordAlgorithm(StopWords(listOf("cs")))
            val q =
                query(
                    "Q",
                    search =
                        SearchHints(
                            keywords =
                                LocalizedTextList(
                                    mapOf(
                                        "cs" to listOf("zákazníci", "klienti"),
                                    ),
                                ),
                        ),
                )
            val out = algo.rebuild(snapshotOf(q), "cs")
            // Query has two tokens; one matches → 0.5 × 1.0 (keyword factor) = 0.5
            val hits = algo.search(req("zákazníci foo", "cs"), out.index)
            hits.size shouldBe 1
            hits[0].score shouldBe 0.5f
        }

        "alias match is outscored by keyword match for the same query" {
            val algo = KeywordAlgorithm(StopWords(listOf("cs")))
            val q1 = query("Q1", search = SearchHints(keywords = LocalizedTextList(mapOf("cs" to listOf("foo")))))
            val q2 = query("Q2", search = SearchHints(aliases = listOf("foo")))
            val out = algo.rebuild(snapshotOf(q1, q2), "cs")
            val hits = algo.search(req("foo", "cs"), out.index).sortedByDescending { it.score }
            hits.size shouldBe 2
            hits[0].matchedField shouldBe "keyword"
            hits[0].score shouldBe 1.0f
            hits[1].matchedField shouldBe "alias"
            hits[1].score shouldBe 0.5f
        }

        "example match scores below alias which scores below keyword" {
            val algo = KeywordAlgorithm(StopWords(listOf("cs")))
            val q1 = query("Q1", search = SearchHints(keywords = LocalizedTextList(mapOf("cs" to listOf("foo")))))
            val q2 = query("Q2", search = SearchHints(aliases = listOf("foo")))
            val q3 = query("Q3", search = SearchHints(examples = listOf("foo bar")))
            val out = algo.rebuild(snapshotOf(q1, q2, q3), "cs")
            val hits = algo.search(req("foo", "cs"), out.index).sortedByDescending { it.score }
            hits[0].score shouldBe 1.0f
            hits[1].score shouldBe 0.5f
            hits[2].score shouldBe 0.4f
        }

        "empty user query returns no results without throwing" {
            val algo = KeywordAlgorithm(StopWords(listOf("cs")))
            val q = query("Q", search = SearchHints(keywords = LocalizedTextList(mapOf("cs" to listOf("zákazníci")))))
            val out = algo.rebuild(snapshotOf(q), "cs")
            algo.search(req("", "cs"), out.index) shouldBe emptyList()
        }
    })

private fun req(
    query: String,
    language: String,
): SearchQuery = SearchQuery(query = query, language = language)

private fun query(
    name: String,
    search: SearchHints = SearchHints(),
): Query =
    Query(
        internalId = "id-$name",
        qname = qn("query", "query", name),
        sourceLanguage = "SQL",
        sourceText = "select 1",
        search = search,
    )

private fun snapshotOf(vararg q: Query): RegistrySnapshot {
    val model =
        Model(
            descriptor = ModelDescriptor("test-id", "test"),
            version = ModelVersion("0", Instant.EPOCH),
            schemas = emptyMap(),
            mappings = emptyList(),
            queries = q.associateBy { it.qname },
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
