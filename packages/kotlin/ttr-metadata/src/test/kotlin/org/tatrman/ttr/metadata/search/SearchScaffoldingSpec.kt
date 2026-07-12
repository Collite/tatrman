// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.search

import org.tatrman.ttr.metadata.search.SearchQuery
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.model.parseSchemaCode
import org.tatrman.ttr.metadata.graph.ModelGraph
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.model.ModelVersion
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.Instant

class SearchScaffoldingSpec :
    StringSpec({

        "registry returns supported names and lookup" {
            val a = StubAlgorithm("substring")
            val b = StubAlgorithm("regex")
            val r = SearchAlgorithmRegistry(mapOf(a.name to a, b.name to b))
            r.supportedNames shouldContainExactlyInAnyOrder listOf("substring", "regex")
            r.get("substring") shouldBe a
            r.get("nonexistent") shouldBe null
        }

        "holder rebuilds for every algorithm × language" {
            val a = StubAlgorithm("substring")
            val b = StubAlgorithm("regex")
            val r = SearchAlgorithmRegistry(mapOf(a.name to a, b.name to b))
            val holder = SearchIndexHolder(r, listOf("cs", "en"))
            holder.rebuild(emptySnapshot())
            holder.get("substring", "cs") shouldBe SearchIndex.Empty
            holder.get("substring", "en") shouldBe SearchIndex.Empty
            holder.get("regex", "cs") shouldBe SearchIndex.Empty
            holder.get("regex", "en") shouldBe SearchIndex.Empty
            holder.get("substring", "fr") shouldBe null
        }

        "holder retains last-known-good index when an algorithm rebuild throws" {
            val flaky = FlakyAlgorithm("substring")
            val r = SearchAlgorithmRegistry(mapOf(flaky.name to flaky))
            val holder = SearchIndexHolder(r, listOf("cs"))

            flaky.shouldThrow = false
            holder.rebuild(emptySnapshot())
            val first = holder.get("substring", "cs")
            first shouldBe SearchIndex.Empty

            flaky.shouldThrow = true
            holder.rebuild(emptySnapshot())
            holder.get("substring", "cs") shouldBe first
        }

        "holder aggregates compile errors from all algorithms" {
            val a =
                StubAlgorithm(
                    "regex",
                    errors =
                        listOf(
                            CompileError(
                                objectQname = qn("query", "query", "Q"),
                                kind = "query",
                                field = "patterns[0]",
                                message = "unclosed bracket",
                            ),
                        ),
                )
            val r = SearchAlgorithmRegistry(mapOf(a.name to a))
            val holder = SearchIndexHolder(r, listOf("cs", "en"))
            holder.rebuild(emptySnapshot())
            holder.stats().compileErrors.size shouldBe 1
            holder.stats().compileErrors[0].field shouldBe "patterns[0]"
        }
    })

private class StubAlgorithm(
    override val name: String,
    private val errors: List<CompileError> = emptyList(),
) : SearchAlgorithm {
    override fun rebuild(
        snapshot: RegistrySnapshot,
        language: String,
    ): RebuildOutcome = RebuildOutcome(SearchIndex.Empty, errors)

    override fun search(
        request: SearchQuery,
        index: SearchIndex,
    ): List<SearchHit> = emptyList()
}

private class FlakyAlgorithm(
    override val name: String,
) : SearchAlgorithm {
    var shouldThrow: Boolean = false

    override fun rebuild(
        snapshot: RegistrySnapshot,
        language: String,
    ): RebuildOutcome {
        if (shouldThrow) error("simulated rebuild failure")
        return RebuildOutcome(SearchIndex.Empty)
    }

    override fun search(
        request: SearchQuery,
        index: SearchIndex,
    ): List<SearchHit> = emptyList()
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

private fun emptySnapshot(): RegistrySnapshot {
    val model =
        Model(
            descriptor = ModelDescriptor("test-id", "test"),
            version = ModelVersion("0", Instant.EPOCH),
            schemas = emptyMap(),
            mappings = emptyList(),
            queries = emptyMap(),
        )
    return RegistrySnapshot(
        model = model,
        graph = ModelGraph.build(model),
        swappedAt = Instant.EPOCH,
        warnings = emptyList(),
    )
}
