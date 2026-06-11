package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.ImportStatement

/**
 * Shared test helpers: parse TTR source and populate a [SymbolTable], mirroring
 * the `tableWith` / `setup` helpers in the TS semantics specs under
 * `packages/semantics/src/__tests__/`.
 */
object Fixtures {
    /** Upsert one source document into [table] under [uri]. */
    fun upsert(
        table: SymbolTable,
        uri: String,
        src: String,
    ) {
        val r = TtrLoader.parseString(src, uri)
        val schemaCode = r.schemaDirective?.schemaCode ?: "db"
        val namespace = r.schemaDirective?.namespace ?: ""
        table.upsertDocument(uri, r.definitions, schemaCode, namespace, r.packageName ?: "")
    }

    /** Build a [SymbolTable] from `(uri to src)` pairs. */
    fun symbolTable(vararg docs: Pair<String, String>): SymbolTable {
        val table = SymbolTable()
        for ((uri, src) in docs) upsert(table, uri, src)
        return table
    }

    /** Build a [PackageGraphBuilder] (symbol table + per-document imports) from `(uri to src)` pairs. */
    fun packageGraph(vararg docs: Pair<String, String>): PackageGraphBuilder {
        val table = SymbolTable()
        val imports = mutableMapOf<String, List<ImportStatement>>()
        for ((uri, src) in docs) {
            val r = TtrLoader.parseString(src, uri)
            val schemaCode = r.schemaDirective?.schemaCode ?: "db"
            val namespace = r.schemaDirective?.namespace ?: ""
            table.upsertDocument(uri, r.definitions, schemaCode, namespace, r.packageName ?: "")
            imports[uri] = r.imports
        }
        return PackageGraphBuilder(table, imports)
    }
}
