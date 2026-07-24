// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.snapshot

import com.github.luben.zstd.ZstdInputStream
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/**
 * Byte-determinism is the contract of the ② seam (B-3). These are the plan's golden-hash proofs;
 * the pinned hash is expected to match on Linux AND macOS runners (the compressor version is pinned
 * in the catalog). If the golden churns without a content change, that is the tripwire (plan risk #1).
 */
class ArchiveDeterminismTest :
    FunSpec({
        // A fixed, minimal doc tree — deterministic regardless of shared-fixture churn.
        val manifest =
            SnapshotManifest(
                kind = "models",
                qnames = listOf("shop.sales"),
                producedBy = "ttr-snapshot-test",
                resolvedFrom = mapOf("note" to "golden"),
            )
        val docs =
            mapOf(
                "shop/db.ttrm" to "model db\n\ndef table sales { }\n",
                "shop/er.ttrm" to "model er\n\ndef entity Sale { }\n",
                // out-of-order on purpose — the writer must re-sort bytewise.
                "acme/worlds/world.ttrm" to "schema world\n\ndef world prod { }\n",
            )

        // Pinned once, then guarded. Computed value is printed by the golden test if this is PENDING.
        val goldenHash = "sha256:4239aef936c3074608eea9374e43dc53940016677cb7446abf853c48586a3dca"

        test("packing the fixture docs twice yields identical bytes") {
            val a = SnapshotWriter.write(manifest, docs)
            val b = SnapshotWriter.write(manifest, docs)
            a.contentEquals(b) shouldBe true
        }

        test("entry order is bytewise path order, mtime=0, uid=gid=0") {
            val bytes = SnapshotWriter.write(manifest, docs)
            val names = mutableListOf<String>()
            TarArchiveInputStream(ZstdInputStream(ByteArrayInputStream(bytes))).use { tar ->
                while (true) {
                    val e = tar.nextEntry ?: break
                    names += e.name
                    e.modTime.time shouldBe 0L
                    e.longUserId shouldBe 0L
                    e.longGroupId shouldBe 0L
                    e.mode shouldBe 420 // 0o644
                }
            }
            // snapshot.json sorts before docs/… bytewise; docs/ entries in sorted order.
            names shouldBe
                listOf(
                    "docs/acme/worlds/world.ttrm",
                    "docs/shop/db.ttrm",
                    "docs/shop/er.ttrm",
                    "snapshot.json",
                )
        }

        test("archive id equals sha256 of compressed bytes, spelled sha256:<hex>") {
            val bytes = SnapshotWriter.write(manifest, docs)
            val id = SnapshotId.of(bytes)
            id shouldMatch Regex("^sha256:[0-9a-f]{64}$")
            val manual =
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            id shouldBe "sha256:$manual"
        }

        test("golden: fixture archive hash is stable") {
            val id = SnapshotId.of(SnapshotWriter.write(manifest, docs))
            if (goldenHash.endsWith("PENDING")) error("PIN THIS GOLDEN: $id")
            id shouldBe goldenHash
        }
    })
