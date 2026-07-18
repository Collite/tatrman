// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import org.tatrman.ttr.semantics.md.MdModel

/**
 * The outcome of pre-binding a path's components (R6/R7): qualified pairs and inferable
 * set/range/star selectors become [coordinates]; a `dim.*` whose attribute depends on the cubelet's
 * grain is held in [dimStars] for the search to finalise (S2-B); the leftover scalar tokens are
 * [free] (members/measures/aggs/cubelets/calc the search assigns); [diagnostics] carries MD-004 for
 * unbindable selectors.
 */
data class BindResult(
    val coordinates: List<Coordinate>,
    val dimStars: List<String>,
    val free: List<PathComponent>,
    val diagnostics: List<MdDiagnostic>,
)

/**
 * Binds qualified pairs (R6) and star/set/range selectors (R7) before the free search. Pairs are
 * tried first and remove their tokens from the free pool. A selector (`*`/`{…}`/range) must reach an
 * attribute — by an explicit qualifier, or a uniquely inferable one from its atoms' membership — else
 * `TTRP-MD-004`.
 *
 * Interlocks deferred to the search (S2-B): a `dim.*` (dimension-qualified star) can't fix its
 * attribute until the cubelet is known ([dimStars]); disconnected-mode deferral of `dim.member`
 * existence (R13) is finished in S2-C.
 */
object PairBinder {
    fun bind(
        components: List<PathComponent>,
        model: MdModel,
        snapshot: MemberSnapshot?,
    ): BindResult {
        val coordinates = mutableListOf<Coordinate>()
        val dimStars = mutableListOf<String>()
        val free = mutableListOf<PathComponent>()
        val diagnostics = mutableListOf<MdDiagnostic>()

        var i = 0
        while (i < components.size) {
            val c = components[i]
            // Pre-qualified pair, handed in as one unit (never produced by PathText).
            if (c is PathComponent.Pair) {
                bindPair(c.qualifier, c.target, model, snapshot, coordinates, dimStars, diagnostics, free)
                i++
                continue
            }
            // Adjacency pair: a qualifier (dimension/attribute) immediately followed by a target.
            val next = components.getOrNull(i + 1)
            if (c is PathComponent.Ident && next != null && isQualifier(c.text, model) && isTarget(next, model)) {
                bindPair(c, next, model, snapshot, coordinates, dimStars, diagnostics, free)
                i += 2
                continue
            }
            // Unqualified selector — infer its attribute from the atoms (R7), or MD-004.
            when (c) {
                is PathComponent.SetLit -> bindUnqualifiedSet(c, model, snapshot, coordinates, diagnostics)
                is PathComponent.RangeLit -> bindUnqualifiedRange(c, model, snapshot, coordinates, diagnostics)
                PathComponent.Star ->
                    diagnostics += MdDiagnostic(MdDiagId.UNBINDABLE_SELECTOR, "bare `*` — name its dimension", "*")
                else -> free += c // scalar token → the free search
            }
            i++
        }
        return BindResult(coordinates, dimStars, free, diagnostics)
    }

    // ---- pair binding ------------------------------------------------------------------------

    private fun bindPair(
        qualifier: PathComponent,
        target: PathComponent,
        model: MdModel,
        snapshot: MemberSnapshot?,
        coordinates: MutableList<Coordinate>,
        dimStars: MutableList<String>,
        diagnostics: MutableList<MdDiagnostic>,
        free: MutableList<PathComponent>,
    ) {
        val qText = (qualifier as? PathComponent.Ident)?.text
        if (qText == null) {
            free += qualifier
            free += target
            return
        }
        val attr = uniqueAttribute(qText, model)
        val dim = attr?.first ?: dimensionOf(qText, model)
        if (dim == null) { // qualifier is neither an attribute nor a dimension → fall back to free
            free += qualifier
            free += target
            return
        }
        val attrName = attr?.second
        when (target) {
            PathComponent.Star ->
                if (attrName != null) {
                    coordinates += Coordinate(dim, "$dim.$attrName", Selector.Star)
                } else {
                    dimStars += dim // dim.* — attribute comes from the cubelet grain (S2-B)
                }
            is PathComponent.SetLit ->
                coordinates +=
                    Coordinate(dim, resolvedAttr(dim, attrName, target.atoms, model, snapshot), memberSet(target.atoms))
            is PathComponent.RangeLit ->
                coordinates +=
                    Coordinate(
                        dim,
                        resolvedAttr(dim, attrName, listOf(target.lo, target.hi), model, snapshot),
                        Selector.Range(memberRef(target.lo), memberRef(target.hi)),
                    )
            else -> { // scalar member target
                val raw = memberRef(target)
                val ref = if (snapshot == null) raw.copy(deferred = true) else raw // R13 defers existence offline
                val boundAttr = attrName ?: memberAttributeInDimension(dim, ref.text, model, snapshot)
                coordinates += Coordinate(dim, "$dim.$boundAttr", Selector.Pinned(ref))
            }
        }
    }

    // ---- unqualified selector binding (R7) ---------------------------------------------------

    private fun bindUnqualifiedSet(
        set: PathComponent.SetLit,
        model: MdModel,
        snapshot: MemberSnapshot?,
        coordinates: MutableList<Coordinate>,
        diagnostics: MutableList<MdDiagnostic>,
    ) {
        val attr = commonAttribute(set.atoms, model, snapshot)
        if (attr == null) {
            diagnostics +=
                MdDiagnostic(MdDiagId.UNBINDABLE_SELECTOR, "set members share no single attribute", set.sourceText())
            return
        }
        coordinates += Coordinate(attr.dimension, attr.qname, memberSet(set.atoms))
    }

    private fun bindUnqualifiedRange(
        range: PathComponent.RangeLit,
        model: MdModel,
        snapshot: MemberSnapshot?,
        coordinates: MutableList<Coordinate>,
        diagnostics: MutableList<MdDiagnostic>,
    ) {
        val attr = commonAttribute(listOf(range.lo, range.hi), model, snapshot)
        if (attr == null) {
            diagnostics +=
                MdDiagnostic(
                    MdDiagId.UNBINDABLE_SELECTOR,
                    "range endpoints share no single attribute",
                    range.sourceText(),
                )
            return
        }
        if (!isOrdered(attr.domain, model)) {
            diagnostics +=
                MdDiagnostic(
                    MdDiagId.UNBINDABLE_SELECTOR,
                    "range over unordered domain `${attr.domain}`",
                    range.sourceText(),
                )
            return
        }
        coordinates += Coordinate(attr.dimension, attr.qname, Selector.Range(memberRef(range.lo), memberRef(range.hi)))
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun isQualifier(
        text: String,
        model: MdModel,
    ): Boolean = dimensionOf(text, model) != null || uniqueAttribute(text, model) != null

    /** A target follows a qualifier: any selector, or a member-like scalar (not itself a slot name). */
    private fun isTarget(
        c: PathComponent,
        model: MdModel,
    ): Boolean =
        when (c) {
            PathComponent.Star, is PathComponent.SetLit, is PathComponent.RangeLit -> true
            is PathComponent.Ident -> !isStructural(c.text, model) // a structural slot is a drill, not a member

            is PathComponent.IntLit, is PathComponent.Quoted -> true
            is PathComponent.Pair -> false
        }

    /** True when the text names a structural slot (attribute/dimension/cubelet/measure/agg). */
    private fun isStructural(
        text: String,
        model: MdModel,
    ): Boolean =
        uniqueAttribute(text, model) != null ||
            dimensionOf(text, model) != null ||
            model.cubelets.containsKey(text) ||
            model.measures.containsKey(text) ||
            text.lowercase() in setOf("sum", "avg", "min", "max", "count")

    private fun dimensionOf(
        text: String,
        model: MdModel,
    ): String? = model.dimensions.keys.firstOrNull { it.equals(text, ignoreCase = true) }

    /** The unique `(dimension, attribute)` an attribute name denotes, or null if none / ambiguous. */
    private fun uniqueAttribute(
        text: String,
        model: MdModel,
    ): kotlin.Pair<String, String>? {
        val hits = model.attributes.values.filter { it.name.equals(text, ignoreCase = true) }
        return hits.singleOrNull()?.let { it.dimension to it.name }
    }

    private data class AttrRef(
        val dimension: String,
        val attribute: String,
        val domain: String,
    ) {
        val qname: String get() = "$dimension.$attribute"
    }

    /** The single attribute every atom is a member of, or null when zero / more than one. */
    private fun commonAttribute(
        atoms: List<PathComponent>,
        model: MdModel,
        snapshot: MemberSnapshot?,
    ): AttrRef? {
        val perAtom =
            atoms.map { atom ->
                TokenClassifier
                    .classify(atom, model, snapshot)
                    .filterIsInstance<SlotCandidate.Member>()
                    .map { AttrRef(it.dimension, it.attribute, it.domain) }
                    .toSet()
            }
        if (perAtom.any { it.isEmpty() }) return null
        val common = perAtom.reduce { a, b -> a intersect b }
        return common.singleOrNull()
    }

    /** The attribute of [dim] that member [text] belongs to (connected), or the dimension key offline. */
    private fun memberAttributeInDimension(
        dim: String,
        text: String,
        model: MdModel,
        snapshot: MemberSnapshot?,
    ): String {
        val hit =
            TokenClassifier
                .classify(PathComponent.Ident(text), model, snapshot)
                .filterIsInstance<SlotCandidate.Member>()
                .firstOrNull { it.dimension == dim }
        return hit?.attribute ?: model.dimensions[dim]?.key ?: "key"
    }

    /** Resolve a selector's attribute: the explicit qualifier if attribute-level, else infer from atoms. */
    private fun resolvedAttr(
        dim: String,
        attrName: String?,
        atoms: List<PathComponent>,
        model: MdModel,
        snapshot: MemberSnapshot?,
    ): String {
        if (attrName != null) return "$dim.$attrName"
        val inferred = commonAttribute(atoms, model, snapshot)?.takeIf { it.dimension == dim }
        return inferred?.qname ?: "$dim.${model.dimensions[dim]?.key ?: "key"}"
    }

    private fun isOrdered(
        domain: String,
        model: MdModel,
    ): Boolean =
        model.domains[domain]?.type?.lowercase() in setOf("int", "integer", "date", "timestamp", "instant", "datetime")

    private fun memberSet(atoms: List<PathComponent>): Selector.MemberSet =
        Selector.MemberSet(atoms.map { memberRef(it) })

    private fun memberRef(c: PathComponent): MemberRef =
        when (c) {
            is PathComponent.Ident -> MemberRef(c.text)
            is PathComponent.IntLit -> MemberRef(c.text)
            is PathComponent.Quoted -> MemberRef(c.text)
            else -> MemberRef(c.sourceText()) // atoms are Ident/IntLit/Quoted; this is a defensive fallback
        }
}
