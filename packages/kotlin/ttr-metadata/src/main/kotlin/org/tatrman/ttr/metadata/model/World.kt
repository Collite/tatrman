package org.tatrman.ttr.metadata.model

import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.SourceLocation

/**
 * v4.1 world model tier (M2 — the half Ariadne never had). Typed, reconciled
 * counterparts of the parser world AST. Manifest entries are transported opaque
 * (T6 β data — MD5, never interpreted here). `sourceLocation` carries the def
 * span so `WorldResolver` failures can point the consumer at the instance def
 * (E-d-adjacent rendering).
 */
data class WorldSchema(
    val worlds: Map<QualifiedName, World> = emptyMap(),
) : SchemaContents {
    override val schemaCode: String = "world"

    override fun objects(): Sequence<ModelObject> =
        sequence {
            for (w in worlds.values) {
                yield(w)
                yieldAll(w.engines)
                yieldAll(w.executors)
                for (s in w.storages) {
                    yield(s)
                    yieldAll(s.schemas)
                }
            }
        }
}

data class World(
    override val internalId: String,
    override val qname: QualifiedName,
    override val description: String = "",
    override val tags: List<String> = emptyList(),
    override val sourceFile: String = "",
    override val binding: Binding = Binding.BoundReal,
    val extendsRef: String? = null,
    val engines: List<WorldEngine> = emptyList(),
    val executors: List<WorldExecutor> = emptyList(),
    val storages: List<WorldStorage> = emptyList(),
    val sourceLocation: SourceLocation? = null,
) : ModelObject {
    override val kind: String = "world"
}

data class WorldEngine(
    override val internalId: String,
    override val qname: QualifiedName,
    override val description: String = "",
    override val tags: List<String> = emptyList(),
    override val sourceFile: String = "",
    override val binding: Binding = Binding.BoundReal,
    val type: String? = null,
    val version: String? = null,
    val extendsRef: String? = null,
    val manifest: Map<String, PropertyValue> = emptyMap(),
    val sourceLocation: SourceLocation? = null,
) : ModelObject {
    override val kind: String = "engine"
}

data class WorldExecutor(
    override val internalId: String,
    override val qname: QualifiedName,
    override val description: String = "",
    override val tags: List<String> = emptyList(),
    override val sourceFile: String = "",
    override val binding: Binding = Binding.BoundReal,
    val type: String? = null,
    val version: String? = null,
    val extendsRef: String? = null,
    val manifest: Map<String, PropertyValue> = emptyMap(),
    val sourceLocation: SourceLocation? = null,
) : ModelObject {
    override val kind: String = "executor"
}

data class WorldStorage(
    override val internalId: String,
    override val qname: QualifiedName,
    override val description: String = "",
    override val tags: List<String> = emptyList(),
    override val sourceFile: String = "",
    override val binding: Binding = Binding.BoundReal,
    val type: String? = null,
    val extendsRef: String? = null,
    val via: String? = null,
    val hosts: List<String> = emptyList(),
    val staging: Boolean = false,
    val schemas: List<WorldSchemaObject> = emptyList(),
    val manifest: Map<String, PropertyValue> = emptyMap(),
    val sourceLocation: SourceLocation? = null,
) : ModelObject {
    override val kind: String = "storage"
}

/** A named `def schema` declared on a world storage (D-c world home). */
data class WorldSchemaObject(
    override val internalId: String,
    override val qname: QualifiedName,
    override val description: String = "",
    override val tags: List<String> = emptyList(),
    override val sourceFile: String = "",
    override val binding: Binding = Binding.BoundReal,
    val fields: Map<String, String> = emptyMap(),
    val sourceLocation: SourceLocation? = null,
) : ModelObject {
    override val kind: String = "worldSchema"
}
