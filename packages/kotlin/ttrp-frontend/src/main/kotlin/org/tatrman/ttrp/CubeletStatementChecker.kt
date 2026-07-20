// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp

import org.tatrman.ttr.md.resolve.CanonicalRenderer
import org.tatrman.ttr.md.resolve.MdDiagId
import org.tatrman.ttr.md.resolve.PathContext
import org.tatrman.ttr.md.resolve.ResolutionOutcome
import org.tatrman.ttr.semantics.md.AllocationStrategy
import org.tatrman.ttr.semantics.md.MdCubelet
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttrp.ast.CubeletLhs
import org.tatrman.ttrp.ast.CubeletOp
import org.tatrman.ttrp.ast.CubeletStmt
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.ExpressionTypechecker
import org.tatrman.ttrp.expr.MdContext
import org.tatrman.ttrp.expr.MdResolution
import org.tatrman.ttrp.expr.TypedResult
import org.tatrman.ttrp.expr.frontendMessage
import org.tatrman.ttrp.expr.toFrontendId
import org.tatrman.ttrp.expr.toResolverComponents

/**
 * Checks a cubelet-assignment statement (contracts §5/§11, writeback). Dispatches on the LHS form and
 * operator (R24):
 *
 * - **Slice LHS** ([CubeletLhs.Path], a path with coordinates/measure) admits only `=` / `+=` and
 *   follows §5 (R19 strict LHS, R20 context overlay, R21 grain reconciliation); `:=` / `-=` on a slice
 *   is `TTRP-MD-020`. This is the S5-A/S5-B write path (unchanged).
 * - **Bare-identifier LHS** ([CubeletLhs.Name]) admits all four operators against a target that is a
 *   model cubelet, an in-scope **virtual** cubelet (session variable, R25), or — for `=` / `:=` only —
 *   a fresh name. `=` binds a virtual cubelet (Q7-γ SSA); `:=` materializes (R26/R27, lowered in
 *   S5C-B); `+=` / `-=` merge / delete against an existing target (R28/R29). `+=` / `-=` on a fresh
 *   name is `TTRP-MD-021`.
 *
 * The **session namespace** ([sessionCubelets]) is a mutable map the caller threads in statement order:
 * a virtual cubelet becomes visible to later statements the moment its `=` binding is checked (SSA).
 * This phase (S5C-A) is compile-side: dispatch, virtual-cubelet resolution, and merge/delete/materialize
 * **checking** with diagnostics. The write **lowering** (materialize DDL, generated `.ttrm`, merge/delete
 * plans, §8) is S5C-B.
 */
internal class CubeletStatementChecker(
    private val typechecker: ExpressionTypechecker,
) {
    fun check(
        stmt: CubeletStmt,
        md: MdContext?,
        variables: Set<String>,
        sessionCubelets: MutableMap<String, MdCubelet>,
        out: MutableList<TtrpDiagnostic>,
        mdOut: MutableList<MdResolution>,
    ) {
        val model = md?.model ?: return // MD resolution deferred (no model injected) — no-op, like reads
        when (val lhs = stmt.lhs) {
            is CubeletLhs.Path -> checkSlice(stmt, lhs, md, variables, sessionCubelets, out, mdOut)
            is CubeletLhs.Name -> checkNamed(stmt, lhs, md, model, variables, sessionCubelets, out, mdOut)
        }
    }

    // ---- slice LHS (§5) --------------------------------------------------------------------------

    private fun checkSlice(
        stmt: CubeletStmt,
        lhs: CubeletLhs.Path,
        md: MdContext,
        variables: Set<String>,
        sessionCubelets: Map<String, MdCubelet>,
        out: MutableList<TtrpDiagnostic>,
        mdOut: MutableList<MdResolution>,
    ) {
        // R24: `:=` / `-=` need a bare-identifier target — they are illegal on a slice path.
        if (stmt.op == CubeletOp.MATERIALIZE || stmt.op == CubeletOp.DELETE) {
            out +=
                err(
                    TtrpDiagnosticId.MD_020,
                    "`${opText(stmt.op)}` needs a bare-identifier target, not a slice path (R24)",
                    lhs.location,
                )
            return
        }

        // R19: the LHS resolves STRICT — no context, no grain defaults, no hops. Incomplete ⇒ MD-009.
        val resolvedLhs =
            when (
                val outcome =
                    md.resolver.resolve(
                        lhs.path.components.toResolverComponents(),
                        md.model!!,
                        md.members,
                        md.asof,
                        context = null,
                        strict = true,
                        sessionCubelets = sessionCubelets,
                    )
            ) {
                is ResolutionOutcome.Resolved -> {
                    mdOut += resolutionOf(lhs.location, outcome)
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
        val rhs = checkRhs(stmt, md, variables, sessionCubelets, overlay = PathContext(resolvedLhs.path))
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

    // ---- bare-identifier LHS (R24 dispatch → R25/R26/R27/R28/R29) --------------------------------

    private fun checkNamed(
        stmt: CubeletStmt,
        lhs: CubeletLhs.Name,
        md: MdContext,
        model: MdModel,
        variables: Set<String>,
        sessionCubelets: MutableMap<String, MdCubelet>,
        out: MutableList<TtrpDiagnostic>,
        mdOut: MutableList<MdResolution>,
    ) {
        val name = lhs.name
        val isModel = model.cubelets.containsKey(name)
        val isSession = sessionCubelets.containsKey(name)
        val fresh = !isModel && !isSession
        when (stmt.op) {
            CubeletOp.ASSIGN -> bindVirtual(name, stmt, md, isModel, variables, sessionCubelets, out, mdOut)
            CubeletOp.MATERIALIZE ->
                checkMaterialize(name, stmt, md, model, isModel, variables, sessionCubelets, out, mdOut)
            CubeletOp.MERGE, CubeletOp.DELETE ->
                checkMergeDelete(
                    name,
                    stmt,
                    lhs,
                    md,
                    model,
                    isModel,
                    isSession,
                    fresh,
                    variables,
                    sessionCubelets,
                    out,
                    mdOut,
                )
        }
    }

    /**
     * R25 — `C = e` binds a **virtual cubelet**: a session variable whose shape is `PathShape × measure`.
     * Resolves the RHS, records `C ↦ MdCubelet(grain = RHS free dims, measures = [RHS measure])` into the
     * session map (SSA: visible to later statements, rebinding overwrites). A name that shadows a model
     * cubelet gets `TTRP-MD-022` (warning, mirrors R23's column-shadow philosophy).
     */
    private fun bindVirtual(
        name: String,
        stmt: CubeletStmt,
        md: MdContext,
        shadowsModel: Boolean,
        variables: Set<String>,
        sessionCubelets: MutableMap<String, MdCubelet>,
        out: MutableList<TtrpDiagnostic>,
        mdOut: MutableList<MdResolution>,
    ) {
        if (shadowsModel) {
            out +=
                warn(
                    TtrpDiagnosticId.MD_022,
                    "the script variable `$name` shadows a model cubelet (R25)",
                    stmt.lhs.location,
                )
        }
        val rhs = checkRhs(stmt, md, variables, sessionCubelets)
        out += rhs.diagnostics
        mdOut += rhs.mdResolutions
        val root = rootResolution(stmt, rhs) ?: return // RHS didn't resolve — diagnostics already emitted
        sessionCubelets[name] =
            MdCubelet(name = name, grain = root.shape.freeDims, measures = listOf(root.path.measure))
    }

    /**
     * R26/R27 — `C := e` materialize. On a **model cubelet** with a binding (R26): the `with` keys must
     * match the binding, and the RHS grain/measures must equal C's declared grain/measures (no silent
     * reshape) → `TTRP-MD-023`. On a **fresh / virtual** target (R27): `with { shape: … }` is required
     * (missing/unknown keys → `TTRP-MD-015`); the logical definition is inferred from the RHS shape. The
     * generated `.ttrm` + DDL/Store lowering is S5C-B; this phase validates and records the RHS resolution.
     */
    private fun checkMaterialize(
        name: String,
        stmt: CubeletStmt,
        md: MdContext,
        model: MdModel,
        isModel: Boolean,
        variables: Set<String>,
        sessionCubelets: MutableMap<String, MdCubelet>,
        out: MutableList<TtrpDiagnostic>,
        mdOut: MutableList<MdResolution>,
    ) {
        val rhs = checkRhs(stmt, md, variables, sessionCubelets)
        out += rhs.diagnostics
        mdOut += rhs.mdResolutions
        val root = rootResolution(stmt, rhs)
        if (isModel) {
            // R26: existing target — `with` keys must match the binding; RHS shape must equal C's.
            validateWithKeys(stmt, out, requireShape = false)
            val target = model.cubelets.getValue(name)
            if (root != null && !shapesEqual(target, root)) {
                out +=
                    err(
                        TtrpDiagnosticId.MD_023,
                        "the RHS grain/measures must equal `$name`'s declared " +
                            "grain/measures — no silent reshape on materialize (R26)",
                        stmt.lhs.location,
                    )
            }
        } else {
            // R27: fresh/virtual target — `with { shape }` is required to define the new cubelet.
            validateWithKeys(stmt, out, requireShape = true)
            // The inferred definition (grain = RHS free dims, measure = RHS measure) is emitted as a
            // generated `.ttrm` in S5C-B; recording the RHS resolution is enough here.
        }
    }

    /**
     * R28/R29 — `C += e` (merge) / `C -= e` (delete) against an existing target. A fresh name has nothing
     * to merge into / delete from → `TTRP-MD-021`. The RHS must cover the target's grain; an RHS dimension
     * outside the target's grain → `TTRP-MD-023`. For `-=`: a measure/agg token in the RHS is ignored
     * (delete is keys-only) → `TTRP-MD-016` (warning); a `-=` on a **diff-journaled** model cubelet is
     * undefined → `TTRP-MD-017` (error).
     */
    private fun checkMergeDelete(
        name: String,
        stmt: CubeletStmt,
        lhs: CubeletLhs.Name,
        md: MdContext,
        model: MdModel,
        isModel: Boolean,
        isSession: Boolean,
        fresh: Boolean,
        variables: Set<String>,
        sessionCubelets: MutableMap<String, MdCubelet>,
        out: MutableList<TtrpDiagnostic>,
        mdOut: MutableList<MdResolution>,
    ) {
        if (fresh) {
            out +=
                err(
                    TtrpDiagnosticId.MD_021,
                    "`${opText(stmt.op)}` needs an existing target — `$name` does not " +
                        "exist; create it first with `=` / `:=` (R24)",
                    lhs.location,
                )
            return
        }
        val rhs = checkRhs(stmt, md, variables, sessionCubelets)
        out += rhs.diagnostics
        mdOut += rhs.mdResolutions
        val root = rootResolution(stmt, rhs)

        // Target grain: a model cubelet's declared grain, else the virtual's session grain.
        val targetGrain =
            if (isModel) model.cubelets.getValue(name).grainDims() else sessionCubelets.getValue(name).grainDims()

        if (root != null) {
            // R28/R29 grain coverage: every RHS dimension must belong to the target's grain.
            val rhsDims =
                root.path.coordinates
                    .map { it.dimension }
                    .toSet()
            val stray = rhsDims - targetGrain
            for (dim in stray.sorted()) {
                out +=
                    err(
                        TtrpDiagnosticId.MD_023,
                        "dimension `$dim` in the RHS is not part of `$name`'s grain (R28)",
                        stmt.rhs.location,
                    )
            }
        }

        if (stmt.op == CubeletOp.DELETE) {
            // R29: a delete is keys-only — a named measure in the RHS is ignored.
            root?.let {
                if (it.path.measure.isNotBlank()) {
                    out +=
                        warn(
                            TtrpDiagnosticId.MD_016,
                            "the measure `${it.path.measure}` is ignored — `-=` deletes " +
                                "by key (R29)",
                            stmt.rhs.location,
                        )
                }
            }
            // R29: `-=` is undefined on a diff-journaled cubelet (deltas can't be deleted).
            if (isModel && isDiffJournaled(name, md)) {
                out +=
                    err(
                        TtrpDiagnosticId.MD_017,
                        "`-=` is not defined on the diff-journaled cubelet `$name` (R29)",
                        lhs.location,
                    )
            }
        }
    }

    // ---- shared helpers --------------------------------------------------------------------------

    /** Typecheck the statement RHS (aggregates allowed, session cubelets in scope, optional LHS overlay). */
    private fun checkRhs(
        stmt: CubeletStmt,
        md: MdContext,
        variables: Set<String>,
        sessionCubelets: Map<String, MdCubelet>,
        overlay: PathContext? = null,
    ): TypedResult =
        typechecker.check(
            stmt.rhs,
            inputSchema = null,
            aggregatesAllowed = true,
            variableNames = variables,
            md = md,
            mdOverlay = overlay,
            sessionCubelets = sessionCubelets,
        )

    /** The RHS root MD resolution (the path at the RHS expression's own location), or null if unresolved. */
    private fun rootResolution(
        stmt: CubeletStmt,
        rhs: TypedResult,
    ): MdResolution? =
        rhs.mdResolutions.firstOrNull { it.location == stmt.rhs.location } ?: rhs.mdResolutions.lastOrNull()

    private fun resolutionOf(
        location: org.tatrman.ttrp.ast.SourceLocation,
        outcome: ResolutionOutcome.Resolved,
    ) = MdResolution(
        location = location,
        canonical = CanonicalRenderer.render(outcome.path),
        path = outcome.path,
        shape = outcome.shape,
        explanation = outcome.explanation,
    )

    /** Grain/measures equality for materialize (R26): same free-dim set and same measure set. */
    private fun shapesEqual(
        target: MdCubelet,
        rhs: MdResolution,
    ): Boolean {
        val sameDims =
            target.grainDims() ==
                rhs.path.coordinates
                    .filter { it.selector !is org.tatrman.ttr.md.resolve.Selector.Pinned }
                    .map { it.dimension }
                    .toSet()
        val sameMeasure = target.measures.toSet() == setOf(rhs.path.measure)
        return sameDims && sameMeasure
    }

    /** `with` key validation (R26/R27): `shape` ∈ {wide, long} (required for fresh), `table`/`journal` optional. */
    private fun validateWithKeys(
        stmt: CubeletStmt,
        out: MutableList<TtrpDiagnostic>,
        requireShape: Boolean,
    ) {
        val entries = stmt.withClause?.entries ?: emptyList()
        val keys = entries.associate { it.key to it.value }
        for (entry in entries) {
            if (entry.key !in WITH_KEYS) {
                out +=
                    err(
                        TtrpDiagnosticId.MD_015,
                        "unknown `with` key `${entry.key}` — expected one of $WITH_KEYS " +
                            "(R27)",
                        entry.location,
                    )
            }
        }
        keys["shape"]?.let {
            if (it !in SHAPES) {
                out +=
                    err(TtrpDiagnosticId.MD_015, "`shape` must be one of $SHAPES, got `$it` (R27)", stmt.lhs.location)
            }
        }
        if (requireShape && "shape" !in keys) {
            out +=
                err(
                    TtrpDiagnosticId.MD_015,
                    "materializing a new cubelet needs `with { shape: wide|long }` (R27)",
                    stmt.lhs.location,
                )
        }
    }

    private fun isDiffJournaled(
        name: String,
        md: MdContext,
    ): Boolean =
        md.bindings
            ?.cubelets
            ?.get(name)
            ?.journaling is org.tatrman.ttr.semantics.md.Journaling.Diff

    private fun opText(op: CubeletOp): String =
        when (op) {
            CubeletOp.ASSIGN -> "="
            CubeletOp.MATERIALIZE -> ":="
            CubeletOp.MERGE -> "+="
            CubeletOp.DELETE -> "-="
        }

    private fun err(
        id: TtrpDiagnosticId,
        message: String,
        location: org.tatrman.ttrp.ast.SourceLocation,
    ) = TtrpDiagnostic(id = id, severity = Severity.ERROR, message = message, location = location)

    private fun warn(
        id: TtrpDiagnosticId,
        message: String,
        location: org.tatrman.ttrp.ast.SourceLocation,
    ) = TtrpDiagnostic(id = id, severity = Severity.WARNING, message = message, location = location)

    private companion object {
        val WITH_KEYS = setOf("shape", "table", "journal")
        val SHAPES = setOf("wide", "long")
    }
}

/** The grain dimensions of a cubelet (its `Dimension.attribute` grain refs, reduced to dimension names). */
private fun MdCubelet.grainDims(): Set<String> = grain.map { it.substringBefore('.') }.toSet()
