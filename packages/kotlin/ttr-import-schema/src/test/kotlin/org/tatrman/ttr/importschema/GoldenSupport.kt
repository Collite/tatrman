// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema

import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/**
 * Golden-file comparison for the deterministic outputs (db mirror, er first cut, checklist).
 * Goldens live under `src/test/golden/<relPath>`; run with `-DupdateGolden=true` to regenerate
 * (then review the diff before committing — the golden is a reviewed artifact, GI-2).
 */
object GoldenSupport {
    private val goldenRoot: Path = Path.of("src", "test", "golden")
    private val update: Boolean = System.getProperty("updateGolden") == "true"

    fun assertMatchesGolden(
        actual: String,
        relPath: String,
    ) {
        val path = goldenRoot.resolve(relPath)
        if (update) {
            Files.createDirectories(path.parent)
            Files.writeString(path, actual)
            throw AssertionError("updated golden $relPath — re-run without -DupdateGolden to verify")
        }
        if (!Files.exists(path)) {
            Files.createDirectories(path.parent)
            Files.writeString(path, actual)
            throw AssertionError("golden $relPath did not exist — wrote it; review and re-run")
        }
        actual shouldBe Files.readString(path)
    }
}
