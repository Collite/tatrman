package org.tatrman.ttr.writer

import org.tatrman.ttr.parser.loader.ParseResult
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.ColumnDef
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.DrillMapDef
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.Er2CncRoleDef
import org.tatrman.ttr.parser.model.Er2DbAttributeDef
import org.tatrman.ttr.parser.model.Er2DbEntityDef
import org.tatrman.ttr.parser.model.Er2DbRelationDef
import org.tatrman.ttr.parser.model.FkDef
import org.tatrman.ttr.parser.model.ImportStatement
import org.tatrman.ttr.parser.model.LocalizedStringListValue
import org.tatrman.ttr.parser.model.LocalizedStringValue
import org.tatrman.ttr.parser.model.ModelDef
import org.tatrman.ttr.parser.model.ProcedureDef
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.QueryDef
import org.tatrman.ttr.parser.model.RelationDef
import org.tatrman.ttr.parser.model.RoleDef
import org.tatrman.ttr.parser.model.SchemaDirective
import org.tatrman.ttr.parser.model.SearchHintsValue
import org.tatrman.ttr.parser.model.TableDef
import org.tatrman.ttr.parser.model.TargetObjectValue
import org.tatrman.ttr.parser.model.TargetReferenceValue
import org.tatrman.ttr.parser.model.TargetValue
import org.tatrman.ttr.parser.model.ViewDef

/**
 * Renders the typed model back to TTR text. Property ordering within a block is
 * stable so output is diff-stable, and triple-string form is used for any value
 * containing a newline. See contracts.md §3.
 */
object TtrRenderer {
    private val KIND_ORDER =
        listOf(
            ModelDef::class,
            RoleDef::class,
            EntityDef::class,
            TableDef::class,
            ViewDef::class,
            RelationDef::class,
            Er2DbEntityDef::class,
            Er2DbAttributeDef::class,
            Er2DbRelationDef::class,
            QueryDef::class,
            FkDef::class,
            ProcedureDef::class,
            Er2CncRoleDef::class,
            DrillMapDef::class,
        )

    /** Contract §3 — render definitions with an optional leading schema directive. */
    fun render(
        definitions: List<Definition>,
        schemaDirective: SchemaDirective? = null,
    ): String = renderFile(schemaDirective?.schemaCode, schemaDirective?.namespace, definitions)

    /** Contract §3 — convenience overload rendering a whole [ParseResult] (package, imports, schema, defs). */
    fun render(result: ParseResult): String =
        renderFile(
            schemaCode = result.schemaDirective?.schemaCode,
            namespace = result.schemaDirective?.namespace,
            definitions = result.definitions,
            packageName = result.packageName,
            imports = result.imports.map { renderImport(it) },
        )

    /** Renders a file with optional package / imports / schema directive followed by sorted definitions. */
    fun renderFile(
        schemaCode: String?,
        namespace: String?,
        definitions: List<Definition>,
        packageName: String? = null,
        imports: List<String> = emptyList(),
    ): String {
        val sb = StringBuilder()
        if (packageName != null) {
            sb.append("package $packageName")
            sb.appendLine()
        }
        for (imp in imports) {
            sb.append("import $imp")
            sb.appendLine()
        }
        if (schemaCode != null) {
            sb.append("schema $schemaCode")
            if (namespace != null) sb.append(" namespace $namespace")
            sb.appendLine()
            sb.appendLine()
        }
        sb.append(renderDefinitions(definitions))
        return sb.toString()
    }

    private fun renderImport(imp: ImportStatement): String = if (imp.wildcard) "${imp.target}.*" else imp.target

    private fun renderDefinitions(definitions: List<Definition>): String {
        val sb = StringBuilder()
        val grouped = definitions.groupBy { it::class }
        val sortedGroups = grouped.entries.sortedBy { (kind, _) -> KIND_ORDER.indexOf(kind) }
        for ((_, defs) in sortedGroups) {
            for (def in defs.sortedBy { it.name }) {
                sb.appendLine(renderDef(def))
            }
        }
        return sb.toString()
    }

    fun renderDef(def: Definition): String =
        when (def) {
            is ModelDef -> renderModel(def)
            is DrillMapDef -> renderDrillMap(def)
            is RoleDef -> renderRole(def)
            is EntityDef -> renderEntity(def)
            is TableDef -> renderTable(def)
            is ViewDef -> renderView(def)
            is ColumnDef -> renderColumn(def)
            is RelationDef -> renderRelation(def)
            is Er2DbEntityDef -> renderEr2DbEntity(def)
            is Er2DbAttributeDef -> renderEr2DbAttribute(def)
            is Er2DbRelationDef -> renderEr2DbRelation(def)
            is QueryDef -> renderQuery(def)
            is FkDef -> renderFk(def)
            is ProcedureDef -> renderProcedure(def)
            is Er2CncRoleDef -> renderEr2CncRole(def)
            is AttributeDef -> renderAttribute(def)
            else -> error("Unsupported Definition subtype: ${def::class.simpleName}")
        }

    private fun renderModel(def: ModelDef): String {
        val sb = StringBuilder()
        sb.append("def model ${def.name} {")
        def.version?.let { sb.append(" version: ${renderString(it)},") }
        def.description?.let { sb.append(" description: ${renderString(it)},") }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        sb.append(" }")
        return sb.toString()
    }

    private fun renderDrillMap(def: DrillMapDef): String {
        val sb = StringBuilder()
        sb.append("def drill_map ${def.name} {")
        def.from?.let { sb.append(" from: ${it.path},") }
        def.to?.let { sb.append(" to: ${it.path},") }
        sb.append(" args: { ")
        sb.append(def.args.entries.joinToString(", ") { (k, v) -> "$k: ${renderString(v)}" })
        sb.append(" },")
        def.display?.let { sb.append(" display: ${renderLocalizedString(it)},") }
        if (def.overrideAuto) sb.append(" override: true,")
        def.description?.let { sb.append(" description: ${renderString(it)},") }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        sb.append(" }")
        return sb.toString()
    }

    private fun renderRole(def: RoleDef): String {
        val sb = StringBuilder()
        sb.append("def role ${def.name}")
        sb.append(" {")
        def.label?.let { lbl ->
            sb.append(" label: ")
            sb.append(renderLocalizedString(lbl))
            sb.append(",")
        }
        def.description?.let { d ->
            sb.append(" description: ")
            sb.append(renderString(d))
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        renderSearchHintsIfAny(def.search)?.let { sb.append(it) }
        sb.append(" }")
        return sb.toString()
    }

    private fun renderEntity(def: EntityDef): String {
        val sb = StringBuilder()
        sb.append("def entity ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        def.labelPlural?.let {
            sb.append(" labelPlural: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        def.nameAttribute?.let {
            sb.append(" nameAttribute: ")
            sb.append(it.path)
            sb.append(",")
        }
        def.codeAttribute?.let {
            sb.append(" codeAttribute: ")
            sb.append(it.path)
            sb.append(",")
        }
        if (def.aliases.isNotEmpty()) {
            sb.append(" aliases: [")
            sb.append(def.aliases.joinToString(", ") { renderString(it) })
            sb.append("],")
        }
        // entity-level `roles: [...]` shorthand — the writer must round-trip them so a
        // model loaded → rendered → reparsed yields the same role attachments.
        if (def.roles.isNotEmpty()) {
            sb.append(" roles: [")
            sb.append(def.roles.joinToString(", ") { it.path })
            sb.append("],")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        def.displayLabel?.let { dl ->
            if (dl.byLanguage.isNotEmpty()) {
                sb.append(" displayLabel: ")
                sb.append(renderLocalizedString(dl))
                sb.append(",")
            }
        }
        renderSearchHintsIfAny(def.search)?.let { sb.append(it) }
        if (def.attributes.isNotEmpty()) {
            sb.appendLine()
            sb.append("    attributes: [")
            sb.appendLine()
            for (attr in def.attributes) {
                sb.append("        ")
                sb.append(renderAttribute(attr))
                sb.appendLine(",")
            }
            sb.append("    ]")
            sb.appendLine()
        }
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderAttribute(def: AttributeDef): String {
        val sb = StringBuilder()
        sb.append("def attribute ${def.name} {")
        def.type?.let {
            sb.append(" type: ${it.name},")
        }
        if (def.isKey) sb.append(" isKey: true,")
        if (def.optional) sb.append(" optional: true,")
        def.description?.let {
            sb.append(" description: ${renderString(it)},")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        def.displayLabel?.let {
            if (it.byLanguage.isNotEmpty()) {
                sb.append(" displayLabel: ${renderLocalizedString(it)},")
            }
        }
        if (def.valueLabels.isNotEmpty()) {
            sb.append(" valueLabels: { ")
            sb.append(
                def.valueLabels.entries.joinToString(", ") { (k, v) ->
                    "\"$k\": ${renderLocalizedString(v)}"
                },
            )
            sb.append(" },")
        }
        renderSearchHintsIfAny(def.search)?.let { sb.append(it) }
        sb.append(" }")
        return sb.toString()
    }

    private fun renderTable(def: TableDef): String {
        val sb = StringBuilder()
        sb.append("def table ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        if (def.primaryKey.isNotEmpty()) {
            sb.append(" primaryKey: [")
            sb.append(def.primaryKey.joinToString(", ") { renderString(it) })
            sb.append("],")
        }
        if (def.columns.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("    columns: [")
            for (col in def.columns) {
                sb.append("        ")
                sb.append(renderColumn(col))
                sb.appendLine(",")
            }
            sb.append("    ]")
        }
        renderSearchHintsIfAny(def.search)?.let { sb.append(it) }
        sb.appendLine()
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderView(def: ViewDef): String {
        val sb = StringBuilder()
        sb.append("def view ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        def.definitionSql?.let {
            sb.append(" definitionSql: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        if (def.columns.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("    columns: [")
            for (col in def.columns) {
                sb.append("        ")
                sb.append(renderColumn(col))
                sb.appendLine(",")
            }
            sb.append("    ]")
        }
        renderSearchHintsIfAny(def.search)?.let { sb.append(it) }
        sb.appendLine()
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderColumn(def: ColumnDef): String {
        val sb = StringBuilder()
        sb.append("def column ${def.name} {")
        def.type?.let {
            // The grammar has no `name(len, prec)` paren form — a structured type
            // must render as the object form `{ type: X, length: N, precision: P }`
            // so it round-trips. (The ai-platform renderer emitted unparseable
            // paren syntax here; modeler fixes it.)
            if (it.length != null || it.precision != null) {
                val parts = mutableListOf("type: ${it.name}")
                it.length?.let { l -> parts.add("length: $l") }
                it.precision?.let { p -> parts.add("precision: $p") }
                sb.append(" type: { ${parts.joinToString(", ")} },")
            } else {
                sb.append(" type: ${it.name},")
            }
        }
        if (def.isKey) sb.append(" isKey: true,")
        if (def.optional) sb.append(" optional: true,")
        if (def.indexed) sb.append(" indexed: true,")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        renderSearchHintsIfAny(def.search)?.let { sb.append(it) }
        sb.append(" }")
        return sb.toString()
    }

    private fun renderRelation(def: RelationDef): String {
        val sb = StringBuilder()
        sb.append("def relation ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        def.from?.let {
            sb.append(" from: ")
            sb.append(renderPropertyValue(it))
            sb.append(",")
        }
        def.to?.let {
            sb.append(" to: ")
            sb.append(renderPropertyValue(it))
            sb.append(",")
        }
        def.cardinality?.let {
            sb.append(" cardinality: ")
            sb.append(renderPropertyValue(it))
            sb.append(",")
        }
        if (def.join.isNotEmpty()) {
            sb.append(" join: [")
            sb.append(def.join.joinToString(", ") { renderPropertyValue(it) })
            sb.append("],")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        renderSearchHintsIfAny(def.search)?.let { sb.append(it) }
        sb.appendLine()
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderEr2DbEntity(def: Er2DbEntityDef): String {
        val sb = StringBuilder()
        sb.append("def er2db_entity ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        def.entity?.let {
            sb.append(" entity: ")
            sb.append(it.path)
            sb.append(",")
        }
        def.target?.let {
            sb.append(" target: ")
            sb.append(renderTargetValue(it))
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        sb.appendLine()
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderEr2DbAttribute(def: Er2DbAttributeDef): String {
        val sb = StringBuilder()
        sb.append("def er2db_attribute ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        def.attribute?.let {
            sb.append(" attribute: ")
            sb.append(it.path)
            sb.append(",")
        }
        def.target?.let {
            sb.append(" target: ")
            sb.append(renderTargetValue(it))
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        sb.appendLine()
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderEr2DbRelation(def: Er2DbRelationDef): String {
        val sb = StringBuilder()
        sb.append("def er2db_relation ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        def.relation?.let {
            sb.append(" relation: ")
            sb.append(it.path)
            sb.append(",")
        }
        def.fk?.let {
            sb.append(" fk: ")
            sb.append(it.path)
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        sb.appendLine()
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderEr2CncRole(def: Er2CncRoleDef): String {
        val sb = StringBuilder()
        sb.append("def er2cnc_role ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        def.entity?.let {
            sb.append(" entity: ")
            sb.append(it.path)
            sb.append(",")
        }
        def.role?.let {
            sb.append(" role: ")
            sb.append(it.path)
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        sb.appendLine()
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderQuery(def: QueryDef): String {
        val sb = StringBuilder()
        sb.append("def query ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        def.language?.let {
            sb.append(" language: $it,")
        }
        def.sourceText?.let {
            sb.append(" sourceText: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        renderSearchHintsIfAny(def.search)?.let { sb.append(it) }
        if (def.parameters.isNotEmpty()) {
            sb.appendLine()
            sb.append("    parameters: [")
            sb.appendLine()
            for (p in def.parameters) {
                sb.append("        ")
                sb.append(renderPropertyValue(p))
                sb.appendLine(",")
            }
            sb.append("    ]")
            sb.appendLine()
        }
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderFk(def: FkDef): String {
        val sb = StringBuilder()
        sb.append("def fk ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        def.from?.let {
            sb.append(" from: ")
            sb.append(renderPropertyValue(it))
            sb.append(",")
        }
        def.to?.let {
            sb.append(" to: ")
            sb.append(renderPropertyValue(it))
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        sb.appendLine()
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderProcedure(def: ProcedureDef): String {
        val sb = StringBuilder()
        sb.append("def procedure ${def.name}")
        sb.append(" {")
        def.description?.let {
            sb.append(" description: ")
            sb.append(renderString(it))
            sb.append(",")
        }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it") }
        if (def.parameters.isNotEmpty()) {
            sb.append(" parameters: [")
            sb.append(def.parameters.joinToString(", ") { renderPropertyValue(it) })
            sb.append("],")
        }
        if (def.resultColumns.isNotEmpty()) {
            sb.append(" resultColumns: [")
            sb.append(def.resultColumns.joinToString(", ") { renderColumn(it) })
            sb.append("],")
        }
        sb.appendLine()
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderLocalizedString(v: LocalizedStringValue): String {
        val entries =
            v.byLanguage.entries.joinToString(", ") { (lang, text) ->
                "$lang: ${renderString(text)}"
            }
        return "{ $entries }"
    }

    private fun renderLocalizedStringList(v: LocalizedStringListValue): String {
        val entries =
            v.byLanguage.entries.joinToString(", ") { (lang, items) ->
                "$lang: [${items.joinToString(", ") { renderString(it) }}]"
            }
        return "{ $entries }"
    }

    private fun renderTagsIfAny(tags: List<String>): String? {
        if (tags.isEmpty()) return null
        return "tags: [${tags.joinToString(", ") { renderString(it) }}],"
    }

    private fun renderSearchHintsIfAny(search: SearchHintsValue): String? {
        val hasContent =
            search.keywords.byLanguage.isNotEmpty() ||
                search.patterns.isNotEmpty() ||
                search.descriptions.byLanguage.isNotEmpty() ||
                search.examples.isNotEmpty() ||
                search.aliases.isNotEmpty() ||
                search.searchable ||
                search.fuzzy
        if (!hasContent) return null

        val sb = StringBuilder()
        sb.append(" search { ")
        val entries = mutableListOf<String>()
        if (search.searchable) entries.add("searchable: true")
        if (search.fuzzy) entries.add("fuzzy: true")
        if (search.keywords.byLanguage.isNotEmpty()) {
            entries.add("keywords ${renderLocalizedStringList(search.keywords)}")
        }
        if (search.patterns.isNotEmpty()) {
            entries.add("patterns: [${search.patterns.joinToString(", ") { renderString(it) }}]")
        }
        if (search.descriptions.byLanguage.isNotEmpty()) {
            entries.add("descriptions ${renderLocalizedStringList(search.descriptions)}")
        }
        if (search.examples.isNotEmpty()) {
            entries.add("examples: [${search.examples.joinToString(", ") { renderString(it) }}]")
        }
        if (search.aliases.isNotEmpty()) {
            entries.add("aliases: [${search.aliases.joinToString(", ") { renderString(it) }}]")
        }
        sb.append(entries.joinToString(", "))
        sb.append(" }")
        return sb.toString()
    }

    private fun renderTargetValue(t: TargetValue): String =
        when (t) {
            is TargetObjectValue -> renderPropertyValue(t.obj)
            is TargetReferenceValue -> t.ref.path
        }

    private fun renderPropertyValue(v: PropertyValue): String =
        when (v) {
            is PropertyValue.StringValue -> renderString(v.raw)
            is PropertyValue.TripleStringValue ->
                if (v.raw.contains("\"\"\"")) renderString(v.raw) else "\"\"\"${v.raw}\"\"\""
            is PropertyValue.NumberValue ->
                v.raw
                    .toBigDecimal()
                    .stripTrailingZeros()
                    .toPlainString()
            is PropertyValue.BoolValue -> v.raw.toString()
            is PropertyValue.NullValue -> "null"
            is PropertyValue.IdValue -> v.ref.path
            is PropertyValue.ListValue -> {
                val items = v.items.joinToString(", ") { renderPropertyValue(it) }
                "[$items]"
            }
            is PropertyValue.ObjectValue -> {
                val entries =
                    v.entries.entries.joinToString(", ") { (k, vp) ->
                        "$k: ${renderPropertyValue(vp)}"
                    }
                "{ $entries }"
            }
            is PropertyValue.FunctionCall -> {
                val args = v.args.joinToString(", ") { renderPropertyValue(it) }
                "${v.name}($args)"
            }
        }

    internal fun renderString(raw: String): String {
        if (raw.isEmpty()) return "\"\""
        if (raw.contains('\n') && !raw.contains("\"\"\"")) {
            return "\"\"\"$raw\"\"\""
        }
        val escaped =
            raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
