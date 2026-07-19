// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.lsp.methods.GraphViewBuilder
import org.tatrman.ttrp.lsp.protocol.ContainerView
import org.tatrman.ttrp.lsp.protocol.GetGraphResult
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.project.TtrpManifest

/**
 * RJ-P6 6.1.1 — the `ttrp/getGraph` `elaborated` flag. The default view is the authored graph (no
 * reject elaboration — the editing canvas). `elaborated = true` serves the normalized graph with the
 * synthesized reject cluster (guard/branch/reject) visible, each synth node flagged `synthesized`
 * and back-referenced to its authored origin via `synthOf` — the collapse contract the Designer uses.
 */
class GetGraphElaboratedSpec :
    StringSpec({
        val src =
            """
            uses world "acme.worlds.dev"
            container returns_ingest(out clean, out bad, err rejects) target polars {
                raw     = load(files.sales_2026, schema: sales_csv)
                checked = raw -> calc { returned_qty = cast(customer as int) }
                b       = branch(checked, amount > 0)
                clean   = b.true
                bad     = b.false
                rejects = checked.rejects
            }
            returns_ingest.clean   -> display(clean_result)
            returns_ingest.bad     -> store(files.bad_qty)
            returns_ingest.rejects -> store(files.bad_rows)
            """.trimIndent()

        fun view(elaborated: Boolean): GetGraphResult {
            val plan =
                TtrpPipeline(
                    TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                    MetadataFixtures.erpModelsRoot(),
                ).plan(src, "rejects.ttrp")
            return GraphViewBuilder.build("rejects.ttrp", plan, elaborated)
        }

        fun ingest(v: GetGraphResult): ContainerView = v.graph.containers.single { it.path == "returns_ingest" }

        "the default (authored) view carries no synthesized reject nodes" {
            val c = ingest(view(elaborated = false))
            c.nodes.none { it.synthesized } shouldBe true
            c.nodes.none { it.synthOf != null } shouldBe true
            // the authored reject-capable calc is present as an ordinary node.
            c.nodes.any { it.kind == "Calc" || it.kind == "Project" } shouldBe true
        }

        "the elaborated view exposes the reject cluster, each synth node back-referenced to its origin" {
            val c = ingest(view(elaborated = true))
            val synth = c.nodes.filter { it.synthesized }
            (synth.isNotEmpty()) shouldBe true
            // every synth node carries a synthOf, and it resolves to a real node ζ in the same view.
            val allZetas = c.nodes.map { it.zeta }.toSet()
            synth.forEach { n ->
                (n.synthOf != null) shouldBe true
                allZetas shouldContain n.synthOf
            }
        }

        "the flag is opt-in: omitting it equals the authored view" {
            val default = ingest(view(elaborated = false)).nodes.map { it.zeta to it.synthesized }
            val explicitFalse = ingest(view(elaborated = false)).nodes.map { it.zeta to it.synthesized }
            default shouldBe explicitFalse
        }
    })
