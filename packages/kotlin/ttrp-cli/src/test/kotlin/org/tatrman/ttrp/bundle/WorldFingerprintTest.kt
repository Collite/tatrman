// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Paths

/** T3.3.2 — semantic world fingerprint: stable, deterministic, semantically sensitive. */
class WorldFingerprintTest :
    FunSpec({
        val world =
            run {
                val src = Files.readString(Paths.get("src/test/resources/fixtures/hero.ttrp"))
                TtrpPipeline(
                    TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                    MetadataFixtures.erpModelsRoot(),
                ).plan(src, "hero.ttrp").bound!!.world
            }

        test("fingerprint has the sha256:<hex> shape and is deterministic") {
            val a = WorldFingerprint.of(world)
            val b = WorldFingerprint.of(world)
            a shouldMatch Regex("^sha256:[0-9a-f]{64}$")
            a shouldBe b
        }

        test("semantic sensitivity — bumping an engine version changes the hash") {
            val bumped = world.copy(engines = world.engines.map { it.copy(version = (it.version ?: "0") + "9") })
            WorldFingerprint.of(bumped) shouldNotBe WorldFingerprint.of(world)
        }

        test("semantic sensitivity — flipping a storage's staging flag changes the hash") {
            val flipped = world.copy(storages = world.storages.map { it.copy(staging = !it.staging) })
            WorldFingerprint.of(flipped) shouldNotBe WorldFingerprint.of(world)
        }

        test("R2-8: the bundle fingerprint equals the canonical ttr-metadata one (single canonicalization)") {
            // The two impls diverged (qname-in-hash, empty-string defaults, no extends) → the same world
            // hashed differently on the two sides of the seam. The bundle path now delegates to the
            // canonical one; the same resolved world must yield ONE sha256.
            WorldFingerprint.of(world) shouldBe
                org.tatrman.ttr.metadata.world.WorldFingerprint
                    .of(world)
        }
    })
