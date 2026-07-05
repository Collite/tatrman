package org.tatrman.ttrp.cli

import org.tatrman.ttrp.diagnostics.Severity
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
        if (args.isEmpty() || args[0] != "check") {
            out("usage: ttrp check <file>.ttrp")
            return 2
        }
        if (args.size < 2) {
            out("ttrp check: missing <file>.ttrp")
            return 2
        }
        return runCheck(Path.of(args[1]), out)
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
