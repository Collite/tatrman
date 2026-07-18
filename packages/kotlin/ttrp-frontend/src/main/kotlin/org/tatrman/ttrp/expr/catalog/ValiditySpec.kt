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
        val root = Yaml().load<Map<String, Any?>>(text)
        val domain = root["domain"] as Map<String, Any?>
        val corpus = root["corpus"] as Map<String, Any?>
        return ValiditySpec(
            function = root["function"].toString(),
            typePair = root["typePair"].toString(),
            code = root["code"].toString(),
            pure = root["pure"] as? Boolean ?: true,
            domain =
                ValidityDomain(
                    kind = domain["kind"].toString(),
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
        )
    }
}
