// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.securitygen.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.SecurityBlock
import org.tatrman.ttr.securitygen.SecurityGen
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/** The `ttr` command family. This module contributes the `security-gen` subcommand. */
fun main(args: Array<String>) {
    TtrCommand()
        .subcommands(SecurityGenCommand())
        .main(args)
}

class TtrCommand : CliktCommand(name = "ttr") {
    override fun help(context: Context) = "TTR toolchain — the standard's command-line front door."

    override fun run() = Unit
}

/**
 * `ttr security-gen <model-repo> --out <dir>` (PL-P4.S3) — parse every `.ttrm`
 * under the model repo, collect its `security { }` blocks, and emit the
 * deterministic Rego fragments + `data.json` under `--out`. Perun's build pipeline
 * (S4) calls the SAME [SecurityGen] core in-process — one generator, two callers.
 *
 * Exit: 0 ok · 1 model/generation error (parse error, qname collision) · 2 usage.
 */
class SecurityGenCommand : CliktCommand(name = "security-gen") {
    override fun help(context: Context) = "Generate OPA/Rego policy fragments from TTR-M `security { }` blocks."

    private val modelRepo by argument("model-repo", help = "root dir of the TTR-M model repo (scanned for *.ttrm)")
    private val out by option("--out", help = "output dir for the generated Rego fragments + data.json").default(".")

    override fun run() {
        val repo = Path.of(modelRepo)
        if (!Files.isDirectory(repo)) {
            echo("ttr security-gen: '$modelRepo' is not a directory", err = true)
            throw ProgramResult(2)
        }

        val ttrmFiles =
            Files.walk(repo).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.extension == "ttrm" }.sorted().toList()
            }

        val blocks = mutableListOf<SecurityBlock>()
        for (file in ttrmFiles) {
            val result = TtrLoader.parseFile(file)
            if (!result.ok) {
                echo("ttr security-gen: parse error in ${repo.relativize(file)}:", err = true)
                result.errors.forEach { echo("  $it", err = true) }
                throw ProgramResult(1)
            }
            blocks += result.securityBlocks
        }

        val policy =
            try {
                SecurityGen.generate(blocks)
            } catch (e: IllegalStateException) {
                echo("ttr security-gen: ${e.message}", err = true)
                throw ProgramResult(1)
            }

        val outDir = Path.of(out)
        Files.createDirectories(outDir)
        val files = policy.files()
        files.forEach { (name, content) -> Files.writeString(outDir.resolve(name), content) }
        echo("wrote ${files.size} file(s) to $out (${policy.regoFiles.size} rego fragment(s) + data.json)")
        if (blocks.isEmpty()) echo("note: no `security { }` blocks found under $modelRepo")
    }
}
