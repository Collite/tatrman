// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * PL-P4.S3 (grammar 0.11, H-1) — structural parse of the document-level
 * `security { … }` block. Mirrors the TS `security-block.test.ts`; the negative
 * roster is in `SecurityNegativeSpec`, cross-target parity in `ConformanceSpec`.
 */
class SecurityParseSpec :
    StringSpec({
        val src =
            """
            model db schema dbo
            def table sales { columns: [ def column region { type: text } ] }
            def table order_line { columns: [ def column customer_email { type: text } ] }
            security {
                own      sales: team_sales
                classify order_line.customer_email: pii
                grant    read on sales to accounting
                mask     order_line.customer_email
            }
            """.trimIndent()

        "parses one block with four typed statements" {
            val result = TtrLoader.parseString(src)
            result.ok shouldBe true
            result.securityBlocks shouldHaveSize 1
            val stmts = result.securityBlocks[0].statements
            stmts shouldHaveSize 4

            val own = stmts[0].shouldBeInstanceOf<SecurityStatement.Own>()
            own.objectRef shouldBe "sales"
            own.owner shouldBe "team_sales"

            val classify = stmts[1].shouldBeInstanceOf<SecurityStatement.Classify>()
            classify.objectRef shouldBe "order_line.customer_email" // dotted id kept opaque
            classify.classification shouldBe "pii"

            val grant = stmts[2].shouldBeInstanceOf<SecurityStatement.Grant>()
            grant.privilege shouldBe "read"
            grant.objectRef shouldBe "sales"
            grant.grantee shouldBe "accounting"

            stmts[3].shouldBeInstanceOf<SecurityStatement.Mask>().objectRef shouldBe "order_line.customer_email"
        }

        "supports several blocks in one document" {
            val two =
                """
                model db schema dbo
                def table sales { columns: [ def column region { type: text } ] }
                security { own sales: team_sales }
                security { mask sales }
                """.trimIndent()
            TtrLoader.parseString(two).securityBlocks shouldHaveSize 2
        }

        "keeps the verb keywords usable as ordinary identifiers (idPart)" {
            // `own`, `grant`, `mask`, `on`, `security` all remain legal id fragments.
            val ids = "model db schema dbo\ndef table grant { columns: [ def column own { type: text } ] }"
            TtrLoader.parseString(ids).ok shouldBe true
        }
    })
