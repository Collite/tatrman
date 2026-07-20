// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.project.CompileRecord
import org.tatrman.ttrp.project.LockPlugin
import org.tatrman.ttrp.project.LockWorld
import org.tatrman.ttrp.project.RecordStaleness
import org.tatrman.ttrp.project.StatsEntry
import org.tatrman.ttrp.project.TtrLock
import org.tatrman.ttrp.project.TtrLockCodec
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** PL-P1.S3.T2 — the compile record (§5): a bundle-ADJACENT sidecar, binding-dependent, never hashed. */
class CompileRecordTest :
    FunSpec({
        val heroSource = Files.readString(Paths.get("src/test/resources/fixtures/hero.ttrp"))

        val statEntry =
            StatsEntry(
                "shop.sales.db.dbo.ORDER_LINE",
                "sha256:${"aa".repeat(32)}",
                "2026-07-09T02:00:00Z",
                mapOf(
                    "rowCount" to 42.0,
                ),
            )
        val lock =
            TtrLock(
                world = LockWorld("acme.worlds.dev", "sha256:${"9f".repeat(32)}"),
                models = mapOf("shop.sales" to "sha256:${"77".repeat(32)}"),
                manifests = mapOf("tatrman-executor" to "sha256:${"c0".repeat(32)}"),
                plugins = mapOf("org.tatrman:ttr-emit-bash" to LockPlugin("1.0.0", "sha256:${"ab".repeat(32)}")),
            )

        fun buildHero(
            outDir: Path,
            spec: BundleAssembler.CompileRecordSpec?,
        ) = BundleAssembler("1.0.0").build(
            source = heroSource,
            fileName = "hero.ttrp",
            pipelineManifest = TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
            modelsRoot = MetadataFixtures.erpModelsRoot(),
            outDir = outDir,
            compileRecord = spec,
        )

        test("connected record carries lock hash, snapshot ids, worldFingerprint, plugins, statsUsed, objectsRead") {
            val out = Files.createTempDirectory("ttrp-cr")
            val lockBytes = TtrLockCodec.write(lock).toByteArray()
            val result =
                buildHero(
                    out,
                    BundleAssembler.CompileRecordSpec(
                        lockBytes,
                        lock,
                        listOf(statEntry),
                        RecordStaleness(offline = false),
                    ),
                )
            val sidecar = out.resolve("hero.compile-record.json")
            Files.exists(sidecar) shouldBe true
            val record = CompileRecord.JSON.decodeFromString(CompileRecord.serializer(), Files.readString(sidecar))

            record.mode shouldBe "connected"
            record.lock!!.hash shouldBe CompileRecord.sha256Of(lockBytes)
            record.snapshot.world shouldBe lock.world.archive
            record.snapshot.models shouldBe lock.models
            record.worldFingerprint shouldBe result.manifest.world.fingerprint
            record.plugins.map { it.id } shouldContain "org.tatrman:ttr-emit-bash"
            record.statsUsed shouldBe listOf(statEntry) // verbatim
            record.objectsRead.shouldContainSalesSource()
        }

        test("the record is a SIDECAR: beside .bundle/, absent from the bundle and from manifest files{}") {
            val out = Files.createTempDirectory("ttrp-cr")
            val result = buildHero(out, null) // standalone
            val sidecar = out.resolve("hero.compile-record.json")

            Files.exists(sidecar) shouldBe true // beside the bundle dir
            Files.exists(result.dir.resolve("hero.compile-record.json")) shouldBe false // NOT inside .bundle/
            result.manifest.files.keys
                .none { it.contains("compile-record") } shouldBe true // NOT hashed
            val record = CompileRecord.JSON.decodeFromString(CompileRecord.serializer(), Files.readString(sidecar))
            record.mode shouldBe "standalone"
        }

        test("--offline sets staleness.offline (closes the S2.T2 marker)") {
            val out = Files.createTempDirectory("ttrp-cr")
            buildHero(
                out,
                BundleAssembler.CompileRecordSpec(
                    TtrLockCodec.write(lock).toByteArray(),
                    lock,
                    emptyList(),
                    RecordStaleness(offline = true),
                ),
            )
            val record =
                CompileRecord.JSON.decodeFromString(
                    CompileRecord.serializer(),
                    Files.readString(out.resolve("hero.compile-record.json")),
                )
            record.mode shouldBe "offline"
            record.staleness.offline shouldBe true
        }
    })

private fun List<String>.shouldContainSalesSource() {
    // the hero loads files.sales_2026; objectsRead is the F-7 slice of what it read.
    require(any { it.contains("sales") }) { "objectsRead should include the sales source, was $this" }
    // R2-6: it must NOT carry movement-synthesized boundary loads — "accounts" is the container IN-port
    // name a cross-engine staging Load reports as its `source`, not an object the program reads.
    require(none { it == "accounts" }) { "objectsRead leaked a synthesized boundary port name, was $this" }
    // Every entry is a real object ref (has a dotted qname / path), never a bare port token.
    require(all { it.contains(".") || it.contains("/") }) { "objectsRead had a non-object entry, was $this" }
}
