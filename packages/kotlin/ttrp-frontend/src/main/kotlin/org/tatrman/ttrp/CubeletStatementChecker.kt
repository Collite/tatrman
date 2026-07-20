// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp

import org.tatrman.ttr.md.resolve.CanonicalRenderer
import org.tatrman.ttr.md.resolve.MdDiagId
import org.tatrman.ttr.md.resolve.PathContext
import org.tatrman.ttr.md.resolve.ResolutionOutcome
import org.tatrman.ttr.semantics.md.AllocationStrategy
import org.tatrman.ttrp.ast.CubeletLhs
import org.tatrman.ttrp.ast.CubeletOp
import org.tatrman.ttrp.ast.CubeletStmt
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.ExpressionTypechecker
import org.tatrman.ttrp.expr.MdContext
import org.tatrman.ttrp.expr.MdResolution
import org.tatrman.ttrp.expr.frontendMessage
import org.tatrman.ttrp.expr.toFrontendId
import org.tatrman.ttrp.expr.toResolverComponents

/**
 * Checks a cubelet-assignment statement (contracts §5, writeback) — the `mdPath = expr` / `mdPath +=
 * expr` slice form (R24). This is the S5-A front half: recognition, strict-LHS resolution (R19),
 * the LHS→RHS context overlay (R20), and grain reconciliation (R21). It emits diagnostics and records
 * the resolved LHS/RHS; the actual write **lowering** is S5-B.
 *
 * Out of S5-A scope (left untouched, wired in S5C): a bare-identifier LHS ([CubeletLhs.Name] — virtual
 * cubelets, R25) and the `:=` / `-=` operators (materialize / delete). Only a slice ([CubeletLhs.Path])
 * with `=` / `+=` is handled here.
 */
internal class CubeletStatementChecker(
    private val typechecker: ExpressionTypechecker,
) {
    fun check(
        stmt: CubeletStmt,
        md: MdContext?,
        variables: Set<String>,
        out: MutableList<TtrpDiagnostic>,
        mdOut: MutableList<MdResolution>,
    ) {
        val model = md?.model ?: return // MD resolution deferred (no model injected) — no-op, like reads
        val lhs = stmt.lhs
        if (lhs !is CubeletLhs.Path) return // bare-identifier target (virtual cubelet) is S5C
        if (stmt.op != CubeletOp.ASSIGN && stmt.op != CubeletOp.MERGE) return // `:=` / `-=` are S5C

        // R19: the LHS resolves STRICT — no context, no grain defaults, no hops. Incomplete ⇒ MD-009.
        val resolvedLhs =
            when (
                val outcome =
                    md.resolver.resolve(
                        lhs.path.components.toResolverComponents(),
                        model,
                        md.members,
                        md.asof,
                        context = null,
                        strict = true,
                    )
            ) {
                is ResolutionOutcome.Resolved -> {
                    mdOut +=
                        MdResolution(
                            location = lhs.location,
                            canonical = CanonicalRenderer.render(outcome.path),
                            path = outcome.path,
                            shape = outcome.shape,
                            explanation = outcome.explanation,
                        )
                    outcome
                }

                is ResolutionOutcome.Ambiguous -> {
                    val alts = outcome.alternatives.joinToString("  |  ") { CanonicalRenderer.render(it.path) }
                    out += err(TtrpDiagnosticId.MD_003, "${MdDiagId.AMBIGUOUS.text}: $alts", lhs.location)
                    return
                }

                is ResolutionOutcome.Failed -> {
                    for (d in outcome.diagnostics) out += err(d.id.toFrontendId(), d.frontendMessage(), lhs.location)
                    return
                }
            }

        // R20: the resolved LHS becomes the RHS context overlay (inherited coordinates/measure/agg).
        val rhs =
            typechecker.check(
                stmt.rhs,
                inputSchema = null,
                aggregatesAllowed = true,
                variableNames = variables,
                md = md,
                mdOverlay = PathContext(resolvedLhs.path),
            )
        out += rhs.diagnostics
        mdOut += rhs.mdResolutions

        // R21: reconcile the LHS grain against the RHS shape at the assignment boundary.
        reconcile(resolvedLhs.path.cubelet, resolvedLhs.shape.freeDims, rhs.shape.freeDims, lhs, md, out)
    }

    /**
     * R21 grain reconciliation. RHS-only free dims collapse via the default agg (legal — the write
     * aggregates them, lowered in S5-B); LHS ∩ RHS align (R16); **LHS-only** free dims are a spread —
     * legal only if the target's binding declares an allocation strategy for that dimension (R21, v0.10),
     * else `TTRP-MD-010`. A declared **equal** strategy additionally needs an enumerable finer member set
     * (the domain restrict-set, or a connected catalog); when neither is available the members are
     * deferred/unknown → `TTRP-MD-011` (R22/D13). An unrecognised strategy is treated as no strategy.
     */
    private fun reconcile(
        cubelet: String,
        lhsFree: List<String>,
        rhsFree: List<String>,
        lhs: CubeletLhs,
        md: MdContext,
        out: MutableList<TtrpDiagnostic>,
    ) {
        val binding = md.bindings?.cubelets?.get(cubelet.substringAfterLast('.'))
        val spreadAttrs = (lhsFree.toSet() - rhsFree.toSet()).distinct().sortedBy { it.substringBefore('.') }
        for (attr in spreadAttrs) {
            val dim = attr.substringBefore('.')
            when (val strategy = binding?.allocationFor(dim)) {
                null, is AllocationStrategy.Unknown ->
                    out +=
                        err(
                            TtrpDiagnosticId.MD_010,
                            "${MdDiagId.SPREAD_WITHOUT_STRATEGY.text}: dimension `$dim` is free on the LHS but not " +
                                "the RHS, and the binding declares no allocation strategy for it (R21)",
                            lhs.location,
                        )
                AllocationStrategy.Equal ->
                    if (!equalMembersEnumerable(attr, md)) {
                        out +=
                            err(
                                TtrpDiagnosticId.MD_011,
                                "${MdDiagId.UNKNOWN_MEMBER.text}: an equal spread over `$dim` needs an enumerable " +
                                    "member set, but the domain declares no restrict members and no connected " +
                                    "member catalog is available (R22/D13)",
                                lhs.location,
                            )
                    }
                AllocationStrategy.Proportional -> Unit // legal; proportional distributes over existing rows
            }
        }
    }

    /** Whether the finer members of a spread [attribute] can be enumerated: a restrict-set, or a live catalog. */
    private fun equalMembersEnumerable(
        attribute: String,
        md: MdContext,
    ): Boolean {
        val model = md.model ?: return false
        val domain = model.underlyingDomain(attribute)
        val hasRestrict = domain?.let { model.domains[it]?.members }?.isNotEmpty() == true
        return hasRestrict || md.members != null // connected: the catalog can enumerate (MemberSource)
    }

    private fun err(
        id: TtrpDiagnosticId,
        message: String,
        location: org.tatrman.ttrp.ast.SourceLocation,
    ) = TtrpDiagnostic(id = id, severity = Severity.ERROR, message = message, location = location)
}
