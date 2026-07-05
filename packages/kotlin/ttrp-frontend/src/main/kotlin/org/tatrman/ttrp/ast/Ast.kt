package org.tatrman.ttrp.ast

/**
 * The TTR-P canonical AST (Stage 1.1). Every node carries a [SourceLocation] with
 * the repo's ANTLR-style convention; the edit synthesizer and diagnostics rely on
 * accurate spans. Cross-references ([DottedRef], [Qname]) are opaque strings here —
 * resolution is Stage 1.3 (D-b position typing).
 *
 * The provisional [Expr] arms are replaced wholesale by the one PL expression IR in
 * Stage 1.2; keep downstream code off their internal shape until then.
 */
sealed interface TtrpNode {
    val location: SourceLocation
}

/** A top-level statement; carries attached comment trivia (C2-f). */
sealed interface Statement : TtrpNode {
    val leadingTrivia: List<Trivia>
    val trailingTrivia: List<Trivia>
}

data class TtrpDocument(
    val statements: List<Statement>,
    override val location: SourceLocation,
) : TtrpNode

data class UsesWorld(
    val world: String,
    override val location: SourceLocation,
    override val leadingTrivia: List<Trivia> = emptyList(),
    override val trailingTrivia: List<Trivia> = emptyList(),
) : Statement

data class ImportDecl(
    val qname: Qname,
    val wildcard: Boolean,
    override val location: SourceLocation,
    override val leadingTrivia: List<Trivia> = emptyList(),
    override val trailingTrivia: List<Trivia> = emptyList(),
) : Statement

/** Only ever parsed to name the S12 rejection (walker → TTRP-PRS-002). */
data class ProgramHeader(
    val name: String,
    override val location: SourceLocation,
    override val leadingTrivia: List<Trivia> = emptyList(),
    override val trailingTrivia: List<Trivia> = emptyList(),
) : Statement

data class Assignment(
    val target: String,
    val targetLocation: SourceLocation,
    val chain: Chain,
    override val location: SourceLocation,
    override val leadingTrivia: List<Trivia> = emptyList(),
    override val trailingTrivia: List<Trivia> = emptyList(),
) : Statement

data class ChainStmt(
    val chain: Chain,
    override val location: SourceLocation,
    override val leadingTrivia: List<Trivia> = emptyList(),
    override val trailingTrivia: List<Trivia> = emptyList(),
) : Statement

enum class ControlKind { FS, SS, FF }

/** `b after a` (FS) · `a with b` (SS) · `a finishes with b` (FF, reserved). */
data class ControlDep(
    val kind: ControlKind,
    val subject: String,
    val reference: String,
    override val location: SourceLocation,
    override val leadingTrivia: List<Trivia> = emptyList(),
    override val trailingTrivia: List<Trivia> = emptyList(),
) : Statement

data class ControlBlock(
    val deps: List<ControlDep>,
    override val location: SourceLocation,
    override val leadingTrivia: List<Trivia> = emptyList(),
    override val trailingTrivia: List<Trivia> = emptyList(),
) : Statement

data class ContainerDecl(
    val name: String,
    val ports: List<PortDecl>,
    val target: Qname,
    val body: ContainerBody,
    override val location: SourceLocation,
    override val leadingTrivia: List<Trivia> = emptyList(),
    override val trailingTrivia: List<Trivia> = emptyList(),
) : Statement

enum class PortKind { IN, OUT, ERR }

data class PortDecl(
    val kind: PortKind,
    val name: String,
    override val location: SourceLocation,
) : TtrpNode

sealed interface ContainerBody : TtrpNode

data class FlowBody(
    val statements: List<Statement>,
    override val location: SourceLocation,
) : ContainerBody

/**
 * A tagged-block container body (C3-g). [sourceText] is the interior VERBATIM —
 * the raw bytes between the tag line's newline and the closing `"""`, no dedent, no
 * trim (C2-f). [tag] is the dialect marker (`sql` | `pandas` | `ttrb`).
 */
data class FragmentBody(
    val tag: String,
    val sourceText: String,
    val interiorLocation: SourceLocation,
    override val location: SourceLocation,
) : ContainerBody

data class Chain(
    val elements: List<ChainElem>,
    override val location: SourceLocation,
) : TtrpNode

sealed interface ChainElem : TtrpNode

data class OpCall(
    val name: String,
    val args: List<Arg>,
    val config: ConfigBlock?,
    override val location: SourceLocation,
) : ChainElem

/** `variable` | `node.port` | `qname` — position typing is a Stage 1.3 question (D-b). */
data class DottedRef(
    val parts: List<String>,
    override val location: SourceLocation,
) : ChainElem,
    Expr

data class Arg(
    val name: String?,
    val value: ArgValue,
    override val location: SourceLocation,
) : TtrpNode

sealed interface ArgValue : TtrpNode

data class RelationArg(
    val qname: Qname,
    override val location: SourceLocation,
) : ArgValue

data class SchemaLiteralArg(
    val columns: List<SchemaColumn>,
    override val location: SourceLocation,
) : ArgValue

data class SchemaColumn(
    val name: String,
    val type: String,
    override val location: SourceLocation,
) : TtrpNode

data class ExprArg(
    val expr: Expr,
    override val location: SourceLocation,
) : ArgValue

data class ConfigBlock(
    val entries: List<ConfigEntry>,
    override val location: SourceLocation,
) : TtrpNode

sealed interface ConfigEntry : TtrpNode

data class GroupByEntry(
    val keys: List<String>,
    override val location: SourceLocation,
) : ConfigEntry

data class AssignEntry(
    val name: String,
    val value: Expr,
    override val location: SourceLocation,
) : ConfigEntry

data class Qname(
    val parts: List<String>,
    override val location: SourceLocation,
) : TtrpNode {
    val text: String get() = parts.joinToString(".")
}

// ---- provisional expression AST (STAGE 1.2 REPLACES with the one PL expr IR) ----

sealed interface Expr : TtrpNode

enum class LiteralKind { STRING, NUMBER, BOOL, NULL }

data class LiteralExpr(
    val kind: LiteralKind,
    val raw: String,
    override val location: SourceLocation,
) : Expr

data class CallExpr(
    val name: String,
    val args: List<Expr>,
    override val location: SourceLocation,
) : Expr

data class BinaryExpr(
    val op: String,
    val left: Expr,
    val right: Expr,
    override val location: SourceLocation,
) : Expr

data class UnaryExpr(
    val op: String,
    val operand: Expr,
    override val location: SourceLocation,
) : Expr

data class IsNullExpr(
    val operand: Expr,
    val negated: Boolean,
    override val location: SourceLocation,
) : Expr

data class ParenExpr(
    val inner: Expr,
    override val location: SourceLocation,
) : Expr
