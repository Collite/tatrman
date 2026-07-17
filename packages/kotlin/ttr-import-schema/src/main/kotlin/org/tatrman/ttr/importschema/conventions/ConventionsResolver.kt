// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.conventions

import org.tatrman.ttr.importschema.dbmodel.ImportSchemaException
import org.tatrman.ttr.importschema.introspect.Dialect
import java.nio.file.Files
import java.nio.file.Path

/**
 * Conventions resolution + the packaged starter profiles (Q-1 lookup order):
 *   `--conventions <path>` → package-root `conventions.yaml` → `--profile <name>` → dialect default.
 * When resolution falls through to a profile (the first-run case), [Resolved.materializeYaml]
 * carries the raw profile text so the CLI can write it into the package as the starting
 * `conventions.yaml` — from run two the estate's truth is fully pinned.
 */
object ConventionsResolver {
    private const val PROFILE_RESOURCE_DIR = "/org/tatrman/ttr/importschema/profiles"

    data class Resolved(
        val conventions: ConventionsFile,
        /** Human description of where the conventions came from (for the CLI/checklist). */
        val source: String,
        /** Raw YAML to materialise as the package's `conventions.yaml`; null when it already exists. */
        val materializeYaml: String?,
    )

    fun resolve(
        explicitPath: Path?,
        packageRoot: Path,
        profileName: String?,
        dialect: Dialect,
    ): Resolved {
        // 1. explicit --conventions <path>
        if (explicitPath != null) {
            if (!Files.isRegularFile(explicitPath)) {
                throw ImportSchemaException("TTRP-IMP-002", "conventions: --conventions file not found: $explicitPath")
            }
            return Resolved(
                ConventionsLoader.parse(Files.readString(explicitPath)),
                "file: $explicitPath",
                materializeYaml = null,
            )
        }
        // 2. package-root conventions.yaml (pins the estate from run two)
        val packageFile = packageRoot.resolve("conventions.yaml")
        if (Files.isRegularFile(packageFile)) {
            return Resolved(
                ConventionsLoader.parse(Files.readString(packageFile)),
                "package: $packageFile",
                materializeYaml = null,
            )
        }
        // 3. --profile <name>, else 4. dialect default profile — materialised on first run.
        val chosen = profileName ?: dialect.defaultProfile
        val yaml = loadProfileResource(chosen)
        return Resolved(ConventionsLoader.parse(yaml), "profile: $chosen", materializeYaml = yaml)
    }

    /** Load a packaged starter profile (`mssql-default`, `czech-erp`) by name. */
    fun loadProfileResource(name: String): String {
        val stream =
            ConventionsResolver::class.java.getResourceAsStream("$PROFILE_RESOURCE_DIR/$name.yaml")
                ?: throw ImportSchemaException(
                    "TTRP-IMP-002",
                    "conventions: unknown profile '$name' (packaged: ${availableProfiles().joinToString(", ")})",
                )
        return stream.bufferedReader().use { it.readText() }
    }

    private fun availableProfiles(): List<String> = listOf("czech-erp", "mssql-default")
}
