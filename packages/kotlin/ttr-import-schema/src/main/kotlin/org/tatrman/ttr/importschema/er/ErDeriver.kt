// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.er

import org.tatrman.ttr.importschema.conventions.ConventionsFile
import org.tatrman.ttr.importschema.dbmodel.GeneratedFile
import org.tatrman.ttr.importschema.dbmodel.SqlTypeMapper
import org.tatrman.ttr.importschema.introspect.IntrospectedCatalog
import org.tatrman.ttr.importschema.introspect.IntrospectedTable
import org.tatrman.ttr.importschema.naming.IdentifierMangler
import org.tatrman.ttr.importschema.probe.ColumnRef
import org.tatrman.ttr.importschema.probe.ProbeOrigin
import org.tatrman.ttr.importschema.probe.ProbeResult
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.Reference
import org.tatrman.ttr.parser.model.RelationDef
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.writer.TtrRenderer

/**
 * F1+F2 er first-cut derivation (S4·T1/T2/T3), PURE. Given the catalog, conventions and the probe
 * results, it produces the graded relations + shaped entities and renders `er.ttrm` through the
 * canonical writer. The model text stays clean (Q-3); grades/evidence live in [ErDerivationResult.notes]
 * for the S4·T4 review checklist.
 */
class ErDeriver(
    private val packageName: String,
    private val conventions: ConventionsFile,
) {
    fun derive(
        catalog: IntrospectedCatalog,
        probeResults: List<ProbeResult> = emptyList(),
    ): ErDerivationResult {
        val cascade = RelationCascade(conventions).derive(catalog)
        val shaped = EntityShaper(conventions).shape(catalog, cascade.candidates)
        val probeByKey = probeResults.associateBy { key(it.candidate.child, it.candidate.parent) }

        val notes = mutableListOf<ChecklistNote>()

        // Entities: every non-collapsed table.
        val entities =
            catalog.schemas
                .flatMap { s ->
                    s.tables.mapNotNull { t ->
                        val src = "${s.name}.${t.name}"
                        if (src in shaped.collapsedTables) {
                            null
                        } else {
                            DerivedEntity(
                                name = entityName(t.name),
                                sourceTable = src,
                                isCodebook =
                                    src in shaped.codebookTables,
                            )
                        }
                    }
                }.sortedBy { it.name }

        val relations = mutableListOf<DerivedRelation>()

        // M:N relations from collapsed junctions.
        for (j in shaped.junctions) {
            relations +=
                DerivedRelation(
                    name = relationName("${entityName(j.entityA)}_${entityName(j.entityB)}", relations),
                    fromEntity = entityName(j.entityA),
                    toEntity = entityName(j.entityB),
                    fromColumns = emptyList(),
                    toColumns = emptyList(),
                    cardinality = RelationCardinality.MANY_TO_MANY,
                    evidence =
                        RelationEvidence(
                            EvidenceGrade.DECLARED,
                            ProbeOrigin.DECLARED,
                            "junction:${j.sourceTable}",
                        ),
                )
            notes +=
                ChecklistNote(
                    ChecklistNote.Kind.JUNCTION_COLLAPSED,
                    j.sourceTable,
                    "pure M:N junction collapsed into relation ${entityName(j.entityA)} ↔ ${entityName(j.entityB)}",
                )
        }

        // FK / heuristic relations (skipping columns belonging to collapsed junctions).
        for (c in cascade.candidates) {
            val childSrc = "${c.child.schema}.${c.child.table}"
            if (childSrc in shaped.collapsedTables) continue

            val probe = probeByKey[key(c.child, c.parent)]
            val grade = GradeAssigner.grade(c.origin, probe)
            if (grade == EvidenceGrade.CONTRADICTED) {
                notes +=
                    ChecklistNote(
                        ChecklistNote.Kind.CONTRADICTED,
                        relLabel(c.child, c.parent),
                        "name/FK suggests a relation but data has ${probe?.orphanCount ?: "?"} orphan(s) — NOT emitted",
                    )
                continue
            }
            val evidence =
                RelationEvidence(
                    grade = grade,
                    origin = c.origin,
                    rule = c.rule,
                    provenance = probe?.provenance,
                    orphanCount = probe?.orphanCount,
                    childRowCount = probe?.childRowCount,
                )
            relations +=
                DerivedRelation(
                    name = relationName("${entityName(c.child.table)}_${entityName(c.parent.table)}", relations),
                    fromEntity = entityName(c.child.table),
                    toEntity = entityName(c.parent.table),
                    fromColumns = c.child.columns.map { mangle(it) },
                    toColumns = c.parent.columns.map { mangle(it) },
                    cardinality = GradeAssigner.cardinality(probe),
                    evidence = evidence,
                )
            notes +=
                ChecklistNote(
                    ChecklistNote.Kind.RELATION_EVIDENCE,
                    relLabel(c.child, c.parent),
                    "grade=$grade rule=${c.rule}" +
                        (probe?.let { " provenance=${it.provenance} orphans=${it.orphanCount}" } ?: ""),
                )
            if (grade == EvidenceGrade.NAMED_ONLY_UNPROBED_BUDGET) {
                notes +=
                    ChecklistNote(
                        ChecklistNote.Kind.UNPROBED_BUDGET,
                        relLabel(c.child, c.parent),
                        "left unprobed — Q-5 budget exhausted",
                    )
            }
        }

        for ((child, parent) in shaped.headerDetail) {
            notes +=
                ChecklistNote(
                    ChecklistNote.Kind.HEADER_DETAIL_PROPOSED,
                    "$child/$parent",
                    "possible header/detail fold ($child is detail of $parent) — PROPOSED only; accept in review",
                )
        }
        for (cb in shaped.codebookTables.sorted()) {
            notes +=
                ChecklistNote(
                    ChecklistNote.Kind.CODEBOOK_PROPOSED,
                    cb,
                    "codebook table — proposed as an enum-like entity",
                )
        }
        for (pj in shaped.payloadJunctions.sorted()) {
            notes +=
                ChecklistNote(
                    ChecklistNote.Kind.JUNCTION_COLLAPSED,
                    pj,
                    "junction carries payload — kept as an entity (not collapsed)",
                )
        }
        for (u in cascade.unmatched) {
            notes +=
                ChecklistNote(
                    ChecklistNote.Kind.UNMATCHED_COLUMN,
                    u.qkey,
                    "looks like a foreign key but resolves to no table — left as a plain attribute",
                )
        }

        return ErDerivationResult(packageName, entities, relations.sortedBy { it.name }, notes)
    }

    /** Render the er first cut to `er.ttrm` (clean canonical form; grades stay in the checklist). */
    fun render(
        result: ErDerivationResult,
        catalog: IntrospectedCatalog,
    ): GeneratedFile {
        val defs = mutableListOf<Definition>()
        val byName = catalog.schemas.flatMap { s -> s.tables.map { entityName(it.name) to it } }.toMap()
        for (e in result.entities) {
            byName[e.name]?.let { defs += entityDef(e, it) }
        }
        for (r in result.relations) defs += relationDef(r)
        val content =
            TtrRenderer.renderFile(
                schemaCode = "er",
                namespace = null,
                definitions = defs,
                packageName = packageName,
            )
        return GeneratedFile("er.ttrm", content)
    }

    private fun entityDef(
        entity: DerivedEntity,
        table: IntrospectedTable,
    ): EntityDef =
        EntityDef(
            name = entity.name,
            source = SourceLocation.UNKNOWN,
            description = table.comment?.takeIf { it.isNotBlank() },
            attributes =
                table.columns.sortedBy { it.ordinal }.map { col ->
                    AttributeDef(
                        name = mangle(col.name),
                        source = SourceLocation.UNKNOWN,
                        description = col.comment?.takeIf { it.isNotBlank() },
                        type = SqlTypeMapper.toDataType(col),
                        isKey = col.name in table.primaryKey,
                        optional = col.nullable,
                    )
                },
        )

    private fun relationDef(r: DerivedRelation): RelationDef {
        val (fromCard, toCard) =
            when (r.cardinality) {
                RelationCardinality.MANY_TO_ONE -> "0..*" to "1"
                RelationCardinality.ONE_TO_ONE -> "0..1" to "1"
                RelationCardinality.MANY_TO_MANY -> "0..*" to "0..*"
            }
        val join =
            if (r.fromColumns.isNotEmpty() && r.fromColumns.size == r.toColumns.size) {
                r.fromColumns.indices.map { i ->
                    PropertyValue.ObjectValue(
                        linkedMapOf(
                            "from" to idRef("er.entity.${r.fromEntity}.${r.fromColumns[i]}"),
                            "to" to idRef("er.entity.${r.toEntity}.${r.toColumns[i]}"),
                        ),
                        SourceLocation.UNKNOWN,
                    )
                }
            } else {
                emptyList()
            }
        return RelationDef(
            name = r.name,
            source = SourceLocation.UNKNOWN,
            from = idRef("er.entity.${r.fromEntity}"),
            to = idRef("er.entity.${r.toEntity}"),
            cardinality =
                PropertyValue.ObjectValue(
                    linkedMapOf(
                        "from" to PropertyValue.StringValue(fromCard, SourceLocation.UNKNOWN),
                        "to" to PropertyValue.StringValue(toCard, SourceLocation.UNKNOWN),
                    ),
                    SourceLocation.UNKNOWN,
                ),
            join = join,
        )
    }

    private fun idRef(path: String) = PropertyValue.IdValue(Reference(path), path.split("."), SourceLocation.UNKNOWN)

    private fun entityName(table: String) = mangle(table)

    private fun mangle(name: String) = IdentifierMangler.mangle(name).ttrName

    private fun relationName(
        base: String,
        existing: List<DerivedRelation>,
    ): String {
        val name = mangle(base)
        if (existing.none { it.name == name }) return name
        var i = 2
        while (existing.any { it.name == "${name}_$i" }) i++
        return "${name}_$i"
    }

    private fun relLabel(
        child: ColumnRef,
        parent: ColumnRef,
    ) = "${child.table}.${child.columns.joinToString(",")}→${parent.table}"

    private fun key(
        child: ColumnRef,
        parent: ColumnRef,
    ) = "${child.qkey}->${parent.qkey}"
}
