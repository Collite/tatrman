package org.tatrman.ttrp.bundle

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch

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

        test("strict decoding rejects unknown keys") {
            val withExtra = manifest.toJson().replaceFirst("{", "{\n  \"bogus\": 1,")
            shouldThrow<Exception> { RunManifest.fromJson(withExtra) }
        }
    })
