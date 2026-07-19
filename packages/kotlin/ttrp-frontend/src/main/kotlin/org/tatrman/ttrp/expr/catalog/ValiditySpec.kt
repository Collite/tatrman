// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.expr.catalog

import org.yaml.snakeyaml.Yaml

/**
 * A canonical validity spec (contracts §2, RJ-P0): the machine-checkable acceptance domain of
 * one reject-capable (function, type-pair), its row-level reject [code], and an accept/reject
 * example [corpus] that doubles as conform fixtures (R-D3). Loaded from the `ttrp/validity`
 * resource YAMLs — the normative RJ-P0 output, signed off under control-room R-A3.
 */
data class ValiditySpec(
    val function: String,
    val typePair: String,
    val code: String,
    val pure: Boolean,
    val domain: ValidityDomain,
    val corpus: ValidityCorpus,
    val nullIsSuccess: Boolean,
    /**
     * True iff the guard emitters render this spec's domain **faithfully on both engines** and it is
     * live-sealed PG↔Polars (RJ-P5). Only these produce rejects in v1; every other shipped spec is
     * parsed (for its corpus/code) but a wired reject site on it is a compile error `TTRP-RJ-107`
     * (fail-closed) until its domain semantics are implemented and sealed — see [ValidityCatalog.V1_SUPPORTED]
     * and contracts §2. Derived at load, not authored in the YAML.
     */
    val supported: Boolean,
)

/** The acceptance domain (contracts §2): `regex+bounds` for text casts, `predicate` for op.div, etc. */
data class ValidityDomain(
    val kind: String,
    val regex: String? = null,
    val trim: String? = null,
    val bounds: String? = null,
    val predicate: String? = null,
)

/** The accept/reject corpus (the real RJ-P0 divergence probes, partitioned by the domain). */
data class ValidityCorpus(
    val accept: List<String>,
    val reject: List<String>,
)

/**
 * Loads and indexes the shipped validity specs. Resource ids are an explicit list (the same
 * pattern [org.tatrman.ttrp.graph.capability.ClasspathManifestSource] uses — a classpath
 * directory cannot be listed portably across jar/dir), so a new spec is one obvious edit here.
 */
object ValidityCatalog {
    /** The RS-1 spec set (RJ-P0): six casts, op.div, and the two format-anchored datetime parses. */
    val SHIPPED: List<String> =
        listOf(
            "cast.text-int64",
            "cast.text-decimal18_4",
            "cast.text-float64",
            "cast.text-date",
            "cast.text-timestamp",
            "cast.text-bool",
            "op.div",
            "fn.to_date",
            "fn.to_timestamp",
        )

    /**
     * The (function, typePair) pairs whose guard the emitters render **faithfully on BOTH engines**
     * and that are live-sealed PG↔Polars (RJ-P5). Every OTHER shipped spec is loaded for its corpus
     * and row code but is **not emittable** in v1: a wired reject site on it is a fail-closed compile
     * error (`TTRP-RJ-107`), never a silent accept-all guard. Flip an entry here only once its domain
     * semantics are implemented on both engines and re-sealed — the single auditable "v1 rejects
     * roster" (contracts §2). `text->{decimal,float64,date,timestamp,bool}` and the datetime parses
     * are intentionally absent: their domains (decimal/float bounds, `enum-ci` tokens, case-insensitive
     * regex, format-anchored parse) are not yet rendered faithfully and diverge PG↔Polars.
     */
    val V1_SUPPORTED: Set<Pair<String, String>> =
        setOf(
            "cast" to "text->int64",
            "op.div" to "numeric,numeric->numeric",
        )

    val all: List<ValiditySpec> by lazy { SHIPPED.map(::load) }

    private val byPair: Map<Pair<String, String>, ValiditySpec> by lazy {
        all.associateBy { it.function to it.typePair }
    }

    /**
     * The single reject-capability query (contracts §2): the spec for a (function, type-pair), or
     * null when the pair is not in the RS-1 set. This is what the elaboration rule and the
     * dead-wire check ask.
     */
    fun rejectCapability(
        function: String,
        typePair: String,
    ): ValiditySpec? = byPair[function to typePair]

    private fun load(id: String): ValiditySpec {
        val text =
            javaClass.getResourceAsStream("/ttrp/validity/$id.yaml")?.bufferedReader()?.readText()
                ?: error("validity spec `$id` not found on the classpath")

        @Suppress("UNCHECKED_CAST")
        val root =
            (Yaml().load<Any?>(text) as? Map<String, Any?>)
                ?: error("validity spec `$id` is not a YAML mapping")
        val domain = mapAt(root, "domain", id)
        val corpus = mapAt(root, "corpus", id)
        val function = requireStr(root, "function", id)
        val typePair = requireStr(root, "typePair", id)
        return ValiditySpec(
            function = function,
            typePair = typePair,
            code = requireStr(root, "code", id),
            pure = root["pure"] as? Boolean ?: true,
            domain =
                ValidityDomain(
                    kind = requireStr(domain, "kind", "$id.domain"),
                    regex = domain["regex"]?.toString(),
                    trim = domain["trim"]?.toString(),
                    bounds = domain["bounds"]?.toString(),
                    predicate = domain["predicate"]?.toString(),
                ),
            corpus =
                ValidityCorpus(
                    accept = (corpus["accept"] as? List<Any?>).orEmpty().map { it.toString() },
                    reject = (corpus["reject"] as? List<Any?>).orEmpty().map { it.toString() },
                ),
            nullIsSuccess = corpus["null_is_success"] as? Boolean ?: false,
            supported = (function to typePair) in V1_SUPPORTED,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapAt(
        root: Map<String, Any?>,
        key: String,
        id: String,
    ): Map<String, Any?> =
        (root[key] as? Map<String, Any?>) ?: error("validity spec `$id` is missing a `$key:` mapping")

    private fun requireStr(
        m: Map<String, Any?>,
        key: String,
        id: String,
    ): String = m[key]?.toString() ?: error("validity spec `$id` is missing required key `$key`")
}
