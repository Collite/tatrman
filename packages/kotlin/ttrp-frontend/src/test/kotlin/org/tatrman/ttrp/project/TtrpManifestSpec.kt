package org.tatrman.ttrp.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.resolve.ResolutionFixtures

/**
 * `[ttrp]` manifest reader (T1.3.2, S5/S18, contracts §2). Walk-up to `modeler.toml`,
 * all-optional keys, enum violations → `TTRP-CFG-001`, unknown keys → `TTRP-CFG-002`.
 */
class TtrpManifestSpec :
    StringSpec({

        "walk-up from programs/ finds the [ttrp] table with every key parsed" {
            val result = TtrpManifestReader.resolve(ResolutionFixtures.projectDir().resolve("programs"))
            result.found shouldBe true
            val m = result.manifest
            m.world shouldBe "acme.worlds.dev"
            m.bareTarget shouldBe "erp_pg"
            m.bareShell shouldBe "bash"
            m.splitPolicy shouldBe SplitPolicy.WARN
            m.displayDefault shouldBe "arrow"
            m.rlsEgress shouldBe RlsEgress.WARN
            m.defaultImports shouldContainExactly listOf("erp.*")
            result.diagnostics shouldBe emptyList()
        }

        "a missing modeler.toml yields an all-defaults manifest, not an error" {
            val result =
                TtrpManifestReader.resolve(
                    java.nio.file.Path
                        .of(System.getProperty("java.io.tmpdir")),
                )
            result.found shouldBe false
            result.manifest.world.shouldBeNull()
            result.manifest.splitPolicy shouldBe SplitPolicy.WARN
            result.diagnostics shouldBe emptyList()
        }

        "split-policy enum violation is TTRP-CFG-001" {
            val r = TtrpManifestReader.parse("[ttrp]\nsplit-policy = \"maybe\"\n", ResolutionFixtures.projectDir())
            r.diagnostics.map { it.id.id } shouldContainExactly listOf("TTRP-CFG-001")
        }

        "unknown [ttrp] key is TTRP-CFG-002 with a closed-table suggestion" {
            val r = TtrpManifestReader.parse("[ttrp]\nworlds = \"x\"\n", ResolutionFixtures.projectDir())
            val d = r.diagnostics.first { it.id.id == "TTRP-CFG-002" }
            (d.suggestedAlternative ?: "") shouldBe "did you mean `world`?"
        }

        "other tables (TTR-M's own) are ignored, never diagnosed" {
            val r =
                TtrpManifestReader.parse(
                    "[modeler]\nsomething = \"x\"\n[ttrp]\nworld = \"acme.worlds.dev\"\n",
                    ResolutionFixtures.projectDir(),
                )
            r.diagnostics shouldBe emptyList()
            r.manifest.world shouldBe "acme.worlds.dev"
        }

        "S18: default-imports is exposed but the canonical-doc resolver never reads it" {
            // A program with NO import but the manifest carrying default-imports=["erp.er.*"]
            // still fails to resolve a bare er name — canonical docs need their own import.
            val manifest =
                TtrpManifest(
                    world = "acme.worlds.dev",
                    defaultImports = listOf("erp.er.*"),
                    manifestDir = ResolutionFixtures.projectDir(),
                )
            val src = "container c target polars {\n    x = load(customer)\n}\n"
            val report =
                org.tatrman.ttrp.resolve
                    .TtrpChecker(
                        manifest,
                        ResolutionFixtures.modelsRoot(),
                    ).check(src, "s18.ttrp")
            report.errors.map { it.id.id } shouldContainExactly listOf("TTRP-RES-001")
        }
    })
