// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.project

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import org.tatrman.ttr.metadata.source.SourceSnapshot
import org.tatrman.ttr.snapshot.SnapshotCache
import org.tatrman.ttr.snapshot.SnapshotManifest
import org.tatrman.ttr.snapshot.SnapshotWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.relativeTo

/**
 * PL-P1.S2.T4 + T2 — the connected `ModelSource` reads canon **only from the cache** per the lock
 * pins (B-5). Frozen/offline behaviours (T2) close here rather than in a separate file.
 */
class MetadataServerSourceTest :
    FunSpec({
        fun packModelsRoot(root: Path): ByteArray {
            val docs =
                Files
                    .walk(root)
                    .use { s ->
                        s
                            .filter { Files.isRegularFile(it) }
                            .filter { it.fileName.toString().endsWith(".ttrm") }
                            .toList()
                    }.associate { it.relativeTo(root).toString() to Files.readString(it) }
            return SnapshotWriter.write(SnapshotManifest(kind = "world", producedBy = "veles-test"), docs)
        }

        fun qnames(snap: SourceSnapshot): Set<String> =
            buildSet {
                addAll(snap.tables.keys.map { it.toString() })
                addAll(snap.entities.keys.map { it.toString() })
                addAll(snap.relations.keys.map { it.toString() })
                addAll(snap.queries.keys.map { it.toString() })
                addAll(snap.worlds.keys.map { it.toString() })
            }

        test("connected load resolves the SAME model qnames as LocalFsStorage, no network") {
            val root = MetadataFixtures.erpModelsRoot()
            val cache = SnapshotCache(Files.createTempDirectory("velescache"))
            val archiveId = cache.put(packModelsRoot(root))
            val lock = TtrLock(world = LockWorld("acme.worlds.prod", archiveId))

            val load = MetadataServerSource(lock, cache, LockMode.CONNECTED).loadResult()

            load.missing shouldBe emptyList()
            qnames(load.snapshot) shouldBe qnames(FileBasedSource("fs", 0, LocalFsStorage("fs", root)).load())
        }

        test("--frozen with a pin absent from cache → TTRP-LCK-002 naming the missing id") {
            val cache = SnapshotCache(Files.createTempDirectory("velescache"))
            val missingId = "sha256:${"de".repeat(32)}"
            val lock = TtrLock(world = LockWorld("acme.worlds.prod", missingId))

            val load = MetadataServerSource(lock, cache, LockMode.FROZEN).loadResult()

            load.missing shouldContain missingId
            val d = load.diagnostics.first { it.id.id == "TTRP-LCK-002" }
            d.message shouldContain missingId
        }

        test("--offline with cache → compiles, warns TTRP-LCK-003, staleness.offline recorded") {
            val root = MetadataFixtures.erpModelsRoot()
            val cache = SnapshotCache(Files.createTempDirectory("velescache"))
            val archiveId = cache.put(packModelsRoot(root))
            val lock = TtrLock(world = LockWorld("acme.worlds.prod", archiveId))

            val load = MetadataServerSource(lock, cache, LockMode.OFFLINE).loadResult()

            load.staleness.offline shouldBe true
            load.staleness.servedFromCache shouldContain archiveId
            load.diagnostics.map { it.id.id } shouldContain "TTRP-LCK-003"
        }
    })
