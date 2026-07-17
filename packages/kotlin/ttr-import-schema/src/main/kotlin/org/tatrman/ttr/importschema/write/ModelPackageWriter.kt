// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.write

import org.tatrman.ttr.importschema.ImportResult
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes an [ImportResult] to a model-package directory: the `db.<schema>.ttrm` documents, the
 * `er.ttrm` first cut, the `import-review.{md,json}` checklist and a `modeler.toml` manifest.
 * Deterministic (no timestamps — GI-2). Conventions.yaml materialisation (Q-1) is the CLI's job.
 */
object ModelPackageWriter {
    fun write(
        outDir: Path,
        packageName: String,
        result: ImportResult,
    ): List<Path> {
        Files.createDirectories(outDir)
        val written = mutableListOf<Path>()
        for (file in (result.dbFiles + result.erFile).sortedBy { it.path }) {
            val target = outDir.resolve(file.path)
            Files.writeString(target, file.content)
            written.add(target)
        }
        written.add(writeFile(outDir, "import-review.md", result.reviewMarkdown))
        written.add(writeFile(outDir, "import-review.json", result.reviewJson))
        written.add(writeFile(outDir, "modeler.toml", manifestToml(packageName, result)))
        return written
    }

    private fun writeFile(
        outDir: Path,
        name: String,
        content: String,
    ): Path {
        val target = outDir.resolve(name)
        Files.writeString(target, content)
        return target
    }

    private fun manifestToml(
        packageName: String,
        result: ImportResult,
    ): String {
        val schemas =
            result.dbFiles
                .mapNotNull {
                    it.path
                        .removePrefix("db.")
                        .removeSuffix(".ttrm")
                        .takeIf { s -> s.isNotEmpty() }
                }.sorted()
        return buildString {
            append("[project]\n")
            append("name = \"$packageName\"\n\n")
            append("[schemas]\n")
            append("declared = [\"db\", \"er\"]\n")
            if (schemas.isNotEmpty()) {
                val ns = schemas.joinToString(", ") { "db = \"$it\"" }
                append("# db-schema namespaces introspected: $ns\n")
            }
        }
    }
}
