// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * S2-C3 — disconnected mode (R13, D18). With no member snapshot a bare member/INT token is illegal
 * (`TTRP-MD-007` — qualify it); a qualified pair resolves structurally with the coordinate marked
 * `deferred` (existence + exact attribute checked at bind time, S6).
 *
 * Coder note (C3 decision, recorded in contracts changelog): INT components are member-candidates,
 * so a bare `2025` is illegal offline too — only `year.2025` (qualified) resolves.
 */
class DisconnectedSpec :
    StringSpec({
        val model = ResolverFixtures.model
        val resolver = DefaultMdPathResolver()

        fun resolveOffline(input: String) = resolver.resolve(PathText.parse(input), model, null, Instant.EPOCH)

        "a bare member token is illegal offline (MD-007)" {
            val outcome = resolveOffline("Kaufland.net")
            check(outcome is ResolutionOutcome.Failed) { "expected Failed, got $outcome" }
            outcome.diagnostics.single().code shouldBe "TTRP-MD-007"
        }

        "a bare INT member is illegal offline too (MD-007)" {
            val outcome = resolveOffline("sales.2025.net")
            check(outcome is ResolutionOutcome.Failed) { "expected Failed, got $outcome" }
            outcome.diagnostics.single().code shouldBe "TTRP-MD-007"
        }

        "a qualified pair resolves offline with a deferred coordinate" {
            val outcome = resolveOffline("sales.customer.Kaufland.net")
            check(outcome is ResolutionOutcome.Resolved) { "expected Resolved, got $outcome" }
            val coord = outcome.path.coordinates.first { it.dimension == "Customer" }
            (coord.selector as Selector.Pinned).member.deferred shouldBe true
        }

        "a qualified INT pair (year.2025) resolves offline with a deferred coordinate" {
            val outcome = resolveOffline("sales.year.2025.net")
            check(outcome is ResolutionOutcome.Resolved) { "expected Resolved, got $outcome" }
            val coord = outcome.path.coordinates.first { it.attribute == "Time.year" }
            (coord.selector as Selector.Pinned).member.deferred shouldBe true
        }

        "structural tokens still resolve offline (cubelet/measure need no snapshot)" {
            // A fully-qualified path with a calc token resolves with no catalog server at all.
            val outcome = resolveOffline("sales.customer.Kaufland")
            check(outcome is ResolutionOutcome.Resolved) { "expected Resolved, got $outcome" }
        }
    })
