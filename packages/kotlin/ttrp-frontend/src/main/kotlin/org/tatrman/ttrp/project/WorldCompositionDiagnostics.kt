// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.project

import org.tatrman.ttr.metadata.world.CompositionResult
import org.tatrman.ttr.metadata.world.Contradiction
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * The frontend id layer for K world composition (PL-P1.S4). `ttr-metadata`'s [CompositionResult] is
 * id-free (MD5); a composition [Contradiction] — the project world contradicting a platform-governed
 * fact (contracts §3/§16 K) — surfaces here as **`TTRP-LCK-004`** (closing the PL-P1.S2.T6 marker).
 */
object WorldCompositionDiagnostics {
    fun lck004(c: Contradiction): TtrpDiagnostic =
        TtrpDiagnostic(
            id = TtrpDiagnosticId.LCK_004,
            severity = Severity.ERROR,
            message =
                "world `${c.member.name}` contradicts the platform world's `${c.field}` " +
                    "(platform: ${c.platformValue ?: "—"}, project: ${c.projectValue ?: "—"}) — reconcile the worlds",
            location = SourceLocation("ttr.lock", 1, 0, 1, 1, -1, -1),
            suggestedAlternative = TtrpDiagnosticId.LCK_004.suggestedAlternative,
        )

    /** Every contradiction in a failed composition, as `TTRP-LCK-004` diagnostics. */
    fun lck004(result: CompositionResult.Contradiction): List<TtrpDiagnostic> = result.conflicts.map { lck004(it) }
}
