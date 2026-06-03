package org.tatrman.ttr.parser.model

/**
 * Typed model produced by the TTR parser.
 *
 * Each `def <kind> <name> { ... }` block in a `.ttr` file becomes one
 * [Definition] subtype. Cross-references are kept as raw [Reference]s — the
 * parser does NOT resolve them; semantic resolution is the consumer's job.
 *
 * Triple-string content is dedented (Python `textwrap.dedent` semantics) by
 * the walker before it lands here, so consumers see the canonical form.
 *
 * This is the modeler-canonical shape (group `org.tatrman`). Differences from
 * ai-platform's historical `shared.ttr.parser.model`:
 *  - v2.0.0: top-level `searchable` is GONE from [ColumnDef]/[AttributeDef];
 *    searchable lives only inside [SearchHintsValue]. (`indexed` stays a
 *    top-level column field — it matches the canonical TS `ColumnDef`.)
 *  - D4: [SourceLocation] is the richer ANTLR superset (endLine, endColumn,
 *    offsetStart, offsetEnd) and every [PropertyValue] variant carries a `source`.
 *  - [PropertyValue.TripleStringValue] is split out from `StringValue`.
 *  - [PropertyValue.IdValue] carries `parts` (the reference split on `.`).
 */
sealed interface Definition {
    val name: String
    val source: SourceLocation
    val description: String?
    val tags: List<String>
}

data class ModelDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val version: String? = null,
) : Definition

data class TableDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val primaryKey: List<String> = emptyList(),
    val columns: List<ColumnDef> = emptyList(),
    val indices: List<IndexDef> = emptyList(),
    val constraints: List<ConstraintDef> = emptyList(),
) : Definition

data class ViewDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val columns: List<ColumnDef> = emptyList(),
    val definitionSql: String? = null,
) : Definition

data class ColumnDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val type: DataType? = null,
    val optional: Boolean = false,
    val isKey: Boolean = false,
    val indexed: Boolean = false,
    val search: SearchHintsValue = SearchHintsValue(),
) : Definition

data class IndexDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val indexType: String? = null,
    val columns: List<String> = emptyList(),
) : Definition

data class ConstraintDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val constraintType: String? = null,
    val columns: List<String> = emptyList(),
) : Definition

data class FkDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val from: PropertyValue? = null,
    val to: PropertyValue? = null,
) : Definition

data class ProcedureDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val parameters: List<PropertyValue> = emptyList(),
    val resultColumns: List<ColumnDef> = emptyList(),
) : Definition

data class EntityDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val labelPlural: String? = null,
    val nameAttribute: Reference? = null,
    val codeAttribute: Reference? = null,
    val aliases: List<String> = emptyList(),
    val attributes: List<AttributeDef> = emptyList(),
    /** `roles: [fact, dimension]` shorthand; refs are resolved by the consumer. */
    val roles: List<Reference> = emptyList(),
    /** `displayLabel { cs: "...", en: "..." }`; null when absent. */
    val displayLabel: LocalizedStringValue? = null,
    /** `search { keywords {...} patterns [...] ... }`. Empty when absent. */
    val search: SearchHintsValue = SearchHintsValue(),
    /** v2.1 — inline `mapping: { target: ..., columns: { ... } }` block; null when absent. */
    val mapping: MappingProperty? = null,
) : Definition

data class AttributeDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val type: DataType? = null,
    val isKey: Boolean = false,
    val optional: Boolean = false,
    /** `displayLabel { cs: "...", en: "..." }`; null when absent. */
    val displayLabel: LocalizedStringValue? = null,
    /** `valueLabels { "1": { cs: "Aktivní", en: "Active" }, ... }`. */
    val valueLabels: Map<String, LocalizedStringValue> = emptyMap(),
    /** `search { keywords {...} patterns [...] ... }`. Empty when absent. */
    val search: SearchHintsValue = SearchHintsValue(),
    /** v2.1 — inline `mapping: <bareId>` or `mapping: { target: { column: ... } }`; null when absent. */
    val mapping: MappingProperty? = null,
) : Definition

data class RelationDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val from: PropertyValue? = null,
    val to: PropertyValue? = null,
    val cardinality: PropertyValue.ObjectValue? = null,
    val join: List<PropertyValue> = emptyList(),
    /** v2.1 — inline `mapping: <fkRef>` or `mapping: { fk: <fkRef> }`; null when absent. */
    val mapping: MappingProperty? = null,
) : Definition

data class Er2DbEntityDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val entity: Reference? = null,
    /**
     * v2.1 — `TargetValue?` accommodates the relaxed `target: <bareId>` form
     * (equivalent to `target: { table: <bareId> }`). Pattern-match on
     * [TargetObjectValue] when the entries map is needed.
     */
    val target: TargetValue? = null,
    val whereFilter: PropertyValue.ObjectValue? = null,
) : Definition

data class Er2DbAttributeDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val attribute: Reference? = null,
    /** v2.1 — `TargetValue?` accommodates the relaxed `target: <bareId>` form. */
    val target: TargetValue? = null,
) : Definition

data class Er2DbRelationDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val relation: Reference? = null,
    val fk: Reference? = null,
) : Definition

data class QueryDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val language: String? = null,
    val parameters: List<PropertyValue> = emptyList(),
    val sourceText: String? = null,
    /** `search { keywords {...} patterns [...] ... }`. Empty when absent. */
    val search: SearchHintsValue = SearchHintsValue(),
) : Definition

/** `def role <name> { label { cs: "...", en: "..." }, description: "..." }`. */
data class RoleDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val label: LocalizedStringValue? = null,
    /** `search { keywords {...} patterns [...] ... }`. Empty when absent. */
    val search: SearchHintsValue = SearchHintsValue(),
) : Definition

/** `def er2cnc_role <name> { entity: er.X, role: cnc.role.fact }`. */
data class Er2CncRoleDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val entity: Reference? = null,
    val role: Reference? = null,
) : Definition

/**
 * v2.2 — `def drill_map <id> { from, to, args, display?, override? }`.
 *
 * `from` / `to` are unresolved references to `def query` patterns. `args` maps
 * target-parameter names to source-column names or literal strings (raw here;
 * the validator decides column-vs-literal). `display` is a localised chip
 * label. `overrideAuto` suppresses auto-derived drills with the same target.
 */
data class DrillMapDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val from: Reference? = null,
    val to: Reference? = null,
    val args: Map<String, String> = emptyMap(),
    val display: LocalizedStringValue? = null,
    val overrideAuto: Boolean = false,
) : Definition

/**
 * Parser-side carrier for `{ cs: "...", en: "..." }` blocks. No proto
 * dependency; consumers convert into their own localised-string type.
 */
data class LocalizedStringValue(
    val byLanguage: Map<String, String> = emptyMap(),
)

/** Parser-side carrier for `{ cs: ["...", "..."], en: ["..."] }` blocks. */
data class LocalizedStringListValue(
    val byLanguage: Map<String, List<String>> = emptyMap(),
)

/**
 * Parser-side carrier for the `search { ... }` block on queries, entities,
 * attributes, columns, relations, and conceptual roles. Absence of an inner
 * property is an empty value (not null) so consumers can iterate freely.
 */
data class SearchHintsValue(
    val keywords: LocalizedStringListValue = LocalizedStringListValue(),
    val patterns: List<String> = emptyList(),
    val descriptions: LocalizedStringListValue = LocalizedStringListValue(),
    val examples: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val searchable: Boolean = false,
    val fuzzy: Boolean = false,
)

/**
 * Surface or physical type carrier. `name` is the canonical type token
 * (`text`, `int`, `varchar`, `decimal`, etc.). `length`/`precision` are
 * present on physical types only (e.g. `decimal(19, 5)`).
 */
data class DataType(
    val name: String,
    val length: Int? = null,
    val precision: Int? = null,
)

/**
 * Cross-reference identifier. Kept as a string here; the consumer resolves
 * dotted paths against the in-memory model graph.
 */
@JvmInline
value class Reference(
    val path: String,
) {
    override fun toString(): String = path
}

/**
 * Generic value form for ANY-typed properties (`from`, `to`, `target`, etc.).
 * The walker emits the structural shape it observed; the consumer interprets
 * per-property semantics. D4: every variant carries a [source].
 */
sealed interface PropertyValue {
    val source: SourceLocation

    data class StringValue(
        val raw: String,
        override val source: SourceLocation,
    ) : PropertyValue

    /** Split out from [StringValue] per the canonical TS shape — content is dedented. */
    data class TripleStringValue(
        val raw: String,
        override val source: SourceLocation,
    ) : PropertyValue

    data class NumberValue(
        val raw: Double,
        override val source: SourceLocation,
    ) : PropertyValue

    data class BoolValue(
        val raw: Boolean,
        override val source: SourceLocation,
    ) : PropertyValue

    data class NullValue(
        override val source: SourceLocation,
    ) : PropertyValue

    data class IdValue(
        val ref: Reference,
        /** The reference split on `.` (matches the TS `IdValue.parts`). */
        val parts: List<String>,
        override val source: SourceLocation,
    ) : PropertyValue

    data class ListValue(
        val items: List<PropertyValue>,
        override val source: SourceLocation,
    ) : PropertyValue

    data class ObjectValue(
        val entries: Map<String, PropertyValue>,
        override val source: SourceLocation,
    ) : PropertyValue

    data class FunctionCall(
        val name: String,
        val args: List<PropertyValue>,
        override val source: SourceLocation,
    ) : PropertyValue
}

// ----- v2.1: inline mappings -----

/**
 * v2.1 — inline `mapping:` property on entity / attribute / relation defs and
 * on explicit `def er2db_*` declarations' `target:` slot. Two surface forms:
 *
 * - [MappingPropertyBareId] — `mapping: db.dbo.fk_artikl_produkt` (relation FK
 *   shorthand) or attribute-level `mapping: COLUMN_NAME`.
 * - [MappingPropertyBlock]  — `mapping: { target: ..., columns: { ... } }`.
 */
sealed interface MappingProperty {
    val source: SourceLocation
}

data class MappingPropertyBareId(
    val id: Reference,
    override val source: SourceLocation,
) : MappingProperty

data class MappingPropertyBlock(
    val target: TargetValue? = null,
    val columns: List<MappingColumnEntry> = emptyList(),
    val fk: Reference? = null,
    override val source: SourceLocation,
) : MappingProperty

/**
 * v2.1 — `target:` value union. Both inline `mapping: { target: ... }` blocks
 * and explicit `def er2db_*` `target:` slots accept either an object (e.g.
 * `{ table: db.dbo.T }`) or a bare reference (e.g. `db.dbo.T`).
 */
sealed interface TargetValue {
    val source: SourceLocation
}

data class TargetObjectValue(
    val obj: PropertyValue.ObjectValue,
    override val source: SourceLocation,
) : TargetValue

data class TargetReferenceValue(
    val ref: Reference,
    override val source: SourceLocation,
) : TargetValue

/** v2.1 — one entry in a `mapping: { columns: { id_artiklu: IDZBOZI, ... } }` map. */
data class MappingColumnEntry(
    val name: String,
    val value: MappingColumnValue,
    val source: SourceLocation,
)

/**
 * v2.1 — one value in the inline `columns:` map. Two surface forms:
 *  - [MappingColumnBareId] — `id_artiklu: IDZBOZI`
 *  - [MappingColumnObject] — `kód_artiklu: { target: KOD_ZBOZI }` etc.
 */
sealed interface MappingColumnValue {
    val source: SourceLocation
}

data class MappingColumnBareId(
    val id: Reference,
    override val source: SourceLocation,
) : MappingColumnValue

data class MappingColumnObject(
    val obj: PropertyValue.ObjectValue,
    override val source: SourceLocation,
) : MappingColumnValue

/** File-level `schema <code> [namespace <id>]` directive. */
data class SchemaDirective(
    val schemaCode: String,
    val namespace: String? = null,
    val source: SourceLocation,
)

/** File-level `import <qualifiedName> [.*]` statement. */
data class ImportStatement(
    val target: String,
    val wildcard: Boolean,
    val source: SourceLocation,
)

/** File-level `package <qualifiedName>` declaration. Mirrors [ImportStatement]'s shape. */
data class PackageDeclaration(
    val name: String,
    val source: SourceLocation,
)

/**
 * ANTLR-style source span (D4 superset).
 *  - `line` / `endLine` are 1-indexed (match ANTLR `token.line`).
 *  - `column` / `endColumn` are 0-indexed (match ANTLR `charPositionInLine`);
 *    `endColumn` is one past the last character.
 *  - `offsetStart` / `offsetEnd` are 0-indexed byte offsets; `offsetEnd` exclusive.
 *
 * LSP-style consumers subtract 1 from `line` / `endLine`.
 */
data class SourceLocation(
    val file: String,
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
    val offsetStart: Int,
    val offsetEnd: Int,
) {
    override fun toString(): String = "$file:$line:$column"

    companion object {
        val UNKNOWN = SourceLocation("<unknown>", -1, -1, -1, -1, -1, -1)
    }
}
