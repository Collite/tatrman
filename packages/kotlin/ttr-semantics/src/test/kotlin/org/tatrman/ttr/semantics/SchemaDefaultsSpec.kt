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
            "model" to "db",
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
            "er2dbEntity" to "map",
            "er2dbAttribute" to "map",
            "er2dbRelation" to "map",
            "role" to "cnc",
            "er2cncRole" to "cnc",
            "query" to "query",
            "drillMap" to "query",
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
            val schemaCode = r.schemaDirective?.schemaCode ?: "" // Stage 3 contract
            val namespace = r.schemaDirective?.namespace ?: ""
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
            Group("table ⇒ db", "def table tbl_t { columns: [def column c { type: int }] }", "db.table.tbl_t", "db"),
            Group("role ⇒ cnc", "def role rol_r { description: \"r\" }", "cnc.role.rol_r", "cnc"),
            Group(
                "query ⇒ query",
                "def query qry_q { language: SQL, sourceText: \"SELECT 1\" }",
                "query.query.qry_q",
                "query",
            ),
            Group(
                "er2db_entity ⇒ map",
                "def er2db_entity map_m { entity: er.entity.x, target: { table: db.dbo.T } }",
                "map.er2dbEntity.map_m",
                "map",
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

        "2.4 schema db namespace dbo + def table ⇒ db.dbo.t" {
            val t =
                tableOf(
                    "file:///file.ttr",
                    "schema db namespace dbo\ndef table t { columns: [def column c { type: int }] }",
                )
            t.get("db.dbo.t").shouldNotBeNull()
        }

        "2.4 explicit schema db over def entity keeps db (directive overrides kind)" {
            val t =
                tableOf(
                    "file:///file.ttr",
                    "schema db\ndef entity e { attributes: [def attribute a { type: int }] }",
                )
            t.get("db.entity.e").shouldNotBeNull()
            t.get("er.entity.e").shouldBeNull()
        }
    })
