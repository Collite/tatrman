// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.snapshot

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import org.tatrman.ttr.metadata.source.ModelStorage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.relativeTo

/**
 * The archive is only the *transport* for the connected binding (B-1): loading through it must be
 * indistinguishable from loading the on-disk repo it was packed from (S1.T2).
 */
class SnapshotArchiveStorageTest :
    FunSpec({
        fun packModelsRoot(root: Path): ByteArray {
            val docs =
                Files
                    .walk(root)
                    .use { s ->
                        s
                            .filter { Files.isRegularFile(it) }
                            .filter {
                                it.fileName.toString().endsWith(".ttrm") ||
                                    it.fileName.toString().endsWith(".ttr")
                            }.toList()
                    }.associate { it.relativeTo(root).toString() to Files.readString(it) }
            return SnapshotWriter.write(
                SnapshotManifest(kind = "models", producedBy = "test", qnames = emptyList()),
                docs,
            )
        }

        fun qnameKeys(storage: ModelStorage): Set<String> {
            val snap = FileBasedSource("s", 0, storage).load()
            return buildSet {
                addAll(snap.tables.keys.map { it.toString() })
                addAll(snap.views.keys.map { it.toString() })
                addAll(snap.entities.keys.map { it.toString() })
                addAll(snap.relations.keys.map { it.toString() })
                addAll(snap.queries.keys.map { it.toString() })
                addAll(snap.worlds.keys.map { it.toString() })
            }
        }

        test("listFiles/read over an archive load the SAME model qnames as LocalFsStorage") {
            val root = MetadataFixtures.erpModelsRoot()
            val archive = packModelsRoot(root)
            val fromArchive =
                SnapshotArchiveStorage.of("erp", archive).getOrThrow()

            qnameKeys(fromArchive) shouldBe qnameKeys(LocalFsStorage("erp", root))
        }

        test("snapshot.json round-trips {formatVersion, kind, qnames, producedBy, resolvedFrom}") {
            val m =
                SnapshotManifest(
                    kind = "world",
                    qnames = listOf("acme.worlds.prod"),
                    producedBy = "veles 0.9.0",
                    resolvedFrom = mapOf("platformWorldCommit" to "abc123"),
                )
            val bytes = SnapshotWriter.write(m, mapOf("acme/worlds/world.ttrm" to "schema world\n"))
            val storage = SnapshotArchiveStorage.of("w", bytes).getOrThrow()
            storage.manifest shouldBe m
        }

        test("corrupt archive → structured failure, no throw") {
            val result = SnapshotArchiveStorage.of("bad", byteArrayOf(1, 2, 3, 4, 5))
            result.isFailure.shouldBeTrue()
        }
    })
