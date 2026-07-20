// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.md

import org.tatrman.ttr.md.resolve.MemberSnapshot
import org.tatrman.ttr.md.resolve.fixtures.InMemoryMemberSnapshot
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures
import org.tatrman.ttrp.expr.MdContext
import java.time.Instant

/**
 * Shared fixtures for the MD dot-path checker specs (S3): the S1 `sales-model` [MdModel] plus a
 * connected member snapshot mirroring the resolver suite's `ResolverFixtures` (so the frontend
 * exercises the same members S2 proved). The snapshot seeds the published `Name` domain alongside
 * numeric `Year`/`Month` so INT member candidacy works. Member text is case-sensitive (R5f), so the
 * specs use the exact spelling `Kaufland`.
 */
object MdCheckerFixtures {
    val model: MdModel = MdFixtures.salesModel()
    val bindings: MdBindings = MdFixtures.salesBindings()

    /** The pinned `asof` used across the specs — the S2 calc golden's anchor (`lastMonth` → month 6). */
    val asof: Instant = Instant.parse("2026-07-08T00:00:00Z")

    fun snapshot(): MemberSnapshot =
        InMemoryMemberSnapshot(
            mapOf(
                "Name" to listOf("Kaufland", "Lidl", "Kaufland K123"),
                "Year" to listOf("2024", "2025", "2026"),
                "Month" to (1..12).map { it.toString() },
                "Region" to listOf("North", "South"),
            ),
        )

    /** A connected MD context (default) or disconnected (`members = null`, R13) at a pinned [asof]. */
    fun mdContext(
        members: MemberSnapshot? = snapshot(),
        asof: Instant = MdCheckerFixtures.asof,
    ): MdContext = MdContext(model = model, members = members, asof = asof, bindings = bindings)
}
