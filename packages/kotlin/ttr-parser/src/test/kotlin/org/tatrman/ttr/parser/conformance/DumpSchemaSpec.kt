// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.conformance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * Conformance JSON dump schema (contracts.md §5). Both runtimes (this Kotlin
 * dumper and the TS one in tests/conformance/) must emit byte-identical JSON
 * after normalisation:
 *   1. no SourceLocation anywhere,
 *   2. object keys sorted alphabetically,
 *   3. `kind` = the TTR keyword (lowercased),
 *   4. property names = the TTR surface name (per AST-NAMING.md).
 *
 * This spec is owned by stage 1.6 (it stays red until ConformanceDump lands);
 * it is introduced here so the format is pinned by an expected snapshot.
 */
class DumpSchemaSpec :
    StringSpec({

        "dump of a minimal model def matches the §5 schema snapshot" {
            val r = TtrLoader.parseString("def project M { version: \"1.2.3\" }")
            val actual = ConformanceDump.dump(r)

            val expected =
                DumpSchemaSpec::class.java
                    .getResource("/conformance/model-min.json")!!
                    .readText()
                    .trimEnd()

            actual.trimEnd() shouldBe expected
        }
    })
