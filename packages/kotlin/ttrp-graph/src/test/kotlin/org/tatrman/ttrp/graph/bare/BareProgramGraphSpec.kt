package org.tatrman.ttrp.graph.bare

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.build.GraphBuilder
import org.tatrman.ttrp.graph.explain.NormalizedGraphJson
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.project.TtrpManifest
import org.tatrman.ttrp.resolve.TtrpChecker

/**
 * T6.3.3 payoff: a bare `.ttr.sql` / `.ttr.py` file compiles all the way to a graph — the wrapper
 * container (name from filename), a synthesized program-level `load` per derived in-port, the
 * decomposed island interior, and a default Display — through the SAME checker + GraphBuilder as
 * every other program (the wrapper is derived; the source is never rewritten, C0/C2-f).
 */
class BareProgramGraphSpec :
    StringSpec({
        fun bareGraph(
            fileName: String,
            source: String,
        ): TtrpGraph {
            val manifest =
                TtrpManifest(
                    world = "acme.worlds.dev",
                    bareTarget = "erp_pg",
                    defaultImports = listOf("erp.*"),
                    manifestDir = GraphFixtures.root,
                )
            val report = TtrpChecker(manifest, MetadataFixtures.erpModelsRoot()).check(source, fileName)
            report.diagnostics.filter { it.severity == org.tatrman.ttrp.diagnostics.Severity.ERROR } shouldBe
                emptyList()
            return GraphBuilder().build(report).graph
        }

        "bare crunch.ttr.sql → derived container `crunch` @ erp_pg with a synthesized Load, Filter, Project, Display" {
            val g = bareGraph("crunch.ttr.sql", "SELECT account_id, region\nFROM accounts\nWHERE status = 'ACTIVE'\n")
            val norm = NormalizedGraphJson.write(g)
            norm shouldContain "crunch target=erp_pg"
            norm shouldContain "Load(source=accounts"
            norm shouldContain "Filter("
            norm shouldContain "Project("
            norm shouldContain "Display(name=main_result)"
        }

        "bare prep.ttr.py → the pandas receiver becomes a fed in-port + Filter/Project island" {
            val g = bareGraph("prep.ttr.py", "accounts.filter(status == 'ACTIVE').select(account_id, region)\n")
            val norm = NormalizedGraphJson.write(g)
            norm shouldContain "prep target=erp_pg"
            norm shouldContain "Load(source=accounts"
            norm shouldContain "Filter("
        }
    })
