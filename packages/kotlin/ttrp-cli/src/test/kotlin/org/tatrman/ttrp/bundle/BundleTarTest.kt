// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Files

/**
 * review-072 R2-4: the F-lite bundle tar's load-bearing property is that the SAME logical bundle always
 * derives the SAME `sha256:` (the door re-derives it and rejects a mismatch, PLT-ENV-001; a re-deploy of
 * a hash-drifted bundle trips PLT-ENV-009). These guard byte-determinism + cruft-invariance.
 */
class BundleTarTest :
    StringSpec({
        "R2-4: packing is byte-deterministic — two packs of the same bundle are identical" {
            val dir = Files.createTempDirectory("bundle")
            Files.writeString(dir.resolve("manifest.json"), """{"schemaVersion":2}""")
            Files.createDirectories(dir.resolve("islands"))
            Files.writeString(dir.resolve("islands/crunch.sql"), "select 1")

            val a = BundleTar.pack(dir)
            val b = BundleTar.pack(dir)
            a.toList() shouldBe b.toList()
            BundleTar.sha256(a) shouldBe BundleTar.sha256(b)
        }

        "R2-4: OS/editor cruft is excluded so it can't drift the bundle hash" {
            val dir = Files.createTempDirectory("bundle")
            Files.writeString(dir.resolve("manifest.json"), """{"schemaVersion":2}""")
            Files.createDirectories(dir.resolve("islands"))
            Files.writeString(dir.resolve("islands/crunch.sql"), "select 1")
            val clean = BundleTar.sha256(BundleTar.pack(dir))

            // The kind of files Finder / an open editor leave behind.
            Files.writeString(dir.resolve(".DS_Store"), "junk")
            Files.writeString(dir.resolve("._manifest.json"), "appledouble")
            Files.writeString(dir.resolve("manifest.json~"), "editor backup")
            Files.writeString(dir.resolve(".manifest.json.swp"), "vim swap")

            BundleTar.sha256(BundleTar.pack(dir)) shouldBe clean // cruft excluded → identical hash
        }

        "sha256 is the door-shape content address (sha256: + 64 lowercase hex)" {
            val h = BundleTar.sha256("x".toByteArray())
            h shouldStartWith "sha256:"
            h.removePrefix("sha256:") shouldMatch Regex("[0-9a-f]{64}")
        }
    })
