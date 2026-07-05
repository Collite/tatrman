package org.tatrman.ttrp.parser

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.tatrman.ttrp.ast.Arg
import org.tatrman.ttrp.ast.ArgValue
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.AssignEntry
import org.tatrman.ttrp.ast.BinaryExpr
import org.tatrman.ttrp.ast.CallExpr
import org.tatrman.ttrp.ast.Chain
import org.tatrman.ttrp.ast.ChainElem
import org.tatrman.ttrp.ast.ChainStmt
import org.tatrman.ttrp.ast.ConfigBlock
import org.tatrman.ttrp.ast.ConfigEntry
import org.tatrman.ttrp.ast.ContainerBody
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.ControlBlock
import org.tatrman.ttrp.ast.ControlDep
import org.tatrman.ttrp.ast.ControlKind
import org.tatrman.ttrp.ast.DottedRef
import org.tatrman.ttrp.ast.Expr
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.ast.GroupByEntry
import org.tatrman.ttrp.ast.ImportDecl
import org.tatrman.ttrp.ast.IsNullExpr
import org.tatrman.ttrp.ast.LiteralExpr
import org.tatrman.ttrp.ast.LiteralKind
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.ParenExpr
import org.tatrman.ttrp.ast.PortDecl
import org.tatrman.ttrp.ast.PortKind
import org.tatrman.ttrp.ast.ProgramHeader
import org.tatrman.ttrp.ast.Qname
import org.tatrman.ttrp.ast.RelationArg
import org.tatrman.ttrp.ast.SchemaColumn
import org.tatrman.ttrp.ast.SchemaLiteralArg
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.ast.UsesWorld
import org.tatrman.ttrp.parser.generated.TTRPLexer
import org.tatrman.ttrp.parser.generated.TTRPParser

/**
 * Converts the ANTLR parse tree into the typed TTR-P AST. Pure structure — no
 * semantic validation (that is [TtrpChecks]); trivia attachment happens in
 * [TtrpParser] over the token stream. Fragment interiors are captured VERBATIM from
 * the [Token] text (C2-f) — no dedent, no trim.
 */
internal class TtrpWalker(
    private val fileName: String,
    private val source: String,
    private val tokens: CommonTokenStream,
) {
    // Token indices already claimed as a statement's trailing trivia, so the next
    // statement's leading scan does not double-count a same-line comment (C2-f).
    private val consumedTrailing = mutableSetOf<Int>()

    fun walk(ctx: TTRPParser.DocumentContext): TtrpDocument =
        TtrpDocument(
            statements = ctx.statement().mapNotNull { statement(it) },
            location = loc(ctx),
        )

    /**
     * Returns null for an error/partial statement (the parser already reported the
     * syntax error) so recovery does not crash the walk — the rest of the document
     * still yields a usable AST (Stage 1.1 error-recovery baseline).
     */
    private fun statement(ctx: TTRPParser.StatementContext): Statement? {
        val built =
            when {
                ctx.usesWorld() != null -> usesWorld(ctx.usesWorld())
                ctx.importDecl() != null -> importDecl(ctx.importDecl())
                ctx.containerDecl() != null -> containerDecl(ctx.containerDecl())
                ctx.controlBlock() != null -> controlBlock(ctx.controlBlock())
                ctx.programHeader() != null -> programHeader(ctx.programHeader())
                ctx.controlStmt() != null -> controlStmt(ctx.controlStmt())
                ctx.bindingOrChain() != null -> bindingOrChain(ctx.bindingOrChain())
                else -> return null
            }
        return attachTrivia(built, ctx)
    }

    private fun usesWorld(ctx: TTRPParser.UsesWorldContext): UsesWorld =
        UsesWorld(world = unquote(ctx.STRING().text), location = loc(ctx))

    private fun importDecl(ctx: TTRPParser.ImportDeclContext): ImportDecl =
        ImportDecl(qname = qname(ctx.qname()), wildcard = ctx.STAR() != null, location = loc(ctx))

    private fun programHeader(ctx: TTRPParser.ProgramHeaderContext): ProgramHeader =
        ProgramHeader(name = ctx.identifier().text, location = loc(ctx))

    private fun bindingOrChain(ctx: TTRPParser.BindingOrChainContext): Statement =
        when (ctx) {
            is TTRPParser.AssignmentContext ->
                Assignment(
                    target = ctx.identifier().text,
                    targetLocation = loc(ctx.identifier()),
                    chain = chain(ctx.chain()),
                    location = loc(ctx),
                )
            is TTRPParser.ChainStmtContext ->
                ChainStmt(chain = chain(ctx.chain()), location = loc(ctx))
            else -> error("unhandled bindingOrChain at ${loc(ctx)}")
        }

    private fun controlStmt(ctx: TTRPParser.ControlStmtContext): ControlDep =
        when (ctx) {
            is TTRPParser.AfterFsContext ->
                ControlDep(ControlKind.FS, ctx.identifier(0).text, ctx.identifier(1).text, loc(ctx))
            is TTRPParser.WithSsContext ->
                ControlDep(ControlKind.SS, ctx.identifier(0).text, ctx.identifier(1).text, loc(ctx))
            is TTRPParser.FinishesFfContext ->
                ControlDep(ControlKind.FF, ctx.identifier(0).text, ctx.identifier(1).text, loc(ctx))
            else -> error("unhandled controlStmt at ${loc(ctx)}")
        }

    private fun controlBlock(ctx: TTRPParser.ControlBlockContext): ControlBlock =
        ControlBlock(deps = ctx.controlStmt().map { controlStmt(it) }, location = loc(ctx))

    private fun containerDecl(ctx: TTRPParser.ContainerDeclContext): ContainerDecl {
        val ports = ctx.portSig()?.portDecl()?.map { portDecl(it) } ?: emptyList()
        val body: ContainerBody =
            when {
                ctx.TAGGED_BLOCK() != null -> fragmentBody(ctx.TAGGED_BLOCK().symbol)
                ctx.LBRACE() != null && ctx.RBRACE() != null ->
                    FlowBody(
                        statements = ctx.statement().mapNotNull { statement(it) },
                        location =
                            SourceLocation(
                                fileName,
                                ctx.LBRACE().symbol.line,
                                ctx.LBRACE().symbol.charPositionInLine,
                                ctx.RBRACE().symbol.line,
                                ctx.RBRACE().symbol.charPositionInLine + 1,
                                ctx.LBRACE().symbol.startIndex,
                                ctx.RBRACE().symbol.stopIndex + 1,
                            ),
                    )
                // Malformed/incomplete container body (error recovery) — best-effort empty flow body.
                else -> FlowBody(statements = ctx.statement().mapNotNull { statement(it) }, location = loc(ctx))
            }
        return ContainerDecl(
            name = ctx.identifier().text,
            ports = ports,
            target = qname(ctx.qname()),
            body = body,
            location = loc(ctx),
        )
    }

    private fun portDecl(ctx: TTRPParser.PortDeclContext): PortDecl {
        val kind =
            when {
                ctx.IN() != null -> PortKind.IN
                ctx.OUT() != null -> PortKind.OUT
                else -> PortKind.ERR
            }
        return PortDecl(kind = kind, name = ctx.identifier().text, location = loc(ctx))
    }

    /** Peels the tag and captures the interior VERBATIM (C2-f) — the raw bytes between the tag line's newline and the closing `"""`. */
    private fun fragmentBody(token: Token): FragmentBody {
        val body = token.text
        val tag = body.substring(3).takeWhile { it.isLetterOrDigit() || it == '-' }
        val firstNl = body.indexOf('\n')
        val interiorStart = firstNl + 1
        val interiorEnd = body.length - 3 // strip the closing """
        val sourceText =
            if (firstNl in 0 until interiorEnd) body.substring(interiorStart, interiorEnd) else ""
        val absStart = token.startIndex + interiorStart
        val absEnd = token.startIndex + interiorEnd
        val (sLine, sCol) = offsetToLineCol(absStart)
        val (eLine, eCol) = offsetToLineCol(absEnd)
        val interiorLocation =
            SourceLocation(fileName, sLine, sCol, eLine, eCol, absStart, absEnd)
        return FragmentBody(
            tag = tag,
            sourceText = sourceText,
            interiorLocation = interiorLocation,
            location = loc(token),
        )
    }

    private fun chain(ctx: TTRPParser.ChainContext): Chain =
        Chain(elements = ctx.chainElem().map { chainElem(it) }, location = loc(ctx))

    private fun chainElem(ctx: TTRPParser.ChainElemContext): ChainElem =
        ctx.opCall()?.let { opCall(it) } ?: dottedRef(ctx.dottedRef())

    private fun opCall(ctx: TTRPParser.OpCallContext): OpCall =
        OpCall(
            name = ctx.identifier().text,
            args = ctx.argList()?.arg()?.map { arg(it) } ?: emptyList(),
            config = ctx.configBlock()?.let { configBlock(it) },
            location = loc(ctx),
        )

    private fun arg(ctx: TTRPParser.ArgContext): Arg {
        val name = if (ctx.COLON() != null) ctx.identifier().text else null
        return Arg(name = name, value = argValue(ctx.argValue()), location = loc(ctx))
    }

    private fun argValue(ctx: TTRPParser.ArgValueContext): ArgValue =
        when {
            ctx.RELATION() != null -> RelationArg(qname(ctx.qname()), loc(ctx))
            ctx.schemaLiteral() != null -> schemaLiteral(ctx.schemaLiteral())
            else -> ExprArg(expr(ctx.expr()), loc(ctx))
        }

    private fun schemaLiteral(ctx: TTRPParser.SchemaLiteralContext): SchemaLiteralArg {
        val ids = ctx.identifier()
        val columns =
            (0 until ids.size / 2).map { i ->
                SchemaColumn(name = ids[i * 2].text, type = ids[i * 2 + 1].text, location = loc(ids[i * 2]))
            }
        return SchemaLiteralArg(columns = columns, location = loc(ctx))
    }

    private fun configBlock(ctx: TTRPParser.ConfigBlockContext): ConfigBlock =
        ConfigBlock(entries = ctx.configEntry().map { configEntry(it) }, location = loc(ctx))

    private fun configEntry(ctx: TTRPParser.ConfigEntryContext): ConfigEntry =
        if (ctx.GROUP() != null) {
            GroupByEntry(keys = ctx.identifier().map { it.text }, location = loc(ctx))
        } else {
            AssignEntry(name = ctx.identifier(0).text, value = expr(ctx.expr()), location = loc(ctx))
        }

    private fun qname(ctx: TTRPParser.QnameContext): Qname =
        Qname(parts = ctx.identifier().map { it.text }, location = loc(ctx))

    private fun dottedRef(ctx: TTRPParser.DottedRefContext): DottedRef {
        val parts = mutableListOf(ctx.identifier().text)
        ctx.idPart().forEach { parts += it.text }
        return DottedRef(parts = parts, location = loc(ctx))
    }

    // ---- provisional expression folding (STAGE 1.2 REPLACES) --------------------

    private fun expr(ctx: TTRPParser.ExprContext): Expr = orExpr(ctx.orExpr())

    private fun orExpr(ctx: TTRPParser.OrExprContext): Expr =
        foldBinary(ctx.andExpr().map { andExpr(it) }, "or", loc(ctx))

    private fun andExpr(ctx: TTRPParser.AndExprContext): Expr =
        foldBinary(ctx.notExpr().map { notExpr(it) }, "and", loc(ctx))

    private fun notExpr(ctx: TTRPParser.NotExprContext): Expr =
        if (ctx.NOT() != null) {
            UnaryExprOf("not", notExpr(ctx.notExpr()), loc(ctx))
        } else {
            comparison(ctx.comparison())
        }

    private fun comparison(ctx: TTRPParser.ComparisonContext): Expr {
        val left = additive(ctx.additive(0))
        if (ctx.IS() != null) {
            return IsNullExpr(operand = left, negated = ctx.NOT() != null, location = loc(ctx))
        }
        val op =
            when {
                ctx.ASSIGN() != null -> "="
                ctx.EQEQ() != null -> "=="
                ctx.NEQ() != null -> "<>"
                ctx.LT() != null -> "<"
                ctx.LTE() != null -> "<="
                ctx.GT() != null -> ">"
                ctx.GTE() != null -> ">="
                else -> null
            } ?: return left
        return BinaryExpr(op, left, additive(ctx.additive(1)), loc(ctx))
    }

    private fun additive(ctx: TTRPParser.AdditiveContext): Expr {
        var acc = multiplicative(ctx.multiplicative(0))
        val ops =
            ctx.children
                .orEmpty()
                .mapNotNull { (it as? org.antlr.v4.runtime.tree.TerminalNode)?.text }
                .filter { it == "+" || it == "-" }
        for (i in 1 until ctx.multiplicative().size) {
            acc = BinaryExpr(ops[i - 1], acc, multiplicative(ctx.multiplicative(i)), loc(ctx))
        }
        return acc
    }

    private fun multiplicative(ctx: TTRPParser.MultiplicativeContext): Expr {
        var acc = unary(ctx.unary(0))
        val ops =
            ctx.children
                .orEmpty()
                .mapNotNull { (it as? org.antlr.v4.runtime.tree.TerminalNode)?.text }
                .filter { it == "*" || it == "/" }
        for (i in 1 until ctx.unary().size) {
            acc = BinaryExpr(ops[i - 1], acc, unary(ctx.unary(i)), loc(ctx))
        }
        return acc
    }

    private fun unary(ctx: TTRPParser.UnaryContext): Expr =
        if (ctx.MINUS() != null) UnaryExprOf("-", unary(ctx.unary()), loc(ctx)) else primary(ctx.primary())

    private fun primary(ctx: TTRPParser.PrimaryContext): Expr =
        when {
            ctx.literal() != null -> literal(ctx.literal())
            ctx.functionCall() != null -> functionCall(ctx.functionCall())
            ctx.dottedRef() != null -> dottedRef(ctx.dottedRef())
            else -> ParenExpr(inner = expr(ctx.expr()), location = loc(ctx))
        }

    private fun functionCall(ctx: TTRPParser.FunctionCallContext): CallExpr =
        CallExpr(name = ctx.identifier().text, args = ctx.expr().map { expr(it) }, location = loc(ctx))

    private fun literal(ctx: TTRPParser.LiteralContext): LiteralExpr {
        val t = ctx.text
        val kind =
            when {
                ctx.STRING() != null || ctx.CHAR_STRING() != null -> LiteralKind.STRING
                ctx.NUMBER() != null -> LiteralKind.NUMBER
                ctx.NULL() != null -> LiteralKind.NULL
                else -> LiteralKind.BOOL
            }
        return LiteralExpr(kind = kind, raw = t, location = loc(ctx))
    }

    private fun foldBinary(
        operands: List<Expr>,
        op: String,
        at: SourceLocation,
    ): Expr {
        var acc = operands.first()
        for (i in 1 until operands.size) acc = BinaryExpr(op, acc, operands[i], at)
        return acc
    }

    @Suppress("FunctionName")
    private fun UnaryExprOf(
        op: String,
        operand: Expr,
        at: SourceLocation,
    ): Expr =
        org.tatrman.ttrp.ast
            .UnaryExpr(op, operand, at)

    // ---- trivia attach ----------------------------------------------------------

    /**
     * Attaches comment trivia (HIDDEN channel) to a statement: nearest preceding
     * hidden tokens are leading; same-line following comments are trailing. A
     * same-line trailing comment is claimed here so the next statement's leading
     * scan skips it (no double-count).
     */
    private fun attachTrivia(
        stmt: Statement,
        ctx: TTRPParser.StatementContext,
    ): Statement {
        val trailing = trailingTriviaOf(ctx.stop)
        val leading = leadingTriviaOf(ctx.start)
        if (leading.isEmpty() && trailing.isEmpty()) return stmt
        return when (stmt) {
            is UsesWorld -> stmt.copy(leadingTrivia = leading, trailingTrivia = trailing)
            is ImportDecl -> stmt.copy(leadingTrivia = leading, trailingTrivia = trailing)
            is ProgramHeader -> stmt.copy(leadingTrivia = leading, trailingTrivia = trailing)
            is Assignment -> stmt.copy(leadingTrivia = leading, trailingTrivia = trailing)
            is ChainStmt -> stmt.copy(leadingTrivia = leading, trailingTrivia = trailing)
            is ControlDep -> stmt.copy(leadingTrivia = leading, trailingTrivia = trailing)
            is ControlBlock -> stmt.copy(leadingTrivia = leading, trailingTrivia = trailing)
            is ContainerDecl -> stmt.copy(leadingTrivia = leading, trailingTrivia = trailing)
        }
    }

    private fun leadingTriviaOf(start: Token): List<org.tatrman.ttrp.ast.Trivia> =
        tokens
            .getHiddenTokensToLeft(start.tokenIndex)
            .orEmpty()
            .filter { it.tokenIndex !in consumedTrailing && isComment(it) }
            .map { trivia(it) }

    private fun trailingTriviaOf(stop: Token): List<org.tatrman.ttrp.ast.Trivia> =
        tokens
            .getHiddenTokensToRight(stop.tokenIndex)
            .orEmpty()
            .filter { it.line == stop.line && isComment(it) }
            .onEach { consumedTrailing += it.tokenIndex }
            .map { trivia(it) }

    private fun isComment(t: Token): Boolean = t.type == TTRPLexer.LINE_COMMENT || t.type == TTRPLexer.BLOCK_COMMENT

    private fun trivia(t: Token): org.tatrman.ttrp.ast.Trivia =
        org.tatrman.ttrp.ast
            .Trivia(text = t.text, location = loc(t))

    // ---- source-location helpers ------------------------------------------------

    private fun loc(ctx: ParserRuleContext): SourceLocation {
        val start = ctx.start
        val stop = ctx.stop ?: ctx.start
        val stopText = stop.text ?: ""
        return SourceLocation(
            file = fileName,
            line = start.line,
            column = start.charPositionInLine,
            endLine = stop.line,
            endColumn = stop.charPositionInLine + stopText.length,
            offsetStart = start.startIndex,
            offsetEnd = stop.stopIndex + 1,
        )
    }

    private fun loc(token: Token): SourceLocation {
        val text = token.text ?: ""
        val (eLine, eCol) = offsetToLineCol(token.stopIndex + 1)
        return SourceLocation(
            file = fileName,
            line = token.line,
            column = token.charPositionInLine,
            endLine = eLine,
            endColumn = eCol,
            offsetStart = token.startIndex,
            offsetEnd = token.stopIndex + 1,
        )
    }

    private fun offsetToLineCol(offset: Int): Pair<Int, Int> {
        var line = 1
        var col = 0
        val end = offset.coerceIn(0, source.length)
        for (i in 0 until end) {
            if (source[i] == '\n') {
                line++
                col = 0
            } else {
                col++
            }
        }
        return line to col
    }

    private fun unquote(s: String): String =
        if (s.length >= 2 && (s.first() == '"' || s.first() == '\'')) s.substring(1, s.length - 1) else s
}
