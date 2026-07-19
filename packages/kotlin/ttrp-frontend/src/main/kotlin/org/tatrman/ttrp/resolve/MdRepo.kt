// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.resolve

import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MdModel
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * Loads the MD (multidimensional) tier of a TTR-M model repo — the production seam that S3/S4 left as
 * a null-defaulted injection point. Parses every `.ttrm` under [modelsRoot] and builds the logical
 * [MdModel] (dimensions / cubelets / measures / maps, ttr-semantics MDS2) and the physical
 * [MdBindings] (`md2db_*`, MDS-binding). Offline, no service (D-g) — mirrors [ModelRepo], the ER/DB
 * tier, which is separate because ttr-metadata's registry carries **no** MD objects (MD is a distinct
 * representation). Non-MD definitions in the tree (world / db / er) are ignored by the MD builders.
 *
 * Returns null when the repo declares no cubelets, so a project with no MD model injects nothing and
 * the pipeline behaves exactly as before. The member snapshot
 * ([org.tatrman.ttr.md.resolve.MemberSnapshot]) is a further seam (production catalog loading is
 * S6-B), so a model loaded here resolves in **disconnected mode** (R13): bare members are illegal,
 * dimension-qualified members resolve as deferred coordinates.
 */
object MdRepo {
    /** The loaded MD tier: the logical model + its physical `md2db_*` bindings, paired by simple name. */
    data class Loaded(
        val model: MdModel,
        val bindings: MdBindings,
    )

    /** Loads [modelsRoot] (the `models/` dir). Returns null when the dir is absent or declares no cubelets. */
    fun loadFrom(modelsRoot: Path): Loaded? {
        if (!Files.isDirectory(modelsRoot)) return null
        // Resolve symlinks for the same reason [ModelRepo] does: a `models/` symlink (the test
        // fixture's link to the shared models) must be canonicalised before the walk descends it.
        val realRoot = runCatching { modelsRoot.toRealPath() }.getOrDefault(modelsRoot)
        val files =
            Files.walk(realRoot).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".ttrm") }
                    .sorted()
                    .collect(Collectors.toList())
            }
        val defs = mutableListOf<Definition>()
        for (file in files) {
            // A file with parse errors is skipped, not fatal: the ER/DB tier ([ModelRepo]) reports
            // those diagnostics; here a broken `.ttrm` just contributes no MD defs.
            val parsed = TtrLoader.parseString(Files.readString(file), realRoot.relativize(file).toString())
            if (parsed.ok) defs += parsed.definitions
        }
        val model = MdModel.from(defs)
        if (model.cubelets.isEmpty()) return null
        return Loaded(model, MdBindings.from(defs))
    }
}
