package org.tatrman.ttr.semantics.conformance

import org.tatrman.ttr.parser.loader.ParseResult
import org.tatrman.ttr.semantics.ResolutionContext
import org.tatrman.ttr.semantics.ResolutionResult
import org.tatrman.ttr.semantics.Resolver
import org.tatrman.ttr.semantics.ResolvedManifest
import org.tatrman.ttr.semantics.SemanticDocument
import org.tatrman.ttr.semantics.StockLoader
import org.tatrman.ttr.semantics.SymbolTable
import org.tatrman.ttr.semantics.Validator
import org.tatrman.ttr.semantics.collectAllReferences
import org.tatrman.ttr.semantics.enclosingQnameOf

/**
 * Kotlin side of the *semantics* conformance dump (contracts.md §5): loads the
 * stock vocab, builds the symbol table, resolves every reference and runs the
 * portable validator subset, then emits a `{ diagnostics, resolved }` object
 * byte-identical to the TS `dump-sem.ts` output.
 *
 * The validator subset and the always-load-stock behaviour are mirrored exactly
 * on the TS side; positions are excluded (the Kotlin `Reference` value class
 * carries none), so `resolved` keys on `<refPath> => <qname>` and `diagnostics`
 * on the code only.
 */
object SemanticsConformanceDump {
    fun dump(
        result: ParseResult,
        uri: String,
    ): String {
        val symbols = SymbolTable()
        symbols.upsertDocument("stock://cnc-roles.ttr", StockLoader.load(), "cnc", "role", "")

        val schemaCode = result.schemaDirective?.schemaCode ?: "db"
        val namespace = result.schemaDirective?.namespace ?: ""
        val packageName = result.packageName ?: ""
        symbols.upsertDocument(uri, result.definitions, schemaCode, namespace, packageName)

        val resolver = Resolver(symbols)
        val validator = Validator(symbols, resolver, ResolvedManifest())
        val doc = SemanticDocument(uri, result.definitions, schemaCode, namespace, packageName, result.imports)

        val resolved =
            collectAllReferences(result.definitions)
                .mapNotNull { c ->
                    val enc = enclosingQnameOf(c.ownerDef, schemaCode, namespace, packageName)
                    val res =
                        resolver.resolveReference(
                            Resolver.Ref(c.path, c.parts),
                            ResolutionContext(schemaCode, namespace, enc, result.imports, packageName),
                        )
                    if (res is ResolutionResult.Resolved) "${c.path} => ${res.symbol.qname}" else null
                }.sorted()

        val diagnostics =
            (
                validator.validateDocument(doc) +
                    validator.validateReferences(doc) +
                    validator.validateProject() +
                    validator.validateImports(doc)
            ).map { it.code.id }.sorted()

        return render(diagnostics, resolved)
    }

    /** Matches `JSON.stringify({ diagnostics, resolved }, null, 4)`. */
    private fun render(
        diagnostics: List<String>,
        resolved: List<String>,
    ): String {
        fun arr(items: List<String>): String =
            if (items.isEmpty()) {
                "[]"
            } else {
                items.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n    ]") { "        ${quote(it)}" }
            }
        return "{\n    \"diagnostics\": ${arr(diagnostics)},\n    \"resolved\": ${arr(resolved)}\n}"
    }

    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
