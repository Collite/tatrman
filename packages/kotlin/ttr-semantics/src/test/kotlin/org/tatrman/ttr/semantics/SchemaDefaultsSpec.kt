// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * Stage 2 (RED until Stage 3) — kind → default-schema parity with TS
 * (`packages/semantics/src/__tests__/schema-defaults.test.ts`).
 *
 * Contract Stage 3 establishes: callers pass the `schema` directive's code, or
 * `""` when the file has no directive; the qname layer then derives the default
 * schema per definition from its kind. So these tests build the table with
 * `?: ""` (NOT Fixtures' legacy `?: "db"`).
 *
 * The `defaultSchemaForKind` unit case (TS 2.5) lands here in Stage 3 alongside
 * the function — referencing a not-yet-existing symbol would break the whole
 * Kotlin test module's compilation, defeating the "nothing else regressed" check.
 */
class SchemaDefaultsSpec :
    StringSpec({
        // ----- 2.5 — defaultSchemaForKind unit map (parity with TS) -----

        mapOf(
            "project" to "db",
            "table" to "db",
            "view" to "db",
            "column" to "db",
            "index" to "db",
            "constraint" to "db",
            "fk" to "db",
            "procedure" to "db",
            "entity" to "er",
            "attribute" to "er",
            "relation" to "er",
            "er2dbEntity" to "binding",
            "er2dbAttribute" to "binding",
            "er2dbRelation" to "binding",
            "role" to "cnc",
            "er2cncRole" to "cnc",
            // D14 — query + drillMap are db-layer objects; there is no `query` model.
            "query" to "db",
            "drillMap" to "db",
        ).forEach { (kind, schema) ->
            "2.5 defaultSchemaForKind($kind) == $schema" {
                defaultSchemaForKind(kind) shouldBe schema
            }
        }

        "2.5 unknown kind falls back to db (matches TS)" {
            defaultSchemaForKind("totally-unknown") shouldBe "db"
        }

        fun tableOf(
            uri: String,
            src: String,
        ): SymbolTable {
            val r = TtrLoader.parseString(src, uri)
            val schemaCode = r.modelDirective?.modelCode ?: "" // Stage 3 contract
            val namespace = r.modelDirective?.schema ?: ""
            val t = SymbolTable()
            t.upsertDocument(uri, r.definitions, schemaCode, namespace, r.packageName ?: "")
            return t
        }

        // ----- 2.2 — symbol-table qname schema component (no directive ⇒ per-kind) -----

        data class Group(
            val name: String,
            val src: String,
            val qname: String,
            val schema: String,
        )
        listOf(
            Group(
                "entity ⇒ er",
                "def entity ent_e { attributes: [def attribute a { type: int }] }",
                "er.entity.ent_e",
                "er",
            ),
            Group(
                "table ⇒ db",
                "def table tbl_t { columns: [def column c { type: int }] }",
                "db.dbo.table.tbl_t",
                "db",
            ),
            Group("role ⇒ cnc", "def role rol_r { description: \"r\" }", "cnc.role.rol_r", "cnc"),
            Group(
                "query ⇒ db (D14)",
                "def query qry_q { language: SQL, sourceText: \"SELECT 1\" }",
                "db.dbo.query.qry_q",
                "db",
            ),
            Group(
                "er2db_entity ⇒ binding",
                "def er2db_entity map_m { entity: er.entity.x, target: { table: db.dbo.T } }",
                "binding.er2dbEntity.map_m",
                "binding",
            ),
        ).forEach { g ->
            "2.2 ${g.name}" {
                val t = tableOf("file:///file.ttr", g.src)
                val entry = t.get(g.qname)
                entry.shouldNotBeNull()
                entry.schemaCode shouldBe g.schema
            }
        }

        "2.2 entity child attribute also inherits the er default" {
            val t = tableOf("file:///file.ttr", "def entity ent_e { attributes: [def attribute a { type: int }] }")
            t.get("er.entity.ent_e.a").shouldNotBeNull()
        }

        // ----- 2.4 — explicit directive still wins (regression, stays green) -----

        "2.4 model db schema dbo + def table ⇒ db.dbo.table.t" {
            val t =
                tableOf(
                    "file:///file.ttr",
                    "model db schema dbo\ndef table t { columns: [def column c { type: int }] }",
                )
            t.get("db.dbo.table.t").shouldNotBeNull()
        }

        "2.4 def entity in a model db file is keyed by its kind, not the directive (D12)" {
            val t =
                tableOf(
                    "file:///file.ttr",
                    "model db\ndef entity e { attributes: [def attribute a { type: int }] }",
                )
            t.get("er.entity.e").shouldNotBeNull()
            t.get("db.dbo.e").shouldBeNull()
        }
    })
