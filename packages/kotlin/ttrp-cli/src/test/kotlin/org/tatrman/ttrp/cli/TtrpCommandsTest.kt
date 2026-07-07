package org.tatrman.ttrp.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** T3.3.5 — the `ttrp` clikt command wiring. */
class TtrpCommandsTest :
    FunSpec({
        fun ttrp() =
            TtrpCommand().subcommands(BuildCommand(), RunCommand(), ExplainCommand(), CheckCommand(), ConformCommand())

        test("--help lists the subcommand roster") {
            val result = ttrp().test("--help")
            listOf("build", "run", "explain", "check", "conform").forEach { result.stdout shouldContain it }
        }

        test("conform is a stub with the distinct exit 3 (until Stage 3.4)") {
            val result = ttrp().test("conform some.ttrp")
            result.statusCode shouldBe 3
        }

        test("build on a missing file exits 2") {
            val result = ttrp().test("build /nonexistent/does-not-exist.ttrp")
            result.statusCode shouldBe 2
        }
    })
