package org.tatrman.ttrp.lsp.methods

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.eclipse.lsp4j.Position
import org.tatrman.ttrp.ast.ContainerBody
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.ast.ImportDecl
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.lsp.nav.SourceNav
import org.tatrman.ttrp.resolve.TtrpChecker
import org.tatrman.ttr.metadata.world.ResolvedWorld

/**
 * Builds the deterministic, prompt-ready authoring-context bundle (contracts §7 /
 * `docs/features/ttr-p/architecture/authoring-context.schema.json`, v1). Serialized with Gson
 * (LSP4J's JSON), every object closed. `scope` is present only when a cursor
 * [position] was supplied. The schema is final; some content (capability node/function
 * rosters, model objects, TTR-SQL/TTR-B grammar rosters) grows in later phases — the
 * fields are present now with the shapes the schema pins.
 */
object AuthoringContextBuilder {
    const val SCHEMA_VERSION = 1
    const val SCHEMA_ID = "https://tatrman.org/schemas/ttrp/authoring-context/v1"

    /** The canonical TTR-P op roster surfaced to assist hosts (T10). */
    private val TTRP_OPS =
        listOf(
            "load",
            "store",
            "transfer",
            "display",
            "join",
            "aggregate",
            "union",
            "branch",
            "filter",
            "sort",
            "distinct",
            "limit",
            "sample",
            "head",
            "tail",
            "project",
            "select",
            "calc",
            "switch",
            "values",
            "intersect",
            "except",
            "pivot",
        )

    /** TTR-pandas method roster (S17). */
    private val PANDAS_METHODS =
        listOf("select", "calc", "filter", "join", "aggregate", "sort", "union", "limit", "load", "store", "display")

    /** TTR-SQL clause roster (C2-b α; the clauses `TTRSql.g4` accepts) — surfaced to assist. */
    private val SQL_CLAUSES =
        listOf(
            "with",
            "select",
            "distinct",
            "from",
            "join",
            "where",
            "group by",
            "having",
            "order by",
            "limit",
            "union",
            "intersect",
            "except",
            "values",
        )

    /** TTR-B sentence-verb roster (C4-b; the verbs `TTRB.g4` accepts) — surfaced to assist. */
    private val TTRB_VERBS =
        listOf(
            "Load",
            "Keep/Take/Select",
            "Remove/Delete",
            "Rename",
            "Convert/Retype",
            "Create/Compute",
            "Summarize",
            "Join",
            "Sort",
            "Combine/Append",
            "Store",
            "Show/Display",
        )

    fun build(
        report: TtrpChecker.Report,
        position: Position?,
    ): JsonObject {
        val root = JsonObject()
        root.addProperty("version", SCHEMA_VERSION)
        root.add("world", world(report.world))
        root.add("capabilities", capabilities(report.world))
        root.add("modelObjects", JsonArray()) // grows with deeper model enumeration (documented in the schema)
        if (position != null) root.add("scope", scope(report, position))
        root.add("grammar", grammar())
        root.add("diagnostics", diagnostics())
        return root
    }

    private fun world(world: ResolvedWorld?): JsonObject {
        val obj = JsonObject()
        if (world == null) {
            obj.add("engines", JsonArray())
            obj.add("executors", JsonArray())
            obj.add("storages", JsonArray())
            return obj
        }
        obj.addProperty("qname", "${world.qname.`package`}.${world.qname.name}")
        obj.addProperty("fingerprint", world.fingerprint)
        obj.add(
            "engines",
            JsonArray().apply {
                world.engines.forEach {
                    add(
                        JsonObject().apply {
                            addProperty("name", it.qname.name)
                            addNullable("type", it.type)
                            addNullable("version", it.version)
                        },
                    )
                }
            },
        )
        obj.add(
            "executors",
            JsonArray().apply {
                world.executors.forEach {
                    add(
                        JsonObject().apply {
                            addProperty("name", it.qname.name)
                            addNullable("type", it.type)
                            addNullable("version", it.version)
                        },
                    )
                }
            },
        )
        obj.add(
            "storages",
            JsonArray().apply {
                world.storages.forEach {
                    add(
                        JsonObject().apply {
                            addProperty("name", it.qname.name)
                            addProperty("staging", it.staging)
                            addProperty("rls", it.manifest["rls"]?.let { p -> p.toString().contains("true") } ?: false)
                        },
                    )
                }
            },
        )
        obj.addNullable("staging", world.staging?.qname?.name)
        return obj
    }

    private fun capabilities(world: ResolvedWorld?): JsonObject {
        val obj = JsonObject()
        obj.add(
            "engines",
            JsonArray().apply {
                world?.engines?.forEach {
                    add(
                        JsonObject().apply {
                            addProperty("engine", it.qname.name)
                            addNullable("type", it.type)
                            // Node/function support rosters (T6 manifests) are surfaced in Stage 7.2;
                            // the arrays are present now (schema-final) and grow there.
                            add("nodes", JsonArray())
                            add("functions", JsonArray())
                        },
                    )
                }
            },
        )
        return obj
    }

    private fun scope(
        report: TtrpChecker.Report,
        position: Position,
    ): JsonObject {
        val obj = JsonObject()
        obj.add(
            "imports",
            JsonArray().apply {
                report.document.statements.filterIsInstance<ImportDecl>().forEach {
                    add(JsonPrimitive(it.qname.text + if (it.wildcard) ".*" else ""))
                }
            },
        )
        // Only the cursor's own scope — leaking every container's vars would put out-of-scope
        // (and cross-container name-colliding) names into the deterministic prompt (contracts §7).
        val cursorScope = report.schemas[SourceNav.scopeLabel(report.document, position)].orEmpty()
        obj.add(
            "variables",
            JsonArray().apply {
                cursorScope.forEach { (name, cols) ->
                    add(
                        JsonObject().apply {
                            addProperty("name", name)
                            add(
                                "schema",
                                JsonArray().apply {
                                    cols?.forEach { c ->
                                        add(
                                            JsonObject().apply {
                                                addProperty("name", c.name)
                                                addProperty("type", c.type.toString())
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
            },
        )
        val container: ContainerDecl? = SourceNav.containerAt(report.document, position)
        obj.add(
            "portsAtCursor",
            JsonArray().apply { container?.ports?.forEach { add(JsonPrimitive(it.name)) } },
        )
        // Cursor-scoped dialect insertion (C4-d-i γ): the host declares the target by the position;
        // the assist emits in the container's dialect (sql/pandas/ttrb), or `ttrp` at program scope.
        obj.add(
            "insertionTarget",
            JsonObject().apply {
                addProperty("dialect", container?.let { dialectOf(it.body) } ?: "ttrp")
                addNullable("containerName", container?.name)
                addNullable("targetEngine", container?.target?.parts?.lastOrNull())
            },
        )
        return obj
    }

    /** The dialect the assist inserts at a container: a fragment's tag, or `ttrp` for a canonical body. */
    private fun dialectOf(body: ContainerBody): String =
        when (body) {
            is FragmentBody -> body.tag
            else -> "ttrp"
        }

    private fun grammar(): JsonObject =
        JsonObject().apply {
            addProperty("specVersion", 1)
            addProperty(
                "statementSummary",
                "chains (`a -> filter(…) -> sort(…)`) + SSA assignment, freely mixed; `=` is the one " +
                    "equality (`==` only in TTR-pandas, S9); multi-in named-only; union list-form (S11).",
            )
            add(
                "dialectRosters",
                JsonObject().apply {
                    add("ttrp", JsonArray().apply { TTRP_OPS.forEach { add(JsonPrimitive(it)) } })
                    add("sql", JsonArray().apply { SQL_CLAUSES.forEach { add(JsonPrimitive(it)) } })
                    add("pandas", JsonArray().apply { PANDAS_METHODS.forEach { add(JsonPrimitive(it)) } })
                    add("ttrb", JsonArray().apply { TTRB_VERBS.forEach { add(JsonPrimitive(it)) } })
                },
            )
        }

    private fun diagnostics(): JsonArray =
        JsonArray().apply {
            TtrpDiagnosticId.entries.forEach { d ->
                add(
                    JsonObject().apply {
                        addProperty("id", d.id)
                        addProperty("area", d.id.substringAfter("TTRP-").substringBeforeLast('-'))
                        addNullable("suggestedAlternative", d.suggestedAlternative)
                    },
                )
            }
        }

    // Omit null-valued keys: Gson's JsonElement.toString() drops explicit JsonNull members, so an
    // absent-when-null field (marked optional in the schema) round-trips consistently over JSON-RPC.
    private fun JsonObject.addNullable(
        key: String,
        value: String?,
    ) {
        if (value != null) addProperty(key, value)
    }
}
