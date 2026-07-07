package org.tatrman.ttrp.conform

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** T3.4.3 — invoker + ConformRunner over stub bundles (run.sh writes canned Arrow; no engines). */
class ConformRunnerTest :
    FunSpec({
        val fixtureDir = Paths.get("src/test/resources/arrow").toAbsolutePath()

        /** A stub bundle whose run.sh copies a committed .arrow fixture to out/main_result.arrow. */
        fun stubBundle(
            arrowFixture: String,
            exit: Int = 0,
            requireEnv: String? = null,
        ): Path {
            val dir = Files.createTempDirectory("stub-bundle")
            val guard = requireEnv?.let { "[[ -z \"\${$it:-}\" ]] && { echo missing >&2; exit 2; }\n" } ?: ""
            val body =
                if (exit != 0) {
                    "exit $exit\n"
                } else {
                    "mkdir -p out\ncp \"$fixtureDir/$arrowFixture\" out/main_result.arrow\nexit 0\n"
                }
            Files.writeString(dir.resolve("run.sh"), "#!/usr/bin/env bash\nset -euo pipefail\n$guard$body")
            return dir
        }

        test("happy path — two variants producing the same result exit 0") {
            val runner = ConformRunner(BundleInvoker())
            val outcome =
                runner.run(
                    linkedMapOf(
                        "A" to stubBundle("multiset_a.arrow"),
                        "B" to stubBundle("multiset_b.arrow"), // same multiset, permuted order
                    ),
                )
            outcome.exitCode shouldBe 0
            outcome.reports.getValue("main_result").pass shouldBe true
        }

        test("run.sh exit 1 propagates as an invocation failure (exit 2)") {
            val runner = ConformRunner(BundleInvoker())
            val outcome =
                runner.run(
                    linkedMapOf("A" to stubBundle("multiset_a.arrow"), "B" to stubBundle("", exit = 1)),
                )
            outcome.exitCode shouldBe 2
        }

        test("missing TTR_CONN_* (run.sh exit 2) is reported as invocation failure") {
            val runner = ConformRunner(BundleInvoker(env = emptyMap()))
            val outcome =
                runner.run(
                    linkedMapOf(
                        // A var guaranteed absent from any env — the spawned run.sh inherits the
                        // parent env, and the conform CI job (+ local live runs) set TTR_CONN_ERP_PG,
                        // which would otherwise defeat the missing-conn guard.
                        "A" to stubBundle("multiset_a.arrow", requireEnv = "TTR_CONN_ABSENT_FOR_TEST"),
                        "B" to stubBundle("multiset_b.arrow"),
                    ),
                )
            outcome.exitCode shouldBe 2
        }

        test("differing results exit 1 (comparison failure)") {
            val runner = ConformRunner(BundleInvoker())
            val outcome =
                runner.run(
                    linkedMapOf(
                        "A" to stubBundle("multiset_a.arrow"),
                        "B" to stubBundle("twocol.arrow"),
                    ),
                )
            outcome.exitCode shouldBe 1
        }
    })
