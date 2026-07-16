// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.conform.eval

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Aggregation
import org.tatrman.ttrp.graph.model.Branch
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.Display
import org.tatrman.ttrp.graph.model.Distinct
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType
import org.tatrman.ttrp.graph.model.Limit
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Select
import org.tatrman.ttrp.graph.model.Sort
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.graph.model.Union
import java.nio.file.Files
import java.nio.file.Path

/**
 * Engine-free test scaffolding for the eval harness: builds a `TtrpGraph` from a list of node
 * kinds, and a stub front-half compiler that reads a `shape:` / `invalid:` marker from a source.
 * The real front-half compiler is wired in the CLI; the harness is compile-agnostic (injected),
 * so its logic is exercised deterministically here without a world.
 */
object EvalTestGraphs {
    private val loc = SourceLocation.UNKNOWN

    private fun node(
        kind: String,
        id: String,
    ): Node =
        when (kind.trim()) {
            "load" -> Load(id, id, loc, source = "src")
            "filter" -> Filter(id, id, loc, predicate = ColumnRef(null, "x", loc))
            "aggregate" ->
                Aggregate(
                    id,
                    id,
                    loc,
                    groupBy = listOf("region"),
                    aggregations = listOf(Aggregation("total", ColumnRef(null, "amount", loc))),
                )
            "sort" -> Sort(id, id, loc, keys = listOf("total"))
            "limit" -> Limit(id, id, loc, count = 10L)
            "calc" -> Calc(id, id, loc)
            "distinct" -> Distinct(id, id, loc)
            "display" -> Display(id, id, loc, name = "out")
            "join" -> Join(id, id, loc, type = JoinType.INNER, on = ColumnRef(null, "x", loc))
            "branch" -> Branch(id, id, loc, predicate = ColumnRef(null, "x", loc))
            "select" -> Select(id, id, loc, columns = listOf("x"))
            "union" -> Union(id, id, loc, arity = 2)
            else -> error("unknown node kind in test shape: $kind")
        }

    fun graphOf(kinds: List<String>): TtrpGraph {
        val nodes = kinds.mapIndexed { i, k -> "n$i" to node(k, "n$i") }.toMap()
        return TtrpGraph(nodes, emptyList(), emptyMap())
    }

    /** A stub compiler: `shape: a,b,c` → a graph of those kinds; `invalid: id|id` → error diagnostics. */
    fun stubCompile(
        source: String,
        @Suppress("UNUSED_PARAMETER") fileName: String,
    ): EvalRunner.CompileOutcome {
        Regex("""invalid:\s*(.+)""").find(source)?.let {
            return EvalRunner.CompileOutcome(
                null,
                it.groupValues[1]
                    .trim()
                    .split("|")
                    .map(String::trim),
            )
        }
        val shape = Regex("""shape:\s*(.+)""").find(source)?.groupValues?.get(1)
        val kinds = shape?.split(",")?.map(String::trim)?.filter { it.isNotEmpty() } ?: emptyList()
        return EvalRunner.CompileOutcome(graphOf(kinds), emptyList())
    }

    /** Locate the committed eval corpus dir from any working directory. */
    fun corpusDir(): Path {
        val rel = Path.of("packages/kotlin/ttrp-conform/src/test/eval")
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            if (Files.isDirectory(dir.resolve(rel))) return dir.resolve(rel)
            if (Files.isDirectory(dir.resolve("src/test/eval"))) return dir.resolve("src/test/eval")
            dir = dir.parent
        }
        error("could not locate the eval corpus dir")
    }
}
