// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.conventions

import org.tatrman.ttr.importschema.dbmodel.ImportSchemaException
import org.yaml.snakeyaml.Yaml

/**
 * Parses `conventions.yaml` into a typed [ConventionsFile] with **strict** unknown-key rejection
 * (F3-β typo safety): a misspelled key is a hard error, not a silently-ignored setting — because
 * a silently-dropped probe budget would break GI-2 invisibly. Kebab-case keys on the wire map to
 * camelCase fields. Bad conventions raise `TTRP-IMP-002`.
 */
object ConventionsLoader {
    private const val CODE = "TTRP-IMP-002"

    fun parse(yaml: String): ConventionsFile {
        val root = load(yaml)
        checkKeys(root, setOf("profile", "locale", "naming", "scope", "probes"), "(root)")
        return ConventionsFile(
            profile = string(root, "profile"),
            locale = string(root, "locale"),
            naming = naming(subMap(root, "naming")),
            scope = scope(subMap(root, "scope")),
            probes = probes(subMap(root, "probes")),
        )
    }

    private fun naming(map: Map<String, Any?>?): NamingConventions {
        if (map == null) return NamingConventions()
        checkKeys(
            map,
            setOf("primary-key-patterns", "foreign-key-patterns", "junction-patterns", "codebook-prefixes"),
            "naming",
        )
        return NamingConventions(
            primaryKeyPatterns = stringList(map, "primary-key-patterns"),
            foreignKeyPatterns = stringList(map, "foreign-key-patterns"),
            junctionPatterns = stringList(map, "junction-patterns"),
            codebookPrefixes = stringList(map, "codebook-prefixes"),
        )
    }

    private fun scope(map: Map<String, Any?>?): ScopeConfig {
        if (map == null) return ScopeConfig()
        checkKeys(map, setOf("include", "exclude"), "scope")
        return ScopeConfig(include = stringList(map, "include"), exclude = stringList(map, "exclude"))
    }

    private fun probes(map: Map<String, Any?>?): ProbeConfig {
        if (map == null) return ProbeConfig()
        checkKeys(map, setOf("full-scan-threshold-rows", "sample", "budget", "overrides"), "probes")
        val defaults = ProbeConfig()
        return ProbeConfig(
            fullScanThresholdRows = long(map, "full-scan-threshold-rows") ?: defaults.fullScanThresholdRows,
            sample = sample(subMap(map, "sample")),
            budget = budget(subMap(map, "budget")),
            overrides = overrides(map["overrides"]),
        )
    }

    private fun sample(map: Map<String, Any?>?): SampleConfig {
        if (map == null) return SampleConfig()
        checkKeys(map, setOf("hash", "modulus", "keep"), "probes.sample")
        val d = SampleConfig()
        return SampleConfig(
            hash = string(map, "hash") ?: d.hash,
            modulus = long(map, "modulus") ?: d.modulus,
            keep = long(map, "keep") ?: d.keep,
        )
    }

    private fun budget(map: Map<String, Any?>?): BudgetConfig {
        if (map == null) return BudgetConfig()
        checkKeys(map, setOf("max-candidates", "max-probe-rows"), "probes.budget")
        val d = BudgetConfig()
        return BudgetConfig(
            maxCandidates = long(map, "max-candidates") ?: d.maxCandidates,
            maxProbeRows = long(map, "max-probe-rows") ?: d.maxProbeRows,
        )
    }

    private fun overrides(raw: Any?): List<ProbeOverride> {
        if (raw == null) return emptyList()
        val list = raw as? List<*> ?: fail("probes.overrides must be a list")
        return list.map { item ->
            val m = asMap(item) ?: fail("probes.overrides entries must be maps")
            checkKeys(m, setOf("table", "mode"), "probes.overrides[]")
            val table = string(m, "table") ?: fail("probes.overrides[].table is required")
            val mode = string(m, "mode") ?: fail("probes.overrides[].mode is required")
            if (mode !in
                setOf("full", "sample", "skip")
            ) {
                fail("probes.overrides[].mode must be full|sample|skip, got '$mode'")
            }
            ProbeOverride(table, mode)
        }
    }

    // ---- primitive helpers ----

    private fun load(yaml: String): Map<String, Any?> {
        val loaded = Yaml().load<Any?>(yaml) ?: return emptyMap()
        return asMap(loaded) ?: fail("conventions.yaml must be a mapping at the top level")
    }

    @Suppress("UNCHECKED_CAST")
    private fun asMap(v: Any?): Map<String, Any?>? =
        (v as? Map<*, *>)?.also { m ->
            m.keys.forEach { if (it !is String) fail("non-string key '$it' in conventions.yaml") }
        } as Map<String, Any?>?

    private fun subMap(
        map: Map<String, Any?>,
        key: String,
    ): Map<String, Any?>? = map[key]?.let { asMap(it) ?: fail("'$key' must be a mapping") }

    private fun string(
        map: Map<String, Any?>,
        key: String,
    ): String? = map[key]?.toString()

    private fun long(
        map: Map<String, Any?>,
        key: String,
    ): Long? =
        when (val v = map[key]) {
            null -> null
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: fail("'$key' must be an integer, got '$v'")
            else -> fail("'$key' must be an integer")
        }

    private fun stringList(
        map: Map<String, Any?>,
        key: String,
    ): List<String> {
        val v = map[key] ?: return emptyList()
        val list = v as? List<*> ?: fail("'$key' must be a list")
        return list.map { it?.toString() ?: fail("'$key' has a null entry") }
    }

    private fun checkKeys(
        map: Map<String, Any?>,
        allowed: Set<String>,
        context: String,
    ) {
        val unknown = map.keys.filter { it !in allowed }
        if (unknown.isNotEmpty()) {
            fail(
                "unknown key(s) in $context: ${unknown.sorted().joinToString(
                    ", ",
                )} (allowed: ${allowed.sorted().joinToString(", ")})",
            )
        }
    }

    private fun fail(message: String): Nothing = throw ImportSchemaException(CODE, "conventions: $message")
}
