// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.walker

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.QueryDef
import org.tatrman.ttr.parser.model.TaggedBlockValue

/**
 * Phase 1 (embedded-sql) — RED. Mirrors the TypeScript `tagged-block.test.ts`
 * for the conformance golden cases (DESIGN §9; contracts §2.2/§2.3). Red until
 * the Kotlin walker (stage 1.4) produces a `TaggedBlockValue` whose
 * `tag`/`language`/`dialect`/`value` match the TS dump byte-for-byte.
 *
 * Note: `QueryDef.sourceText` is currently `String?`; stage 1.4 exposes the
 * structured block value (here via `sourceTextBlock`) carrying the contract.
 */
class TaggedBlockSpec :
    StringSpec({

        fun block(src: String): TaggedBlockValue {
            val r = TtrLoader.parseString(src)
            r.ok shouldBe true
            val q = r.definitions[0] as QueryDef
            return q.sourceTextBlock as TaggedBlockValue
        }

        "C1 — bare sql tag, clean body" {
            val v = block("def query c1 {\n  sourceText: \"\"\"sql\nSELECT 1\n\"\"\"\n}")
            v.tag shouldBe "sql"
            v.language shouldBe "SQL"
            v.dialect shouldBe null
            v.value shouldBe "SELECT 1"
        }

        "C3 — uniform 2-indent + trailing hspace after tag" {
            val v = block("def query c3 {\n  sourceText: \"\"\"sql  \n  SELECT 1\n  \"\"\"\n}")
            v.tag shouldBe "sql"
            v.value shouldBe "SELECT 1"
            v.indentWidth shouldBe 2
        }

        "C4 — ragged indent (2 vs 6): common 2 stripped" {
            val v = block("def query c4 {\n  sourceText: \"\"\"sql\n  SELECT a,\n      b\n  \"\"\"\n}")
            v.value shouldBe "SELECT a,\n    b"
        }

        "C9 — empty body" {
            val v = block("def query c9 {\n  sourceText: \"\"\"sql\n\"\"\"\n}")
            v.value shouldBe ""
        }

        "C11 — internal blank line kept; only the close-fence newline stripped" {
            val v = block("def query c11 {\n  sourceText: \"\"\"sql\nSELECT 1\n\nFROM t\n\"\"\"\n}")
            v.value shouldBe "SELECT 1\n\nFROM t"
        }
    })
