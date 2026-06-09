package org.tatrman.ttr.semantics

import java.net.URI
import java.nio.file.Path

/**
 * Directory → package inference (contract §4.4), ported from
 * `packages/semantics/src/package-inference.ts`.
 */
object PackageInference {
    private val EXT = Regex("\\.(ttr|ttrg)$")

    data class Inference(
        val inferred: String,
        val isRootFile: Boolean,
    )

    /**
     * Infers the expected package name from a document URI relative to
     * [projectRoot]. Handles both plain filesystem paths and `file://` URIs.
     * Mirrors TS `inferPackageFromUri`.
     */
    fun inferFromUri(
        uri: String,
        projectRoot: String,
    ): Inference {
        val path = if (uri.startsWith("file://")) URI(uri).path else uri
        val relativePath = if (path.startsWith(projectRoot)) path.substring(projectRoot.length) else path
        val segments = relativePath.split('/').filter { it.isNotEmpty() }

        val isRootFile = segments.size == 1 || (segments.size == 2 && segments[1].startsWith("."))

        val inferred =
            if (segments.size >= 2) {
                segments.dropLast(1).joinToString(".") { it.replace(EXT, "") }
            } else {
                ""
            }
        return Inference(inferred, isRootFile)
    }

    /**
     * Contract §4.4 path-based form: `<root>/foo/bar/baz.ttr` → `Qname("foo.bar")`,
     * a file directly under the root → empty `Qname`. Throws when [filePath] is
     * not under [projectRoot].
     */
    fun inferPackage(
        filePath: Path,
        projectRoot: Path,
    ): Qname {
        val file = filePath.toAbsolutePath().normalize()
        val root = projectRoot.toAbsolutePath().normalize()
        require(file.startsWith(root)) { "file $filePath is not under project root $projectRoot" }
        val rel = root.relativize(file)
        val parts = (0 until rel.nameCount).map { rel.getName(it).toString() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return Qname("")
        return Qname(parts.dropLast(1).joinToString(".") { it.replace(EXT, "") })
    }
}
