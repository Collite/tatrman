// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.snapshot

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

class SnapshotCacheTest :
    FunSpec({
        test("put is content-addressed (sha256/<first2>/<hex>), get round-trips, has reflects presence") {
            val root = Files.createTempDirectory("ttrcache")
            val cache = SnapshotCache(root)
            val bytes =
                SnapshotWriter.write(
                    SnapshotManifest(kind = "models", producedBy = "t"),
                    mapOf(
                        "a/x.ttrm" to "model db\n",
                    ),
                )
            val id = cache.put(bytes)

            id shouldBe SnapshotId.of(bytes)
            cache.has(id).shouldBeTrue()
            cache.get(id)!!.contentEquals(bytes) shouldBe true

            val hex = id.removePrefix("sha256:")
            root
                .resolve("sha256")
                .resolve(hex.substring(0, 2))
                .resolve(hex)
                .exists()
                .shouldBeTrue()
        }

        test("put is idempotent and leaves no partial temp files") {
            val root = Files.createTempDirectory("ttrcache")
            val cache = SnapshotCache(root)
            val bytes =
                SnapshotWriter.write(
                    SnapshotManifest(kind = "models", producedBy = "t"),
                    mapOf(
                        "a/x.ttrm" to "model db\n",
                    ),
                )
            val id1 = cache.put(bytes)
            val id2 = cache.put(bytes)
            id1 shouldBe id2
            // exactly one file in the shard dir, no .tmp-*.part left behind.
            val hex = id1.removePrefix("sha256:")
            val shard = root.resolve("sha256").resolve(hex.substring(0, 2))
            shard.listDirectoryEntries().size shouldBe 1
        }

        test("gc evicts everything not in keep, retains pinned") {
            val root = Files.createTempDirectory("ttrcache")
            val cache = SnapshotCache(root)
            val a =
                cache.put(
                    SnapshotWriter.write(
                        SnapshotManifest(kind = "models", producedBy = "t"),
                        mapOf(
                            "a/x.ttrm" to "model db\n// a\n",
                        ),
                    ),
                )
            val b =
                cache.put(
                    SnapshotWriter.write(
                        SnapshotManifest(kind = "models", producedBy = "t"),
                        mapOf(
                            "a/x.ttrm" to "model db\n// b\n",
                        ),
                    ),
                )

            val removed = cache.gc(keep = setOf(a))
            removed shouldBe 1
            cache.has(a).shouldBeTrue()
            cache.has(b).shouldBeFalse()
        }

        test("miss returns null, invalid id rejected") {
            val cache = SnapshotCache(Files.createTempDirectory("ttrcache"))
            cache.get("sha256:${"0".repeat(64)}") shouldBe null
            runCatching { cache.has("not-an-id") }.isFailure shouldBe true
        }
    })
