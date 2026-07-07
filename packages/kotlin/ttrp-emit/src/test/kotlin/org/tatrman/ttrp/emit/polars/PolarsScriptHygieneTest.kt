package org.tatrman.ttrp.emit.polars

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.streams.toList

/**
 * T3.2.6 — static sanity, no live execution. Every emitted Polars/transfer golden must be valid
 * Python (`python3 -m py_compile`, when python3 is on PATH — CI has it); emit is deterministic.
 */
class PolarsScriptHygieneTest :
    FunSpec({
        val python3 = which("python3")

        fun goldensUnder(vararg dirs: String): List<Path> =
            dirs.flatMap { d ->
                val root = Paths.get("src/test/golden", d)
                if (!Files.exists(root)) {
                    emptyList()
                } else {
                    Files.walk(root).use { s -> s.toList().filter { it.extension == "py" } }
                }
            }

        test("every emitted Python golden compiles with py_compile") {
            if (python3 == null) {
                System.err.println("SKIP: python3 not on PATH — py_compile check skipped (CI runs it)")
                return@test
            }
            val files = goldensUnder("polars", "transfers")
            files.forEach { f ->
                val proc =
                    ProcessBuilder(python3.toString(), "-m", "py_compile", f.toString())
                        .redirectErrorStream(true)
                        .start()
                val out = proc.inputStream.readBytes().decodeToString()
                val code = proc.waitFor()
                withClue(f, out) { code shouldBe 0 }
            }
        }

        test("emit is deterministic — the same island yields byte-identical scripts") {
            val salesSchema = listOf("customer" to "string", "amount" to "float")
            val l =
                org.tatrman.ttrp.graph.model
                    .Load("l1", "sales", org.tatrman.ttrp.ast.SourceLocation.UNKNOWN, "files.sales")
            val step = PolarsStep("sales", l, source = PolarsSource.Csv("/data/sales.csv", salesSchema))
            val first = PolarsIslandEmitter().emit("d", listOf(step)).text
            val second = PolarsIslandEmitter().emit("d", listOf(step)).text
            first shouldBe second
        }
    })

private fun which(cmd: String): Path? =
    System
        .getenv("PATH")
        .orEmpty()
        .split(java.io.File.pathSeparatorChar)
        .map { Paths.get(it, cmd) }
        .firstOrNull { Files.isExecutable(it) }

private fun TestScope.withClue(
    file: Path,
    output: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("py_compile failed for $file:\n$output", e)
    }
}
