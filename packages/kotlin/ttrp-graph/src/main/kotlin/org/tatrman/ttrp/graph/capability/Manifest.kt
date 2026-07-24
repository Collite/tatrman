// SPDX-License-Identifier: Apache-2.0
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
    /**
     * Rejects producer capability (RJ-P2, contracts §3; `manifestVersion: 2`). Absent on a
     * version-1 manifest ⇒ the engine declares no rejects support: [rejectsSupport] returns the
     * empty (`produces = false`) model, so an un-bumped manifest is byte-for-byte backward
     * compatible and a rejects-wired cluster placed on it is a §4 capability miss.
     */
    val rejects: RejectsSupport? = null,
    /**
     * Execution engines (PL-P2.S1, F-4): the runtime-param / on-failure / retries vocabulary this
     * executor supports (contracts §7). Absent (bash, F-lite) ⇒ none supported — a program using
     * `param`s, `on failure of`, or `retries` against this executor is a T6 capability miss
     * (TTRP-CAP-2xx). The tatrman platform executor declares them true.
     */
    val executor: ExecutorCapability? = null,
) {
    /** The rejects model, normalizing an absent (v1) section to the empty `produces = false` model. */
    fun rejectsSupport(): RejectsSupport = rejects ?: RejectsSupport.NONE

    /** The F-4 executor capabilities, normalizing an absent section to the empty (all-false) bash model. */
    fun executorCapability(): ExecutorCapability = executor ?: ExecutorCapability()
}

/**
 * An execution engine's F-4 capability vocabulary (PL-P2.S1, contracts §7). All default false/empty
 * so an un-declared (bash) executor supports none; the tatrman platform executor sets them.
 * `absorbs`/`resume`/`events` stay out of the S1 gate (reserved / platform-consumed later).
 */
@Serializable
data class ExecutorCapability(
    /** F-4-i: runtime params are supported. */
    val params: Boolean = false,
    /** F-4-i: the scalar param types this executor accepts (`string`/`int`/`decimal`/`date`/`datetime`/`bool`). */
    val paramTypes: List<String> = emptyList(),
    /** F-4-i: the trigger-time builtins this executor provides (`run-date`). */
    val builtins: List<String> = emptyList(),
    /** F-4-iv: on-failure islands are supported. */
    val onFailure: Boolean = false,
    /** F-4-ii: per-island retries are supported. */
    val retries: Boolean = false,
)

/**
 * An engine's rejects producer capability (contracts §3). [produces] is the engine-level gate
 * (can this engine host a rejects output stream at all); [entries] refine per (function, type-pair)
 * whether a `nativeForm` is emit-usable — a native form is honored **only** when its [RejectsEntry.domain]
 * is [RejectDomain.CANONICAL] (proof = the RJ-P0 corpus). A missing entry ≡ `{nativeForm: null,
 * domain: unknown}` ⇒ the emitter lowers the canonical guard from the §2 validity spec.
 */
@Serializable
data class RejectsSupport(
    val produces: Boolean = false,
    val entries: List<RejectsEntry> = emptyList(),
) {
    fun entry(
        function: String,
        typePair: String,
    ): RejectsEntry? = entries.firstOrNull { it.function == function && it.typePair == typePair }

    companion object {
        val NONE = RejectsSupport(produces = false, entries = emptyList())
    }
}

/** One (function, type-pair) rejects entry (contracts §3). */
@Serializable
data class RejectsEntry(
    /** Catalogue id of the reject-capable function (`cast`, `op.div`, `fn.to_date`, …). */
    val function: String,
    /** The validity type-pair this entry covers (`text->int64`, `numeric,numeric->numeric`, …). */
    val typePair: String,
    /** Native validity oracle usable in emit ONLY when [domain] is canonical; null ⇒ canonical guard. */
    val nativeForm: String? = null,
    /** The engine's native acceptance relative to the canonical domain (contracts §3 / RJ-P0 verdicts). */
    val domain: RejectDomain,
    /** Minimum engine major version at which [nativeForm] holds (documentary while nativeForm is null). */
    val minVersion: Int? = null,
    /** RJ-P0 provenance for this verdict (the spike-report line that proved the domain relation). */
    val evidence: String? = null,
)

/**
 * The engine-vs-canonical acceptance relation (contracts §3). `canonical` = identical (the ONLY
 * value that unlocks [RejectsEntry.nativeForm]); `wider`/`narrower`/`divergent` = the engine's
 * native cast diverges from canonical ⇒ guard from the §2 spec; `unknown` = not yet measured.
 *
 * `divergent` is an RJ-P0-spike addition to contracts §3's `{canonical, wider, narrower, unknown}`
 * vocabulary (both PG and Polars `text->float64` are incomparable to canonical); it behaves exactly
 * like wider/narrower/unknown (guard emit), so no emit path changed. Recorded in the design log.
 */
enum class RejectDomain {
    @SerialName("canonical")
    CANONICAL,

    @SerialName("wider")
    WIDER,

    @SerialName("narrower")
    NARROWER,

    @SerialName("divergent")
    DIVERGENT,

    @SerialName("unknown")
    UNKNOWN,
}

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
