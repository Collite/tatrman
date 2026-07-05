package org.tatrman.ttrp.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttrp.ast.Arg
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.AssignEntry
import org.tatrman.ttrp.ast.BinaryExpr
import org.tatrman.ttrp.ast.CallExpr
import org.tatrman.ttrp.ast.Chain
import org.tatrman.ttrp.ast.ChainStmt
import org.tatrman.ttrp.ast.ConfigBlock
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.ControlBlock
import org.tatrman.ttrp.ast.ControlDep
import org.tatrman.ttrp.ast.DottedRef
import org.tatrman.ttrp.ast.Expr
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.ast.GroupByEntry
import org.tatrman.ttrp.ast.ImportDecl
import org.tatrman.ttrp.ast.IsNullExpr
import org.tatrman.ttrp.ast.LiteralExpr
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.ParenExpr
import org.tatrman.ttrp.ast.PortDecl
import org.tatrman.ttrp.ast.ProgramHeader
import org.tatrman.ttrp.ast.Qname
import org.tatrman.ttrp.ast.RelationArg
import org.tatrman.ttrp.ast.SchemaLiteralArg
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.ast.UnaryExpr
import org.tatrman.ttrp.ast.UsesWorld

/**
 * Deterministic, field-ordered JSON dump of the TTR-P AST for golden snapshotting
 * (test-only, mirrors ttr-parser's ConformanceDump). Insertion order in
 * `buildJsonObject` is preserved, so output is stable across runs. Includes node
 * kind, children, source spans, and fragment `sourceText`.
 */
object TtrpAstDump {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    fun dump(doc: TtrpDocument): String = json.encodeToString(JsonElement.serializer(), document(doc))

    private fun span(loc: SourceLocation): JsonElement =
        JsonPrimitive("${loc.line}:${loc.column}-${loc.endLine}:${loc.endColumn}")

    private fun document(doc: TtrpDocument) =
        buildJsonObject {
            put("kind", "Document")
            put("statements", buildJsonArray { doc.statements.forEach { add(statement(it)) } })
        }

    private fun statement(s: Statement): JsonElement =
        when (s) {
            is UsesWorld ->
                obj("UsesWorld", s.location) {
                    put("world", s.world)
                    put("leading", trivia(s.leadingTrivia))
                    put("trailing", trivia(s.trailingTrivia))
                }
            is ImportDecl ->
                obj("ImportDecl", s.location) {
                    put("qname", s.qname.text)
                    put("wildcard", s.wildcard)
                }
            is ProgramHeader -> obj("ProgramHeader", s.location) { put("name", s.name) }
            is Assignment ->
                obj("Assignment", s.location) {
                    put("target", s.target)
                    put("chain", chain(s.chain))
                    put("leading", trivia(s.leadingTrivia))
                    put("trailing", trivia(s.trailingTrivia))
                }
            is ChainStmt -> obj("ChainStmt", s.location) { put("chain", chain(s.chain)) }
            is ControlDep ->
                obj("ControlDep", s.location) {
                    put("controlKind", s.kind.name)
                    put("subject", s.subject)
                    put("reference", s.reference)
                }
            is ControlBlock ->
                obj("ControlBlock", s.location) {
                    put("deps", buildJsonArray { s.deps.forEach { add(statement(it)) } })
                }
            is ContainerDecl ->
                obj("ContainerDecl", s.location) {
                    put("name", s.name)
                    put("ports", buildJsonArray { s.ports.forEach { add(portDecl(it)) } })
                    put("target", s.target.text)
                    put("body", containerBody(s))
                }
        }

    private fun portDecl(p: PortDecl) =
        obj("PortDecl", p.location) {
            put("portKind", p.kind.name)
            put("name", p.name)
        }

    private fun containerBody(c: ContainerDecl): JsonElement =
        when (val b = c.body) {
            is FragmentBody ->
                obj("FragmentBody", b.location) {
                    put("tag", b.tag)
                    put("sourceText", b.sourceText)
                }
            is FlowBody ->
                obj("FlowBody", b.location) {
                    put("statements", buildJsonArray { b.statements.forEach { add(statement(it)) } })
                }
        }

    private fun chain(c: Chain): JsonElement =
        buildJsonObject {
            put("kind", "Chain")
            put("loc", span(c.location))
            put(
                "elements",
                buildJsonArray {
                    c.elements.forEach {
                        when (it) {
                            is OpCall -> add(opCall(it))
                            is DottedRef -> add(dottedRef(it))
                        }
                    }
                },
            )
        }

    private fun opCall(o: OpCall) =
        obj("OpCall", o.location) {
            put("name", o.name)
            put("args", buildJsonArray { o.args.forEach { add(arg(it)) } })
            put("config", o.config?.let { configBlock(it) } ?: JsonNull)
        }

    private fun arg(a: Arg) =
        obj("Arg", a.location) {
            put("name", a.name ?: "")
            put(
                "value",
                when (val v = a.value) {
                    is ExprArg -> expr(v.expr)
                    is RelationArg -> obj("RelationArg", v.location) { put("qname", v.qname.text) }
                    is SchemaLiteralArg ->
                        obj("SchemaLiteralArg", v.location) {
                            put(
                                "columns",
                                buildJsonArray { v.columns.forEach { add(JsonPrimitive("${it.name}:${it.type}")) } },
                            )
                        }
                },
            )
        }

    private fun configBlock(c: ConfigBlock) =
        obj("ConfigBlock", c.location) {
            put(
                "entries",
                buildJsonArray {
                    c.entries.forEach {
                        when (it) {
                            is GroupByEntry -> add(obj("GroupBy", it.location) { put("keys", jsonStrings(it.keys)) })
                            is AssignEntry ->
                                add(
                                    obj("Assign", it.location) {
                                        put("name", it.name)
                                        put("value", expr(it.value))
                                    },
                                )
                        }
                    }
                },
            )
        }

    private fun dottedRef(d: DottedRef) = obj("DottedRef", d.location) { put("parts", jsonStrings(d.parts)) }

    private fun expr(e: Expr): JsonElement =
        when (e) {
            is DottedRef -> dottedRef(e)
            is LiteralExpr ->
                obj("Literal", e.location) {
                    put("literalKind", e.kind.name)
                    put("raw", e.raw)
                }
            is CallExpr ->
                obj("Call", e.location) {
                    put("name", e.name)
                    put("args", buildJsonArray { e.args.forEach { add(expr(it)) } })
                }
            is BinaryExpr ->
                obj("Binary", e.location) {
                    put("op", e.op)
                    put("left", expr(e.left))
                    put("right", expr(e.right))
                }
            is UnaryExpr ->
                obj("Unary", e.location) {
                    put("op", e.op)
                    put("operand", expr(e.operand))
                }
            is IsNullExpr ->
                obj("IsNull", e.location) {
                    put("negated", e.negated)
                    put("operand", expr(e.operand))
                }
            is ParenExpr -> obj("Paren", e.location) { put("inner", expr(e.inner)) }
        }

    private fun trivia(ts: List<org.tatrman.ttrp.ast.Trivia>): JsonElement =
        buildJsonArray { ts.forEach { add(JsonPrimitive(it.text)) } }

    private fun jsonStrings(xs: List<String>): JsonArray = buildJsonArray { xs.forEach { add(JsonPrimitive(it)) } }

    private fun obj(
        kind: String,
        loc: SourceLocation,
        block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ) = buildJsonObject {
        put("kind", kind)
        put("loc", span(loc))
        block()
    }

    private fun Qname.render(): String = text
}
