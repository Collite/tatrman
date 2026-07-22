// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.GraphFixtures
import java.nio.file.Files
import java.nio.file.Path

/**
 * EN-P3.1 T1 — golden snapshots of the lowered step model, one per §9 row (contracts §9). Regenerate
 * with `-DupdateGolden=true`, review the diff, then re-run. Goldens live under
 * `fixtures/graph/entry/<name>.entry.txt` (the explain-golden convention).
 */
class EntryLoweringGoldenSpec :
    StringSpec({
        val f = EntryLowerFixtures

        fun render(
            table: org.tatrman.ttr.metadata.model.DbTable,
            verbId: String,
            batch: String,
        ): String {
            val result = EntryLowering.lower(f.unit(table, verbId, batch))
            return EntryLoweringRender.write(result.plan!!)
        }

        "scd1 update-in-place" {
            assertGolden(
                render(
                    f.refRegion,
                    "entry.update-rows",
                    proposal(
                        """{ "op": "update", "key": { "region_code": "NA" }, "values": { "region_name": "North" } }""",
                    ),
                ),
                "scd1-update",
            )
        }

        "scd2 effective-date-change closes current and inserts successor" {
            assertGolden(
                render(
                    f.dimCustomer,
                    "entry.effective-date-change",
                    proposal(
                        """{ "op": "update", "key": { "customer_id": 1 }, "values": { "customer_name": "Acme" }, "effectiveDate": "2026-01-01" }""",
                    ),
                ),
                "scd2-effective-date-change",
            )
        }

        "ledger reverse-and-replace threads the reversal-link and F3 ids" {
            assertGolden(
                render(
                    f.txnBook,
                    "entry.reverse-and-replace",
                    proposal("""{ "op": "update", "key": { "entry_id": "e1" }, "values": { "amount": 42 } }"""),
                ),
                "ledger-reverse-and-replace",
            )
        }

        "undeclared update lowers to an optimistic guard + update" {
            assertGolden(
                render(
                    f.plainNotes,
                    "entry.update-rows",
                    proposal(
                        """{ "op": "update", "key": { "note_id": 7 }, "values": { "body": "hi" }, "baseRowVersion": "v3" }""",
                    ),
                ),
                "optimistic-update",
            )
        }

        "insert-rows lowers to a plain insert" {
            assertGolden(
                render(
                    f.refRegion,
                    "entry.insert-rows",
                    proposal("""{ "op": "insert", "values": { "region_code": "SA", "region_name": "South" } }"""),
                ),
                "insert-rows",
            )
        }

        "delete-rows on a plain target physically deletes by key" {
            assertGolden(
                render(f.plainNotes, "entry.delete-rows", proposal("""{ "op": "delete", "key": { "note_id": 7 } }""")),
                "delete-physical",
            )
        }

        "delete-rows on an scd2 target soft-closes with no successor" {
            assertGolden(
                render(
                    f.dimCustomer,
                    "entry.delete-rows",
                    proposal("""{ "op": "delete", "key": { "customer_id": 1 }, "effectiveDate": "2026-02-01" }"""),
                ),
                "delete-scd2-softclose",
            )
        }

        "delete-rows on a ledger target lowers to a pure reversal (replacement omitted)" {
            assertGolden(
                render(f.txnBook, "entry.delete-rows", proposal("""{ "op": "delete", "key": { "entry_id": "e1" } }""")),
                "delete-ledger-reversal",
            )
        }

        "reject-row lowers to a reject envelope only" {
            assertGolden(
                render(
                    f.plainNotes,
                    "entry.reject-row",
                    proposal(
                        """{ "op": "update", "key": { "note_id": 7 }, "values": { "code": "STALE", "detail": "conflict" } }""",
                    ),
                ),
                "reject-row",
            )
        }
    })

private fun proposal(body: String) = """{ "target": { "table": "entry.db.dbo.x" }, "proposals": [ $body ] }"""

private fun goldenPath(name: String): Path = GraphFixtures.root.resolve("entry/$name.entry.txt")

private fun assertGolden(
    actual: String,
    name: String,
) {
    val path = goldenPath(name)
    val update = System.getProperty("updateGolden") == "true"
    if (!Files.exists(path) || update) {
        Files.createDirectories(path.parent)
        Files.writeString(path, actual)
        throw AssertionError("golden `$name` written — review the diff, then re-run without -DupdateGolden")
    }
    actual shouldBe Files.readString(path)
}
