// SPDX-License-Identifier: Apache-2.0
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

data class ProjectDef(
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
    /** Top-level `search { ... }` block (grammar allows it on tables). Empty when absent. */
    val search: SearchHintsValue = SearchHintsValue(),
    /** Grounding Phase 1 (grammar 4.2) — the `semantics { … }` block; null when absent. */
    val semantics: SemanticsBlock? = null,
    /** v4.4 — inline `lexicon { … }` sugar; null when absent. Desugared in semantics. */
    val lexicon: LexiconBlock? = null,
) : Definition

data class ViewDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val columns: List<ColumnDef> = emptyList(),
    /** Flattened `definitionSql` text (see [QueryDef.sourceText]); structure in [definitionSqlBlock]. */
    val definitionSql: String? = null,
    /**
     * embedded-sql (stage 1.4): the structured `definitionSql` value
     * ([PropertyValue.StringValue] / [PropertyValue.TripleStringValue] / [TaggedBlockValue]).
     */
    val definitionSqlBlock: PropertyValue? = null,
    /** Top-level `search { ... }` block (grammar allows it on views). Empty when absent. */
    val search: SearchHintsValue = SearchHintsValue(),
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
    /** Grounding Phase 1 (grammar 4.2) — the `semantics { … }` block; null when absent. */
    val semantics: SemanticsBlock? = null,
    /** v4.4 — inline `lexicon { … }` sugar; null when absent. Desugared in semantics. */
    val lexicon: LexiconBlock? = null,
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
    /** Grounding Phase 1 (grammar 4.2) — the `semantics { … }` block; null when absent. */
    val semantics: SemanticsBlock? = null,
    /** v4.4 — inline `lexicon { … }` sugar; null when absent. Desugared in semantics. */
    val lexicon: LexiconBlock? = null,
    /** v3.0 — inline `binding: { target: ..., columns: { ... } }` block; null when absent. */
    val binding: BindingProperty? = null,
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
    /** Grounding Phase 1 (grammar 4.2) — the `semantics { … }` block; null when absent. */
    val semantics: SemanticsBlock? = null,
    /** v4.4 — inline `lexicon { … }` sugar; null when absent. Desugared in semantics. */
    val lexicon: LexiconBlock? = null,
    /** v3.0 — inline `binding: <bareId>` or `binding: { target: { column: ... } }`; null when absent. */
    val binding: BindingProperty? = null,
) : Definition

/**
 * v4.4 lexicon surface (RG-P4, RS-9) — inline `lexicon { … }` sugar. The canonical
 * shorthand keys the walker extracts from the free-form object; desugared in
 * semantics. Mirrors the TS `LexiconBlock`.
 */
data class LexiconBlock(
    val terms: List<String> = emptyList(),
    val patterns: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
)

/**
 * v4.4 — a canonical lexicon entry (`def term|pattern|example`). One shared body;
 * [entryKind] is the TTR keyword (`"term"`/`"pattern"`/`"example"`) and the dump
 * discriminator. Per-kind required-field validity lives in semantics.
 */
data class LexiconEntryDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    val entryKind: String = "term",
    /** The `for:` target ref (er/db/md), span-carrying; resolved in semantics. */
    val target: Reference? = null,
    val forms: List<String> = emptyList(),
    val match: String? = null,
    val text: String? = null,
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
    /** Top-level `search { ... }` block (grammar allows it on relations). Empty when absent. */
    val search: SearchHintsValue = SearchHintsValue(),
    /** v3.0 — inline `binding: <fkRef>` or `binding: { fk: <fkRef> }`; null when absent. */
    val binding: BindingProperty? = null,
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
    /**
     * Flattened text of the `sourceText` property: the plain decoded string, the
     * dedented triple-string, or — for a tagged block — its extracted [value].
     * Kept for consumers that only want the SQL text (ai-platform). The structured
     * carrier (tag/dialect/spans) lives in [sourceTextBlock].
     */
    val sourceText: String? = null,
    /**
     * embedded-sql (stage 1.4): the structured `sourceText` value —
     * [PropertyValue.StringValue] / [PropertyValue.TripleStringValue] / [TaggedBlockValue].
     * Null when the property is absent. Mirrors the TS `QueryDef.sourceText` union.
     */
    val sourceTextBlock: PropertyValue? = null,
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
 * v3.0 — `def area <id> { ... }` subject area, replacing the v2.3 `.ttrd` domain
 * block. A normal definition that lives in ordinary model files and registers a
 * resolvable symbol. Drives the resolved-packages `domains` artifact (recursive
 * package closure + entity set). Mirrors the TS `AreaDef` (`ast.ts`).
 */
data class AreaDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    /** Recursive members: each pulls the package and all descendants. May be empty. */
    val packages: List<String> = emptyList(),
    /** Individual entity qnames loaded in addition to whole packages. May be empty. */
    val entities: List<String> = emptyList(),
    /** Per-member source locations, parallel to [packages] (editor-only). */
    val packageSources: List<SourceLocation> = emptyList(),
    /** Per-member source locations, parallel to [entities] (editor-only). */
    val entitySources: List<SourceLocation> = emptyList(),
) : Definition

/**
 * v4.1 — `def world <id> { … }`, a deployment world (D-d-α; ttr-metadata M0).
 * The ONLY top-level world def kind; engine/executor/storage/world-schema are
 * nested. Manifest entries are transported opaque (T6 β data — MD5).
 */
data class WorldDef(
    override val name: String,
    override val source: SourceLocation,
    override val description: String? = null,
    override val tags: List<String> = emptyList(),
    /** Optional world-level type overlay ref (grammar-permissive; usually unused). */
    val extends: String? = null,
    val engines: List<EngineDef> = emptyList(),
    val executors: List<ExecutorDef> = emptyList(),
    val storages: List<StorageDef> = emptyList(),
) : Definition

/** `def engine <id> { … }` inside a world (D-d-α). */
data class EngineDef(
    val name: String,
    val source: SourceLocation,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    /** `type:` discriminator (raw dataType text, e.g. `postgres`). */
    val type: String? = null,
    /** `version:` string literal. */
    val version: String? = null,
    /** `extends:` type-manifest ref (dotted id; resolved in M2). */
    val extends: String? = null,
    /** Free-form manifest entries (T6 β data — transported opaque, MD5). */
    val manifest: Map<String, PropertyValue> = emptyMap(),
)

/** `def executor <id> { … }` inside a world (same body as engine). */
data class ExecutorDef(
    val name: String,
    val source: SourceLocation,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val type: String? = null,
    val version: String? = null,
    val extends: String? = null,
    val manifest: Map<String, PropertyValue> = emptyMap(),
)

/** `def storage <id> { … }` inside a world (D-d-α/D-d-i/D-f). */
data class StorageDef(
    val name: String,
    val source: SourceLocation,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val type: String? = null,
    val extends: String? = null,
    /** `via:` engine this storage is reached through. */
    val via: String? = null,
    /** `hosts: [pkg]` — model packages this storage serves (D-d-i). */
    val hosts: List<String> = emptyList(),
    /** `staging: true` — exactly-one enforced in semantics/WorldResolver (D-f). */
    val staging: Boolean = false,
    /** Named `def schema` declarations local to this storage (D-c world home). */
    val schemas: List<WorldSchemaDef> = emptyList(),
    val manifest: Map<String, PropertyValue> = emptyMap(),
)

/** `def schema <id> { field: type, … }` nested in a storage (D-c world home). */
data class WorldSchemaDef(
    val name: String,
    val source: SourceLocation,
    val fields: List<WorldSchemaField> = emptyList(),
)

data class WorldSchemaField(
    val name: String,
    /** Raw dataType text (e.g. `string`, `decimal`). */
    val type: String,
    val source: SourceLocation,
)

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
 * Grounding Phase 1 (grammar 4.2) — the free-form `semantics { … }` block. The
 * parser stays mechanical: entries are captured as raw scalar key→value pairs
 * (ids as their identifier text, string literals unquoted, numbers/booleans as
 * primitives, `null` as [SemanticsValue.NullV]) with NO vocabulary or shape
 * checking — that is ttr-semantics' job. Nested objects/lists are rejected at
 * walk time into a `ttr/semantics-non-scalar` parser diagnostic so the
 * validator's input stays flat. Mirrors the TS `SemanticsBlock` (`ast.ts`).
 *
 * `source` spans the `{ … }` object (the search-block convention). Last-wins on
 * a duplicate key; repeated keys are recorded in [duplicateProperties].
 */
data class SemanticsBlock(
    val entries: Map<String, SemanticsValue> = emptyMap(),
    val duplicateProperties: List<String> = emptyList(),
    val source: SourceLocation,
)

/**
 * A single `semantics` entry value — a flat scalar. Ids arrive as their
 * identifier text (a [Str]). Mirrors the TS `SemanticsValue = string | number |
 * boolean | null`.
 */
sealed interface SemanticsValue {
    data class Str(
        val value: String,
    ) : SemanticsValue

    data class Num(
        val value: Double,
    ) : SemanticsValue

    data class Bool(
        val value: Boolean,
    ) : SemanticsValue

    data object NullV : SemanticsValue

    /** Display form for diagnostic messages (matches the TS `String(value)`). */
    fun display(): String =
        when (this) {
            is Str -> value
            is Num ->
                if (value == kotlin.math.floor(value) &&
                    value.isFinite()
                ) {
                    value.toLong().toString()
                } else {
                    value.toString()
                }
            is Bool -> value.toString()
            is NullV -> "null"
        }
}

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
 * Cross-reference identifier. The consumer resolves the dotted [path] against the
 * in-memory model graph. Carries [parts] (the path split on `.`) and the [source]
 * span of the reference *token* — mirrors the canonical TS `Reference`
 * (`{ path, parts, source }` in `ast.ts`), so diagnostics/navigation built from a
 * collected reference point at the reference itself, not its enclosing def.
 *
 * The single-arg [constructor] (path only) is a convenience for model construction
 * outside the parser (tests, synthesizers) where no token span exists; it derives
 * `parts` and uses [SourceLocation.UNKNOWN]. The walker always supplies a real span.
 */
data class Reference(
    val path: String,
    val parts: List<String>,
    val source: SourceLocation,
) {
    constructor(path: String) : this(path, path.split("."), SourceLocation.UNKNOWN)

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

/**
 * Resolved embedded-language kind. Mirrors the TS `LanguageKind = QueryLanguage`
 * (`'SQL' | 'TRANSFORMATION_DSL' | 'DATAFRAME_DSL' | 'REL_NODE'`). Kept as a
 * `String` alias so the conformance dump matches byte-for-byte (the TS union
 * serialises to the same bare strings).
 */
typealias LanguageKind = String

/**
 * embedded-sql (DESIGN §3, contracts §2.2): a tagged triple-quoted block
 * (`"""<tag>␊…"""`) carrying embedded foreign-language source. Produced only by
 * the `sourceText` / `definitionSql` properties via the `embeddedBlock` rule.
 * The tag is peeled before [value], so it never reaches the executed SQL.
 *
 * A top-level [PropertyValue] variant (same package) mirroring the TS
 * `TaggedBlockValue` interface; carries the value contract only — no SQL
 * analysis (that lands in `@modeler/sql` / Phase 2).
 */
data class TaggedBlockValue(
    val tag: String,
    val language: LanguageKind,
    val dialect: String?, // dialect id string; null for a bare `sql` (→ modeler.toml default) or non-SQL
    val value: String,
    val tagSource: SourceLocation,
    val valueSource: SourceLocation,
    val indentWidth: Int,
    override val source: SourceLocation,
) : PropertyValue

// ----- v3.0: inline bindings (was v2.1 `mapping:`) -----

/**
 * v3.0 — inline `binding:` property on entity / attribute / relation defs and
 * on explicit `def er2db_*` declarations' `target:` slot. Two surface forms:
 *
 * - [BindingPropertyBareId] — `binding: db.dbo.fk_artikl_produkt` (relation FK
 *   shorthand) or attribute-level `binding: COLUMN_NAME`.
 * - [BindingPropertyBlock]  — `binding: { target: ..., columns: { ... } }`.
 */
sealed interface BindingProperty {
    val source: SourceLocation
}

data class BindingPropertyBareId(
    val id: Reference,
    override val source: SourceLocation,
) : BindingProperty

data class BindingPropertyBlock(
    val target: TargetValue? = null,
    val columns: List<BindingColumnEntry> = emptyList(),
    val fk: Reference? = null,
    override val source: SourceLocation,
) : BindingProperty

/**
 * v2.1 — `target:` value union. Both inline `binding: { target: ... }` blocks
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

/** v3.0 — one entry in a `binding: { columns: { id_artiklu: IDZBOZI, ... } }` map. */
data class BindingColumnEntry(
    val name: String,
    val value: BindingColumnValue,
    val source: SourceLocation,
)

/**
 * v3.0 — one value in the inline `columns:` map. Two surface forms:
 *  - [BindingColumnBareId] — `id_artiklu: IDZBOZI`
 *  - [BindingColumnObject] — `kód_artiklu: { target: KOD_ZBOZI }` etc.
 */
sealed interface BindingColumnValue {
    val source: SourceLocation
}

data class BindingColumnBareId(
    val id: Reference,
    override val source: SourceLocation,
) : BindingColumnValue

data class BindingColumnObject(
    val obj: PropertyValue.ObjectValue,
    override val source: SourceLocation,
) : BindingColumnValue

/** File-level `model <code> [schema <id>]` directive. */
data class ModelDirective(
    val modelCode: String,
    val schema: String? = null,
    /** v4.4 — unit-level `model lexicon locale <id>` (permissive; semantics enforces lexicon-only). */
    val locale: String? = null,
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
