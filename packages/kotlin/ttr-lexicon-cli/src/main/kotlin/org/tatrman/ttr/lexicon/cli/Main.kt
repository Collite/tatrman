// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import java.nio.file.Path
import kotlin.system.exitProcess

/** The `ttr-lexicon` command: today, `build`. */
fun main(args: Array<String>) {
    LexiconCommand().subcommands(BuildCommand()).main(args)
}

class LexiconCommand : CliktCommand(name = "ttr-lexicon") {
    override fun help(context: Context) = "Build an estate's compiled lexicon archive."

    override fun run() = Unit
}

/**
 * `ttr-lexicon build <repoRoot> --out <path> [--check] [--no-stdlib] [--verbose]`.
 *
 * A thin shell: every decision lives in [LexiconBuildCli.run], so the behaviour is tested without
 * spawning a process and this class stays a flag map.
 */
class BuildCommand : CliktCommand(name = "build") {
    override fun help(context: Context) =
        "Compile the declared + metadata lexicon layers of a repo into its deterministic archive."

    private val repoRoot by argument(help = "Root of the estate repo (the directory holding model/ and lexicon/)")

    private val out by option("--out", help = "Where to write the archive").required()

    private val check by option(
        "--check",
        help = "Compare the archive already at --out against a fresh compile; exit non-zero on drift, write nothing",
    ).flag()

    private val noStdlib by option(
        "--no-stdlib",
        help = "Leave out the shipped operator + grounding stdlib; build only this repo's own vocabulary",
    ).flag()

    private val verbose by option("--verbose", help = "Print the model id and the output path to stderr").flag()

    private val builtAt by option(
        "--built-at",
        help =
            "ISO-8601 build stamp. Defaults to SOURCE_DATE_EPOCH, else the epoch — NOT the clock, so the " +
                "archive id stays a function of the sources and --check means something",
    ).default(LexiconBuildCli.defaultBuiltAt())

    private val producedBy by option(
        "--produced-by",
        help = "Producer string recorded in the archive manifest",
    ).default(LexiconBuildCli.DEFAULT_PRODUCED_BY)

    override fun run() {
        val outcome =
            LexiconBuildCli.run(
                repoRoot = Path.of(repoRoot),
                out = Path.of(out),
                check = check,
                includeStdlib = !noStdlib,
                verbose = verbose,
                builtAt = builtAt,
                producedBy = producedBy,
            )

        // Diagnostics on stderr, the machine-readable summary on stdout — so an estate's justfile
        // can capture the id without also capturing the warning stream.
        if (outcome.stdout.isNotEmpty()) print(outcome.stdout)
        if (outcome.stderr.isNotEmpty()) System.err.print(outcome.stderr)
        exitProcess(outcome.exitCode)
    }
}
