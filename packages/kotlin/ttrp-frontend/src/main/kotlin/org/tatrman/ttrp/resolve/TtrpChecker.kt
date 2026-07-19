// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.resolve

import org.tatrman.ttr.md.resolve.MemberSnapshot
import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttr.metadata.model.Entity
import org.tatrman.ttr.metadata.model.Relation
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttr.metadata.world.ResolvedStorage
import org.tatrman.ttr.metadata.world.ResolvedWorld
import org.tatrman.ttrp.SchemaSource
import org.tatrman.ttrp.TtrpFrontend
import org.tatrman.ttrp.ast.Arg
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.ChainStmt
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.DottedRef
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.ast.ImportDecl
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.PortKind
import org.tatrman.ttrp.ast.RelationArg
import org.tatrman.ttrp.ast.SchemaColumn
import org.tatrman.ttrp.ast.SchemaLiteralArg
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.ast.UsesWorld
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.Column
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.ExpressionTypechecker
import org.tatrman.ttrp.expr.MdContext
import org.tatrman.ttrp.expr.MdResolution
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.parser.TtrpParser
import org.tatrman.ttrp.project.TtrpManifest

/**
 * The binding tier (Stage 1.3): parse → world/model resolution → position typing →
 * er→db early rewrite (provenance) → declared-schema handling → expression typing.
 * The `ttrp check` front-half. Everything model/world goes through ttr-metadata (D-g).
 */
class TtrpChecker(
    private val manifest: TtrpManifest,
    modelsRoot: java.nio.file.Path = manifest.modelsRoot(),
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
    private val mdModel: MdModel? = null,
    private val memberSnapshot: MemberSnapshot? = null,
) {
    private val modelIndex: ModelIndex? = ModelRepo.snapshotOf(modelsRoot)?.let { ModelIndex(it) }
    private val typechecker = ExpressionTypechecker()

    data class Report(
        val document: TtrpDocument,
        val diagnostics: List<TtrpDiagnostic>,
        val world: ResolvedWorld?,
        val rewrites: List<ErRewrite>,
        /**
         * Resolved output schema per SSA-variable / container-port name (the dataflow
         * pass's result), keyed **by scope then by name** so same-named vars in different
         * containers never collide. The outer key is the enclosing scope label — `""` for
         * program level, the container name for a container body (matching
         * `SourceNav.scopeLabel`). The inner column list is null when typing was deferred
         * (unresolved source). Exposed for LSP hover / authoring-context (Stage 4.1 T4.1.5).
         */
        val schemas: Map<String, Map<String, List<Column>?>> = emptyMap(),
        /**
         * The resolved model repo (db tables / er entities), if one loaded. Exposed for
         * authoring-context's `modelObjects` enumeration (Stage 7.2 tail).
         */
        val modelIndex: ModelIndex? = null,
        /**
         * MD dot-paths that resolved in expression positions (S3-A): canonical form + shape +
         * explanation, for the frontend API / future ttrp-lsp hover. Empty until an [MdModel] is
         * injected (production model loading is a later seam).
         */
        val mdResolutions: List<MdResolution> = emptyList(),
        /**
         * The resolved MD `asof` (D17) for the bundle manifest (S4-B5, decision-13 staleness): the
         * `[ttrp] md-asof` value, else the compile-pass clock. Null when no [MdModel] is in play (a
         * non-MD program records no asof). Paired with [memberFingerprint].
         */
        val mdAsof: java.time.Instant? = null,
        /**
         * The [MemberSnapshot] fingerprint recorded alongside [mdAsof] (decision-13 staleness). Null in
         * disconnected mode (no snapshot) — production snapshot loading is the S6-B seam.
         */
        val memberFingerprint: String? = null,
    ) {
        val errors: List<TtrpDiagnostic> get() = diagnostics.filter { it.severity == Severity.ERROR }
    }

    fun check(
        source: String,
        fileName: String = "<memory>",
        manifestDiagnostics: List<TtrpDiagnostic> = emptyList(),
    ): Report {
        // A marked bare fragment file (.ttr.sql/.ttr.py/.ttrb) desugars to a canonical wrapper program
        // (T6.3.3, C0); the wrapper is derived — the file's source text is never rewritten (C2-f).
        val synth =
            org.tatrman.ttrp.dialect.bare.WrapperSynthesizer
                .synthesize(fileName, source, manifest)
        val effectiveSource = synth?.wrapperSource ?: source

        val parsed = TtrpParser.parseString(effectiveSource, fileName)
        val diags = mutableListOf<TtrpDiagnostic>()
        diags += manifestDiagnostics
        synth?.let { diags += it.diagnostics }
        diags += parsed.diagnostics
        val doc = parsed.document

        // ---- world selection + resolution (WLD) ----
        val pin = doc.statements.filterIsInstance<UsesWorld>().firstOrNull()
        val selection =
            TtrpWorldResolver.resolve(modelIndex?.snapshot, manifest, pin?.world, pin?.location)
        diags += selection.diagnostics
        val world = selection.world

        // ---- imports (RES-006) ----
        val imports = mutableListOf<ImportScope>()
        for (imp in doc.statements.filterIsInstance<ImportDecl>()) {
            val scope = ModelIndex.importScope(imp.qname.parts, imp.qname.text)
            if (modelIndex != null && !modelIndex.packageExists(scope.pkg)) {
                diags +=
                    diag(TtrpDiagnosticId.RES_006, "import `${imp.qname.text}.*` resolves to no package", imp.location)
            } else {
                imports += scope
            }
        }

        // ---- program-level declared schemas (SCH-001 duplicates, SCH-003 bad types) ----
        val programSchemas = mutableMapOf<String, List<Column>>()
        val seenSchemaNames = mutableSetOf<String>()
        for (s in doc.statements.filterIsInstance<org.tatrman.ttrp.ast.SchemaDecl>()) {
            if (!seenSchemaNames.add(s.name)) {
                diags += diag(TtrpDiagnosticId.SCH_001, "duplicate program schema `${s.name}`", s.location)
            }
            programSchemas[s.name] = columnsOf(s.columns, diags)
        }

        val rewrites = mutableListOf<ErRewrite>()
        val varSchema = mutableMapOf<String, List<Column>?>()
        val schemasByScope = mutableMapOf<String, MutableMap<String, List<Column>?>>()
        val ctx = Ctx(world, imports, programSchemas, varSchema, schemasByScope, rewrites, diags)

        // ---- resolution + dataflow pass ----
        for (stmt in doc.statements) {
            when (stmt) {
                is ContainerDecl -> resolveContainer(stmt, ctx)
                is Assignment -> assign(stmt.target, stmt.chain.elements, ctx, scope = "")
                is ChainStmt -> evalChain(stmt.chain.elements, ctx, varSchema)
                else -> Unit
            }
        }

        // ---- expression typing via the resolved schema source (EXP/FN/AGG/TYP) + MD dot-paths ----
        // `asof` is the compile-time parameter (D17): the manifest's declared value, else defaulted
        // from the injectable compile-pass clock; threaded verbatim to the resolver. The MdModel /
        // member snapshot are injection seams (production loading is a later stage) — MD resolution
        // is a no-op until a model is supplied.
        val asof = manifest.mdAsof ?: clock.instant()
        val mdContext = MdContext(mdModel, memberSnapshot, asof)
        val resolved = ResolvedSchemaSource(varSchema)
        val exprCheck = TtrpFrontend.checkExpressions(doc, resolved, mdContext)
        diags += exprCheck.diagnostics

        return Report(
            doc,
            diags,
            world,
            rewrites,
            schemasByScope.mapValues { it.value.toMap() },
            modelIndex,
            exprCheck.mdResolutions,
            // Record the resolved asof + snapshot fingerprint only when an MD model is active — a
            // non-MD program carries no MD staleness anchor (BundleAssembler emits no `md` block).
            mdAsof = if (mdModel != null) asof else null,
            memberFingerprint = memberSnapshot?.fingerprint,
        )
    }

    private class Ctx(
        val world: ResolvedWorld?,
        val imports: List<ImportScope>,
        val programSchemas: Map<String, List<Column>>,
        val varSchema: MutableMap<String, List<Column>?>,
        /** Resolved schemas partitioned by scope label (`""` = program, else container name). */
        val schemasByScope: MutableMap<String, MutableMap<String, List<Column>?>>,
        val rewrites: MutableList<ErRewrite>,
        val diags: MutableList<TtrpDiagnostic>,
        val varEntity: MutableMap<String, Entity> = mutableMapOf(),
        /** The er entity a chain just loaded (set by `load`, consumed by the enclosing assignment). */
        var pendingEntity: Entity? = null,
    ) {
        /** Record a name→schema binding both in the flat resolution map and its scope partition. */
        fun bindSchema(
            scope: String,
            name: String,
            cols: List<Column>?,
        ) {
            varSchema[name] = cols
            schemasByScope.getOrPut(scope) { mutableMapOf() }[name] = cols
        }
    }

    // ----- containers -----

    private fun resolveContainer(
        c: ContainerDecl,
        ctx: Ctx,
    ) {
        // target position: engine (RES-003).
        resolveTarget(c.target.parts.last(), c.target.location, ctx)
        val body = c.body
        // A FlowBody, or a P6-decomposed fragment, resolves its (canonical) statements
        // against the container in-ports (C2-d-iii: ports-as-tables). An undecomposed
        // fragment (ttrb — P7) stays opaque here.
        val statements =
            when {
                body is FlowBody -> body.statements
                body is org.tatrman.ttrp.ast.FragmentBody -> body.decomposition?.statements
                else -> null
            }
        if (statements != null) {
            // in-ports start unknown (fragment/wiring-fed; interior schema deferred to Stage 2).
            for (p in c.ports) if (p.kind == PortKind.IN) ctx.bindSchema(c.name, p.name, null)
            for (stmt in statements) {
                when (stmt) {
                    is Assignment -> assign(stmt.target, stmt.chain.elements, ctx, scope = c.name)
                    is ChainStmt -> evalChain(stmt.chain.elements, ctx, ctx.varSchema)
                    else -> Unit
                }
            }
        }
    }

    /** Binds a chain's output schema to [target] in [scope], recording the underlying er entity (if any). */
    private fun assign(
        target: String,
        elements: List<org.tatrman.ttrp.ast.ChainElem>,
        ctx: Ctx,
        scope: String,
    ) {
        ctx.pendingEntity = null
        ctx.bindSchema(scope, target, evalChain(elements, ctx, ctx.varSchema))
        ctx.pendingEntity?.let { ctx.varEntity[target] = it }
    }

    private fun resolveTarget(
        name: String,
        loc: SourceLocation,
        ctx: Ctx,
    ) {
        val world = ctx.world ?: return
        if (world.engines.any { it.qname.name == name }) return
        val asStorage = world.storages.firstOrNull { it.qname.name == name }
        if (asStorage != null) {
            ctx.diags +=
                diag(
                    TtrpDiagnosticId.RES_003,
                    "`target` expects an engine; `$name` is a storage",
                    loc,
                )
        } else {
            ctx.diags += diag(TtrpDiagnosticId.RES_003, "no engine named `$name` in the world", loc)
        }
    }

    // ----- chain evaluation -----

    /** Evaluates a chain to its output schema, resolving refs and threading dataflow. */
    private fun evalChain(
        elements: List<org.tatrman.ttrp.ast.ChainElem>,
        ctx: Ctx,
        scope: MutableMap<String, List<Column>?>,
    ): List<Column>? {
        var prevOut: List<Column>? = null
        for (elem in elements) {
            prevOut =
                when (elem) {
                    is OpCall -> resolveOp(elem, prevOut, ctx, scope)
                    is DottedRef -> scope[elem.parts.first()] // var/port ref; wiring node.port → null
                }
        }
        return prevOut
    }

    private fun resolveOp(
        op: OpCall,
        prevOut: List<Column>?,
        ctx: Ctx,
        scope: MutableMap<String, List<Column>?>,
    ): List<Column>? =
        when (op.name) {
            "load" -> resolveLoad(op, ctx, scope)
            "store" -> {
                resolveStore(op, ctx)
                null
            }
            "display" -> null
            "join" -> resolveJoin(op, ctx, scope)
            "aggregate" -> aggregateOutput(op, inputOf(op, prevOut, scope), ctx)
            "union" -> firstSource(op, scope) ?: prevOut
            "branch", "filter", "sort", "distinct", "limit", "sample", "head", "tail" -> {
                sourceVar(op)?.let { ctx.varEntity[it] }?.let { recordAttributeRewrites(op, it, ctx) }
                inputOf(op, prevOut, scope)
            }
            else -> inputOf(op, prevOut, scope)
        }

    /** The bare source variable name of a single-input op (its first unnamed arg). */
    private fun sourceVar(op: OpCall): String? {
        val first = op.args.firstOrNull { it.name == null }?.value as? ExprArg ?: return null
        return (first.expr as? ColumnRef)?.takeIf { it.port == null }?.column
    }

    /**
     * er→db attribute rewrite (E-d): for an op whose input is an er entity, every
     * attribute reference in a predicate/config expr is rewritten to its db column
     * (mandatory provenance). An attribute with no er2db binding on a bound entity is
     * `TTRP-RES-005` (the entity-load path covers the unbound-entity case).
     */
    private fun recordAttributeRewrites(
        op: OpCall,
        entity: Entity,
        ctx: Ctx,
    ) {
        val entityBound = modelIndex?.erToDb(entity.qname)?.dbQname != null
        if (!entityBound) return
        val refs = mutableListOf<ColumnRef>()
        val sourceArg = op.args.firstOrNull { it.name == null }
        for (arg in op.args) {
            if (arg === sourceArg) continue // the data source, not a predicate
            (arg.value as? ExprArg)?.let { refs += exprColumnRefs(it.expr) }
        }
        op.config?.entries?.forEach { e ->
            if (e is org.tatrman.ttrp.ast.AssignEntry) refs += exprColumnRefs(e.value)
        }
        for (ref in refs) {
            val attr =
                entity.attributes.firstOrNull { it.qname.name.substringAfterLast('.') == ref.column } ?: continue
            val binding = modelIndex!!.erToDb(attr.qname)
            if (binding.dbQname == null) {
                ctx.diags +=
                    diag(
                        TtrpDiagnosticId.RES_005,
                        "attribute `${entity.qname.name}.${ref.column}` has no er2db binding reachable",
                        ref.location,
                    )
            } else {
                ctx.rewrites +=
                    ErRewrite(
                        erSpelling = ref.column,
                        dbSpelling = binding.dbQname!!.name.substringAfterLast('.'),
                        provenance =
                            Provenance("er.entity.${attr.qname.name}", ref.column, ref.location),
                        location = ref.location,
                    )
            }
        }
    }

    private fun exprColumnRefs(e: org.tatrman.ttrp.expr.Expression): List<ColumnRef> =
        when (e) {
            is ColumnRef -> listOf(e)
            is org.tatrman.ttrp.expr.FunctionCall -> e.args.flatMap { exprColumnRefs(it) }
            is org.tatrman.ttrp.expr.AggregateCall -> e.args.flatMap { exprColumnRefs(it) }
            is org.tatrman.ttrp.expr.Cast -> exprColumnRefs(e.expr)
            is org.tatrman.ttrp.expr.CaseWhen ->
                e.branches.flatMap { exprColumnRefs(it.first) + exprColumnRefs(it.second) } +
                    (e.elseExpr?.let { exprColumnRefs(it) } ?: emptyList())
            is org.tatrman.ttrp.expr.InList -> exprColumnRefs(e.expr) + e.items.flatMap { exprColumnRefs(it) }
            is org.tatrman.ttrp.expr.IsNull -> exprColumnRefs(e.expr)
            is org.tatrman.ttrp.expr.Literal -> emptyList()
            // MD dot-path is not a column ref; MD resolution is a separate pass (S3, R23).
            is org.tatrman.ttrp.expr.MdPath -> emptyList()
        }

    /** The single-input schema of an op: its bare source arg, else the chain predecessor. */
    private fun inputOf(
        op: OpCall,
        prevOut: List<Column>?,
        scope: MutableMap<String, List<Column>?>,
    ): List<Column>? = firstSource(op, scope) ?: prevOut

    private fun firstSource(
        op: OpCall,
        scope: MutableMap<String, List<Column>?>,
    ): List<Column>? {
        val first = op.args.firstOrNull { it.name == null }?.value
        if (first is ExprArg) {
            val ref = first.expr as? ColumnRef ?: return null
            if (ref.port == null) return scope[ref.column]
        }
        return null
    }

    // ----- load -----

    private fun resolveLoad(
        op: OpCall,
        ctx: Ctx,
        scope: MutableMap<String, List<Column>?>,
    ): List<Column>? {
        val srcArg = op.args.firstOrNull { it.name == null } ?: return null
        val parts = refParts(srcArg) ?: return null
        val loc = srcArg.location
        val schemaArg = op.args.filter { it.name == "schema" }

        if (schemaArg.size > 1) {
            ctx.diags += diag(TtrpDiagnosticId.SCH_001, "more than one `schema:` on one load", loc)
        }

        if (parts.size == 1) {
            // Bare load: a model object (db table / er entity) by imported simple name (D-b-iii).
            val head = parts[0]
            // No-first-wins (C2-d/D-b): a case-insensitive same-name clash across imports is ambiguous.
            val clash = modelIndex?.findLoadableCi(head, ctx.imports)?.distinctBy { it.qname.name } ?: emptyList()
            if (clash.size > 1) {
                ctx.diags +=
                    diag(
                        TtrpDiagnosticId.RES_002,
                        "`$head` is ambiguous — it matches ${clash.joinToString(", ") { it.qname.name }}; qualify it",
                        loc,
                    )
                return null
            }
            val objs = ctx.imports.let { modelIndex?.findLoadable(head, it) } ?: emptyList()
            when {
                objs.size == 1 -> return loadModelObject(objs[0], loc, ctx)
                objs.size > 1 -> {
                    ctx.diags +=
                        diag(
                            TtrpDiagnosticId.RES_002,
                            "`$head` is ambiguous — exported by ${objs.size} imports; qualify it",
                            loc,
                        )
                    return null
                }
                else -> {
                    // A storage bare-load, or nothing.
                    val storage = ctx.world?.storages?.firstOrNull { it.qname.name == head }
                    if (storage == null) {
                        ctx.diags += diag(TtrpDiagnosticId.RES_001, "no storage or model object named `$head`", loc)
                    }
                    return null
                }
            }
        }

        // Dotted head.member.
        val head = parts[0]
        val member = parts.drop(1).joinToString(".")
        val storage = ctx.world?.storages?.firstOrNull { it.qname.name == head }
        if (storage != null) {
            return resolveStorageLoad(storage, member, schemaArg.firstOrNull(), loc, ctx)
        }
        // Full-qname model object (e.g. erp.accounts): pkg = all-but-last, name = last.
        val pkg = parts.dropLast(1).joinToString(".")
        val objs = modelIndex?.findByPackage(pkg, parts.last()) ?: emptyList()
        if (objs.size == 1) return loadModelObject(objs[0], loc, ctx)
        ctx.diags +=
            diag(TtrpDiagnosticId.RES_001, "no storage or model object named `${parts.joinToString(".")}`", loc)
        return null
    }

    private fun loadModelObject(
        obj: org.tatrman.ttr.metadata.model.ModelObject,
        loc: SourceLocation,
        ctx: Ctx,
    ): List<Column>? =
        when (obj) {
            is DbTable -> modelIndex!!.tableColumns(obj).map { Column(it.first, TtrpType.parse(it.second)) }
            is Entity -> {
                ctx.pendingEntity = obj
                // er entity → db table via er2db (E-d early rewrite, mandatory provenance).
                val binding = modelIndex!!.erToDb(obj.qname)
                if (binding.dbQname == null) {
                    ctx.diags +=
                        diag(
                            TtrpDiagnosticId.RES_005,
                            "entity `${obj.qname.namespace}.${obj.qname.name}` has no er2db binding reachable" +
                                (ctx.world?.let { " in world `${it.qname.`package`}.${it.qname.name}`" } ?: ""),
                            loc,
                        )
                } else {
                    ctx.rewrites +=
                        ErRewrite(
                            erSpelling = obj.qname.name,
                            dbSpelling = binding.dbQname!!.name,
                            provenance = Provenance("er.${obj.qname.namespace}.${obj.qname.name}", obj.qname.name, loc),
                            location = loc,
                        )
                }
                // Schema for typing stays er-named (logical attributes) at Stage 1.3.
                modelIndex.entityAttributes(obj).map { Column(it.first, TtrpType.parse(it.second)) }
            }
            else -> null
        }

    private fun resolveStorageLoad(
        storage: ResolvedStorage,
        member: String,
        schemaArg: Arg?,
        loc: SourceLocation,
        ctx: Ctx,
    ): List<Column>? {
        // Schema precedence (D-c): inline > named-in-program > world-declared.
        val inline = schemaArg?.value as? SchemaLiteralArg
        if (inline != null) return columnsOf(inline.columns, ctx.diags)

        val schemaName = (schemaArg?.value as? ExprArg)?.let { (it.expr as? ColumnRef)?.column } ?: member
        ctx.programSchemas[schemaName]?.let { return it }
        val worldSchema = storage.schemas.firstOrNull { it.qname.name.substringAfterLast('.') == schemaName }
        if (worldSchema != null) {
            return worldSchema.fields.map { Column(it.key, TtrpType.parse(it.value)) }
        }
        // No schema resolved anywhere.
        if (schemaArg != null) {
            ctx.diags +=
                diag(TtrpDiagnosticId.RES_001, "no schema named `$schemaName` — checked program and world", loc)
        } else if (storage.schemas.isNotEmpty()) {
            ctx.diags +=
                diag(
                    TtrpDiagnosticId.RES_001,
                    "no dataset `$member` on storage `${storage.qname.name}` and no schema given",
                    loc,
                )
        } else {
            ctx.diags +=
                diag(
                    TtrpDiagnosticId.SCH_002,
                    "ad-hoc load of `${storage.qname.name}.$member` has no schema anywhere",
                    loc,
                )
        }
        return null
    }

    // ----- store -----

    private fun resolveStore(
        op: OpCall,
        ctx: Ctx,
    ) {
        val srcArg = op.args.firstOrNull { it.name == null } ?: return
        val parts = refParts(srcArg) ?: return
        val head = parts[0]
        val world = ctx.world ?: return
        if (world.storages.any { it.qname.name == head }) return
        if (world.engines.any { it.qname.name == head }) {
            ctx.diags +=
                diag(TtrpDiagnosticId.MOV_001, "`store` expects a storage; `$head` is an engine", srcArg.location)
        } else {
            ctx.diags += diag(TtrpDiagnosticId.RES_001, "no storage named `$head`", srcArg.location)
        }
    }

    // ----- join + relation -----

    private fun resolveJoin(
        op: OpCall,
        ctx: Ctx,
        scope: MutableMap<String, List<Column>?>,
    ): List<Column>? {
        val left = (op.args.firstOrNull { it.name == "left" }?.value as? ExprArg)?.let { colName(it) }
        val right = (op.args.firstOrNull { it.name == "right" }?.value as? ExprArg)?.let { colName(it) }
        // on: relation X → er relation between the joined entities (RES-004) + rewrite.
        val onArg = op.args.firstOrNull { it.name == "on" }
        val rel = onArg?.value as? RelationArg
        if (rel != null) {
            resolveRelation(rel, left, right, ctx)
        }
        val leftCols = left?.let { scope[it] }
        val rightCols = right?.let { scope[it] }
        return merge(leftCols, rightCols)
    }

    private fun resolveRelation(
        rel: RelationArg,
        leftVar: String?,
        rightVar: String?,
        ctx: Ctx,
    ) {
        val name = rel.qname.parts.last()
        val relations = modelIndex?.findRelations(name, ctx.imports) ?: emptyList()
        if (relations.isEmpty()) {
            ctx.diags +=
                diag(TtrpDiagnosticId.RES_004, "no relation named `$name` between the joined entities", rel.location)
            return
        }
        val leftEntity = leftVar?.let { ctx.varEntity[it] }
        val rightEntity = rightVar?.let { ctx.varEntity[it] }
        if (leftEntity != null && rightEntity != null) {
            val endpoints = setOf(leftEntity.qname.name, rightEntity.qname.name)
            val match =
                relations.firstOrNull {
                    setOf(it.fromEntity.name, it.toEntity.name) == endpoints
                }
            if (match == null) {
                val r = relations.first()
                ctx.diags +=
                    diag(
                        TtrpDiagnosticId.RES_004,
                        "relation `$name` is between `${r.fromEntity.name}` and `${r.toEntity.name}`, " +
                            "not `${leftEntity.qname.name}` and `${rightEntity.qname.name}`",
                        rel.location,
                    )
                return
            }
            synthesizeJoinCondition(match, leftEntity, rightEntity, rel, ctx)
        }
    }

    /**
     * The `on: relation X` → port-qualified join-condition `Expression` synthesis
     * (T2.1.0, review-001 1.3-A). For each of the relation's `joinPairs`, resolve
     * both er attributes to their db columns via er2db (E-d) and emit a
     * `left.<col> = right.<col>` equality (`op.eq`); AND them together (`op.and`).
     * The `left`/`right` ports follow which join arm loaded the relation's from/to
     * entity. Mandatory provenance (E-d) lets the condition render er-first. A
     * binding miss on any endpoint is `TTRP-RES-005` (E-d "bind it or reference db").
     */
    private fun synthesizeJoinCondition(
        match: Relation,
        leftEntity: Entity,
        rightEntity: Entity,
        rel: RelationArg,
        ctx: Ctx,
    ) {
        val name = match.qname.name

        // Map the relation's fromEntity/toEntity onto the join's left/right ports. For a
        // self-relation (both arms load the same entity) entity identity can't disambiguate
        // orientation, so fall back to the relation's own orientation: from → left, to → right.
        val fromPort: String?
        val toPort: String?
        if (leftEntity.qname.name == rightEntity.qname.name) {
            fromPort = "left"
            toPort = "right"
        } else {
            fun portFor(entity: String): String? =
                when (entity) {
                    leftEntity.qname.name -> "left"
                    rightEntity.qname.name -> "right"
                    else -> null
                }
            fromPort = portFor(match.fromEntity.name)
            toPort = portFor(match.toEntity.name)
        }
        val eqs = mutableListOf<org.tatrman.ttrp.expr.Expression>()
        val erSides = mutableListOf<String>()
        val dbSides = mutableListOf<String>()
        for (pair in match.joinPairs) {
            val fromCol = modelIndex?.erToDb(pair.fromAttr)?.dbQname
            val toCol = modelIndex?.erToDb(pair.toAttr)?.dbQname
            if (fromCol == null) {
                ctx.diags +=
                    diag(
                        TtrpDiagnosticId.RES_005,
                        "join key `${pair.fromAttr.name}` has no er2db binding reachable",
                        rel.location,
                    )
                return
            }
            if (toCol == null) {
                ctx.diags +=
                    diag(
                        TtrpDiagnosticId.RES_005,
                        "join key `${pair.toAttr.name}` has no er2db binding reachable",
                        rel.location,
                    )
                return
            }
            val fromColName = fromCol.name.substringAfterLast('.')
            val toColName = toCol.name.substringAfterLast('.')
            eqs +=
                org.tatrman.ttrp.expr.FunctionCall(
                    function = org.tatrman.ttrp.expr.CatalogId.EQ,
                    args =
                        listOf(
                            ColumnRef(fromPort, fromColName, rel.location),
                            ColumnRef(toPort, toColName, rel.location),
                        ),
                    location = rel.location,
                )
            erSides += "${pair.fromAttr.name} = ${pair.toAttr.name}"
            dbSides += "${fromPort ?: "?"}.$fromColName = ${toPort ?: "?"}.$toColName"
        }
        val condition =
            eqs.reduceOrNull { a, b ->
                org.tatrman.ttrp.expr
                    .FunctionCall(org.tatrman.ttrp.expr.CatalogId.AND, listOf(a, b), rel.location)
            }
        ctx.rewrites +=
            ErRewrite(
                erSpelling = erSides.joinToString(" and ").ifEmpty { name },
                dbSpelling = dbSides.joinToString(" and ").ifEmpty { "join-condition($name)" },
                provenance = Provenance("er.relation.$name", name, rel.location),
                location = rel.location,
                joinCondition = condition,
            )
    }

    // ----- aggregate output -----

    private fun aggregateOutput(
        op: OpCall,
        input: List<Column>?,
        @Suppress("UNUSED_PARAMETER") ctx: Ctx,
    ): List<Column> {
        val config = op.config ?: return input ?: emptyList()
        val out = mutableListOf<Column>()
        val schemaMap = input?.let { mapOf("" to it) }
        for (entry in config.entries) {
            when (entry) {
                is org.tatrman.ttrp.ast.GroupByEntry ->
                    entry.keys.forEach { key ->
                        val t = input?.firstOrNull { it.name == key }?.type ?: TtrpType.Str
                        out += Column(key, t)
                    }
                is org.tatrman.ttrp.ast.AssignEntry -> {
                    val t =
                        typechecker.check(entry.value, schemaMap, aggregatesAllowed = true).type
                            ?: TtrpType.Named("agg")
                    out += Column(entry.name, t)
                }
            }
        }
        return out
    }

    // ----- helpers -----

    private fun merge(
        left: List<Column>?,
        right: List<Column>?,
    ): List<Column>? {
        if (left == null && right == null) return null
        val combined = (left ?: emptyList()) + (right ?: emptyList())
        return combined.distinctBy { it.name }
    }

    private fun columnsOf(
        columns: List<SchemaColumn>,
        diags: MutableList<TtrpDiagnostic>,
    ): List<Column> =
        columns.map { c ->
            val spelling = c.type.substringBefore('(')
            val t = TtrpType.parse(spelling)
            if (t is TtrpType.Named) {
                diags +=
                    diag(
                        TtrpDiagnosticId.SCH_003,
                        "unknown schema type `${c.type}` for column `${c.name}`",
                        c.location,
                    )
            }
            Column(c.name, t)
        }

    private fun refParts(arg: Arg): List<String>? {
        val v = arg.value
        if (v !is ExprArg) return null
        val ref = v.expr as? ColumnRef ?: return null
        return (ref.port?.split('.') ?: emptyList()) + ref.column
    }

    private fun colName(arg: ExprArg): String? = (arg.expr as? ColumnRef)?.takeIf { it.port == null }?.column

    private fun diag(
        id: TtrpDiagnosticId,
        message: String,
        loc: SourceLocation,
        suggestion: String? = id.suggestedAlternative,
    ) = TtrpDiagnostic(id, Severity.ERROR, message, loc, suggestion)
}

/**
 * Feeds the Stage 1.2 typechecker real column lists (T1.3.6): resolved SSA-variable
 * and container-port schemas computed by [TtrpChecker]'s dataflow pass — replacing
 * the hand-fed `DeclaredSchemaSource` seam. A ref with no resolved schema returns
 * null, which the typechecker treats as "deferred" (no false EXP-001).
 */
class ResolvedSchemaSource(
    private val varSchema: Map<String, List<Column>?>,
) : SchemaSource {
    override fun schemaFor(ref: DottedRef): List<Column>? = varSchema[ref.parts.joinToString(".")]
}
