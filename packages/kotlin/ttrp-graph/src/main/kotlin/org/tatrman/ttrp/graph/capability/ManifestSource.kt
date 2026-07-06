package org.tatrman.ttrp.graph.capability

import kotlinx.serialization.json.Json

/** Thrown when a shipped manifest is malformed (strict decode / unknown node kind / version mismatch). */
class ManifestFormatException(
    message: String,
) : RuntimeException(message)

/** Provides the compiler-shipped engine-type manifests (T6-b type layer). */
interface ManifestSource {
    fun load(id: String): EngineTypeManifest?

    fun all(): List<EngineTypeManifest>
}

/**
 * Classpath [ManifestSource] reading `ttrp/manifests/<id>.json` shipped in this
 * module's resources. Strict decode (P2: a typo'd capability must not silently
 * vanish); node-kind keys validated against the T10 roster; manifestVersion pinned.
 */
class ClasspathManifestSource(
    private val ids: List<String> = SHIPPED,
) : ManifestSource {
    private val json =
        Json {
            ignoreUnknownKeys = false
            isLenient = false
        }
    private val cache = LinkedHashMap<String, EngineTypeManifest>()

    override fun load(id: String): EngineTypeManifest? =
        cache.getOrPut(id) {
            val text =
                javaClass.getResourceAsStream("/ttrp/manifests/$id.json")?.bufferedReader()?.readText()
                    ?: return null
            decodeAndValidate(id, text)
        }

    override fun all(): List<EngineTypeManifest> = ids.mapNotNull { load(it) }

    private fun decodeAndValidate(
        id: String,
        text: String,
    ): EngineTypeManifest {
        val m =
            try {
                json.decodeFromString(EngineTypeManifest.serializer(), text)
            } catch (e: Exception) {
                throw ManifestFormatException("manifest `$id` failed strict decode: ${e.message}")
            }
        if (m.manifestVersion != MANIFEST_VERSION) {
            throw ManifestFormatException("manifest `$id` has version ${m.manifestVersion}, expected $MANIFEST_VERSION")
        }
        val unknown = m.nodes.keys - KNOWN_NODE_KINDS
        if (unknown.isNotEmpty()) {
            throw ManifestFormatException("manifest `$id` names unknown node kind(s): ${unknown.sorted()}")
        }
        return m
    }

    companion object {
        const val MANIFEST_VERSION = 1
        val SHIPPED = listOf("postgres-16", "polars", "bash")

        /** The T10 node roster — valid keys for a manifest's `nodes` map. */
        val KNOWN_NODE_KINDS =
            setOf(
                "Project",
                "Select",
                "Calc",
                "Filter",
                "Branch",
                "Switch",
                "Join",
                "Aggregate",
                "Sort",
                "Union",
                "Intersect",
                "Except",
                "Values",
                "Limit",
                "Pivot",
                "Distinct",
                "Load",
                "Store",
                "Transfer",
                "Index",
                "Display",
            )
    }
}
