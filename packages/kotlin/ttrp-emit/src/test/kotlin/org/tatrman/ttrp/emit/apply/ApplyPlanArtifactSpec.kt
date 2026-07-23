// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import io.kotest.core.spec.style.StringSpec
import org.tatrman.ttrp.emit.GoldenSupport

/**
 * EN-P4b — the cross-repo wire artifacts. Emits the four §9 apply plans for the exact FO-P2 platform
 * `SemanticsFixtures` batch values and pins them as JSON goldens under `golden/apply-json/`. These are
 * the artifacts the platform `ApplyDoorRoundTripSpec` consumes as **test resources** (RO-6: the plan
 * crosses the repo boundary as a resource, never a project dep) — regenerate with `-DupdateGolden=true`
 * then copy the four files into `services/entry-substrate/src/test/resources/entry/emitted/`.
 */
class ApplyPlanArtifactSpec :
    StringSpec({
        val f = EntryEmitFixtures

        fun batch(body: String) = """{ "target": { "table": "x" }, "proposals": [ $body ] }"""

        fun artifact(
            result: ApplyEmitResult,
            name: String,
        ) = GoldenSupport.assertMatchesGolden(
            ApplyPlanJson.encode(result.plan!!),
            "apply-json/$name.json",
        )

        "scd1 update — ref_region" {
            artifact(
                f.emit(
                    f.refRegion,
                    "entry.update-rows",
                    batch(
                        """{ "op": "update", "key": { "region_code": "EU" }, """ +
                            """"values": { "region_name": "EMEA" } }""",
                    ),
                ),
                "ref_region-scd1-update",
            )
        }

        "scd2 effective-date-change — dim_customer" {
            artifact(
                f.emit(
                    f.dimCustomer,
                    "entry.effective-date-change",
                    batch(
                        """{ "op": "update", "key": { "customer_id": "C1" }, "values": { "region": "APAC" }, """ +
                            """"effectiveDate": "2026-06-01" }""",
                    ),
                ),
                "dim_customer-scd2-effective-date-change",
            )
        }

        "ledger reverse-and-replace — txn_book" {
            artifact(
                f.emit(
                    f.txnBook,
                    "entry.reverse-and-replace",
                    batch("""{ "op": "update", "key": { "entry_id": "e1" }, "values": { "amount": 120 } }"""),
                ),
                "txn_book-ledger-reverse-and-replace",
            )
        }

        "optimistic update — raw_notes" {
            artifact(
                f.emit(
                    f.rawNotes,
                    "entry.update-rows",
                    batch(
                        """{ "op": "update", "key": { "k": "n1" }, "values": { "v": "final" }, """ +
                            """"baseRowVersion": "rv-draft" }""",
                    ),
                ),
                "raw_notes-optimistic-update",
            )
        }

        // A call-fn-pinned variant of the scd1 plan (the deploy-resolved `twr@1.2.0` pin baked in) — the
        // platform acceptance loads this to prove a pin reproduces live in the §6 entry record (EN-P6 T2).
        "scd1 update with a deploy-resolved call-fn pin — ref_region" {
            artifact(
                f.emit(
                    f.refRegion,
                    "entry.update-rows",
                    batch(
                        """{ "op": "update", "key": { "region_code": "EU" }, """ +
                            """"values": { "region_name": "EMEA" } }""",
                    ),
                    pluginPins = listOf(PluginPin("twr", "1.2.0")),
                ),
                "ref_region-scd1-update-pinned",
            )
        }
    })
