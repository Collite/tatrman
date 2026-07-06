package org.tatrman.ttrp.cli

import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.project.TtrpManifestReader
import org.tatrman.ttrp.resolve.TtrpChecker
import java.nio.file.Files
import java.nio.file.Path

/**
 * The thin `ttrp check <file>.ttrp` front-half dispatch (S2 — the full build/run/
 * explain/conform CLI, and any framework, land in P3). Hand-rolled `args[0]`
 * dispatch. Pipeline: manifest walk-up → parse → resolve → er-rewrite →
 * schema/world checks → print `FILE:LINE:COL <ID> <message>` (+ suggested); exit 0/1.
 */
fun main(args: Array<String>) {
    val code = TtrpCli.dispatch(args, System.out::println)
    kotlin.system.exitProcess(code)
}

object TtrpCli {
    /** Component-test entry point: no process spawn — call this directly (T1.3.7). */
    fun dispatch(
        args: Array<String>,
        out: (String) -> Unit,
    ): Int {
        if (args.isEmpty() || args[0] !in setOf("check", "explain")) {
            out("usage: ttrp <check|explain> <file>.ttrp")
            return 2
        }
        if (args.size < 2) {
            out("ttrp ${args[0]}: missing <file>.ttrp")
            return 2
        }
        return when (args[0]) {
            "explain" -> runExplain(Path.of(args[1]), out)
            else -> runCheck(Path.of(args[1]), out)
        }
    }

    /**
     * `ttrp explain <file>.ttrp` (S2/S4): front-half → build → normalize → collapse →
     * render the island / wave / movement structure to stdout. Exit 0 on success,
     * 1 on error diagnostics, 2 on a missing file.
     */
    fun runExplain(
        file: Path,
        out: (String) -> Unit,
        modelsRootOverride: Path? = null,
    ): Int {
        val abs = file.toAbsolutePath()
        if (!Files.isRegularFile(abs)) {
            out("ttrp explain: no such file: $file")
            return 2
        }
        val startDir = abs.parent ?: abs
        val manifestResult = TtrpManifestReader.resolve(startDir)
        val modelsRoot = modelsRootOverride ?: manifestResult.manifest.modelsRoot()
        val output = TtrpPipeline(manifestResult.manifest, modelsRoot).explain(Files.readString(abs), abs.toString())
        if (!output.ok) {
            for (d in output.diagnostics
                .filter { it.severity == Severity.ERROR }
                .sortedWith(compareBy({ it.location.line }, { it.location.column }))) {
                out(d.render())
            }
            return 1
        }
        out(output.text.trimEnd())
        return 0
    }

    /**
     * Checks one `.ttrp` file. [modelsRootOverride] lets component tests point at the
     * shared fixture models without a project-relative `models/` symlink.
     */
    fun runCheck(
        file: Path,
        out: (String) -> Unit,
        modelsRootOverride: Path? = null,
    ): Int {
        val abs = file.toAbsolutePath()
        if (!Files.isRegularFile(abs)) {
            out("ttrp check: no such file: $file")
            return 2
        }
        val startDir = abs.parent ?: abs
        val manifestResult = TtrpManifestReader.resolve(startDir)
        val modelsRoot = modelsRootOverride ?: manifestResult.manifest.modelsRoot()
        val checker = TtrpChecker(manifestResult.manifest, modelsRoot)
        val report =
            checker.check(
                source = Files.readString(abs),
                fileName = abs.toString(),
                manifestDiagnostics = manifestResult.diagnostics,
            )
        for (d in report.diagnostics.sortedWith(compareBy({ it.location.line }, { it.location.column }))) {
            out(d.render())
            d.suggestedAlternative?.let { out("  ↳ suggested: $it") }
        }
        return if (report.errors.any { it.severity == Severity.ERROR }) 1 else 0
    }
}
