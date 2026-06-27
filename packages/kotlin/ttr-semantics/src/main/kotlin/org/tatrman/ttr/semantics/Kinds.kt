package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.model.AreaDef
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.ColumnDef
import org.tatrman.ttr.parser.model.ConstraintDef
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.DrillMapDef
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.Er2CncRoleDef
import org.tatrman.ttr.parser.model.Er2DbAttributeDef
import org.tatrman.ttr.parser.model.Er2DbEntityDef
import org.tatrman.ttr.parser.model.Er2DbRelationDef
import org.tatrman.ttr.parser.model.FkDef
import org.tatrman.ttr.parser.model.IndexDef
import org.tatrman.ttr.parser.model.ProjectDef
import org.tatrman.ttr.parser.model.ProcedureDef
import org.tatrman.ttr.parser.model.QueryDef
import org.tatrman.ttr.parser.model.RelationDef
import org.tatrman.ttr.parser.model.RoleDef
import org.tatrman.ttr.parser.model.TableDef
import org.tatrman.ttr.parser.model.ViewDef

/**
 * Maps a Kotlin [Definition] subtype to the canonical TS `def.kind` string
 * (camelCase: `er2dbEntity`, `drillMap`, …). The semantics layer keys symbol
 * entries and resolution on this string so the Kotlin and TS implementations
 * produce identical qnames. See `docs/grammar-master/AST-NAMING.md`.
 */
internal fun kindOf(def: Definition): String =
    when (def) {
        is ProjectDef -> "project"
        is TableDef -> "table"
        is ViewDef -> "view"
        is ColumnDef -> "column"
        is IndexDef -> "index"
        is ConstraintDef -> "constraint"
        is FkDef -> "fk"
        is ProcedureDef -> "procedure"
        is EntityDef -> "entity"
        is AttributeDef -> "attribute"
        is RelationDef -> "relation"
        is Er2DbEntityDef -> "er2dbEntity"
        is Er2DbAttributeDef -> "er2dbAttribute"
        is Er2DbRelationDef -> "er2dbRelation"
        is QueryDef -> "query"
        is RoleDef -> "role"
        is Er2CncRoleDef -> "er2cncRole"
        is DrillMapDef -> "drillMap"
        is AreaDef -> "area"
    }

/**
 * Default schema code derived from a definition's [kind], applied only when a
 * file has no explicit `schema` directive (an explicit directive always wins for
 * the whole file). Mirrors the namespace fallback (`namespace || def.kind`) but
 * per the normative kind→schema map in
 * `docs/features/pkg-schema-defaults/INDEX.md`. Unknown kinds fall back to `db`,
 * kept identical to the TS twin (`defaultSchemaForKind` in `default-schema.ts`).
 */
internal fun defaultSchemaForKind(kind: String): String =
    when (kind) {
        "entity", "attribute", "relation" -> "er"
        "er2dbEntity", "er2dbAttribute", "er2dbRelation" -> "binding"
        "role", "er2cncRole" -> "cnc"
        "query", "drillMap" -> "query"
        "project", "table", "view", "column", "index", "constraint", "fk", "procedure" -> "db"
        else -> "db"
    }

/** The reserved v4.0 model codes (D14: no `query`; D15: `cnc` schema-less). */
internal val MODEL_CODES = setOf("db", "er", "md", "binding", "cnc")

/**
 * Kind → model layer (D14/D15). Mirrors TS `modelForKind` (`qname.ts`).
 * `query`/`drillMap` → `db` (D14); `role`/`er2cncRole` → `cnc` (D15); er2db / md2
 * binding kinds → `binding`; MD logical kinds → `md`; everything else → `db`.
 */
internal fun modelForKind(kind: String): String =
    when (kind) {
        "entity", "attribute", "relation" -> "er"
        "er2dbEntity", "er2dbAttribute", "er2dbRelation",
        "md2dbCubelet", "md2dbDomain", "md2dbMap", "md2erCubelet",
        -> "binding"
        "role", "er2cncRole" -> "cnc"
        "mdDomain", "dimension", "mdMap", "hierarchy", "measure", "cubelet" -> "md"
        else -> "db"
    }

/**
 * The symbol-table kind segment for a def kind, where the namespace alias
 * differs from the camelCase kind (MD logical/binding kinds). Mirrors TS
 * `namespaceForKind` (`default-schema.ts`). Returns "" for every other kind so
 * the caller keeps the raw kind.
 */
internal fun namespaceForKind(kind: String): String =
    when (kind) {
        "mdDomain" -> "domain"
        "mdMap" -> "map"
        "dimension" -> "dimension"
        "hierarchy" -> "hierarchy"
        "measure" -> "measure"
        "cubelet" -> "cubelet"
        "md2dbCubelet" -> "md2db_cubelet"
        "md2dbDomain" -> "md2db_domain"
        "md2dbMap" -> "md2db_map"
        "md2erCubelet" -> "md2er_cubelet"
        else -> ""
    }

/**
 * The single source of the v4.0 uniform canonical-key shape
 * `<package>.<model>.<schema?>.<kind>.<parts>`. Mirrors TS `buildCanonicalKey`
 * (`qname.ts`): model derived from [kind]; schema present only for `db` (default
 * `dbo`); kind segment uses the namespace alias where one exists, else [kind].
 */
internal fun buildCanonicalKey(
    packageName: String,
    schemaId: String,
    kind: String,
    parts: List<String>,
): String {
    val model = modelForKind(kind)
    val segments = mutableListOf<String>()
    if (packageName.isNotEmpty()) segments += packageName
    segments += model
    if (model == "db") segments += schemaId.ifEmpty { "dbo" }
    segments += namespaceForKind(kind).ifEmpty { kind }
    segments += parts
    return segments.joinToString(".")
}
