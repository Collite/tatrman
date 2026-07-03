package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.Definition

/**
 * Loads the bundled stock CNC vocabulary — the single source of truth for stock
 * roles (contract §4.7). Ported from `packages/semantics/src/stock-loader.ts`;
 * the resource is the same `.ttr` content as `packages/semantics/src/stock/`.
 *
 * Resource path: `/builtin/cnc-stock-roles.ttr` inside the jar.
 */
object StockLoader {
    private const val RESOURCE = "builtin/cnc-stock-roles.ttr"

    private val definitions: List<Definition> by lazy {
        val content = readResource()
        val result = TtrLoader.parseString(content, "stock://cnc-stock-roles.ttr")
        if (result.errors.isEmpty()) result.definitions else emptyList()
    }

    /** Parsed stock definitions (the six CNC `def role` entries). */
    fun load(): List<Definition> = definitions

    /**
     * The stock role qnames as they are actually stored/resolved — the v4.0
     * uniform `cnc.role.<name>` form produced by [SymbolTable] (D15: stock cnc no
     * longer doubles). Matches what `Resolver` returns (e.g. `fact => cnc.role.fact`),
     * so a consumer can `symbols.get(q)` each qname and hit the stored symbol.
     */
    fun stockQnames(): Set<Qname> = load().map { Qname("cnc.role.${it.name}") }.toSet()

    private fun readResource(): String {
        val stream =
            Thread.currentThread().contextClassLoader?.getResourceAsStream(RESOURCE)
                ?: StockLoader::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?: error("stock resource not found on the classpath: $RESOURCE")
        return stream.bufferedReader().use { it.readText() }
    }
}
