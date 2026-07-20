// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.semantics.md.MdCubelet
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures
import java.time.Instant

/**
 * S5C-A (R25) — the resolver's **session namespace**: a virtual cubelet bound by `C = e` is passed in
 * as an [MdCubelet] via `sessionCubelets` and resolves exactly like a model cubelet. Over the sales
 * fixture (`plan` grain = Customer.name × Time.month). A virtual `V` with grain `Time.month` + measure
 * `net` (the shape of `plan.name.Kaufland.month.*`) admits a dot-path `V.month.6.net` that pins the
 * free dim and reads the measure — proving the session cubelet participates in resolution.
 */
class SessionCubeletSpec :
    StringSpec({
        val model = MdFixtures.salesModel()
        val asof = Instant.EPOCH
        val resolver = DefaultMdPathResolver()

        // A virtual cubelet is structurally an MdCubelet with NO binding: grain = the RHS free dims,
        // measures = the RHS measure. This is exactly what `V = plan.name.Kaufland.month.*` yields.
        val virtualV = MdCubelet(name = "V", grain = listOf("Time.month"), measures = listOf("net"))

        fun resolve(
            input: String,
            session: Map<String, MdCubelet> = emptyMap(),
        ) = resolver.resolve(PathText.parse(input), model, null, asof, sessionCubelets = session)

        "a dot-path over a session cubelet resolves against its virtual grain" {
            val o = resolve("V.month.6.net", session = mapOf("V" to virtualV))
            o.shouldBeInstanceOf<ResolutionOutcome.Resolved>()
            val canonical = CanonicalRenderer.render((o as ResolutionOutcome.Resolved).path)
            canonical.contains("V") shouldBe true
            canonical.contains("6") shouldBe true
        }

        "without the session binding, the same head token does not resolve" {
            // Disconnected (members=null), the unknown head `V` is an un-catalogued bare token → MD-007.
            val o = resolve("V.month.6.net")
            o.shouldBeInstanceOf<ResolutionOutcome.Failed>()
            (o as ResolutionOutcome.Failed).diagnostics.map { it.code } shouldBe listOf("TTRP-MD-007")
        }

        "a fully free session dot-path keeps its free dim (vector shape)" {
            val o = resolve("V.month.*.net", session = mapOf("V" to virtualV))
            o.shouldBeInstanceOf<ResolutionOutcome.Resolved>()
            (o as ResolutionOutcome.Resolved).shape.freeDims shouldBe listOf("Time.month")
        }
    })
