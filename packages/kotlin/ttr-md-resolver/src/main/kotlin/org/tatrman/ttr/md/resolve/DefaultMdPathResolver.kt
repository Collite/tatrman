// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import org.tatrman.ttr.semantics.md.AggKind
import org.tatrman.ttr.semantics.md.GrainLattice
import org.tatrman.ttr.semantics.md.MdCubelet
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttr.semantics.md.aggKindOf
import org.tatrman.ttr.semantics.md.defaultMeasure
import java.time.Instant

/**
 * The reference resolver (R8–R11 + §3). Exhaustive within a search bound, no scoring, no guessing
 * (P2): classify → bind pairs/selectors → enumerate every consistent slot assignment → fill defaults
 * → render canonical. Zero solutions ⇒ `TTRP-MD-002`; more than one distinct canonical ⇒
 * `TTRP-MD-003`; the pathological bound ⇒ `TTRP-MD-014`.
 *
 * Deferred to S2-C: calc-token coordinates + `asof` (R12), disconnected `dim.member` deferral (R13),
 * `PathContext` overlay (R20). This class already threads [asof] and null [members] through so those
 * slot in without an API change.
 */
class DefaultMdPathResolver(
    private val searchBound: Int = DEFAULT_SEARCH_BOUND,
) : MdPathResolver {
    override fun resolve(
        components: List<PathComponent>,
        model: MdModel,
        members: MemberSnapshot?,
        asof: Instant,
        context: PathContext?,
        strict: Boolean,
        sessionCubelets: Map<String, MdCubelet>,
    ): ResolutionOutcome {
        // R19: a strict LHS ignores any context overlay — it is resolved on its own tokens alone.
        val ctx = if (strict) null else context
        // R25: fold in-scope virtual cubelets into the model so classification/binding/resolution treat
        // them exactly like model cubelets (a session name shadows a model cubelet — map union, RHS wins).
        val model = if (sessionCubelets.isEmpty()) model else model.copy(cubelets = model.cubelets + sessionCubelets)
        val lattice = GrainLattice.of(model)
        val bind = PairBinder.bind(components, model, members)
        if (bind.diagnostics.isNotEmpty()) return ResolutionOutcome.Failed(bind.diagnostics)

        // R12: evaluation-relative calc tokens (`lastMonth`, …) lower to asof-anchored coordinates.
        val (calcCoords, free) = CalcResolver.extract(bind.free, model, asof)
        val preCoords = bind.coordinates + calcCoords

        val perComponent = free.map { it to TokenClassifier.classify(it, model, members).toList() }
        val unknown = perComponent.filter { it.second.isEmpty() }
        if (unknown.isNotEmpty()) {
            // R13: offline, a bare member/INT token is illegal (MD-007); connected, it is unknown (MD-001).
            val id = if (members == null) MdDiagId.BARE_MEMBER_DISCONNECTED else MdDiagId.UNKNOWN_COMPONENT
            return ResolutionOutcome.Failed(
                unknown.map { (c, _) -> MdDiagnostic(id, reasonFor(id, c), c.sourceText()) },
            )
        }

        val assignments = mutableListOf<Assignment>()
        val explored = intArrayOf(0)
        val overflowed = !enumerate(perComponent.map { it.second }, 0, Assignment(), assignments, explored)
        if (overflowed) {
            return ResolutionOutcome.Failed(
                listOf(MdDiagnostic(MdDiagId.SEARCH_BOUND_EXCEEDED, "path search exceeded $searchBound states")),
            )
        }

        val solutions = LinkedHashMap<String, ResolutionOutcome.Resolved>()
        val reasons = mutableListOf<MdDiagnostic>()
        for (a in assignments) {
            resolveAssignment(a, preCoords, bind.dimStars, model, lattice, ctx, strict).fold(
                onSolutions = { for (s in it) solutions[CanonicalRenderer.render(s.path)] = s },
                onReason = { reasons += it },
            )
        }

        return when (solutions.size) {
            0 ->
                ResolutionOutcome.Failed(
                    if (reasons.isNotEmpty()) reasons.distinct() else listOf(unresolvable(components)),
                )
            1 -> solutions.values.first()
            else -> ResolutionOutcome.Ambiguous(solutions.entries.sortedBy { it.key }.map { it.value })
        }
    }

    private fun reasonFor(
        id: MdDiagId,
        c: PathComponent,
    ): String =
        if (id == MdDiagId.BARE_MEMBER_DISCONNECTED) {
            "bare member `${c.sourceText()}` in disconnected mode — qualify as `dim.member`"
        } else {
            "no candidate slot for `${c.sourceText()}`"
        }

    // ---- assignment enumeration --------------------------------------------------------------

    /** One choice of slot for each free component. Coordinates accumulate from member choices. */
    private data class Assignment(
        val cubelet: String? = null,
        val measure: String? = null,
        val agg: AggKind? = null,
        val coords: List<Coordinate> = emptyList(),
        val twoMeasures: Boolean = false,
        val explains: List<ExplainStep> = emptyList(),
    ) {
        fun withAgg(k: AggKind) = copy(agg = k, explains = explains + ExplainStep(k.name.lowercase(), "agg", "token"))

        fun withCubelet(n: String) = copy(cubelet = n, explains = explains + ExplainStep(n, "cubelet", "token"))

        fun withMeasure(n: String) = copy(measure = n, explains = explains + ExplainStep(n, "measure", "token"))

        fun withMember(c: SlotCandidate.Member) =
            copy(
                coords = coords + Coordinate(c.dimension, c.qname, Selector.Pinned(MemberRef(c.text))),
                explains = explains + ExplainStep(c.text, "${c.dimension}.${c.attribute}", "token"),
            )
    }

    /** Returns false if the search bound was hit (caller maps to MD-014); true otherwise. */
    private fun enumerate(
        candidates: List<List<SlotCandidate>>,
        index: Int,
        acc: Assignment,
        out: MutableList<Assignment>,
        explored: IntArray,
    ): Boolean {
        if (++explored[0] > searchBound) return false
        if (index == candidates.size) {
            out += acc
            return true
        }
        for (cand in candidates[index]) {
            val next = apply(acc, cand) ?: continue
            if (!enumerate(candidates, index + 1, next, out, explored)) return false
        }
        return true
    }

    /** Fold one candidate into the assignment, or null if it conflicts (e.g. a second cubelet/agg). */
    private fun apply(
        a: Assignment,
        cand: SlotCandidate,
    ): Assignment? =
        when (cand) {
            is SlotCandidate.Agg -> if (a.agg != null) null else a.withAgg(cand.kind)
            is SlotCandidate.Cubelet -> if (a.cubelet != null) null else a.withCubelet(cand.name)
            is SlotCandidate.Measure -> if (a.measure != null) a.copy(twoMeasures = true) else a.withMeasure(cand.name)
            is SlotCandidate.Member -> a.withMember(cand)
            // A bare attribute or leftover calc token carries no selector — it is not a complete
            // coordinate on its own (R14: one step per component). Reject it (T-S2) so the assignment
            // FAILS rather than silently dropping the token and resolving at a wrong, unmentioned grain
            // (e.g. `sales.month.net` must not resolve at day grain with `month` discarded). Relative
            // calc tokens (`lastMonth`, …) are extracted upstream by CalcResolver, so a Calc reaching
            // here is an incomplete catalog-function token.
            is SlotCandidate.Attribute -> null
            is SlotCandidate.Calc -> null
        }

    // ---- per-assignment cubelet resolution + defaults ----------------------------------------

    private class AssignmentResult(
        val solutions: List<ResolutionOutcome.Resolved>,
        val reason: MdDiagnostic?,
    ) {
        inline fun fold(
            onSolutions: (List<ResolutionOutcome.Resolved>) -> Unit,
            onReason: (MdDiagnostic) -> Unit,
        ) {
            if (solutions.isNotEmpty()) onSolutions(solutions) else reason?.let(onReason)
        }
    }

    private fun resolveAssignment(
        a: Assignment,
        preCoords: List<Coordinate>,
        dimStars: List<String>,
        model: MdModel,
        lattice: GrainLattice,
        context: PathContext?,
        strict: Boolean,
    ): AssignmentResult {
        if (a.twoMeasures) {
            return AssignmentResult(
                emptyList(),
                MdDiagnostic(MdDiagId.MULTIPLE_MEASURES, "a path may carry at most one measure"),
            )
        }
        val fixedCoords = preCoords + a.coords
        repetitionDiagnostic(fixedCoords)?.let { return AssignmentResult(emptyList(), it) }

        val cubeletNames =
            when {
                a.cubelet != null -> listOf(a.cubelet)
                context != null -> listOf(context.path.cubelet) // R20: inherit the LHS cubelet
                else -> model.cubelets.keys.toList()
            }
        val solutions = mutableListOf<ResolutionOutcome.Resolved>()
        var reason: MdDiagnostic? = null
        for (name in cubeletNames) {
            val cubelet = model.cubelets[name] ?: continue
            if (a.measure != null && a.measure !in cubelet.measures) continue
            val grainDims = cubelet.grain.associate { it.substringBeforeLast('.') to it }
            if (dimStars.any { it !in grainDims.keys }) continue
            val effective = effectiveCoords(fixedCoords, dimStars, grainDims, context)
            if (!effective.all { coordValid(it, grainDims, model, lattice, strict) }) continue
            // R19: a strict LHS must mention (pin/restrict/`dim.*`) every grain dimension and the
            // measure — no default-filling. Incomplete ⇒ MD-009 listing exactly what is missing.
            if (strict) {
                val incomplete = incompleteStrictLhs(a, fixedCoords, dimStars, grainDims)
                if (incomplete != null) {
                    reason = incomplete
                    continue
                }
            }
            // R10/R10a (T-S1): resolve the measure, then its aggregation. A nonAdditive measure has
            // NO default aggregation — an agg-less path against one must never be blind-summed; that
            // is a diagnostic, not a silent `sum`.
            val measure =
                a.measure ?: context?.path?.measure?.takeIf { it.isNotEmpty() } ?: (cubelet.defaultMeasure ?: "")
            val agg = a.agg ?: context?.path?.agg ?: defaultAggFor(measure, model)
            if (agg == null) {
                reason =
                    MdDiagnostic(
                        MdDiagId.UNRESOLVABLE,
                        "measure `$measure` is non-additive and has no default aggregation — " +
                            "give one explicitly (e.g. `.$measure.max`)",
                        measure,
                    )
                continue
            }
            solutions +=
                buildResolved(
                    name,
                    cubelet.grain,
                    grainDims,
                    fixedCoords,
                    dimStars,
                    a,
                    model,
                    context,
                    measure,
                    agg,
                    strict,
                )
        }
        return AssignmentResult(solutions, reason)
    }

    /**
     * R10/R10a: a measure's default aggregation, or null when there is none and the path must carry
     * one explicitly. An additive measure defaults to its declared `aggregation` (else SUM); a
     * `nonAdditive` measure has NO default and returns null unless it declares one — the caller turns
     * that null into a diagnostic instead of a silent SUM. An unrecognised declared token also → null.
     */
    private fun defaultAggFor(
        measure: String,
        model: MdModel,
    ): AggKind? {
        val m = model.measures[measure] ?: return AggKind.SUM // unknown measure: tolerate (shouldn't reach here)
        m.aggregation?.default?.let { return aggKindOf(it) } // declared token wins; an unknown one ⇒ null
        return if (m.measureClass?.equals("nonAdditive", ignoreCase = true) == true) null else AggKind.SUM
    }

    /**
     * R19 completeness: the grain dimensions a strict LHS left unmentioned (not pinned/restricted and
     * not an explicit `dim.*`), plus the measure if omitted. Returns an MD-009 naming them, or null
     * when the LHS is complete.
     */
    private fun incompleteStrictLhs(
        a: Assignment,
        fixedCoords: List<Coordinate>,
        dimStars: List<String>,
        grainDims: Map<String, String>,
    ): MdDiagnostic? {
        val touched = (fixedCoords.map { it.dimension } + dimStars).toSet()
        val missingDims = grainDims.keys.filter { it !in touched }.sorted()
        val missingMeasure = a.measure == null
        if (missingDims.isEmpty() && !missingMeasure) return null
        val parts =
            buildList {
                if (missingDims.isNotEmpty()) add("grain ${missingDims.joinToString(", ")}")
                if (missingMeasure) add("the measure")
            }
        return MdDiagnostic(MdDiagId.INCOMPLETE_STRICT_LHS, "strict LHS is missing ${parts.joinToString(" and ")}")
    }

    /** RHS coordinates + dim.* stars, then (R20) inherited context coordinates on untouched grain dims. */
    private fun effectiveCoords(
        fixedCoords: List<Coordinate>,
        dimStars: List<String>,
        grainDims: Map<String, String>,
        context: PathContext?,
    ): List<Coordinate> {
        val out = fixedCoords.toMutableList()
        for (dim in dimStars) grainDims[dim]?.let { out += Coordinate(dim, it, Selector.Star) }
        val rhsTouched = out.map { it.dimension }.toSet()
        if (context != null) {
            for (cc in context.path.coordinates) {
                if (cc.dimension in grainDims.keys && cc.dimension !in rhsTouched) out += cc
            }
        }
        return out
    }

    /** A coordinate is legal for a cubelet iff its attribute is the grain attribute for its dimension, or a hop from it. */
    private fun coordValid(
        coord: Coordinate,
        grainDims: Map<String, String>,
        model: MdModel,
        lattice: GrainLattice,
        strict: Boolean,
    ): Boolean {
        val grainAttr = grainDims[coord.dimension] ?: return false
        val grainDomain = model.attributes[grainAttr]?.domainRef?.let { model.underlyingDomain(it) }
        val coordDomain = model.attributes[coord.attribute]?.domainRef?.let { model.underlyingDomain(it) }
        // R19 (T-S4): a strict LHS coordinate must sit on the grain attribute itself — no derivable
        // hops. The attribute-vs-grain domain equality is decidable OFFLINE (attribute domains are in
        // the model), so this check runs BEFORE the deferred-member bypass below — a disconnected
        // strict LHS still rejects a non-grain attribute; only member EXISTENCE is what defers.
        if (strict) return grainDomain != null && coordDomain == grainDomain
        // R13: a deferred (offline) coordinate can't have its member existence-checked — dimension-in-
        // grain suffices for the non-strict read path; the exact member resolves at bind time (S6).
        val sel = coord.selector
        if (sel is Selector.Pinned && sel.member.deferred) return true
        if (grainDomain == null || coordDomain == null) return false
        // R8: a coordinate is legal on the grain attribute itself, an N:1 coarsening of it, or a 1:1
        // co-leaf of it (T-S5 — `sales.code."…"` where grain is `Customer.name` and code↔name is 1:1).
        return coordDomain == grainDomain ||
            lattice.grainReachable(grainDomain, coordDomain) ||
            lattice.sameCoLeaf(grainDomain, coordDomain)
    }

    private fun buildResolved(
        cubelet: String,
        grain: List<String>,
        grainDims: Map<String, String>,
        fixedCoords: List<Coordinate>,
        dimStars: List<String>,
        a: Assignment,
        model: MdModel,
        context: PathContext?,
        measure: String,
        agg: AggKind,
        strict: Boolean,
    ): ResolutionOutcome.Resolved {
        val explains = a.explains.toMutableList()
        val coords = fixedCoords.toMutableList()

        // dim.* → a Star on that dimension's grain attribute (RHS un-pin, D-"* escape").
        for (dim in dimStars) {
            grainDims[dim]?.let { coords += Coordinate(dim, it, Selector.Star) }
        }
        // R20: inherit context coordinates for grain dimensions the RHS didn't touch.
        val rhsTouched = coords.map { it.dimension }.toSet()
        if (context != null) {
            for (cc in context.path.coordinates) {
                if (cc.dimension in grainDims.keys && cc.dimension !in rhsTouched) {
                    coords += cc
                    explains += ExplainStep(null, cc.attribute, "context")
                }
            }
        }
        // R10: unmentioned grain dimensions become free (explicit Star). A strict LHS (R19) never
        // reaches here with an unmentioned dimension — completeness was enforced upstream (MD-009) —
        // so the default-fill is suppressed to keep the "no defaults" invariant explicit.
        val touched = coords.map { it.dimension }.toSet()
        if (!strict) {
            for ((dim, grainAttr) in grainDims) {
                if (dim !in touched) {
                    coords += Coordinate(dim, grainAttr, Selector.Star)
                    explains += ExplainStep(null, "$grainAttr: *", "default")
                }
            }
        }
        // R10/R20 explain steps for the measure/agg the caller resolved (RHS, else context, else default).
        val via = if (context != null) "context" else "default"
        if (a.measure == null) explains += ExplainStep(null, "measure `$measure`", via)
        if (a.agg == null) explains += ExplainStep(null, "agg `${agg.name.lowercase()}`", via)

        // Sort by the cubelet's declared dimension order; break ties (same dimension, different
        // attributes — a legal drill, R11) deterministically by attribute qname.
        val ordered = coords.sortedWith(compareBy({ grainOrderIndex(it.dimension, grain) }, { it.attribute }))
        val path = CanonicalPath(cubelet, ordered, measure, agg)
        val shape = PathShape(ordered.filter { it.selector !is Selector.Pinned }.map { it.attribute })
        return ResolutionOutcome.Resolved(path, shape, Explanation(explains))
    }

    private fun grainOrderIndex(
        dimension: String,
        grain: List<String>,
    ): Int {
        val idx = grain.indexOfFirst { it.substringBeforeLast('.') == dimension }
        return if (idx >= 0) idx else grain.size
    }

    /**
     * R11 (T-S3): the same **attribute** constrained more than once is always a repetition ⇒ MD-006,
     * whatever the selector kinds — two pinned members, a pin plus a `dim.*`, two sets, a range plus a
     * pin, or even the same member twice. (A legal *drill* is the same DIMENSION with *different*
     * attributes — e.g. `Time.month` and `Time.year` — which groups into distinct buckets here and is
     * left untouched.)
     */
    private fun repetitionDiagnostic(coords: List<Coordinate>): MdDiagnostic? {
        for ((attr, group) in coords.groupBy { it.attribute }) {
            if (group.size > 1) {
                return MdDiagnostic(
                    MdDiagId.SAME_ATTR_REPETITION,
                    "attribute `$attr` is constrained ${group.size} times — combine into one `{…}` set or range",
                    attr,
                )
            }
        }
        return null
    }

    private fun unresolvable(components: List<PathComponent>): MdDiagnostic =
        MdDiagnostic(
            MdDiagId.UNRESOLVABLE,
            "no consistent assignment for `${components.joinToString(".") { it.sourceText() }}`",
        )

    companion object {
        /** The R8/TTRP-MD-014 exhaustive-search state bound. Documented in the diag text. */
        const val DEFAULT_SEARCH_BOUND: Int = 50_000
    }
}
