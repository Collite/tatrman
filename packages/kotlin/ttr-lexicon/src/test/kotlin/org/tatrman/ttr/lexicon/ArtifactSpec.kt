// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json

/**
 * MH T3-data (contracts §4) — the archive's `targets[ref].reachedFrom`, and the v2→v3 seam.
 *
 * `reachedFrom` is the E-R relation graph, projected once at compile time so the resolver never
 * derives structure from names (MS's "no client-side derivation" rule). The seam is the point of
 * most of these cases: the field is DEFAULTED, so a v2 archive decodes here, and `contentHash`
 * covers the entry table only, so adding it cannot move an id that answers "did the vocabulary
 * change?".
 */
class ArtifactSpec :
    StringSpec({

        fun lexicon(targets: Map<String, TargetFacts>) =
            CompiledLexicon(
                header =
                    CompiledLexiconHeader(
                        modelSnapshotHash = "sha256:" + "ab".repeat(32),
                        sourceHashes = SourceHashes(declared = "d", metadata = "m"),
                        builtAt = "2026-09-02T00:00:00Z",
                    ),
                entries =
                    listOf(
                        CompiledEntry(
                            termNormalized = "prodejna",
                            lang = "cs",
                            targetRef = "er.entity.store",
                            targetClass = TargetClass.MODEL_OBJECT,
                            method = "EXACT",
                            sourceTag = SourceTag.METADATA,
                            provenance = EntryProvenance("model/er/parties.ttrm", 0),
                        ),
                    ),
                targets = targets,
            )

        val reach =
            listOf(
                Reach("er.entity.store_returns", mandatory = true),
                Reach("er.entity.store_sales", mandatory = true),
            )

        "reachedFrom round-trips through toJson/fromJson unchanged" {
            val before = lexicon(mapOf("er.entity.store" to TargetFacts("entity", null, reach)))
            val after = CompiledLexicon.fromJson(before.toJson())

            after.targets.getValue("er.entity.store").reachedFrom shouldBe reach
            after shouldBe before
        }

        "a v2-shaped targets object decodes with an empty reachedFrom" {
            // Verbatim v2 bytes: `TargetFacts` had exactly these two keys.
            val v2 =
                """
                {
                  "header": {
                    "schemaVersion": "ttr-lexicon-compiled/v2",
                    "modelSnapshotHash": "sha256:${"ab".repeat(32)}",
                    "sourceHashes": { "declared": "d", "metadata": "m" },
                    "builtAt": "2026-09-02T00:00:00Z"
                  },
                  "entries": [],
                  "targets": {
                    "er.entity.store": { "objectKind": "entity", "ownerRef": null }
                  }
                }
                """.trimIndent()

            val decoded = CompiledLexicon.fromJson(v2)

            decoded.targets.getValue("er.entity.store").objectKind shouldBe "entity"
            decoded.targets.getValue("er.entity.store").reachedFrom shouldBe emptyList()
            // The header keeps what it said — the reader does not rewrite the producer's claim.
            decoded.header.schemaVersion shouldBe "ttr-lexicon-compiled/v2"
        }

        "the schema label moved to v3" {
            CompiledLexiconHeader.SCHEMA_VERSION shouldBe "ttr-lexicon-compiled/v3"
        }

        "contentHash covers the entry table only, so reach does not move the id" {
            lexicon(mapOf("er.entity.store" to TargetFacts("entity", null, reach))).contentHash shouldBe
                lexicon(mapOf("er.entity.store" to TargetFacts("entity", null, emptyList()))).contentHash
        }

        "an unknown key in targets is ignored, not fatal — a v4 archive still reads" {
            val forward =
                """
                {
                  "header": {
                    "schemaVersion": "ttr-lexicon-compiled/v4",
                    "modelSnapshotHash": "sha256:${"ab".repeat(32)}",
                    "sourceHashes": { "declared": "d", "metadata": "m" },
                    "builtAt": "2026-09-02T00:00:00Z"
                  },
                  "entries": [],
                  "targets": {
                    "er.entity.store": {
                      "objectKind": "entity",
                      "reachedFrom": [ { "factRef": "er.entity.store_sales", "mandatory": true } ],
                      "somethingNobodyHasWrittenYet": 7
                    }
                  }
                }
                """.trimIndent()

            CompiledLexicon
                .fromJson(forward)
                .targets
                .getValue("er.entity.store")
                .reachedFrom shouldBe listOf(Reach("er.entity.store_sales", mandatory = true))
        }

        "Reach is a plain serializable pair — factRef and the to-side lower bound" {
            Json.encodeToString(Reach("er.entity.store_sales", mandatory = false)) shouldNotBe ""
            Json.decodeFromString<Reach>("""{"factRef":"a","mandatory":true}""") shouldBe Reach("a", true)
        }
    })
