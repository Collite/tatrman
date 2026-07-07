package org.tatrman.ttrp.lsp.analysis

import org.tatrman.ttrp.lsp.docs.OpenDocument
import org.tatrman.ttrp.lsp.project.ProjectResolver
import org.tatrman.ttrp.resolve.TtrpChecker
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** A resolved front-half analysis of one document version. */
data class Analysis(
    val uri: String,
    val version: Int,
    val report: TtrpChecker.Report,
)

/**
 * Runs the Phase-1 front-half (`TtrpChecker`) for a document and caches the result by
 * document version. Deterministic: the same text always yields the same report (P2 —
 * no shortcut caching that skips re-resolution; the cache is keyed on version, and a
 * new version always re-runs). Feature services (hover/definition/rename) and the
 * diagnostics scheduler share one engine so a hover never re-parses what a just-run
 * diagnostics pass already computed.
 */
class AnalysisEngine(
    private val projects: ProjectResolver,
) {
    private val analysisCache = ConcurrentHashMap<String, Analysis>()
    private val checkerCache = ConcurrentHashMap<String, TtrpChecker>()

    fun analyze(doc: OpenDocument): Analysis {
        analysisCache[doc.uri]?.let { if (it.version == doc.version) return it }
        val ctx = projects.resolve(doc.uri)
        val checker = checkerFor(ctx.modelsRoot) { TtrpChecker(ctx.manifest, ctx.modelsRoot) }
        val report = checker.check(doc.text, doc.uri, ctx.manifestDiagnostics)
        val analysis = Analysis(doc.uri, doc.version, report)
        analysisCache[doc.uri] = analysis
        return analysis
    }

    fun evict(uri: String) {
        analysisCache.remove(uri)
    }

    // The model repo is loaded once per models root (an editor session touches one
    // project); the front-half itself never caches resolution.
    private fun checkerFor(
        modelsRoot: Path,
        build: () -> TtrpChecker,
    ): TtrpChecker = checkerCache.getOrPut(modelsRoot.toString(), build)
}
