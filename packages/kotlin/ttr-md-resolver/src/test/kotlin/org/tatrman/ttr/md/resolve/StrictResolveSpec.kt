// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures
import java.time.Instant

/**
 * S5-A — R19 strict LHS at the resolver level: no context, no grain-dimension defaults, no derivable
 * hops. Over the sales fixture; `plan`'s grain is Customer.name × Time.month, measures net. A strict
 * LHS must pin/`dim.*` every grain dimension and name the measure, else MD-009. The sharpest case is
 * the read/write asymmetry: a path that resolves fine as a READ (defaults fill) is illegal as a strict
 * LHS.
 */
class StrictResolveSpec :
    StringSpec({
        val model = MdFixtures.salesModel()
        val asof = Instant.EPOCH
        val resolver = DefaultMdPathResolver()

        fun strict(input: String) = resolver.resolve(PathText.parse(input), model, null, asof, strict = true)

        fun read(input: String) = resolver.resolve(PathText.parse(input), model, null, asof)

        fun codes(o: ResolutionOutcome) = (o as ResolutionOutcome.Failed).diagnostics.map { it.code }

        "a complete strict LHS (every grain dim + measure, qualified) resolves" {
            strict("plan.name.Kaufland.month.3.net").shouldBeInstanceOf<ResolutionOutcome.Resolved>()
        }

        "an explicit dim.* satisfies a grain dimension on a strict LHS" {
            strict("plan.name.Kaufland.month.*.net").shouldBeInstanceOf<ResolutionOutcome.Resolved>()
        }

        "a missing grain dimension is MD-009 (no default-fill)" {
            val o = strict("plan.name.Kaufland.net") // month omitted
            o.shouldBeInstanceOf<ResolutionOutcome.Failed>()
            codes(o) shouldContain "TTRP-MD-009"
        }

        "a missing measure is MD-009" {
            val o = strict("plan.name.Kaufland.month.3") // no measure
            o.shouldBeInstanceOf<ResolutionOutcome.Failed>()
            codes(o) shouldContain "TTRP-MD-009"
        }

        "the read/write asymmetry: the same path resolves as a read but not as a strict LHS" {
            read("plan.name.Kaufland.net").shouldBeInstanceOf<ResolutionOutcome.Resolved>() // read fills month=*
            strict("plan.name.Kaufland.net").shouldBeInstanceOf<ResolutionOutcome.Failed>() // strict rejects
        }

        "a strict LHS is order-free — permutations resolve to one canonical path" {
            val a = strict("plan.name.Kaufland.month.3.net")
            val b = strict("net.month.3.plan.name.Kaufland")
            a.shouldBeInstanceOf<ResolutionOutcome.Resolved>()
            b.shouldBeInstanceOf<ResolutionOutcome.Resolved>()
            CanonicalRenderer.render((a as ResolutionOutcome.Resolved).path) shouldBe
                CanonicalRenderer.render((b as ResolutionOutcome.Resolved).path)
        }
    })
