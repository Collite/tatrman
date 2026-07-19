// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain

class RunManifestTest :
    FunSpec({
        val manifest =
            RunManifest(
                toolchain = "org.tatrman:ttrp:1.0.0",
                program = "hero.ttrp",
                world = WorldRef("acme.worlds.dev", "sha256:" + "a".repeat(64)),
                islands =
                    listOf(
                        IslandEntry(
                            "acc_prep",
                            "erp_pg",
                            "bash",
                            "psql",
                            "islands/acc_prep.sql",
                            "sha256:" + "b".repeat(64),
                        ),
                        IslandEntry(
                            "crunch",
                            "polars",
                            "bash",
                            "python3",
                            "islands/crunch.py",
                            "sha256:" + "c".repeat(64),
                        ),
                    ),
                transfers =
                    listOf(
                        TransferEntry(
                            "acc_prep",
                            "crunch",
                            "stage",
                            "transfers/x0.py",
                            "sha256:" + "d".repeat(64),
                        ),
                    ),
                waves = listOf(listOf("acc_prep"), listOf("x0"), listOf("crunch")),
                connections = listOf("TTR_CONN_ERP_PG"),
                displays = listOf(DisplayEntry("main_result", "out/main_result.arrow")),
                files = mapOf("run.sh" to "sha256:" + "e".repeat(64)),
            )

        test("serializes with exactly the contracts §5 keys") {
            val json = manifest.toJson()
            listOf(
                "\"ttrpVersion\": 1",
                "\"toolchain\"",
                "\"program\"",
                "\"world\"",
                "\"fingerprint\"",
                "\"islands\"",
                "\"invocation\"",
                "\"transfers\"",
                "\"waves\"",
                "\"connections\"",
                "\"displays\"",
                "\"files\"",
            ).forEach { json shouldContain it }
        }

        test("world fingerprint matches the sha256 shape") {
            manifest.world.fingerprint shouldMatch Regex("^sha256:[0-9a-f]{64}$")
        }

        test("round-trips through decode") {
            RunManifest.fromJson(manifest.toJson()) shouldBe manifest
        }

        test("rejectSites round-trips and defaults empty (RJ-P3, contracts §7)") {
            manifest.rejectSites shouldBe emptyList() // absent ⇒ empty (fail-fast backward compat)
            val withSite =
                manifest.copy(
                    rejectSites =
                        listOf(RejectSiteEntry("checked", "returns_ingest", "rejects", listOf("clean", "bad"))),
                )
            withSite.toJson() shouldContain "\"rejectSites\""
            RunManifest.fromJson(withSite.toJson()) shouldBe withSite
        }

        test("strict decoding rejects unknown keys") {
            val withExtra = manifest.toJson().replaceFirst("{", "{\n  \"bogus\": 1,")
            shouldThrow<Exception> { RunManifest.fromJson(withExtra) }
        }

        test("md block is omitted when null — non-MD manifests stay byte-identical (S4-B5)") {
            manifest.md shouldBe null
            manifest.toJson() shouldNotContain "\"md\""
            RunManifest.fromJson(manifest.toJson()) shouldBe manifest
        }

        test("md block records asof + fingerprint, omitting null fields (S4-B5)") {
            val withAsof = manifest.copy(md = MdManifest(asof = "2026-07-08T00:00:00Z"))
            withAsof.toJson() shouldContain "\"md\""
            withAsof.toJson() shouldContain "\"asof\": \"2026-07-08T00:00:00Z\""
            // Disconnected compile (S4): no snapshot ⇒ memberFingerprint omitted, not `null`.
            withAsof.toJson() shouldNotContain "memberFingerprint"
            RunManifest.fromJson(withAsof.toJson()) shouldBe withAsof

            val withFp =
                manifest.copy(
                    md =
                        MdManifest(
                            asof = "2026-07-08T00:00:00Z",
                            memberFingerprint =
                                "sha256:" + "f".repeat(64),
                        ),
                )
            withFp.toJson() shouldContain "\"memberFingerprint\": \"sha256:"
            RunManifest.fromJson(withFp.toJson()) shouldBe withFp
        }
    })
