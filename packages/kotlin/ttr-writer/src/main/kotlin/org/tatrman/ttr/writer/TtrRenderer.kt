// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.writer

import org.tatrman.ttr.parser.loader.ParseResult
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.ColumnDef
import org.tatrman.ttr.parser.model.CubeletDef
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.DimensionDef
import org.tatrman.ttr.parser.model.DrillMapDef
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.Er2CncRoleDef
import org.tatrman.ttr.parser.model.Er2DbAttributeDef
import org.tatrman.ttr.parser.model.Er2DbEntityDef
import org.tatrman.ttr.parser.model.Er2DbRelationDef
import org.tatrman.ttr.parser.model.FkDef
import org.tatrman.ttr.parser.model.HierarchyDef
import org.tatrman.ttr.parser.model.ImportStatement
import org.tatrman.ttr.parser.model.MdDomainDef
import org.tatrman.ttr.parser.model.MdMapDef
import org.tatrman.ttr.parser.model.MeasureDef
import org.tatrman.ttr.parser.model.LocalizedStringListValue
import org.tatrman.ttr.parser.model.LocalizedStringValue
import org.tatrman.ttr.parser.model.BindingColumnBareId
import org.tatrman.ttr.parser.model.BindingColumnEntry
import org.tatrman.ttr.parser.model.BindingColumnObject
import org.tatrman.ttr.parser.model.BindingColumnValue
import org.tatrman.ttr.parser.model.BindingProperty
import org.tatrman.ttr.parser.model.BindingPropertyBareId
import org.tatrman.ttr.parser.model.BindingPropertyBlock
import org.tatrman.ttr.parser.model.ProjectDef
import org.tatrman.ttr.parser.model.ProcedureDef
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.QueryDef
import org.tatrman.ttr.parser.model.RelationDef
import org.tatrman.ttr.parser.model.RoleDef
import org.tatrman.ttr.parser.model.ModelDirective
import org.tatrman.ttr.parser.model.SearchHintsValue
import org.tatrman.ttr.parser.model.SemanticsBlock
import org.tatrman.ttr.parser.model.SemanticsValue
import org.tatrman.ttr.parser.model.TableDef
import org.tatrman.ttr.parser.model.TaggedBlockValue
import org.tatrman.ttr.parser.model.TargetObjectValue
import org.tatrman.ttr.parser.model.TargetReferenceValue
import org.tatrman.ttr.parser.model.StorageDef
import org.tatrman.ttr.parser.model.TargetValue
import org.tatrman.ttr.parser.model.ViewDef
import org.tatrman.ttr.parser.model.WorldDef
import org.tatrman.ttr.parser.model.WorldSchemaDef

/**
 * Renders the typed model back to TTR text. Property ordering within a block is
 * stable so output is diff-stable, and triple-string form is used for any value
 * containing a newline. See contracts.md §3.
 */
object TtrRenderer {
    private val KIND_ORDER =
        listOf(
            ProjectDef::class,
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
            WorldDef::class,
        )

    /** Contract §3 — render definitions with an optional leading schema directive. */
    fun render(
        definitions: List<Definition>,
        modelDirective: ModelDirective? = null,
    ): String = renderFile(modelDirective?.modelCode, modelDirective?.schema, definitions)

    /** Contract §3 — convenience overload rendering a whole [ParseResult] (package, imports, schema, defs). */
    fun render(result: ParseResult): String =
        renderFile(
            schemaCode = result.modelDirective?.modelCode,
            namespace = result.modelDirective?.schema,
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
            sb.append("model $schemaCode")
            if (namespace != null) sb.append(" schema $namespace")
            sb.appendLine()
            sb.appendLine()
        }
        sb.append(renderDefinitions(definitions))
        return sb.toString()
    }

    private fun renderImport(imp: ImportStatement): String = if (imp.wildcard) "${imp.target}.*" else imp.target

    /**
     * The v3.1 MD *logical* defs (domain/dimension/map/measure/cubelet/hierarchy). Before the MD
     * dot-path arc these were dropped at parse (`ttr-parser` had no MD def types), so they never
     * reached the renderer; there is no surface renderer for them yet. They are skipped here to
     * preserve that effective behaviour until a real MD renderer lands (dot-path arc, later phase).
     */
    private fun isMdLogicalDef(def: Definition): Boolean =
        def is MdDomainDef ||
            def is DimensionDef ||
            def is MdMapDef ||
            def is HierarchyDef ||
            def is MeasureDef ||
            def is CubeletDef

    private fun renderDefinitions(definitions: List<Definition>): String {
        val sb = StringBuilder()
        val grouped = definitions.filterNot { isMdLogicalDef(it) }.groupBy { it::class }
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
            is ProjectDef -> renderModel(def)
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
            is WorldDef -> renderWorld(def)
            // v3.1 MD logical defs have no surface renderer yet — see [isMdLogicalDef]. Emit nothing
            // rather than throwing; `renderDefinitions` already filters them out of file output.
            is MdDomainDef, is DimensionDef, is MdMapDef, is HierarchyDef, is MeasureDef, is CubeletDef -> ""
            else -> error("Unsupported Definition subtype: ${def::class.simpleName}")
        }

    // v4.1 world model. Members render in parse order (engines, executors,
    // storages); typed props precede the free-form manifest. Deterministic (not
    // canonicalising) — the round-trip spec asserts re-parse + render-twice
    // stability, not byte-equality with hand-authored source.
    private fun renderWorld(def: WorldDef): String {
        val sb = StringBuilder("def world ${def.name} {")
        def.description?.let { sb.append(" description: ${renderString(it)},") }
        renderTagsIfAny(def.tags)?.let { sb.append(" $it,") }
        def.extends?.let { sb.append(" extends: $it,") }
        for (e in def.engines) {
            sb.append(" ").append(
                renderEnginePart("engine", e.name, e.description, e.tags, e.type, e.version, e.extends, e.manifest),
            )
        }
        for (e in def.executors) {
            sb.append(" ").append(
                renderEnginePart("executor", e.name, e.description, e.tags, e.type, e.version, e.extends, e.manifest),
            )
        }
        for (s in def.storages) sb.append(" ").append(renderStorage(s))
        sb.append(" }")
        return sb.toString()
    }

    @Suppress("LongParameterList")
    private fun renderEnginePart(
        kind: String,
        name: String,
        description: String?,
        tags: List<String>,
        type: String?,
        version: String?,
        extends: String?,
        manifest: Map<String, PropertyValue>,
    ): String {
        val sb = StringBuilder("def $kind $name {")
        type?.let { sb.append(" type: $it,") }
        version?.let { sb.append(" version: ${renderString(it)},") }
        extends?.let { sb.append(" extends: $it,") }
        description?.let { sb.append(" description: ${renderString(it)},") }
        renderTagsIfAny(tags)?.let { sb.append(" $it,") }
        for ((k, v) in manifest) sb.append(" $k: ${renderPropertyValue(v)},")
        sb.append(" }")
        return sb.toString()
    }

    private fun renderStorage(s: StorageDef): String {
        val sb = StringBuilder("def storage ${s.name} {")
        s.type?.let { sb.append(" type: $it,") }
        s.via?.let { sb.append(" via: $it,") }
        if (s.hosts.isNotEmpty()) sb.append(" hosts: [${s.hosts.joinToString(", ")}],")
        if (s.staging) sb.append(" staging: true,")
        s.extends?.let { sb.append(" extends: $it,") }
        s.description?.let { sb.append(" description: ${renderString(it)},") }
        renderTagsIfAny(s.tags)?.let { sb.append(" $it,") }
        for (sc in s.schemas) sb.append(" ").append(renderWorldSchema(sc))
        for ((k, v) in s.manifest) sb.append(" $k: ${renderPropertyValue(v)},")
        sb.append(" }")
        return sb.toString()
    }

    private fun renderWorldSchema(sc: WorldSchemaDef): String {
        val fields = sc.fields.joinToString(", ") { "${it.name}: ${it.type}" }
        return "def schema ${sc.name} { $fields }"
    }

    private fun renderModel(def: ProjectDef): String {
        val sb = StringBuilder()
        sb.append("def project ${def.name} {")
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
        renderSemanticsIfAny(def.semantics)?.let { sb.append(it) }
        def.binding?.let {
            sb.append(" binding: ")
            sb.append(renderBinding(it))
            sb.append(",")
        }
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
        renderSemanticsIfAny(def.semantics)?.let { sb.append(it) }
        def.binding?.let {
            sb.append(" binding: ")
            sb.append(renderBinding(it))
            sb.append(",")
        }
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
        renderSemanticsIfAny(def.semantics)?.let { sb.append(it) }
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
        (def.definitionSqlBlock?.let { renderPropertyValue(it) } ?: def.definitionSql?.let { renderString(it) })?.let {
            sb.append(" definitionSql: ")
            sb.append(it)
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
        renderSemanticsIfAny(def.semantics)?.let { sb.append(it) }
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
        def.binding?.let {
            sb.append(" binding: ")
            sb.append(renderBinding(it))
            sb.append(",")
        }
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
        // Prefer the structured block (preserves the tagged-block carrier);
        // fall back to the flattened text for models built without a block.
        (def.sourceTextBlock?.let { renderPropertyValue(it) } ?: def.sourceText?.let { renderString(it) })?.let {
            sb.append(" sourceText: ")
            sb.append(it)
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

    /** Grounding Phase 1 (grammar 4.2) — stable `kind`|`role` first, then refs, then params. */
    private val SEM_KEY_ORDER = listOf("kind", "role", "period", "currency", "code_format")

    private val SEM_IDENT = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$")

    private fun renderSemanticsIfAny(s: SemanticsBlock?): String? {
        if (s == null || s.entries.isEmpty()) return null
        val ordered =
            s.entries.keys.sortedWith(
                compareBy({ SEM_KEY_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }),
            )
        val body = ordered.joinToString(", ") { k -> "$k: ${renderSemValue(s.entries.getValue(k))}" }
        return " semantics { $body }"
    }

    private fun renderSemValue(v: SemanticsValue): String =
        when (v) {
            // An id-safe string re-emits as a bare id (round-trips to the same Str);
            // anything else is quoted. Numbers/bools/null keep their primitive form.
            is SemanticsValue.Str -> if (SEM_IDENT.matches(v.value)) v.value else renderString(v.value)
            is SemanticsValue.Num ->
                v.value
                    .toBigDecimal()
                    .stripTrailingZeros()
                    .toPlainString()
            is SemanticsValue.Bool -> v.value.toString()
            is SemanticsValue.NullV -> "null"
        }

    private fun renderTargetValue(t: TargetValue): String =
        when (t) {
            is TargetObjectValue -> renderPropertyValue(t.obj)
            is TargetReferenceValue -> t.ref.path
        }

    /**
     * v3.0 inline `binding:` value (was v2.1 `mapping:`). Two surface forms:
     *  - bare-id: `binding: IDSKUPZBOZI` / `binding: db.dbo.fk_artikl_produkt`.
     *  - block:   `binding: { target: …, columns: { … } }` (entity) /
     *             `{ target: … }` (attribute) / `{ fk: … }` (relation).
     * Single-line by design so it appends inline like the surrounding properties
     * and round-trips through parse→render→parse.
     */
    private fun renderBinding(m: BindingProperty): String =
        when (m) {
            is BindingPropertyBareId -> m.id.path
            is BindingPropertyBlock -> {
                val parts = mutableListOf<String>()
                m.target?.let { parts.add("target: ${renderTargetValue(it)}") }
                if (m.columns.isNotEmpty()) parts.add("columns: ${renderBindingColumns(m.columns)}")
                m.fk?.let { parts.add("fk: ${it.path}") }
                "{ ${parts.joinToString(", ")} }"
            }
        }

    private fun renderBindingColumns(entries: List<BindingColumnEntry>): String =
        entries.joinToString(", ", prefix = "{ ", postfix = " }") { e ->
            "${e.name}: ${renderBindingColumnValue(e.value)}"
        }

    private fun renderBindingColumnValue(v: BindingColumnValue): String =
        when (v) {
            is BindingColumnBareId -> v.id.path
            is BindingColumnObject -> renderPropertyValue(v.obj)
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
            // embedded-sql (1.5): render back to the tagged triple-string carrier
            // `"""<tag>␊<value>␊"""`. Body is emitted un-indented — matching the
            // existing triple-string convention (renderString) — so a re-parse
            // dedents to the same `value`/`indentWidth` (round-trip spec). The
            // close-fence newline the walker strips is restored here. `value`
            // can never contain `"""` (the lexer fence is non-greedy), so no
            // escaping is needed. Canonical re-indentation is a formatter concern.
            is TaggedBlockValue -> "\"\"\"${v.tag}\n${v.value}\n\"\"\""
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
