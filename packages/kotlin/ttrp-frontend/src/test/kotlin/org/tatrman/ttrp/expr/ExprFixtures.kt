// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.expr

/** Loads expression-corpus fixtures verbatim from `src/test/resources/expr`. */
object ExprFixtures {
    /** Parses `golden.exprs`: `<expr> :: <expected-type>` per non-blank line. */
    fun goldenExprs(): List<Pair<String, String>> =
        read("/expr/golden.exprs")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val idx = line.lastIndexOf("::")
                require(idx >= 0) { "malformed golden.exprs line: $line" }
                line.substring(0, idx).trim() to line.substring(idx + 2).trim()
            }.toList()

    /** A negative fixture: (expression source, expected id). First line = source, `# expect: <id>` line = id. */
    fun negative(name: String): Pair<String, String> {
        val text = read("/expr/negative/$name")
        val lines = text.lines()
        val exprLines = lines.takeWhile { !it.trimStart().startsWith("# expect:") }
        val expect =
            lines
                .first { it.trimStart().startsWith("# expect:") }
                .substringAfter("# expect:")
                .trim()
        return exprLines.joinToString("\n").trim() to expect
    }

    fun read(path: String): String =
        ExprFixtures::class.java
            .getResourceAsStream(path)
            ?.readBytes()
            ?.decodeToString()
            ?: error("fixture not found: $path")
}

/** The hand-declared test schema (S23 spellings) shared by the expression specs. */
object TestSchema {
    private val columns =
        listOf(
            Column("amount", TtrpType.Decimal()),
            Column("customer", TtrpType.Str),
            Column("region", TtrpType.Str),
            Column("total", TtrpType.Decimal()),
            Column("account_id", TtrpType.Integer),
            // review-001 1.2-E / 1.2-B: a double + two temporals to exercise the coercion lattice.
            Column("balance", TtrpType.Double),
            Column("order_date", TtrpType.Date),
            Column("ship_ts", TtrpType.Timestamp),
        )

    /** db-schema-typed columns (S23) on the default/left/right ports, shared by the expression specs. */
    val schema: Map<String, List<Column>> = mapOf("" to columns, "left" to columns, "right" to columns)
}
