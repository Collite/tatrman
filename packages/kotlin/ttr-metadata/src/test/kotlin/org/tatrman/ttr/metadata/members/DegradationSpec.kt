// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.tatrman.ttr.md.resolve.CatalogUnavailable
import org.tatrman.ttr.md.resolve.QualifiedName
import org.tatrman.ttr.md.resolve.StaleSnapshot
import java.time.Instant

/**
 * S6-A3 — [DegradingMemberCatalog] GI-19 ladder: unavailable-at-pass-start ⇒ [CatalogUnavailable];
 * lost-mid-session ⇒ held snapshot continues + a [StaleSnapshot] signal.
 */
class DegradationSpec :
    StringSpec({
        val asof = Instant.parse("2026-07-08T00:00:00Z")

        /** A source whose availability can be toggled to simulate catalog loss/restore. */
        class FakeSource(
            var up: Boolean,
            private val data: Map<String, List<String>>,
        ) : MemberSource {
            override fun publishedDomains(): Set<QualifiedName> =
                if (!up) throw MemberSourceUnavailable("source down") else data.keys

            override fun distinctMembers(domain: QualifiedName): List<String> =
                if (!up) throw MemberSourceUnavailable("source down") else data.getValue(domain)
        }

        "connected compile with the catalog unavailable at pass start is a hard error (CatalogUnavailable)" {
            val cat = DegradingMemberCatalog(FakeSource(up = false, data = mapOf("md.Name" to listOf("Kaufland"))))
            shouldThrow<CatalogUnavailable> { cat.snapshot(asof) }
            cat.heldSnapshot shouldBe null // nothing ever held
        }

        "catalog lost mid-session keeps serving the held snapshot and emits a StaleSnapshot" {
            val source = FakeSource(up = true, data = mapOf("md.Name" to listOf("Kaufland", "Lidl")))
            val signals = mutableListOf<StaleSnapshot>()
            val cat = DegradingMemberCatalog(source).apply { addStaleListener { signals += it } }

            val good = cat.snapshot(asof) // first pass succeeds → snapshot held
            good.domains() shouldBe setOf("md.Name")
            signals.shouldBeEmpty()

            source.up = false // catalog lost mid-session
            val later = Instant.parse("2026-07-09T00:00:00Z")
            val degraded = cat.snapshot(later) // must NOT throw
            degraded shouldBeSameInstanceAs good // the same held snapshot, unchanged
            signals.single().heldFingerprint shouldBe good.fingerprint
            signals.single().requestedAsof shouldBe later
        }

        "a fresh pass with the source restored takes a new snapshot again" {
            val source = FakeSource(up = true, data = mapOf("md.Name" to listOf("Kaufland")))
            val cat = DegradingMemberCatalog(source)
            cat.snapshot(asof)
            source.up = false
            cat.snapshot(asof) // serves held
            source.up = true
            val refreshed = cat.snapshot(Instant.parse("2026-07-10T00:00:00Z"))
            refreshed.asof shouldBe Instant.parse("2026-07-10T00:00:00Z")
        }
    })
