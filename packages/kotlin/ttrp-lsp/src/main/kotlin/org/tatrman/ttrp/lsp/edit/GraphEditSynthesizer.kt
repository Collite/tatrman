// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.edit

import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.lsp.format.TtrpFormatter
import org.tatrman.ttrp.lsp.protocol.GraphEdit
import org.tatrman.ttrp.parser.TtrpParser

/**
 * Synthesizes **formatter-owned** canonical text for the closed β edit vocabulary
 * (C1-d-i/-ii). Each op produces a *minimal* γ-hybrid statement inserted at the right
 * structural position; the Stage-4.2 [TtrpFormatter] then owns final placement + style
 * (the synthesizer never concatenates strings into final positions itself). Same edit list
 * from the same base text ⇒ byte-identical output (P2/C1-d-ii): the output is always
 * re-parsed + reformatted, so determinism is the formatter's.
 *
 * v1 cut: the additive hero-build ops (`createContainer`, `addNode`, `connect`,
 * `assignTarget`) are synthesized here; the mutating/rename ops route to their existing
 * owners or return a typed [TtrpDiagnosticId.EDIT_003] (never a silent no-op). Fragment /
 * derived-container targets are rejected (EDIT_002).
 */
class GraphEditSynthesizer {
    sealed interface Result {
        data class Ok(
            val newText: String,
        ) : Result

        data class Err(
            val id: TtrpDiagnosticId,
            val message: String,
        ) : Result
    }

    /** Apply [edits] to [source], returning the formatted new document text or a typed error. */
    fun apply(
        source: String,
        edits: List<GraphEdit>,
        uri: String = "<memory>",
    ): Result {
        var text = source
        for (edit in edits) {
            when (val step = applyOne(text, edit, uri)) {
                is Result.Ok -> text = step.newText
                is Result.Err -> return step // all-or-nothing per request (C1-d)
            }
        }
        // The formatter owns final placement + style (C1-d-ii); determinism is its guarantee.
        return Result.Ok(TtrpFormatter().format(text, uri))
    }

    private fun applyOne(
        source: String,
        edit: GraphEdit,
        uri: String,
    ): Result {
        val doc = TtrpParser.parseString(source, uri).document
        return when (edit.op) {
            "createContainer" -> createContainer(source, doc.statements, edit)
            "addNode" -> addNode(source, doc.statements, edit)
            "connect" -> connect(source, edit)
            "assignTarget" -> assignTarget(source, doc.statements, edit)
            "removeNode", "disconnect", "addControlEdge", "createContainerPorts",
            "bindContainerPorts", "deleteContainer", "renameVariable", "setProperty",
            ->
                Result.Err(
                    TtrpDiagnosticId.EDIT_003,
                    "op `${edit.op}` is not in the v1 applyGraphEdit cut — edit as text or use textDocument/rename",
                )
            else -> Result.Err(TtrpDiagnosticId.EDIT_003, "unknown graph-edit op `${edit.op}`")
        }
    }

    // ---- ops ----

    private fun createContainer(
        source: String,
        statements: List<Statement>,
        edit: GraphEdit,
    ): Result {
        val name = edit.name ?: return invalid("createContainer needs a name")
        val target = edit.target ?: return invalid("createContainer needs a target engine")
        val block =
            if (edit.dialect != null) {
                "container $name target $target \"\"\"${edit.dialect}\n\n\"\"\""
            } else {
                "container $name target $target {\n}"
            }
        // Placement (C1-d-ii): after the last container, else after the last program-level statement.
        val anchor = statements.filterIsInstance<ContainerDecl>().lastOrNull() ?: statements.lastOrNull()
        return Result.Ok(insertAfter(source, anchor?.location?.offsetEnd, "\n\n$block"))
    }

    private fun addNode(
        source: String,
        statements: List<Statement>,
        edit: GraphEdit,
    ): Result {
        val canvas = edit.canvas ?: return invalid("addNode needs a canvas (container)")
        val name = edit.name ?: return invalid("addNode needs a name")
        val kind = edit.kind ?: return invalid("addNode needs a kind")
        val container =
            statements.filterIsInstance<ContainerDecl>().firstOrNull { it.name == canvas }
                ?: return Result.Err(TtrpDiagnosticId.EDIT_004, "unknown container `$canvas`")
        if (container.body is FragmentBody) {
            return Result.Err(
                TtrpDiagnosticId.EDIT_002,
                "container `$canvas` is a fragment — edit its interior as text",
            )
        }
        val body = container.body as? FlowBody ?: return invalid("container `$canvas` has no editable body")
        val op = kind.lowercase()
        val upstream = edit.afterZeta?.substringAfterLast('/')?.substringBefore('#')
        val stmt = if (upstream != null) "$name = $op($upstream)" else "$name = $op()"
        // Insert before the container's closing brace: after the last body statement, else after `{`.
        val insertAt =
            body.statements
                .lastOrNull()
                ?.location
                ?.offsetEnd ?: body.location.offsetStart + 1
        return Result.Ok(insertAfter(source, insertAt, "\n    $stmt"))
    }

    private fun connect(
        source: String,
        edit: GraphEdit,
    ): Result {
        val from = edit.from ?: return invalid("connect needs a `from` port ref")
        val to = edit.to ?: return invalid("connect needs a `to` port ref")
        // Cross-container = just a wire; movement synthesis is the compiler's (C3-d-iv).
        return Result.Ok(appendLine(source, "$from -> $to"))
    }

    private fun assignTarget(
        source: String,
        statements: List<Statement>,
        edit: GraphEdit,
    ): Result {
        val path = edit.path ?: return invalid("assignTarget needs a container path")
        val target = edit.target ?: return invalid("assignTarget needs a target engine")
        val container =
            statements.filterIsInstance<ContainerDecl>().firstOrNull { it.name == path }
                ?: return Result.Err(TtrpDiagnosticId.EDIT_004, "unknown container `$path`")
        val loc = container.target.location
        if (loc.offsetStart < 0) return invalid("container `$path` target has no source span")
        return Result.Ok(source.substring(0, loc.offsetStart) + target + source.substring(loc.offsetEnd))
    }

    // ---- text helpers ----

    private fun insertAfter(
        source: String,
        offset: Int?,
        insertion: String,
    ): String {
        val at = (offset ?: source.length).coerceIn(0, source.length)
        return source.substring(0, at) + insertion + source.substring(at)
    }

    private fun appendLine(
        source: String,
        line: String,
    ): String {
        val sep = if (source.isEmpty() || source.endsWith("\n")) "" else "\n"
        return source + sep + line + "\n"
    }

    private fun invalid(message: String): Result = Result.Err(TtrpDiagnosticId.EDIT_004, message)
}
