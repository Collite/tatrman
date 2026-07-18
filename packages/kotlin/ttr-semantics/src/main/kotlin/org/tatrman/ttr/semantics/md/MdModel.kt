// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

import org.tatrman.ttr.parser.model.AggregationSpec
import org.tatrman.ttr.parser.model.CubeletDef
import org.tatrman.ttr.parser.model.CubeletMeasure
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.DimensionDef
import org.tatrman.ttr.parser.model.HierarchyDef
import org.tatrman.ttr.parser.model.MdDomainDef
import org.tatrman.ttr.parser.model.MdMapDef
import org.tatrman.ttr.parser.model.MeasureDef

/**
 * The Kotlin MD symbol graph — the Layer A subset the dot-path resolver needs (MDS2), ported from
 * the TS `@tatrman/semantics` md stages 2A/2B4/2E. Immutable; built from [org.tatrman.ttr.parser]
 * defs by [MdModelBuilder]. Names are the simple def names within the single `model md` namespace;
 * cross-refs (`md.Money`, `Customer.name`) are resolved to those names on the way in.
 *
 * Validators/diagnostics stay TS-side (MDS2) — this is the structural graph + the 2B4 sugar only.
 */
data class MdModel(
    val domains: Map<String, MdDomain>,
    val dimensions: Map<String, MdDimension>,
    val maps: Map<String, MdMap>,
    val measures: Map<String, MdMeasure>,
    val hierarchies: Map<String, MdHierarchy>,
    val cubelets: Map<String, MdCubelet>,
) {
    /** Every dimension attribute, keyed by its `Dimension.attribute` path. */
    val attributes: Map<String, MdAttribute> =
        dimensions.values.flatMap { dim -> dim.attributes.map { "${dim.name}.${it.name}" to it } }.toMap()

    /**
     * The underlying **domain** a cross-ref ranges over (2B4 sugar): a domain ref (`md.Money`)
     * resolves to itself; an attribute ref (`Customer.name`) lowers through the attribute's
     * `domain:`. Returns the domain's simple name, or null if neither resolves.
     */
    fun underlyingDomain(ref: String): String? {
        val last = ref.substringAfterLast('.')
        if (domains.containsKey(last)) return last
        // attribute ref: `Dimension.attribute` (or a bare attribute name — rare).
        val attr = attributes[ref] ?: attributes.values.firstOrNull { it.name == last }
        return attr?.domainRef?.let { underlyingDomain(it) }
    }

    companion object {
        fun from(defs: List<Definition>): MdModel = MdModelBuilder.build(defs)
    }
}

data class MdDomain(
    val name: String,
    val type: String?,
    val kind: String?,
    val publishMembers: Boolean,
)

data class MdAttribute(
    val name: String,
    val dimension: String,
    /** The `domain:` cross-ref as authored (e.g. `md.Money`); resolve via [MdModel.underlyingDomain]. */
    val domainRef: String?,
    val isKey: Boolean,
    val aggregation: AggregationSpec?,
)

data class MdDimension(
    val name: String,
    val key: String?,
    val attributes: List<MdAttribute>,
    val hierarchies: List<String>,
)

/** How a map coarsens grain: N:1 (demotes leaf-ness), 1:1 (co-leaf), or calc (implicitly N:1). */
enum class MapKind { N1, ONE_ONE, CALC }

data class MdMap(
    val name: String,
    /** `from`/`to` cross-refs as authored (domain or attribute refs). */
    val from: List<String>,
    val to: List<String>,
    val kind: MapKind,
    val calc: String?,
)

data class MdMeasure(
    val name: String,
    val domainRef: String?,
    val measureClass: String?,
    val aggregation: AggregationSpec?,
    val validBy: String?,
)

data class MdHierarchyLevel(
    val attribute: String,
    val via: String?,
)

data class MdHierarchy(
    val name: String,
    val dimension: String?,
    val levels: List<MdHierarchyLevel>,
)

data class MdCubelet(
    val name: String,
    /** Grain as authored `Dimension.attribute` refs. */
    val grain: List<String>,
    val measures: List<String>,
)

/** Builds an [MdModel] from parser defs (MDS2 port of TS md stage 2A). */
internal object MdModelBuilder {
    fun build(defs: List<Definition>): MdModel {
        val domains =
            defs.filterIsInstance<MdDomainDef>().associate { d ->
                d.name to MdDomain(d.name, d.type?.name, d.domainKind, d.publishMembers)
            }
        val dimensions =
            defs.filterIsInstance<DimensionDef>().associate { dim ->
                dim.name to
                    MdDimension(
                        name = dim.name,
                        key = dim.key,
                        attributes =
                            dim.attributes.map { a ->
                                MdAttribute(
                                    name = a.name,
                                    dimension = dim.name,
                                    domainRef = a.domainRef?.path,
                                    isKey = a.isKey,
                                    aggregation = a.aggregation,
                                )
                            },
                        hierarchies = dim.hierarchies.map { it.path },
                    )
            }
        val maps =
            defs.filterIsInstance<MdMapDef>().associate { m ->
                m.name to
                    MdMap(
                        name = m.name,
                        from = m.from.map { it.path },
                        to = m.to.map { it.path },
                        kind =
                            when {
                                m.calc != null -> MapKind.CALC
                                m.cardinality == "1:1" -> MapKind.ONE_ONE
                                else -> MapKind.N1
                            },
                        calc = m.calc?.name,
                    )
            }
        val measures = defs.filterIsInstance<MeasureDef>().associate { it.name to measure(it) }
        val hierarchies =
            defs.filterIsInstance<HierarchyDef>().associate { h ->
                h.name to
                    MdHierarchy(
                        name = h.name,
                        dimension = h.dimensionRef?.path,
                        levels = h.levels.map { MdHierarchyLevel(it.attribute, it.via?.path) },
                    )
            }
        val cubelets =
            defs.filterIsInstance<CubeletDef>().associate { c ->
                c.name to
                    MdCubelet(
                        name = c.name,
                        grain = c.grain.map { it.path },
                        measures =
                            c.measures.map { m ->
                                when (m) {
                                    is CubeletMeasure.Ref -> m.ref.path.substringAfterLast('.')
                                    is CubeletMeasure.Inline -> m.measure.name
                                }
                            },
                    )
            }
        return MdModel(domains, dimensions, maps, measures, hierarchies, cubelets)
    }

    private fun measure(m: MeasureDef): MdMeasure =
        MdMeasure(
            name = m.name,
            domainRef = m.domainRef?.path,
            measureClass = m.measureClass,
            aggregation = m.aggregation,
            validBy = m.validBy,
        )
}
