// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import org.tatrman.ttr.semantics.md.MdCubelet
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
        // R19 strict LHS (§5, writeback): resolve with NO context, NO grain-dimension defaults, and NO
        // derivable hops — every grain dimension must be pinned/restricted/explicitly `dim.*` and the
        // measure an explicit token, else `TTRP-MD-009`. Default false = the read behaviour (S2–S4).
        strict: Boolean = false,
        // R25 session namespace (§5, cubelet statements): in-scope virtual cubelets bound by `C = e`.
        // A virtual cubelet is structurally an [MdCubelet] (name + grain + measures, no binding), so it
        // resolves exactly like a model cubelet; a session name shadows a model cubelet of the same name
        // (the frontend raises `TTRP-MD-022`). Default empty = the read/model-only behaviour (S2–S5-B).
        sessionCubelets: Map<String, MdCubelet> = emptyMap(),
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
