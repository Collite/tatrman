// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import org.tatrman.ttr.semantics.md.MdModel
import java.time.Instant

/**
 * Resolve an order-free `mdPath` (a component list) against a model into a canonical form or a
 * structured ambiguity/failure (§3). Pure and deterministic (P2: no scoring, no guessing).
 *
 * The concrete implementation — classification (R5, [TokenClassifier]) + pair/selector binding
 * (R6/R7, [PairBinder]) + constraint search (R8), ambiguity (R9), defaults (R10), and canonical
 * rendering (§3) — is assembled in S2-B/S2-C. `PathContext` overlay resolution (R20) is the
 * statement-writeback seam (§5); its wiring is S5.
 */
interface MdPathResolver {
    fun resolve(
        components: List<PathComponent>,
        model: MdModel,
        members: MemberSnapshot?,
        asof: Instant,
        context: PathContext? = null,
    ): ResolutionOutcome
}

/**
 * An assignment-context overlay for RHS resolution (R20, §5): the resolved LHS path. RHS tokens win
 * per slot; unmentioned dimensions/cubelet/measure/agg inherit from here; a RHS `dim.*` un-pins an
 * inherited coordinate (D-"* escape"). Statement wiring is S5; S2-C ships it as a library function.
 */
data class PathContext(
    val path: CanonicalPath,
)
