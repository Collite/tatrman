// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.semantics.md.MdModel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

/**
 * Loads an [MdModel] offline from the `.ttrm` files under `<root>/<model>/` (the model-repo layout,
 * one directory per model — matching the compiler's `models/<name>/` convention). Files that fail to parse contribute no
 * defs. Loaded models are cached; an unknown model directory yields `null` (the tools then report a
 * "unknown model" input error). Pure parse + build — no service, mirroring the compiler's `MdRepo`.
 */
class FileModelProvider(
    private val root: Path,
) : ModelProvider {
    private val cache = ConcurrentHashMap<String, MdModel>()

    override fun model(name: String): MdModel? {
        val dir = root.resolve(name)
        if (!Files.isDirectory(dir)) return null
        return cache.getOrPut(name) { MdModel.from(parseDefs(dir)) }
    }

    private fun parseDefs(dir: Path): List<Definition> {
        val files =
            Files.walk(dir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".ttrm") }
                    .sorted()
                    .collect(Collectors.toList())
            }
        val defs = mutableListOf<Definition>()
        for (file in files) {
            val parsed = TtrLoader.parseString(Files.readString(file), dir.relativize(file).toString())
            if (parsed.ok) defs += parsed.definitions
        }
        return defs
    }
}
