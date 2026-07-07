package org.tatrman.ttrp.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import org.tatrman.ttrp.bundle.BundleAssembler
import org.tatrman.ttrp.bundle.PlacementVariants
import org.tatrman.ttrp.conform.BundleInvoker
import org.tatrman.ttrp.conform.ConformRunner
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.project.TtrpManifestReader
import org.tatrman.ttrp.resolve.TtrpChecker
import java.nio.file.Files
import java.nio.file.Path

/** The `ttrp` command (S2): `build` / `run` / `explain` / `conform` (+ the legacy `check`). */
fun main(args: Array<String>) {
    TtrpCommand()
        .subcommands(BuildCommand(), RunCommand(), ExplainCommand(), CheckCommand(), ConformCommand())
        .main(args)
}

class TtrpCommand : CliktCommand(name = "ttrp") {
    override fun help(context: com.github.ajalt.clikt.core.Context) =
        "TTR-P toolchain: compile, bundle, run, and conform .ttrp programs."

    override fun run() = Unit
}

/** `ttrp build <file>.ttrp [--out <dir>]` → assemble the bundle; exit ≠ 0 on error diagnostics. */
class BuildCommand : CliktCommand(name = "build") {
    private val file by argument()
    private val out by option("--out").default(".")

    override fun run() {
        val abs = Path.of(file).toAbsolutePath()
        if (!Files.isRegularFile(abs)) {
            echo("ttrp build: no such file: $file", err = true)
            throw ProgramResult(2)
        }
        val manifestResult = TtrpManifestReader.resolve(abs.parent ?: abs)
        val result =
            try {
                BundleAssembler().build(
                    source = Files.readString(abs),
                    fileName = abs.toString(),
                    pipelineManifest = manifestResult.manifest,
                    modelsRoot = manifestResult.manifest.modelsRoot(),
                    outDir = Path.of(out),
                )
            } catch (e: IllegalArgumentException) {
                echo(e.message ?: "build failed", err = true)
                throw ProgramResult(1)
            }
        echo(result.dir.toString())
    }
}

/** `ttrp run <file>.ttrp | <bundle>` → build if source, then `bash run.sh`, propagating exit 0/1/2. */
class RunCommand : CliktCommand(name = "run") {
    private val target by argument()

    override fun run() {
        val p = Path.of(target).toAbsolutePath()
        val bundleDir =
            when {
                Files.isDirectory(p) && Files.exists(p.resolve("run.sh")) -> p
                Files.isRegularFile(p) && p.toString().endsWith(".ttrp") -> {
                    val manifestResult = TtrpManifestReader.resolve(p.parent ?: p)
                    BundleAssembler()
                        .build(
                            Files.readString(p),
                            p.toString(),
                            manifestResult.manifest,
                            manifestResult.manifest.modelsRoot(),
                            p.parent ?: Path.of("."),
                        ).dir
                }
                else -> {
                    echo("ttrp run: expected a .ttrp file or a <program>.bundle dir", err = true)
                    throw ProgramResult(2)
                }
            }
        val proc =
            ProcessBuilder("bash", "run.sh")
                .directory(bundleDir.toFile())
                .inheritIO()
                .start()
        throw ProgramResult(proc.waitFor())
    }
}

/** `ttrp explain <file>.ttrp` — delegates to the Stage 2.3 explain rendering (S4). */
class ExplainCommand : CliktCommand(name = "explain") {
    private val file by argument()

    override fun run(): Unit = throw ProgramResult(TtrpCli.runExplain(Path.of(file), ::echo))
}

/** `ttrp check <file>.ttrp` — the front-half checker (P1). */
class CheckCommand : CliktCommand(name = "check") {
    private val file by argument()

    override fun run(): Unit = throw ProgramResult(TtrpCli.runCheck(Path.of(file), ::echo))
}

/**
 * `ttrp conform <file>.ttrp [--tolerance <col>=<eps>...]` — builds the placement variants, runs each
 * bundle's `run.sh`, and compares `out/` displays under the Q9 seven-point procedure. Exits 0 all-
 * pass · 1 comparison failure · 2 invocation/pre-flight failure (mirrors the run contract).
 */
class ConformCommand : CliktCommand(name = "conform") {
    private val file by argument()
    private val tolerance by option("--tolerance", help = "per-column float64 tolerance, <col>=<eps>").multiple()

    override fun run() {
        val abs = Path.of(file).toAbsolutePath()
        if (!Files.isRegularFile(abs)) {
            echo("ttrp conform: no such file: $file", err = true)
            throw ProgramResult(2)
        }
        val manifestResult = TtrpManifestReader.resolve(abs.parent ?: abs)
        val outDir = Files.createTempDirectory("ttrp-conform")
        val variants =
            PlacementVariants.build(
                Files.readString(abs),
                abs.toString(),
                manifestResult.manifest,
                manifestResult.manifest.modelsRoot(),
                outDir,
            )
        val tolerances =
            tolerance
                .mapNotNull { spec ->
                    val (c, e) = spec.split("=", limit = 2).let { it.getOrNull(0) to it.getOrNull(1) }
                    if (c != null && e?.toDoubleOrNull() != null) c to e.toDouble() else null
                }.toMap()
        val env = System.getenv().filterKeys { it.startsWith("TTR_CONN_") }
        val outcome = ConformRunner(BundleInvoker(env), tolerances = tolerances).run(variants)
        echo(outcome.summary())
        throw ProgramResult(outcome.exitCode)
    }
}

/**
 * Component-test entry point (no process spawn / no clikt): the front-half + explain dispatch used
 * by the Phase-1/2 CLI specs. The clikt commands above delegate here for explain/check.
 */
object TtrpCli {
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
