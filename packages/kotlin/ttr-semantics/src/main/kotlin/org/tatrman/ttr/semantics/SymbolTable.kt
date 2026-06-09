package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.Er2DbAttributeDef
import org.tatrman.ttr.parser.model.Er2DbEntityDef
import org.tatrman.ttr.parser.model.Er2DbRelationDef
import org.tatrman.ttr.parser.model.ProcedureDef
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TableDef
import org.tatrman.ttr.parser.model.ViewDef

/**
 * Distinguishes explicit `def er2db_*` declarations from symbols synthesized
 * from inline `mapping:` properties (v2.1). Only set on er2db_* kinds.
 */
enum class MappingSource { Explicit, Inline }

/**
 * One symbol-table entry. Mirrors TS `SymbolEntry`
 * (`packages/semantics/src/symbol-table.ts`).
 */
data class SymbolEntry(
    val qname: String,
    val kind: String,
    val name: String,
    val source: SourceLocation,
    val documentUri: String,
    val parent: String?,
    val packageName: String,
    val schemaCode: String,
    val mappingSource: MappingSource? = null,
)

/** A qname that resolves to more than one entry. */
data class Duplicate(
    val qname: String,
    val entries: List<SymbolEntry>,
)

/**
 * Per-document symbol builder. Mirrors TS `DocumentSymbolTable`: it constructs
 * qualified names from the document's package / schema / namespace exactly the
 * way `makeQname` / `makeQnameChild` do (including the transitional doubled
 * `cnc.cnc.role.*` shape for stock CNC vocabulary loaded from a `stock://` URI).
 */
internal class DocumentSymbols(
    private val documentUri: String,
    definitions: List<Definition>,
    private val schemaCode: String,
    private val namespace: String,
    private val packageName: String,
) {
    private val entries = LinkedHashMap<String, SymbolEntry>()

    init {
        for (def in definitions) addEntry(def)
    }

    fun all(): List<SymbolEntry> = entries.values.toList()

    private val isStockCnc: Boolean
        get() = schemaCode == "cnc" && packageName.isEmpty() && documentUri.startsWith("stock://")

    private fun makeQname(
        parts: List<String>,
        namespaceOrKind: String,
    ): String {
        val segments = mutableListOf<String>()
        if (packageName.isNotEmpty()) segments += packageName
        if (isStockCnc) segments += "cnc"
        segments += schemaCode
        if (namespaceOrKind.isNotEmpty()) segments += namespaceOrKind
        segments += parts
        return segments.joinToString(".")
    }

    private fun makeQnameChild(
        parentEntry: SymbolEntry,
        childName: String,
    ): String {
        val segments = mutableListOf<String>()
        if (packageName.isNotEmpty()) segments += packageName
        if (isStockCnc) segments += "cnc"
        segments += schemaCode
        if (namespace.isNotEmpty()) segments += namespace else segments += parentEntry.kind
        segments += parentEntry.name
        segments += childName
        return segments.joinToString(".")
    }

    private fun addEntry(def: Definition) {
        val kind = kindOf(def)
        val nsOrKind = namespace.ifEmpty { kind }
        val qnameStr = makeQname(listOf(def.name), nsOrKind)

        val entry =
            SymbolEntry(
                qname = qnameStr,
                kind = kind,
                name = def.name,
                source = def.source,
                documentUri = documentUri,
                parent = null,
                packageName = packageName,
                schemaCode = schemaCode,
                mappingSource =
                    if (def is Er2DbEntityDef || def is Er2DbAttributeDef || def is Er2DbRelationDef) {
                        MappingSource.Explicit
                    } else {
                        null
                    },
            )
        entries[qnameStr] = entry

        when (def) {
            is EntityDef -> def.attributes.forEach { addChild(entry, it.name, "attribute", it.source) }
            is TableDef -> def.columns.forEach { addChild(entry, it.name, "column", it.source) }
            is ViewDef -> def.columns.forEach { addChild(entry, it.name, "column", it.source) }
            is ProcedureDef -> def.resultColumns.forEach { addChild(entry, it.name, "column", it.source) }
            else -> {}
        }
    }

    private fun addChild(
        parentEntry: SymbolEntry,
        childName: String,
        kind: String,
        source: SourceLocation,
    ) {
        val q = makeQnameChild(parentEntry, childName)
        entries[q] =
            SymbolEntry(
                qname = q,
                kind = kind,
                name = childName,
                source = source,
                documentUri = documentUri,
                parent = parentEntry.qname,
                packageName = packageName,
                schemaCode = schemaCode,
            )
    }
}

/**
 * Project-wide symbol table aggregating [DocumentSymbols] across documents.
 * Mirrors TS `ProjectSymbolTable` (`packages/semantics/src/project-symbols.ts`).
 *
 * Conflict semantics match TS: duplicate qnames are NOT rejected — they are
 * kept and surfaced via [duplicates]; [get] returns the first.
 */
class SymbolTable {
    private val byDocument = LinkedHashMap<String, DocumentSymbols>()
    private val byQname = LinkedHashMap<String, MutableList<SymbolEntry>>()

    fun upsertDocument(
        uri: String,
        definitions: List<Definition>,
        schemaCode: String,
        namespace: String,
        packageName: String = "",
    ) {
        if (byDocument.containsKey(uri)) removeDocument(uri)
        val table = DocumentSymbols(uri, definitions, schemaCode, namespace, packageName)
        byDocument[uri] = table
        for (entry in table.all()) {
            byQname.getOrPut(entry.qname) { mutableListOf() }.add(entry)
        }
    }

    fun removeDocument(uri: String) {
        val table = byDocument[uri] ?: return
        for (entry in table.all()) {
            val list = byQname[entry.qname] ?: continue
            list.removeAll { it.documentUri == uri }
            if (list.isEmpty()) byQname.remove(entry.qname)
        }
        byDocument.remove(uri)
    }

    fun get(qname: String): SymbolEntry? = byQname[qname]?.firstOrNull()

    fun getAll(qname: String): List<SymbolEntry> = byQname[qname] ?: emptyList()

    fun allQnames(): List<String> = byQname.keys.toList()

    fun all(): List<SymbolEntry> {
        val result = mutableListOf<SymbolEntry>()
        val seen = HashSet<String>()
        for (entries in byQname.values) {
            for (entry in entries) {
                if (seen.add(entry.qname)) result += entry
            }
        }
        return result
    }

    fun findByName(name: String): List<SymbolEntry> = all().filter { it.name == name }

    fun duplicates(): List<Duplicate> =
        byQname.entries
            .filter { it.value.size > 1 }
            .map { Duplicate(it.key, it.value.toList()) }

    fun getByPackage(packageName: String): List<SymbolEntry> = all().filter { it.packageName == packageName }

    fun getBySuffix(suffix: String): List<SymbolEntry> {
        val result = mutableListOf<SymbolEntry>()
        for (entries in byQname.values) {
            for (entry in entries) {
                if (entry.qname.endsWith(".$suffix") || entry.qname == suffix) result += entry
            }
        }
        return result
    }

    fun listPackages(): List<String> = all().map { it.packageName }.toSortedSet().toList()

    // ----- contract §4.2 aliases -----

    fun lookup(qname: Qname): SymbolEntry? = get(qname.value)

    fun findUnderPackage(pkg: Qname): List<SymbolEntry> = getByPackage(pkg.value)

    fun findByLastSegment(last: String): List<SymbolEntry> = getBySuffix(last)
}
