package org.tatrman.ttrp.graph.capability

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The engine-**type** capability manifest (T6 β: parameterized declarative entries).
 * Shipped with the compiler as JSON under `resources/ttrp/manifests/` (a reviewable
 * choice — contracts leaves the type-manifest format open, T6-c). Manifests are FACTS
 * about an engine; rewrite rules are compiler knowledge (T6-d α) — the normalizer
 * (Stage 2.3) joins them.
 */
@Serializable
data class EngineTypeManifest(
    val manifestVersion: Int = 1,
    /** Shipped id (`postgres-16`, `polars`, `bash`) — matched against a world engine's `extends`. */
    val id: String,
    val kind: ManifestKind,
    /** Engine type token (`postgres`/`polars`/`bash`) — matched against a resolved engine's `type`. */
    val type: String? = null,
    /** Major version this data manifest targets (e.g. 16 for postgres-16), for type-based matching. */
    val versionMajor: Int? = null,
    val languageDetails: LanguageDetails? = null,
    /** Per-node-kind capability (β entry); absence ⇒ the kind is not native on this engine. */
    val nodes: Map<String, NodeCapability> = emptyMap(),
    /** Catalogue ids (T5-c) the engine supports natively. */
    val functions: List<String> = emptyList(),
    /** Execution engines: the control vocabulary supported (FS/SS). */
    val controls: List<String> = emptyList(),
    /** Execution engines: NONE | WAVE (F-a β). */
    val parallelism: String? = null,
    /** Execution engines: per-(data-engine, channel) invocation bindings. */
    val invocations: List<Invocation> = emptyList(),
)

enum class ManifestKind {
    @SerialName("DATA")
    DATA,

    @SerialName("EXECUTION")
    EXECUTION,

    @SerialName("STORAGE_TYPE")
    STORAGE_TYPE,
}

@Serializable
data class LanguageDetails(
    val dialect: String,
    val identifierNormalization: String? = null,
)

/**
 * The β parameterized capability entry — one small constraint vocabulary per node kind
 * where support varies (B-T6): `Join{types}`, `Aggregate{functions, distinct}`,
 * `Union{all, distinct}`, `Pivot{native}`. All other kinds = the empty entry (α as a
 * degenerate β). NO predicate escape hatch in v1 (design headroom, not a feature).
 */
@Serializable
data class NodeCapability(
    /** Join: supported join types (inner/left/right/full/semi/anti/cross). */
    val types: List<String>? = null,
    /** Aggregate: supported aggregate-function catalogue ids. */
    val functions: List<String>? = null,
    /** Aggregate/Union: DISTINCT support. */
    val distinct: Boolean? = null,
    /** Union: UNION ALL support. */
    val all: Boolean? = null,
    /** Pivot: native pivot support (else CASE lowering). */
    val native: Boolean? = null,
)

/**
 * An execution-engine invocation capability (B-T6): how a data island is delivered
 * (pg via `psql`, polars via `python3`, display via file-drop).
 */
@Serializable
data class Invocation(
    val targetEngineType: String,
    val delivery: String,
    val command: String? = null,
    val interpreter: String? = null,
    val packages: Map<String, String>? = null,
)
