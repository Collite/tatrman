// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.methods

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import java.nio.file.Files
import java.nio.file.attribute.FileTime

/**
 * review-071 T-C5 — [loadMdTier] caches the parsed MD tier per storage root and reuses it while no
 * `.ttrm` under the root has changed (a stat-based path/size/mtime fingerprint), so the `ttrm/…`
 * handlers stop re-parsing the whole model tree on every RPC. Any edit / add / remove busts the cache.
 */
class MdTierCacheSpec :
    StringSpec({
        "caches an unchanged root and re-parses when a .ttrm changes" {
            val root = Files.createTempDirectory("md-tier-cache")
            val file = root.resolve("model.ttrm")
            Files.writeString(file, "model md\ndef domain A { type: string, publish: members }\n")

            val first = loadMdTier(root)
            loadMdTier(root) shouldBeSameInstanceAs first // unchanged → same cached instance, no re-parse

            // Edit the file (new content + a definitively newer mtime) so the fingerprint changes.
            Files.writeString(
                file,
                "model md\ndef domain A { type: string, publish: members }\ndef domain B { type: int }\n",
            )
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10_000))

            val reloaded = loadMdTier(root)
            reloaded shouldNotBeSameInstanceAs first // busted → re-parsed
            reloaded.model.domains.keys shouldContain "B" // and it reflects the edit
        }
    })
