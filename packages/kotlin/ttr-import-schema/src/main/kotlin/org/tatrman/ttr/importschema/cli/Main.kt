// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.tatrman.ttr.importschema.ImportSchemaRunner
import org.tatrman.ttr.importschema.conventions.ConventionsResolver
import org.tatrman.ttr.importschema.dbmodel.ImportSchemaException
import org.tatrman.ttr.importschema.introspect.Dialect
import org.tatrman.ttr.importschema.write.ModelPackageWriter
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties

/** The `ttr` command family. First (and, in 1.0, only) subcommand: `import-schema`. */
fun main(args: Array<String>) {
    TtrCommand()
        .subcommands(ImportSchemaCommand())
        .main(args)
}

class TtrCommand : CliktCommand(name = "ttr") {
    override fun help(context: Context) = "TTR toolchain — the standard's command-line front door."

    override fun run() = Unit
}

/**
 * `ttr import-schema` (SV-P4·S3) — introspect a live database and emit a TTR-M `db` mirror (S4
 * layers the `er` first cut + review checklist). Secrets never ride argv: the password is read
 * from the environment variable named by `--password-env`.
 *
 * Exit: 0 ok · 1 import error (e.g. TTRP-IMP-001 collision) · 2 usage / connection failure.
 */
class ImportSchemaCommand : CliktCommand(name = "import-schema") {
    override fun help(context: Context) = "Bootstrap a TTR-M model from an existing database."

    private val jdbcUrl by option("--jdbc-url", help = "JDBC connection URL").required()
    private val user by option("--user", help = "database user")
    private val passwordEnv by option(
        "--password-env",
        help = "env var holding the password (never pass secrets on argv)",
    )
    private val dialect by option("--dialect", help = "mssql | postgresql").required()
    private val packageName by option("--package", help = "TTR model package (never inferred — §12 rule 2)").required()
    private val out by option("--out", help = "output model-package dir").default(".")

    private val conventions by option(
        "--conventions",
        help = "conventions.yaml path (overrides the package file / profile)",
    )
    private val profile by option("--profile", help = "starter profile: mssql-default | czech-erp")
    private val assist by option(
        "--assist",
        help = "consult the F1-δ relation-assist seam (v1 proposes nothing; proposals still pass the probe gate)",
    ).flag()

    override fun run() {
        val d =
            try {
                Dialect.fromToken(dialect)
            } catch (e: IllegalArgumentException) {
                echo(e.message ?: "bad --dialect", err = true)
                throw ProgramResult(2)
            }

        val outDir = Path.of(out)
        val resolved =
            try {
                ConventionsResolver.resolve(
                    explicitPath = conventions?.let { Path.of(it) },
                    packageRoot = outDir,
                    profileName = profile,
                    dialect = d,
                )
            } catch (e: ImportSchemaException) {
                echo("ttr import-schema: ${e.message}", err = true)
                throw ProgramResult(1)
            }
        echo("conventions: ${resolved.source}")

        val props = Properties()
        user?.let { props.setProperty("user", it) }
        passwordEnv?.let { envName ->
            val secret = System.getenv(envName)
            if (secret == null) {
                echo("ttr import-schema: env var '$envName' (--password-env) is not set", err = true)
                throw ProgramResult(2)
            }
            props.setProperty("password", secret)
        }

        val result =
            try {
                DriverManager.getConnection(jdbcUrl, props).use { conn ->
                    ImportSchemaRunner(d, packageName, resolved.conventions, assist = assist).run(conn)
                }
            } catch (e: ImportSchemaException) {
                echo("ttr import-schema: ${e.message}", err = true)
                throw ProgramResult(1)
            } catch (e: java.sql.SQLException) {
                echo("ttr import-schema: database error: ${e.message}", err = true)
                throw ProgramResult(2)
            }

        val written = ModelPackageWriter.write(outDir, packageName, result)
        // First run materialises the chosen profile into the package (Q-1) so run two is pinned.
        resolved.materializeYaml?.let { yaml ->
            Files.writeString(outDir.resolve("conventions.yaml"), yaml)
            echo("materialised conventions.yaml from ${resolved.source}")
        }
        echo("wrote ${written.size} file(s) to $out")
        if (result.renames.isNotEmpty()) {
            echo("${result.renames.size} identifier(s) mangled (see the review checklist):")
            result.renames.forEach { echo("  ${it.qualifier}.${it.sourceName} → ${it.ttrName}") }
        }
    }
}
