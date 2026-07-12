// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.conform.eval

import org.tomlj.Toml
import java.nio.file.Files
import java.nio.file.Path

/**
 * The assist/agent eval corpus (C4-e): NL request → expected graph shape, a versioned fixture
 * scored `ttrp-conform`-adjacent. Loaded from a `corpus.toml` (the versioned schema; TOML per
 * the P6/P7 fixture-format reconciliation, not the drafting YAML) beside a `fixtures/` dir. The
 * toolchain NEVER generates candidates (no LLM in the compiler, P2/C4-d-ii) — the corpus is the
 * scoring target only.
 */
data class EvalEntry(
    val id: String,
    val prompt: String,
    /** Relative path (under the corpus dir) of the expected canonical fixture. */
    val expected: String,
    /** Host-declared insertion target (C4-d-i γ); null = program scope. */
    val insertionTarget: InsertionTarget?,
    val tolerance: EvalComparator.Tolerance,
)

data class InsertionTarget(
    val fixture: String,
    val container: String,
)

class EvalCorpus(
    val root: Path,
    val entries: List<EvalEntry>,
) {
    fun expectedSource(entry: EvalEntry): String = Files.readString(root.resolve(entry.expected))

    fun fixtureExists(rel: String): Boolean = Files.exists(root.resolve(rel))

    companion object {
        /** Load a corpus directory: `<dir>/corpus.toml` beside a `fixtures` dir. */
        fun load(dir: Path): EvalCorpus {
            val toml = Toml.parse(Files.readString(dir.resolve("corpus.toml")))
            require(toml.errors().isEmpty()) { "corpus.toml parse errors: ${toml.errors()}" }
            val arr = toml.getArrayOrEmpty("entry")
            val entries =
                (0 until arr.size()).map { i ->
                    val t = arr.getTable(i)

                    fun req(k: String): String = t.getString(k) ?: error("corpus entry $i missing `$k`")
                    val insertion =
                        t.getTable("insertionTarget")?.let {
                            InsertionTarget(
                                fixture = it.getString("fixture") ?: error("insertionTarget missing `fixture`"),
                                container = it.getString("container") ?: error("insertionTarget missing `container`"),
                            )
                        }
                    EvalEntry(
                        id = req("id"),
                        prompt = req("prompt"),
                        expected = req("expected"),
                        insertionTarget = insertion,
                        tolerance = EvalComparator.Tolerance(extraCalcNodes = t.getBoolean("extraCalcNodes") ?: false),
                    )
                }
            return EvalCorpus(dir, entries)
        }
    }
}
