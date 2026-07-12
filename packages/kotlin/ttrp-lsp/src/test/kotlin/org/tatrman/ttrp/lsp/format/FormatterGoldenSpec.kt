// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.format

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/**
 * Golden pairs: `<name>.in.ttrp` + `<name>.expected.ttrp` under `src/test/resources/format/`.
 * Each pair asserts `format(.in) == .expected` (byte-equality) and idempotency
 * `format(format(x)) == format(x)` on the `.expected`.
 */
class FormatterGoldenSpec :
    StringSpec({
        val dir = Path.of("src/test/resources/format")
        val formatter = TtrpFormatter()

        Files
            .list(dir)
            .filter { it.fileName.toString().endsWith(".in.ttrp") }
            .sorted()
            .forEach { inFile ->
                val base = inFile.fileName.toString().removeSuffix(".in.ttrp")
                val expectedFile = dir.resolve("$base.expected.ttrp")

                "$base — format(.in) equals .expected" {
                    val input = Files.readString(inFile)
                    val expected = Files.readString(expectedFile)
                    formatter.format(input, "$base.ttrp") shouldBe expected
                }

                "$base — idempotent on .expected" {
                    val expected = Files.readString(expectedFile)
                    val once = formatter.format(expected, "$base.ttrp")
                    formatter.format(once, "$base.ttrp") shouldBe once
                    once shouldBe expected
                }
            }
    })
