// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.source.LoadWarning

/**
 * The finalized LoadIssue taxonomy (M2.2 T2.2.3) is sealed-enum, id-free (MD5).
 * Verifies each `ttr/<code>` prefix maps to the right [LoadIssue.Category] and that
 * no category value is a diagnostic-id string.
 */
class LoadIssueTaxonomySpec :
    StringSpec({

        fun cat(
            msg: String,
            sev: LoadIssue.Severity = LoadIssue.Severity.ERROR,
        ) = LoadIssue.from(LoadWarning("s", "f.ttrm", 1, 2, msg), sev).category

        "categorizes each inherited ttr/<code> prefix" {
            cat("ttr/parse-error: bad") shouldBe LoadIssue.Category.PARSE_ERROR
            cat("ttr/duplicate-definition: x") shouldBe LoadIssue.Category.DUPLICATE_QNAME
            cat("ttr/unresolved-reference: x") shouldBe LoadIssue.Category.UNRESOLVED_REFERENCE
            cat("ttr/ambiguous-reference: x") shouldBe LoadIssue.Category.AMBIGUOUS_REFERENCE
            cat("ttr/unimported-reference: x") shouldBe LoadIssue.Category.UNIMPORTED_REFERENCE
            cat("ttr/package-declaration-mismatch: x") shouldBe LoadIssue.Category.PACKAGE_MISMATCH
            cat("ttr/wrong-file-kind: x") shouldBe LoadIssue.Category.WRONG_FILE_KIND
            cat("Cannot redefine protected qname 'cnc.role.fact'") shouldBe LoadIssue.Category.PROTECTED_QNAME
            cat("something unexpected") shouldBe LoadIssue.Category.OTHER
        }

        "carries structured fields and severity, no diagnostic-id string" {
            val i =
                LoadIssue.from(
                    LoadWarning("s", "f.ttrm", 3, 4, "ttr/wrong-file-kind: x"),
                    LoadIssue.Severity.WARNING,
                )
            i.severity shouldBe LoadIssue.Severity.WARNING
            i.file shouldBe "f.ttrm"
            i.line shouldBe 3
            // category is an enum, never a "TTRP-"/"ttr/" id
            i.category.name
                .startsWith("TTRP")
                .let { it shouldBe false }
        }
    })
