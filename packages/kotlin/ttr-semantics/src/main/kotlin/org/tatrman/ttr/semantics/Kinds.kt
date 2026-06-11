package org.tatrman.ttr.semantics

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
import org.tatrman.ttr.parser.model.ModelDef
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
        is ModelDef -> "model"
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
    }
