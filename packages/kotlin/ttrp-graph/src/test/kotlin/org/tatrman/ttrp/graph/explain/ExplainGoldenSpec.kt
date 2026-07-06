package org.tatrman.ttrp.graph.explain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files

/**
 * Phase-2 DONE bar (T2.3b.6): `ttrp explain` on the hero shows the exact island / wave /
 * movement structure F-lite promised, byte-stable. The golden is committed and
 * hand-reviewed against the F-lite checklist. Regenerate with `-DupdateGolden=true`.
 */
class ExplainGoldenSpec :
    StringSpec({

        fun pipeline() =
            TtrpPipeline(
                TtrpManifest(world = "acme.worlds.dev", manifestDir = GraphFixtures.root),
                MetadataFixtures.erpModelsRoot(),
            )

        fun goldenPath(name: String) = GraphFixtures.root.resolve("explain/$name")

        fun assertGolden(
            actual: String,
            name: String,
        ) {
            val path = goldenPath(name)
            val update = System.getProperty("updateGolden") == "true"
            if (!Files.exists(path) || update) {
                Files.createDirectories(path.parent)
                Files.writeString(path, actual)
                throw AssertionError("golden `$name` written — review the diff, then re-run without -DupdateGolden")
            }
            actual shouldBe Files.readString(path)
        }

        "explain of the hero matches the committed golden (byte-stable)" {
            val out = pipeline().explain(GraphFixtures.program("hero.ttrp"), "hero.ttrp")
            out.ok shouldBe true
            assertGolden(out.text, "hero.explain.txt")
        }

        "explain of the er-hero matches the committed golden (provenance-carrying)" {
            val out = pipeline().explain(GraphFixtures.program("hero-er.ttrp"), "hero-er.ttrp")
            out.ok shouldBe true
            assertGolden(out.text, "hero-er.explain.txt")
        }

        "explain structure: two islands, one transfer wave, waves in order" {
            val out = pipeline().explain(GraphFixtures.program("hero.ttrp"), "hero.ttrp")
            out.text shouldContain "acc_prep  engine=erp_pg  invocation=psql  payload=sql"
            out.text shouldContain "crunch  engine=polars  invocation=python3  payload=python"
            out.text shouldContain "acc_prep -> crunch  via=stage  format=arrow-ipc"
            out.text shouldContain "branch->filter  (polars)"
        }
    })
