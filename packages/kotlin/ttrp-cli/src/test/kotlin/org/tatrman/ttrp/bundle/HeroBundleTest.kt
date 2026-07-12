// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.project.TtrpManifest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/** T3.3.6 — the hero bundle assembles end-to-end offline; no live PG/Python path is touched. */
class HeroBundleTest :
    FunSpec({
        val heroSource = Files.readString(Paths.get("src/test/resources/fixtures/hero.ttrp"))

        fun buildHero(outDir: Path): BundleAssembler.BundleResult =
            BundleAssembler("1.0.0").build(
                source = heroSource,
                fileName = "hero.ttrp",
                pipelineManifest =
                    TtrpManifest(
                        world = "acme.worlds.dev",
                        manifestDir = MetadataFixtures.erpProjectRoot(),
                    ),
                modelsRoot = MetadataFixtures.erpModelsRoot(),
                outDir = outDir,
            )

        test("hero bundle tree, manifest fields, hashes, and run.sh all assemble offline") {
            val outDir = Files.createTempDirectory("ttrp-bundle")
            val result = buildHero(outDir)
            val dir = result.dir
            val m = result.manifest

            // Tree.
            Files.exists(dir.resolve("islands/acc_prep.sql")) shouldBe true
            Files.exists(dir.resolve("islands/crunch.py")) shouldBe true
            Files.exists(dir.resolve("manifest.json")) shouldBe true
            Files.exists(dir.resolve("run.sh")) shouldBe true
            // Runtime dirs are never created at build time (F-e).
            Files.exists(dir.resolve("logs")) shouldBe false
            Files.exists(dir.resolve("staging")) shouldBe false
            Files.exists(dir.resolve("out")) shouldBe false

            // Manifest fields.
            m.world.qname shouldBe "acme.worlds.dev"
            m.world.fingerprint shouldContain "sha256:"
            m.connections shouldContain "TTR_CONN_ERP_PG"
            m.displays.first().file shouldBe "out/main_result.arrow"
            m.islands.map { it.name }.toSet() shouldBe setOf("acc_prep", "crunch")
            // Waves: acc_prep before crunch (transfer between them).
            val flatWaves = m.waves.flatten()
            (flatWaves.indexOf("acc_prep") < flatWaves.indexOf("crunch")) shouldBe true

            // Every files{} hash re-verifies against disk (excluding manifest.json itself).
            m.files.forEach { (rel, hash) ->
                val onDisk = "sha256:" + sha256(Files.readAllBytes(dir.resolve(rel)))
                withClue(rel) { onDisk shouldBe hash }
            }

            // Island SQL is the hero fragment verbatim; crunch is the Polars mainline.
            Files.readString(dir.resolve("islands/acc_prep.sql")) shouldContain "from erp.accounts"
            Files.readString(dir.resolve("islands/crunch.py")) shouldContain ".join("

            // bash -n accepts run.sh.
            val bash = which("bash")
            if (bash != null) {
                val proc =
                    ProcessBuilder(bash.toString(), "-n", dir.resolve("run.sh").toString())
                        .redirectErrorStream(true)
                        .start()
                val out = proc.inputStream.readBytes().decodeToString()
                if (proc.waitFor() != 0) throw AssertionError("bash -n failed:\n$out")
            }
        }

        test("assembly is deterministic — building twice yields identical files{} hashes") {
            val a = buildHero(Files.createTempDirectory("ttrp-bundle-a")).manifest.files
            val b = buildHero(Files.createTempDirectory("ttrp-bundle-b")).manifest.files
            a shouldBe b
        }
    })

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun which(cmd: String): Path? =
    System
        .getenv("PATH")
        .orEmpty()
        .split(File.pathSeparatorChar)
        .map { Paths.get(it, cmd) }
        .firstOrNull { Files.isExecutable(it) }

private fun withClue(
    clue: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("[$clue] ${e.message}", e)
    }
}
