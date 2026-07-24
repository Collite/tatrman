// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * PL-P4.S3.T6 — advisory validation of the `security { }` block. Object refs
 * resolve (or lint as a WARNING); roles are verbatim (never resolved); a
 * violation is NEVER a compile block (H-3).
 */
class SecuritySemanticsSpec :
    StringSpec({

        val model =
            """
            model db schema dbo
            def table sales { columns: [ def column region { type: text }, def column amount { type: decimal } ] }
            def table order_line { columns: [ def column customer_email { type: text } ] }
            """.trimIndent()

        "all object refs resolve → zero diagnostics" {
            val r =
                TtrLoader.parseString(
                    "$model\nsecurity {\n" +
                        "  own sales: team_sales\n" +
                        "  classify order_line.customer_email: pii\n" +
                        "  grant read on sales to accounting\n" +
                        "  mask order_line.customer_email\n}",
                )
            r.ok shouldBe true
            SecurityValidator.validateSecurity(r) shouldBe emptyList()
        }

        "an unknown object → one security/unresolved-object WARNING (never an error)" {
            val r = TtrLoader.parseString("$model\nsecurity { grant read on ghost to accounting }")
            val diags = SecurityValidator.validateSecurity(r)
            diags shouldHaveSize 1
            diags[0].code shouldBe "security/unresolved-object"
            diags[0].severity shouldBe "warning" // H-3: advisory only, never a compile block
            diags[0].message shouldContain "ghost"
        }

        "a column-level ref resolves via its owning object's head segment" {
            // `order_line.unknown_col` — the head `order_line` is a known object, so
            // the ref resolves (member-level checking is Perun's job, not the linter's).
            val r = TtrLoader.parseString("$model\nsecurity { mask order_line.unknown_col }")
            SecurityValidator.validateSecurity(r) shouldBe emptyList()
        }

        "unknown ROLE/classification tokens are NOT flagged (verbatim org-policy data)" {
            // `nobody`/`ultra_secret` are not model objects; only the OBJECT ref is resolved.
            val r =
                TtrLoader.parseString(
                    "$model\nsecurity { grant read on sales to nobody, classify sales.region: ultra_secret }",
                )
            SecurityValidator.validateSecurity(r) shouldBe emptyList()
        }

        "an injected project-wide object set is honoured for cross-file refs" {
            val r = TtrLoader.parseString("model db schema dbo\nsecurity { grant read on shop.sales.order to buyers }")
            // Not in this document, but known project-wide → resolves.
            SecurityValidator.validateSecurity(r, knownObjects = setOf("shop.sales.order")) shouldBe emptyList()
            // Absent from the injected set → one advisory warning.
            SecurityValidator.validateSecurity(r, knownObjects = setOf("other.thing")) shouldHaveSize 1
        }

        "no security block → no diagnostics" {
            SecurityValidator.validateSecurity(TtrLoader.parseString(model)) shouldBe emptyList()
        }
    })
