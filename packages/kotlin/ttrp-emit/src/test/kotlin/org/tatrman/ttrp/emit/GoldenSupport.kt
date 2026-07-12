// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit

import io.kotest.assertions.fail
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Golden-snapshot support. Compares [actual] against `src/test/golden/<relPath>`; on
 * mismatch fails with a unified-ish diff. When the JVM system property `updateGolden=true`
 * is set, rewrites the golden and fails with a re-run message — updated goldens must be
 * reviewed in `git diff`, never silently green.
 *
 * Layout is per-dialect: goldens live under `golden/sql/postgres`, `golden/polars`, and
 * `golden/transfers`. Update: `./gradlew :packages:kotlin:ttrp-emit:test -DupdateGolden=true`.
 */
object GoldenSupport {
    private val root: Path = Paths.get("src", "test", "golden")
    private val updating: Boolean = System.getProperty("updateGolden") == "true"

    fun assertMatchesGolden(
        actual: String,
        relPath: String,
    ) {
        val file = root.resolve(relPath)
        val normalized = actual.trimEnd('\n') + "\n"
        if (updating) {
            Files.createDirectories(file.parent)
            Files.writeString(file, normalized)
            fail("golden updated ($relPath) — re-run without -DupdateGolden")
        }
        if (!Files.exists(file)) {
            fail("golden missing: $relPath — generate with -DupdateGolden=true\n--- actual ---\n$normalized")
        }
        val expected = Files.readString(file)
        if (expected != normalized) {
            fail(diff(relPath, expected, normalized))
        }
    }

    private fun diff(
        relPath: String,
        expected: String,
        actual: String,
    ): String {
        val e = expected.lines()
        val a = actual.lines()
        val sb = StringBuilder("golden mismatch: $relPath\n")
        val max = maxOf(e.size, a.size)
        for (i in 0 until max) {
            val el = e.getOrNull(i)
            val al = a.getOrNull(i)
            if (el != al) {
                sb.append("  line ${i + 1}:\n")
                sb.append("    - expected: ${el ?: "<none>"}\n")
                sb.append("    + actual:   ${al ?: "<none>"}\n")
            }
        }
        sb.append("Run with -DupdateGolden=true to accept (then review git diff).")
        return sb.toString()
    }
}
