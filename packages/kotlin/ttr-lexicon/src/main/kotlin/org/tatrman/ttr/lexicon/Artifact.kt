// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

//
// The COMPILED lexicon artifact (RV-P1.2; contracts §2) — model + codec only.
//
// This lives in `ttr-lexicon`, not in the compiler module, on purpose: the serving side
// (lex-matcher, the resolver — RO-13) READS this and must not drag `ttr-parser`,
// `ttr-metadata` or `ttr-snapshot` in to do it. The compiler that PRODUCES it is
// `ttr-lexicon-compile`.
//
// Kinds are not stored — an entry is an alias or a value by virtue of [TargetClass] (RV-38).
//

/** RV-38/RV-42 — what a `target_ref` points at. Derived at compile time; never authored. */
@Serializable
enum class TargetClass {
    /** An er/db/md model object or attribute. */
    MODEL_OBJECT,

    /** A member (value) of a model object — a VALUE entry. */
    MEMBER,

    /** An `op:` ref — an operator, from a skill file's frontmatter (RV-35). */
    OPERATOR,

    /** A `ground:` ref — a grounding trigger slice (RV-42). */
    GROUNDING_TRIGGER,
}

/**
 * Which layer produced a row. `DATA` and `LEARNED` are contract members of the same
 * enum on the wire (contracts §1) but are NOT compiled: DATA is the member index's own
 * refresh cadence and LEARNED is the estate overlay store. This artifact is exactly the
 * two build-time layers.
 */
@Serializable
enum class SourceTag {
    DECLARED,
    METADATA,
}

/** File + 1-based line the row was authored on. */
@Serializable
data class EntryProvenance(
    val file: String,
    val line: Int,
)

/**
 * One row of the uniform entry table. `lemma` is null until the NLP side lemmatizes at
 * build time — the column exists now so its absence is a value, not a schema change.
 */
@Serializable
data class CompiledEntry(
    val termNormalized: String,
    val lemma: String? = null,
    /** [Lang.wire] — `cs` · `en` · `cs|en`. */
    val lang: String,
    val targetRef: String,
    val targetClass: TargetClass,
    /**
     * [MatchMethod.wire] — `EXACT` · `TOKENS` · `TYPOS(n)`.
     *
     * Since RV-44 this is the *projection* of [matchProfile] for profile-carrying rows
     * ([MatchProfile.sugarMethod]). It is kept, not replaced: it is what a consumer that predates
     * profiles reads, and for the three sugar shapes the projection is exact.
     */
    val method: String,
    val sourceTag: SourceTag,
    val provenance: EntryProvenance,
    /**
     * RV-44 — the **resolved** matching profile, so no consumer re-derives sugar.
     *
     * Present on every DECLARED row (authored `match:`, or expanded from `method:`); **absent on
     * every METADATA row** — ⚑M-2 scopes profiles to the authoring surface, and the member index
     * and model labels keep engine scores. A reader must therefore treat absence as "score this
     * row the way you always did", which is exactly what makes an estate with no profiles
     * byte-identical to the pre-RV-44 service.
     *
     * The ⚑M-4 short-term guard is **not** applied here: the artifact records what the estate
     * authored, and the matcher is where a rule fails to fire (the build already warned).
     */
    val matchProfile: MatchProfile? = null,
)

/** Per-layer input fingerprints, so a rebuild can be attributed to the layer that moved. */
@Serializable
data class SourceHashes(
    val declared: String,
    val metadata: String,
)

@Serializable
data class CompiledLexiconHeader(
    val schemaVersion: String = SCHEMA_VERSION,
    /** The model snapshot every `targetRef` was validated against (RV-20). */
    val modelSnapshotHash: String,
    val sourceHashes: SourceHashes,
    /**
     * ISO-8601, **caller-supplied**. Never `Instant.now()` inside the compiler: two builds over
     * the same inputs must produce the same bytes (contracts §2 determinism, the same rule
     * `SnapshotWriter` lives by). A build passes the model snapshot's stamp or SOURCE_DATE_EPOCH.
     */
    val builtAt: String,
) {
    companion object {
        const val SCHEMA_VERSION: String = "ttr-lexicon-compiled/v1"
    }
}

/**
 * The compiled declared+metadata lexicon: one header, one flat entry table.
 *
 * [contentHash] deliberately covers the ENTRY TABLE ONLY. It is the RV-39 layer tuple's
 * `lexicon_artifact_hash`, and a version that changed because the clock moved would make the
 * tuple useless for "did the vocabulary change?" — which is the only question it is asked.
 */
@Serializable
data class CompiledLexicon(
    val header: CompiledLexiconHeader,
    val entries: List<CompiledEntry>,
) {
    val contentHash: String get() = sha256(CANONICAL.encodeToString(entries).toByteArray(Charsets.UTF_8))

    fun toJson(): String = PRETTY.encodeToString(this)

    companion object {
        /** Compact + key-stable: the bytes [contentHash] is taken over. */
        internal val CANONICAL =
            Json {
                prettyPrint = false
                encodeDefaults = true
                ignoreUnknownKeys = false
            }

        /** What actually lands in the archive — diffable in review. */
        internal val PRETTY =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
                encodeDefaults = true
                ignoreUnknownKeys = false
            }

        fun fromJson(text: String): CompiledLexicon = PRETTY.decodeFromString(text)
    }
}

/** One operator's behavior body, kept verbatim (RV-35 — the matcher never reads this). */
@Serializable
data class OperatorEntry(
    val body: String,
    val version: Int,
    /** `sha256:<hex>` over the body bytes, so a Golem can cache by identity. */
    val checksum: String,
    /** Which file won — estate overrides stdlib (T5). Provenance, not identity. */
    val source: EntryProvenance,
)

/**
 * The operator library — a SEPARATE artifact from the lexicon (RV-35): skill frontmatter
 * compiles into vocabulary, skill bodies never do. Keyed by `op:` id, insertion-ordered by
 * the producer in sorted key order so the JSON bytes are stable.
 */
@Serializable
data class OperatorLibrary(
    val schemaVersion: String = SCHEMA_VERSION,
    val operators: Map<String, OperatorEntry> = emptyMap(),
) {
    val contentHash: String get() = sha256(CompiledLexicon.CANONICAL.encodeToString(this).toByteArray(Charsets.UTF_8))

    fun toJson(): String = CompiledLexicon.PRETTY.encodeToString(this)

    companion object {
        const val SCHEMA_VERSION: String = "ttr-operator-library/v1"

        fun fromJson(text: String): OperatorLibrary = CompiledLexicon.PRETTY.decodeFromString(text)
    }
}

/** `sha256:<hex>`. Public: the compiler module hashes bodies and layer inputs with the same rule. */
fun sha256(bytes: ByteArray): String =
    "sha256:" +
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

/**
 * Term normalization — the one rule the matcher and the compiler must agree on.
 *
 * NFC, trimmed, internal whitespace collapsed, lowercased in the root locale. **Diacritics are
 * preserved**: Czech diacritics are contract-observable (CLAUDE.md), and folding them here would
 * silently make "vyroba" match "výroba" as EXACT, which is a TYPOS decision the author did not make.
 */
object TermNormalizer {
    private val WS = Regex("""\s+""")

    fun normalize(text: String): String =
        java.text.Normalizer
            .normalize(text, java.text.Normalizer.Form.NFC)
            .trim()
            .replace(WS, " ")
            .lowercase()
}

/**
 * The `kind: "lexicon"` archive's layout — the only thing a SERVING consumer needs to know.
 *
 * Ruled 2026-08-02 (a3): the compiled lexicon is its own archive, packed by the same
 * `SnapshotWriter` under the same determinism rules, with its own content id and a manifest
 * naming the model snapshot it was built against. Not entries inside the model archive — that
 * would break "loading over an archive == loading over the repo it was packed from".
 *
 * Reading it takes `ttr-snapshot` + this module and nothing else:
 * ```
 * val docs = SnapshotReader.read(bytes).contents.docs
 * val lexicon = CompiledLexicon.fromJson(docs.getValue(LexiconArchive.LEXICON))
 * ```
 */
object LexiconArchive {
    /** `SnapshotManifest.kind` for this archive. */
    const val KIND: String = "lexicon"

    /** Paths **relative to `docs/`**, which is the container's own prefix. */
    const val LEXICON: String = "lexicon.json"
    const val OPERATORS: String = "operator-library.json"

    /** `SnapshotManifest.resolvedFrom` key naming the model snapshot the refs were checked against. */
    const val RESOLVED_FROM_MODEL: String = "modelSnapshotId"
}
