// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.query

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.MetadataLoader
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * Query-level `search { patterns }` must survive the whole load path
 * (.ttrm → parser QueryDef.search → metadata Query.search). This is the exact
 * surface Veles reads to classify a query as PATTERN vs NAMED
 * (`partition { it.search.patterns.isNotEmpty() }`). Regression cover for the
 * Hartland demo blocker: a query whose `search.patterns` were authored but never
 * reached the runtime would be served as NAMED, so no pattern answering.
 */
class QuerySearchPatternsSpec :
    StringSpec({

        "query search.patterns survive the load path into Model.queries" {
            val models = createTempDirectory("qsp-models")
            val pkg = models.resolve("hartland").also { it.toFile().mkdirs() }
            pkg.resolve("queries.ttrm").writeText(
                """
                package hartland
                model query

                def query channel_revenue_monthly {
                    description: "Monthly revenue per channel."
                    language: SQL
                    sourceText: "select 1"
                    search {
                        patterns: [
                            "(monthly|month by month) revenue (per|by) channel",
                            "revenue by channel over time"
                        ],
                        examples: [
                            "Monthly revenue by channel"
                        ]
                    }
                    parameters: [
                        { name: year_from, type: int, label: "From year" }
                    ]
                }
                """.trimIndent(),
            )

            val storage = LocalFsStorage(id = "qsp", rootPath = models)
            val result = MetadataLoader(FileBasedSource(sourceId = "qsp", priority = 100, storage = storage)).load()
            val model = result.model ?: error("model failed to load: $result")

            val query =
                model.queries.values.firstOrNull { it.qname.name == "channel_revenue_monthly" }
                    ?: error("query not loaded; queries=${model.queries.keys}")

            // The exact predicate Veles uses to classify PATTERN vs NAMED.
            query.search.patterns.isNotEmpty() shouldBe true
            query.search.patterns shouldContainExactly
                listOf(
                    "(monthly|month by month) revenue (per|by) channel",
                    "revenue by channel over time",
                )
            query.search.examples shouldContainExactly listOf("Monthly revenue by channel")
        }
    })
