// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import org.tatrman.ttr.lexicon.CompiledEntry
import org.tatrman.ttr.lexicon.CompiledLexicon
import org.tatrman.ttr.lexicon.CompiledLexiconHeader
import org.tatrman.ttr.lexicon.EntryProvenance
import org.tatrman.ttr.lexicon.MatchMethod
import org.tatrman.ttr.lexicon.OperatorEntry
import org.tatrman.ttr.lexicon.OperatorLibrary
import org.tatrman.ttr.lexicon.SourceHashes
import org.tatrman.ttr.lexicon.SourceTag
import org.tatrman.ttr.lexicon.TargetClass
import org.tatrman.ttr.lexicon.TermNormalizer
import org.tatrman.ttr.lexicon.sha256

/** What one compile produced: the two artifacts plus everything the build should say out loud. */
data class CompileResult(
    val lexicon: CompiledLexicon,
    val operators: OperatorLibrary,
    val warnings: List<CompileWarning>,
)

/**
 * RV-P1.2 — declared (lexicon area + TTR-M sugar) + metadata → the compiled lexicon and the
 * operator library (contracts §2).
 *
 * Determinism is by construction, not by convention: nothing here reads a clock, a locale, a
 * file system or a hash-map iteration order. `builtAt` is a parameter for exactly that reason —
 * see [CompiledLexiconHeader.builtAt].
 */
object LexiconCompiler {
    /** `op:` / `ground:` refs are classified by their prefix; nothing else is. */
    private const val OP_PREFIX = "op:"
    private const val GROUND_PREFIX = "ground:"

    fun compile(
        sources: LexiconSources,
        refs: ModelRefIndex,
        modelSnapshotHash: String,
        builtAt: String,
    ): CompileResult {
        val declared = AreaExtractor.rows(sources.area) + TtrmSugarExtractor.rows(sources.ttrm)
        val metadata = sources.model?.let { MetadataExtractor.rows(it) } ?: emptyList()

        val warnings = mutableListOf<CompileWarning>()
        val classified = (declared + metadata).mapNotNull { classify(it, refs, warnings) }

        val entries = merge(classified, warnings)

        val lexicon =
            CompiledLexicon(
                header =
                    CompiledLexiconHeader(
                        modelSnapshotHash = modelSnapshotHash,
                        sourceHashes =
                            SourceHashes(
                                declared = layerHash(declared),
                                metadata = layerHash(metadata),
                            ),
                        builtAt = builtAt,
                    ),
                entries = entries,
            )

        return CompileResult(lexicon, operatorLibrary(sources, warnings), warnings.sortedWith(WARNING_ORDER))
    }

    /**
     * RV-38/RV-42 — the target's class. `op:`/`ground:` refs resolve by prefix and never touch the
     * index: they are not model objects, so consulting a model snapshot for them would make every
     * operator and every grounding trigger dangle against a snapshot that will never contain it.
     *
     * Anything else must be in the snapshot. Absent ⇒ RV-20: drop the row, warn with the line.
     */
    private fun classify(
        row: SourceRow,
        refs: ModelRefIndex,
        warnings: MutableList<CompileWarning>,
    ): Pair<SourceRow, TargetClass>? {
        val cls =
            when {
                row.targetRef.startsWith(OP_PREFIX) -> TargetClass.OPERATOR
                row.targetRef.startsWith(GROUND_PREFIX) -> TargetClass.GROUNDING_TRIGGER
                else -> refs.classify(row.targetRef)
            }
        if (cls == null) {
            warnings +=
                CompileWarning(
                    code = CompileWarning.DANGLING_REF,
                    message =
                        "term \"${row.text}\" targets `${row.targetRef}`, which is not in the model " +
                            "snapshot — entry dropped",
                    provenance = row.provenance,
                )
            return null
        }
        return row to cls
    }

    /**
     * Collapse to one row per (normalized term, lang, target) and order the table.
     *
     * The identity deliberately excludes `method`: one term pointing at one target with two
     * different match methods is contradictory authoring, not two entries. Precedence is
     * **DECLARED over METADATA** (an author's file states an intent; a model label is a byproduct),
     * then the **widest method** — dropping the wider one would lose matches somebody asked for.
     */
    private fun merge(
        rows: List<Pair<SourceRow, TargetClass>>,
        warnings: MutableList<CompileWarning>,
    ): List<CompiledEntry> {
        val byIdentity = LinkedHashMap<Triple<String, String, String>, MutableList<Pair<SourceRow, TargetClass>>>()
        for ((row, cls) in rows) {
            val key = Triple(TermNormalizer.normalize(row.text), row.lang.wire, row.targetRef)
            byIdentity.getOrPut(key) { mutableListOf() } += row to cls
        }

        val out = mutableListOf<CompiledEntry>()
        for ((key, group) in byIdentity) {
            val winner = group.sortedWith(PRECEDENCE).first()
            val loserWithOtherMethod = group.firstOrNull { it.first.method != winner.first.method }
            if (loserWithOtherMethod != null) {
                warnings +=
                    CompileWarning(
                        code = CompileWarning.METHOD_CONFLICT,
                        message =
                            "term \"${key.first}\" → `${key.third}` is declared with both " +
                                "${winner.first.method.wire} and ${loserWithOtherMethod.first.method.wire}; " +
                                "kept ${winner.first.method.wire}",
                        provenance = loserWithOtherMethod.first.provenance,
                    )
            }
            out +=
                CompiledEntry(
                    termNormalized = key.first,
                    lemma = null,
                    lang = key.second,
                    targetRef = key.third,
                    targetClass = winner.second,
                    method = winner.first.method.wire,
                    sourceTag = winner.first.sourceTag,
                    provenance = winner.first.provenance,
                )
        }
        return out.sortedWith(ENTRY_ORDER)
    }

    /**
     * Skill bodies, keyed by `op:` id — a separate artifact from the lexicon (RV-35).
     *
     * **Estate overrides stdlib** (T5): when two files declare the same op id, the later one in
     * the caller's skill list wins and the build says so. Ordering the stdlib first and the estate
     * second is the loader's job, not a rule this can infer from a file path.
     */
    private fun operatorLibrary(
        sources: LexiconSources,
        warnings: MutableList<CompileWarning>,
    ): OperatorLibrary {
        val byId = LinkedHashMap<String, OperatorEntry>()
        for (skill in sources.area.skills) {
            val existing = byId[skill.opId]
            if (existing != null) {
                warnings +=
                    CompileWarning(
                        code = CompileWarning.OPERATOR_OVERRIDE,
                        message =
                            "`${skill.opId}` is defined in ${existing.source.file} and overridden by " +
                                "${skill.provenance.file}",
                        provenance = EntryProvenance(skill.provenance.file, skill.provenance.line),
                    )
            }
            byId[skill.opId] =
                OperatorEntry(
                    body = skill.body,
                    version = skill.version,
                    checksum = sha256(skill.body.toByteArray(Charsets.UTF_8)),
                    source = EntryProvenance(skill.provenance.file, skill.provenance.line),
                )
        }
        // Sorted keys: the JSON object's key order is part of the artifact's bytes.
        return OperatorLibrary(operators = byId.toSortedMap())
    }

    /** Per-layer input fingerprint, over the layer's rows in a stable order. */
    private fun layerHash(rows: List<SourceRow>): String =
        sha256(
            rows
                .map {
                    listOf(
                        TermNormalizer.normalize(it.text),
                        it.lang.wire,
                        it.method.wire,
                        it.targetRef,
                        it.provenance.file,
                        it.provenance.line.toString(),
                    ).joinToString(" ")
                }.sorted()
                .joinToString("\n")
                .toByteArray(Charsets.UTF_8),
        )

    /** DECLARED first, then widest method, then the earliest provenance — total and stable. */
    private val PRECEDENCE =
        compareBy<Pair<SourceRow, TargetClass>>(
            { if (it.first.sourceTag == SourceTag.DECLARED) 0 else 1 },
            { -breadth(it.first.method) },
            { it.first.provenance.file },
            { it.first.provenance.line },
        )

    /** How much a method admits. Wider wins a conflict; the ranking is the artifact's, not the matcher's. */
    private fun breadth(method: MatchMethod): Int =
        when (method) {
            is MatchMethod.Typos -> 10 + method.maxDistance
            MatchMethod.Tokens -> 5
            MatchMethod.Exact -> 0
        }

    private val ENTRY_ORDER =
        compareBy<CompiledEntry>({ it.termNormalized }, { it.lang }, { it.targetRef }, { it.method })

    private val WARNING_ORDER =
        compareBy<CompileWarning>({ it.provenance.file }, { it.provenance.line }, { it.code }, { it.message })
}
