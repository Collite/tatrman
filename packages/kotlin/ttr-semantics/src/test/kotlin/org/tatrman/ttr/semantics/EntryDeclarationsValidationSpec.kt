// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.diagnostics.DiagnosticCode
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * EN-P1 (grammar 0.10) — semantic validation of the TTR-M entry declarations (`management` /
 * `changeSemantics { roles }`, FO §9/§11): vocabulary, role legality, per-mode required roles, and
 * role-column existence. `EntryMissingRole` (scd2 without a validity pair, ledger without
 * reversal-link) is the model diagnostic the compiler's TTRP-EN-003 escalates in EN-P2.
 */
class EntryDeclarationsValidationSpec :
    StringSpec({

        fun codes(def: String): List<DiagnosticCode> {
            val uri = "db.ttr"
            val r = TtrLoader.parseString("model db schema dbo\n$def", uri)
            val symbols = SymbolTable()
            symbols.upsertDocument(uri, r.definitions, "db", "dbo", "")
            val validator = Validator(symbols, Resolver(symbols), ResolvedManifest(lint = ManifestLint(strict = false)))
            val doc = SemanticDocument(uri, r.definitions, "db", "dbo", "", r.imports)
            return validator.validateDocument(doc).map { it.code }
        }

        val scd2Cols =
            "columns: [ def column customer_id { type: text }, def column valid_from { type: date }, " +
                "def column valid_to { type: date } ]"

        "a well-formed scd2 declaration raises no entry diagnostics" {
            val cs =
                codes("def table dim { changeSemantics: scd2 { validFrom: valid_from, validTo: valid_to }, $scd2Cols }")
            cs.none { it.id.startsWith("ttr/entry-") } shouldBe true
        }

        "a well-formed ledger declaration raises no entry diagnostics" {
            val cs =
                codes(
                    "def table txn { changeSemantics: ledger { reversalLink: reversal_of }, " +
                        "columns: [ def column entry_id { type: text }, def column reversal_of { type: text } ] }",
                )
            cs.none { it.id.startsWith("ttr/entry-") } shouldBe true
        }

        "scd2 missing the valid-to role → EntryMissingRole (feeds TTRP-EN-003)" {
            codes(
                "def table dim { changeSemantics: scd2 { validFrom: valid_from }, " +
                    "columns: [ def column valid_from { type: date } ] }",
            ).contains(DiagnosticCode.EntryMissingRole) shouldBe true
        }

        "ledger missing the reversal-link role → EntryMissingRole" {
            codes("def table txn { changeSemantics: ledger, columns: [ def column entry_id { type: text } ] }")
                .contains(DiagnosticCode.EntryMissingRole) shouldBe true
        }

        "an invalid management mode → EntryInvalidManagement" {
            codes("def table t { management: frozen, columns: [ def column id { type: text } ] }")
                .contains(DiagnosticCode.EntryInvalidManagement) shouldBe true
        }

        "an invalid changeSemantics mode → EntryInvalidChangeSemantics" {
            codes("def table t { changeSemantics: scd7, columns: [ def column id { type: text } ] }")
                .contains(DiagnosticCode.EntryInvalidChangeSemantics) shouldBe true
        }

        "an unknown role name → EntryUnknownRole" {
            codes(
                "def table dim { changeSemantics: scd2 " +
                    "{ validFrom: valid_from, validTo: valid_to, ghost: valid_from }, $scd2Cols }",
            ).contains(DiagnosticCode.EntryUnknownRole) shouldBe true
        }

        "a role column that is not on the table → EntryRoleColumnNotFound" {
            codes(
                "def table dim { changeSemantics: scd2 { validFrom: valid_from, validTo: missing_col }, $scd2Cols }",
            ).contains(DiagnosticCode.EntryRoleColumnNotFound) shouldBe true
        }
    })
