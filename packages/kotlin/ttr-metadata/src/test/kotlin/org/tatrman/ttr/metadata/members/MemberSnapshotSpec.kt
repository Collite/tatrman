// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import java.security.MessageDigest
import java.time.Instant

/**
 * S6-A1 — [MaterializedMemberSnapshot] / [MemberFingerprint] contract, mirroring the world
 * `FingerprintSpec`: fingerprint order-insensitive, sensitive to member and domain-set changes,
 * spelled `sha256:<hex>`, `asof` carried and out of the hash, `domains()` = published only.
 */
class MemberSnapshotSpec :
    StringSpec({
        val asof = Instant.parse("2026-07-08T00:00:00Z")

        fun snap(
            byDomain: Map<String, List<String>>,
            at: Instant = asof,
        ) = MaterializedMemberSnapshot.of(byDomain, at)

        "fingerprint is spelled sha256:<hex>" {
            snap(mapOf("md.Name" to listOf("Kaufland"))).fingerprint shouldMatch Regex("^sha256:[0-9a-f]{64}$")
        }

        "fingerprint is insensitive to member insertion order and duplicates" {
            snap(mapOf("md.Name" to listOf("Kaufland", "Lidl", "Aldi"))).fingerprint shouldBe
                snap(mapOf("md.Name" to listOf("Aldi", "Lidl", "Kaufland", "Lidl"))).fingerprint
        }

        "fingerprint is sensitive to adding a member" {
            snap(mapOf("md.Name" to listOf("Kaufland"))).fingerprint shouldNotBe
                snap(mapOf("md.Name" to listOf("Kaufland", "Lidl"))).fingerprint
        }

        "fingerprint is sensitive to removing or renaming a member" {
            val base = snap(mapOf("md.Name" to listOf("Kaufland", "Lidl"))).fingerprint
            base shouldNotBe snap(mapOf("md.Name" to listOf("Kaufland"))).fingerprint // removed
            base shouldNotBe snap(mapOf("md.Name" to listOf("Kaufland", "Metro"))).fingerprint // renamed
        }

        "fingerprint is sensitive to the published-domain set — even for an empty domain" {
            snap(mapOf("md.Name" to listOf("Kaufland"))).fingerprint shouldNotBe
                snap(mapOf("md.Name" to listOf("Kaufland"), "md.Region" to emptyList())).fingerprint
        }

        "fingerprint is asof-independent — content only; asof is carried separately (decision 13)" {
            val a = snap(mapOf("md.Name" to listOf("Kaufland")), Instant.parse("2026-01-01T00:00:00Z"))
            val b = snap(mapOf("md.Name" to listOf("Kaufland")), Instant.parse("2026-07-08T00:00:00Z"))
            a.fingerprint shouldBe b.fingerprint
            a.asof shouldNotBe b.asof
        }

        "asof is carried onto the snapshot" {
            snap(mapOf("md.Name" to listOf("Kaufland"))).asof shouldBe asof
        }

        "domains() are exactly the published domains; members() is null for unpublished" {
            val s = snap(mapOf("md.Name" to listOf("Kaufland"), "md.Region" to listOf("West")))
            s.domains() shouldBe setOf("md.Name", "md.Region")
            s.members("md.Name")!!.contains("Kaufland") shouldBe true
            s.members("md.Unpublished") shouldBe null
        }

        "canonical form recomputes to the reported fingerprint (idempotent, mirrors FingerprintSpec)" {
            val byDomain = mapOf("md.Name" to listOf("Lidl", "Kaufland"))
            val canon = MemberFingerprint.canonicalForm(byDomain)
            MemberFingerprint.canonicalForm(byDomain) shouldBe canon // idempotent
            val expected =
                "sha256:" +
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(canon.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
            snap(byDomain).fingerprint shouldBe expected
        }
    })
