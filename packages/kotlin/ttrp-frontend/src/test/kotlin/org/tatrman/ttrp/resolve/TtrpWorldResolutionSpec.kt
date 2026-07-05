package org.tatrman.ttrp.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.project.TtrpManifest

/**
 * World selection + resolution (T1.3.3, D-g offline). Selection precedence
 * (pin > `[ttrp] world` > WLD-001) is the caller's contract; ttr-metadata's
 * structured failures map to `TTRP-WLD-*`.
 */
class TtrpWorldResolutionSpec :
    StringSpec({

        // Pre-flight #3: the shared world/model fixtures are TTR-M-valid before resolution exists.
        "shared erp-project fixtures load standalone via ttr-metadata" {
            MetadataFixtures
                .loadErpSnapshot()
                .model.schemas.keys shouldContain "world"
        }

        fun resolve(
            source: String,
            world: String? = "acme.worlds.dev",
            models: java.nio.file.Path = ResolutionFixtures.modelsRoot(),
        ) = TtrpChecker(TtrpManifest(world = world, manifestDir = ResolutionFixtures.projectDir()), models)
            .check(source, "w.ttrp")

        "acme.worlds.dev resolves; engines/storages/staging enumerated" {
            val r = resolve("container c target erp_pg { }")
            val w = r.world.shouldNotBeNull()
            w.engines.map { it.qname.name } shouldContainExactlyInAnyOrder listOf("erp_pg", "polars")
            w.storages.map { it.qname.name } shouldContainExactlyInAnyOrder listOf("erp_db", "files", "stage")
            w.staging
                .shouldNotBeNull()
                .qname.name shouldBe "stage"
        }

        "the uses-world pin wins over the [ttrp] world default" {
            // pin names a non-world → WLD-003, proving the pin (not the manifest default) drove it.
            val r =
                TtrpChecker(
                    TtrpManifest(world = "acme.worlds.dev", manifestDir = ResolutionFixtures.projectDir()),
                    ResolutionFixtures.modelsRoot(),
                ).check("uses world \"acme.worlds.dev.erp_pg\"\ncontainer c target polars { }", "w.ttrp")
            r.errors.map { it.id.id } shouldContain "TTRP-WLD-003"
        }

        "no world anywhere is TTRP-WLD-001" {
            resolve("container c target polars { }", world = null).errors.map { it.id.id } shouldContain "TTRP-WLD-001"
        }

        "unknown world qname is TTRP-WLD-002" {
            resolve(
                "uses world \"acme.worlds.prod\"\ncontainer c target polars { }",
            ).errors.map { it.id.id } shouldContain
                "TTRP-WLD-002"
        }

        "two staging storages is TTRP-WLD-004" {
            resolve(
                "container c target polars { }",
                models = MetadataFixtures.worldsNegativeRoot("two-staging"),
            ).errors.map { it.id.id } shouldContain "TTRP-WLD-004"
        }
    })
