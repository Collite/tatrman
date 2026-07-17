// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.write

import org.tatrman.ttr.importschema.dbmodel.DbMirrorResult
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes a [DbMirrorResult] to a model-package directory: the `db.<schema>.ttrm` documents plus a
 * `modeler.toml` manifest. Deterministic (no timestamps — GI-2). The conventions.yaml
 * materialisation (Q-1) and the review checklist (F5) are layered on in S3·T4 / S4·T4.
 */
object ModelPackageWriter {
    fun write(
        outDir: Path,
        packageName: String,
        result: DbMirrorResult,
    ): List<Path> {
        Files.createDirectories(outDir)
        val written = mutableListOf<Path>()
        for (file in result.files.sortedBy { it.path }) {
            val target = outDir.resolve(file.path)
            Files.writeString(target, file.content)
            written.add(target)
        }
        val manifest = outDir.resolve("modeler.toml")
        Files.writeString(manifest, manifestToml(packageName, result))
        written.add(manifest)
        return written
    }

    private fun manifestToml(
        packageName: String,
        result: DbMirrorResult,
    ): String {
        val schemas =
            result.files
                .mapNotNull {
                    it.path.removePrefix("db.").removeSuffix(".ttrm").takeIf { s ->
                        s.isNotEmpty()
                    }
                }.sorted()
        return buildString {
            append("[project]\n")
            append("name = \"$packageName\"\n\n")
            append("[schemas]\n")
            append("declared = [\"db\"]\n")
            if (schemas.isNotEmpty()) {
                val ns = schemas.joinToString(", ") { "db = \"$it\"" }
                append("# db-schema namespaces introspected: $ns\n")
            }
        }
    }
}
