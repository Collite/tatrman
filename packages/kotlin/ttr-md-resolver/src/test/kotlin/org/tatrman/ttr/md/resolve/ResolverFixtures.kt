// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import org.tatrman.ttr.md.resolve.fixtures.InMemoryMemberSnapshot
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures

/**
 * Shared resolver-spec fixtures: the S1-A1 `sales-model` plus a default connected member snapshot.
 * The snapshot seeds `Name` (the published string domain — Customer.name members) alongside the
 * numeric/temporal `Year`/`Month` domains so INT member candidacy (R5f) is exercised even though the
 * model's time domains are not `publish: members` (a fixture is free to expose more than the flag).
 */
object ResolverFixtures {
    val model: MdModel = MdFixtures.salesModel()

    val defaultMembers: Map<String, List<String>> =
        mapOf(
            "Name" to listOf("Kaufland", "Lidl", "Kaufland K123"),
            "Year" to listOf("2024", "2025", "2026"),
            "Month" to (1..12).map { it.toString() },
            "Region" to listOf("North", "South"),
        )

    /** The default connected snapshot; [overrides] replace whole domain lists by key. */
    fun snapshot(overrides: Map<String, List<String>> = emptyMap()): InMemoryMemberSnapshot =
        InMemoryMemberSnapshot(defaultMembers + overrides)
}
