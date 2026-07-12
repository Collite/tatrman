// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.bare

import org.tatrman.ttrp.project.TtrpManifest
import org.tatrman.ttrp.resolve.ResolutionFixtures
import org.tatrman.ttrp.resolve.TtrpChecker

/**
 * Loads bare-fragment fixtures under `corpus/bare` and checks them through a `[ttrp]` manifest that
 * carries bare-program defaults (bare-target + the S18 default-imports prelude), over the shared
 * erp world. Exercises the T6.3.3 wrapper synthesis end-to-end (parse → synth → resolve).
 */
object BareFixtures {
    fun read(rel: String): String =
        BareFixtures::class.java
            .getResourceAsStream("/corpus/bare/$rel")
            ?.readBytes()
            ?.decodeToString()
            ?: error("bare fixture not found: /corpus/bare/$rel")

    fun manifest(
        bareTarget: String? = "erp_pg",
        defaultImports: List<String> = listOf("erp.*"),
    ): TtrpManifest =
        TtrpManifest(
            world = "acme.worlds.dev",
            bareTarget = bareTarget,
            defaultImports = defaultImports,
            manifestDir = ResolutionFixtures.projectDir(),
        )

    fun check(
        rel: String,
        manifest: TtrpManifest = manifest(),
    ): TtrpChecker.Report = TtrpChecker(manifest, ResolutionFixtures.modelsRoot()).check(read(rel), rel)
}
