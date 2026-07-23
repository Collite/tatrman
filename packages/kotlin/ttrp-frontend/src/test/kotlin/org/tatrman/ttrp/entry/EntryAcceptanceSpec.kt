// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P6.1 T2 — the **compile half** of the demand's Acceptance (contracts §8), as one executable suite:
 * a mis-typed batch fails at compile (TTRP-EN-001), a verb that does not match the target's declared
 * change-semantics fails at compile (TTRP-EN-002), a well-formed program per semantics typechecks clean,
 * and a `call-fn` resolves to a deploy pin. (The live half — byte-equal replay per verb + F3 + the pin
 * reproducing in the §6 entry record — is the platform `EntryAcceptanceSpec`; TTR-P does not run in this
 * repo, RO-6. The two halves together are the demand Acceptance.)
 */
class EntryAcceptanceSpec :
    StringSpec({
        val loc = SourceLocation("acceptance.ttrp", 1, 0, 1, 0, 0, 0)

        fun shape(
            table: String,
            body: String,
        ) = BatchShapeChecker
            .check(
                RowBatch.parse("""{ "target": { "table": "entry.db.dbo.$table" }, "proposals": [ $body ] }"""),
                EntryFixtures.table(table),
                effectiveDateRequired = false,
                loc,
            ).map { it.id }

        fun verbAgainst(
            verbId: String,
            table: String,
        ) = VerbDeclarationChecker
            .check(EntryVerbCatalog.byId(verbId)!!, EntryFixtures.table(table), physicalDelete = false, loc)
            .map { it.id }

        "mis-typed batch fails at compile — TTRP-EN-001 (a text value for a numeric column)" {
            val body = """{ "op": "update", "key": { "customer_id": 1 }, "values": { "customer_id": "x" } }"""
            shape("dim_customer", body) shouldContain TtrpDiagnosticId.EN_001
        }

        "verb/semantics mismatch fails at compile — TTRP-EN-002 (reverse-and-replace on a non-ledger)" {
            verbAgainst("entry.reverse-and-replace", "dim_customer") shouldContain TtrpDiagnosticId.EN_002
        }

        "a well-formed program per semantics typechecks clean — scd1 update-rows on ref_region" {
            val body = """{ "op": "update", "key": { "region_code": "EU" }, "values": { "region_name": "X" } }"""
            shape("ref_region", body).shouldBeEmpty()
            verbAgainst("entry.update-rows", "ref_region").shouldBeEmpty()
        }

        "a call-fn resolves to a deploy pin against the registry seam" {
            val demand =
                CallFnDemand(
                    functionId = "twr",
                    idIsLiteral = true,
                    versionConstraint = "^1.0.0",
                    args =
                        listOf(
                            CallFnArg("number[]", SourceLocation.UNKNOWN),
                            CallFnArg("number[]", SourceLocation.UNKNOWN),
                        ),
                    location = SourceLocation.UNKNOWN,
                )
            val r = CallFnResolver.resolve(listOf(demand), CanonFunctionFixtureRegistry)
            r.ok shouldBe true
            r.pins shouldContainExactly listOf(CallFnPin("twr", "1.2.0"))
        }
    })
