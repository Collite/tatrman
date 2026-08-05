// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import org.tatrman.ttr.lexicon.TargetClass
import org.tatrman.ttr.semantics.md.MdModel

/**
 * RV-P3.4 — the md half of the reference index.
 *
 * ## Why this is a second index and not a widening
 *
 * `ttr-metadata`'s `Model` covers `db`/`er`/`cnc`; its `SchemaCode` enum has **no `MD`**, so an md
 * object cannot be a `QualifiedName` and `dotted()` will never render one. md lives in a different
 * type in a different module (`ttr-semantics`' [MdModel]). Rather than widen a wire-facing enum for
 * a compiler-local need, this builds a plain string-keyed index and [orElse] composes the two.
 *
 * ## The grammar (ruled 2026-08-06)
 *
 * ```
 * md.measure.revenue                        object
 * md.dimension.Product                      object
 * md.cubelet.storeSales                     object
 * md.dimension.Customer.state               attribute depth (RV-24)
 * md.dimension.DistributionCentre.dcCode.5  member depth
 * ```
 *
 * **Kinded**, exactly like `er.entity.<entity>.<attribute>.<code>` — because `QualifiedName.dotted()`
 * already renders the kind as the namespace segment, and because md is six independent name maps
 * with nothing enforcing uniqueness across them (hartland carries measure `quantity` beside domain
 * `Quantity`, distinguished only by case). The unkinded form TTR-M uses for its own md cross-refs
 * (`md.Money`, `Customer.state`) is deliberately **not** accepted here: it collides with the kinded
 * form at the same arity in the opposite direction, so honouring both would need a tie-break keyed
 * on a closed word set and would make a ref's meaning depend on the estate's naming.
 *
 * ## What is deliberately absent
 *
 * `domain`, `hierarchy` and `map` are **not addressable**. A domain is a type, not something a
 * query selects — a word bound to one gives the resolver a target it cannot put in a plan, and that
 * word belongs on the attribute ranging over it. A hierarchy is a navigation structure. A map is
 * binding plumbing with a generated-looking name (`addr_to_state`). Their absence is a ruling
 * (RV-P3.4 T1), so a ref naming one dangles like any other unknown — loudly, as `RG-LEXC-001`.
 *
 * A **wrong-kind** ref (`md.measure.Product`) dangles rather than falling back to another map: with
 * six maps behind one namespace, a fallback would make the kind token decorative.
 */
fun ModelRefIndex.Companion.ofMd(md: MdModel): ModelRefIndex {
    val objects = mutableSetOf<String>()
    val members = mutableSetOf<String>()

    for (name in md.measures.keys) objects += MdRefs.measure(name)
    for (name in md.cubelets.keys) objects += MdRefs.cubelet(name)
    for ((dimName, dim) in md.dimensions) {
        objects += MdRefs.dimension(dimName)
        for (attr in dim.attributes) {
            objects += MdRefs.attribute(dimName, attr.name)
            // Declared members only — an author wrote `"5" → "Memphis DC"`. Data-derived members
            // are the lex-matcher index's layer, with its own refresh cadence.
            for (code in attr.valueLabels.keys) members += MdRefs.member(dimName, attr.name, code)
        }
    }

    return ModelRefIndex { ref ->
        when (ref) {
            in members -> TargetClass.MEMBER
            in objects -> TargetClass.MODEL_OBJECT
            else -> null
        }
    }
}

/**
 * First match wins; `null` falls through. The two halves cannot collide — their keys differ in the
 * leading schema token — so the order is about precedence in principle only, and is asserted rather
 * than assumed.
 */
infix fun ModelRefIndex.orElse(next: ModelRefIndex): ModelRefIndex =
    ModelRefIndex { ref -> classify(ref) ?: next.classify(ref) }

/**
 * How an md target ref is spelled. Shared by the index above and by
 * [MdMetadataExtractor], so the layer that HARVESTS a label and the layer that RESOLVES the ref it
 * is harvested against cannot drift into two spellings of the same object.
 */
internal object MdRefs {
    const val MD_MODEL_CODE: String = "md"

    fun measure(name: String): String = "$PREFIX.measure.$name"

    fun cubelet(name: String): String = "$PREFIX.cubelet.$name"

    fun dimension(name: String): String = "$PREFIX.dimension.$name"

    fun attribute(
        dimension: String,
        attribute: String,
    ): String = "${dimension(dimension)}.$attribute"

    fun member(
        dimension: String,
        attribute: String,
        code: String,
    ): String = "${attribute(dimension, attribute)}.$code"

    private const val PREFIX = "md"
}
