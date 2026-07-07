package org.tatrman.ttrp.lsp

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.PortDecl
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.lsp.nav.SourceNav
import org.tatrman.ttrp.lsp.nav.VarRefs

/**
 * Go-to-definition (T4.1.6), resolving in order of node kind:
 *  - a variable use → its assignment (the SSA generation *visible at the use site*, Q7-γ:
 *    the latest assignment strictly before the enclosing statement, so `sales = filter(sales,…)`
 *    jumps to the previous `sales`, never to itself);
 *  - a `container.port` ref → the port declaration in the container header;
 *  - a container name → the container declaration;
 *  - an `in`-port used inside its container → the port declaration.
 *
 * Position typing is unambiguous (D-b), so a single [Location] is always returned.
 */
class DefinitionService {
    fun define(
        uri: String,
        doc: TtrpDocument,
        pos: Position,
    ): Location? {
        // Program-level container / container.port wiring.
        programLevelDefinition(uri, doc, pos)?.let { return it }

        val scope = SourceNav.scopeStatements(doc, pos)
        val container = SourceNav.containerAt(doc, pos)
        val inPorts = container?.ports ?: emptyList()
        val varNames = VarRefs.variableNames(scope, inPorts.map { it.name })
        val occurrence =
            VarRefs.occurrences(scope, varNames).firstOrNull { SourceNav.contains(it.location, pos) } ?: return null

        // A `head.port` on a variable (`b.true`, `j.rejects`) → the producing assignment.
        val enclosing = statementContaining(scope, pos)
        val visible = visibleGeneration(scope, occurrence.name, enclosing)
        if (visible != null) return Location(uri, SourceNav.rangeOf(visible.targetLocation))

        // No prior generation: an in-port used inside its container → the port declaration.
        inPorts.firstOrNull { it.name == occurrence.name }?.let {
            return Location(uri, SourceNav.rangeOf(it.location))
        }
        return null
    }

    private fun programLevelDefinition(
        uri: String,
        doc: TtrpDocument,
        pos: Position,
    ): Location? {
        // Only applies at program level (not inside a container body).
        if (SourceNav.containerAt(doc, pos) != null) return null
        val containers = doc.statements.filterIsInstance<ContainerDecl>()
        val names = containers.map { it.name }.toSet()
        for (ref in VarRefs.containerRefs(doc.statements, names)) {
            if (!SourceNav.contains(ref.location, pos)) continue
            val decl = containers.first { it.name == ref.name }
            // `container.port` → the port decl; bare container name → the container decl.
            val port = ref.tail.firstOrNull()?.let { p -> decl.ports.firstOrNull { it.name == p } }
            val target: PortDecl? = port
            return if (target != null) {
                Location(uri, SourceNav.rangeOf(target.location))
            } else {
                Location(uri, SourceNav.rangeOf(decl.location))
            }
        }
        return null
    }

    private fun statementContaining(
        scope: List<Statement>,
        pos: Position,
    ): Statement? = scope.firstOrNull { SourceNav.contains(it.location, pos) }

    private fun visibleGeneration(
        scope: List<Statement>,
        name: String,
        enclosing: Statement?,
    ): Assignment? {
        val boundary = enclosing?.location?.offsetStart ?: Int.MAX_VALUE
        return scope
            .filterIsInstance<Assignment>()
            .filter { it.target == name && it.location.offsetStart < boundary }
            .maxByOrNull { it.location.offsetStart }
    }
}
