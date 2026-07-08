package org.tatrman.ttrp.conform.eval

import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * Scores host-produced candidates against the eval corpus (C4-e, deterministic half). The
 * runner NEVER compiles by itself — a [compile] function is injected (the CLI wires the real
 * front-half; specs wire an in-test compiler / stub), keeping the harness engine-free and the
 * "no LLM in the toolchain" boundary (P2) intact. Verdict per entry: pass | shape-mismatch |
 * invalid (with the named diagnostics — the repair vocabulary round-trips).
 */
class EvalRunner(
    private val compile: (source: String, fileName: String) -> CompileOutcome,
) {
    /** The front-half outcome for one source: a built graph, or the ERROR diagnostics that stopped it. */
    data class CompileOutcome(
        val graph: TtrpGraph?,
        val diagnostics: List<String>,
    )

    data class EntryVerdict(
        val id: String,
        val verdict: EvalComparator.Verdict,
    )

    data class Report(
        val entries: List<EntryVerdict>,
    ) {
        val pass: Int get() = entries.count { it.verdict is EvalComparator.Verdict.Pass }
        val shapeMismatch: Int get() = entries.count { it.verdict is EvalComparator.Verdict.ShapeMismatch }
        val invalid: Int get() = entries.count { it.verdict is EvalComparator.Verdict.Invalid }
    }

    /** [candidates]: corpus-id → candidate source (one host-produced file per entry). */
    fun score(
        corpus: EvalCorpus,
        candidates: Map<String, String>,
    ): Report {
        val verdicts =
            corpus.entries.map { entry ->
                EntryVerdict(entry.id, verdictFor(corpus, entry, candidates[entry.id]))
            }
        return Report(verdicts)
    }

    private fun verdictFor(
        corpus: EvalCorpus,
        entry: EvalEntry,
        candidateSource: String?,
    ): EvalComparator.Verdict {
        if (candidateSource == null) {
            return EvalComparator.Verdict.Invalid(listOf("no candidate for ${entry.id}"))
        }
        val candidate = compile(candidateSource, "${entry.id}.ttrp")
        if (candidate.graph == null) return EvalComparator.Verdict.Invalid(candidate.diagnostics)
        val expected = compile(corpus.expectedSource(entry), entry.expected)
        val expectedGraph =
            expected.graph
                ?: return EvalComparator.Verdict.Invalid(
                    listOf("expected fixture ${entry.expected} did not compile: ${expected.diagnostics}"),
                )
        return EvalComparator.compare(candidate.graph, expectedGraph, entry.tolerance)
    }
}
