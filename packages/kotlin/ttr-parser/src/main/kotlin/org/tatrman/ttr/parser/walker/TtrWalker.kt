package org.tatrman.ttr.parser.walker

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import org.tatrman.ttr.parser.diagnostics.DiagnosticCode
import org.tatrman.ttr.parser.generated.TTRParser
import org.tatrman.ttr.parser.loader.ParseError
import org.tatrman.ttr.parser.loader.ParseWarning
import org.tatrman.ttr.parser.model.AreaDef
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.BindingColumnBareId
import org.tatrman.ttr.parser.model.BindingColumnEntry
import org.tatrman.ttr.parser.model.BindingColumnObject
import org.tatrman.ttr.parser.model.BindingColumnValue
import org.tatrman.ttr.parser.model.BindingProperty
import org.tatrman.ttr.parser.model.BindingPropertyBareId
import org.tatrman.ttr.parser.model.BindingPropertyBlock
import org.tatrman.ttr.parser.model.ColumnDef
import org.tatrman.ttr.parser.model.ConstraintDef
import org.tatrman.ttr.parser.model.DataType
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.DrillMapDef
import org.tatrman.ttr.parser.model.EngineDef
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.ExecutorDef
import org.tatrman.ttr.parser.model.Er2CncRoleDef
import org.tatrman.ttr.parser.model.Er2DbAttributeDef
import org.tatrman.ttr.parser.model.Er2DbEntityDef
import org.tatrman.ttr.parser.model.Er2DbRelationDef
import org.tatrman.ttr.parser.model.FkDef
import org.tatrman.ttr.parser.model.ImportStatement
import org.tatrman.ttr.parser.model.IndexDef
import org.tatrman.ttr.parser.model.LocalizedStringListValue
import org.tatrman.ttr.parser.model.LocalizedStringValue
import org.tatrman.ttr.parser.model.ProjectDef
import org.tatrman.ttr.parser.model.ProcedureDef
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.QueryDef
import org.tatrman.ttr.parser.model.Reference
import org.tatrman.ttr.parser.model.RelationDef
import org.tatrman.ttr.parser.model.RoleDef
import org.tatrman.ttr.parser.model.ModelDirective
import org.tatrman.ttr.parser.model.SearchHintsValue
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.StorageDef
import org.tatrman.ttr.parser.model.TableDef
import org.tatrman.ttr.parser.model.TargetObjectValue
import org.tatrman.ttr.parser.model.TaggedBlockValue
import org.tatrman.ttr.parser.model.TargetReferenceValue
import org.tatrman.ttr.parser.model.TargetValue
import org.tatrman.ttr.parser.model.ViewDef
import org.tatrman.ttr.parser.model.WorldDef
import org.tatrman.ttr.parser.model.WorldSchemaDef
import org.tatrman.ttr.parser.model.WorldSchemaField

/**
 * Converts an ANTLR parse tree into the typed [Definition] model.
 *
 * The walker is intentionally NOT a `TTRBaseListener` — it walks the tree
 * top-down via direct context traversal. That matches how the grammar
 * naturally nests (table → columns → individual column defs) without the
 * mutable-state tax a listener would impose.
 *
 * Source spans use the D4 superset (line/column/endLine/endColumn/offsets).
 * The multi-token-span invariant is `endColumn = stopToken.column +
 * stopToken.length` — NOT `startColumn + spanLength` (see [location]).
 */
class TtrWalker(
    private val fileLabel: String,
) {
    private val warnings = mutableListOf<ParseWarning>()
    private val errors = mutableListOf<ParseError>()

    /**
     * BCP-47 language codes accepted by the search feature. Unknown codes
     * still parse (last-write-wins on the map) but emit a warning so authors
     * notice typos. Stop-word resource files exist for the same set.
     */
    private val supportedLanguages = setOf("cs", "en", "de", "sk", "hu")

    fun visitDocument(doc: TTRParser.DocumentContext): WalkResult {
        val schema = doc.modelDirective()?.let { visitModelDirective(it) }
        val defs = doc.definition().mapNotNull { visitDefinition(it) }

        val pkg = doc.packageDecl()?.qualifiedName()?.text
        val imports =
            doc.importDecl().map {
                ImportStatement(
                    target = it.qualifiedName().text,
                    wildcard = it.STAR() != null,
                    source = location(it),
                )
            }

        if (doc.graphBlock() != null) {
            warn(doc.graphBlock(), "graph block ignored in a .ttr file — graphs belong in .ttrg render artefacts")
        }

        return WalkResult(
            modelDirective = schema,
            definitions = defs,
            warnings = warnings.toList(),
            errors = errors.toList(),
            packageName = pkg,
            imports = imports,
        )
    }

    private fun warn(
        ctx: ParserRuleContext,
        message: String,
    ) {
        warnings +=
            ParseWarning(
                file = fileLabel,
                line = ctx.start.line,
                column = ctx.start.charPositionInLine + 1,
                message = message,
            )
    }

    private fun err(
        ctx: ParserRuleContext,
        code: DiagnosticCode,
        message: String,
    ) {
        errors +=
            ParseError(
                file = fileLabel,
                line = ctx.start.line,
                column = ctx.start.charPositionInLine + 1,
                // keep the code id in the message text for human-readable output;
                // the typed `code` field carries it for programmatic consumers.
                message = "$code: $message",
                code = code,
            )
    }

    private fun visitModelDirective(ctx: TTRParser.ModelDirectiveContext): ModelDirective {
        val code = ctx.modelCode().text
        val ns = ctx.id()?.text
        return ModelDirective(modelCode = code, schema = ns, source = location(ctx))
    }

    private fun visitDefinition(ctx: TTRParser.DefinitionContext): Definition? {
        val od = ctx.objectDefinition() ?: return null
        return when {
            od.PROJECT() != null -> visitModel(od)
            od.TABLE() != null -> visitTable(od)
            od.VIEW() != null -> visitView(od)
            od.COLUMN() != null -> visitColumnTopLevel(od)
            od.INDEX() != null -> visitIndexTopLevel(od)
            od.CONSTRAINT() != null -> visitConstraintTopLevel(od)
            od.FK() != null -> visitFk(od)
            od.PROCEDURE() != null -> visitProcedure(od)
            od.ENTITY() != null -> visitEntity(od)
            od.ATTRIBUTE() != null -> visitAttributeTopLevel(od)
            od.RELATION() != null -> visitRelation(od)
            od.ER2DB_ENTITY() != null -> visitEr2dbEntity(od)
            od.ER2DB_ATTRIBUTE() != null -> visitEr2dbAttribute(od)
            od.ER2DB_RELATION() != null -> visitEr2dbRelation(od)
            od.QUERY() != null -> visitQuery(od)
            od.ROLE() != null -> visitRole(od)
            od.ER2CNC_ROLE() != null -> visitEr2CncRole(od)
            od.DRILL_MAP() != null -> visitDrillMap(od)
            od.AREA() != null -> visitArea(od)
            od.WORLD() != null -> visitWorld(od)
            else -> null
        }
    }

    // ----- Per-kind visitors -----

    private fun visitModel(od: TTRParser.ObjectDefinitionContext): ProjectDef {
        val props = od.projectDef().projectProperty()
        val description =
            props.firstNotNullOfOrNull {
                it.descriptionProperty()?.let { d ->
                    stringForm(d.stringLiteralForm())
                }
            }
        val tags =
            props.firstNotNullOfOrNull { it.tagsProperty()?.let { t -> stringList(t.listOfStrings()) } } ?: emptyList()
        val version = props.firstNotNullOfOrNull { it.versionProperty()?.STRING_LITERAL()?.let { stringLiteral(it) } }
        return ProjectDef(
            name = od.id().text,
            source = defSource(od),
            description = description,
            tags = tags,
            version = version,
        )
    }

    private fun visitTable(od: TTRParser.ObjectDefinitionContext): TableDef {
        val props = od.tableDef().tableProperty()
        return TableDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            primaryKey =
                props.firstNotNullOfOrNull { it.primaryKeyProperty()?.let { primaryKeyList(it.primaryKeyValue()) } }
                    ?: emptyList(),
            columns =
                props.flatMap {
                    it.columnsProperty()?.let { c -> visitColumnDefList(c.columnDefList()) }
                        ?: emptyList()
                },
            indices =
                props.flatMap {
                    it.indicesProperty()?.let { i -> visitIndexDefList(i.indexDefList()) }
                        ?: emptyList()
                },
            constraints =
                props.flatMap {
                    it.constraintsProperty()?.let { c -> visitConstraintDefList(c.constraintDefList()) }
                        ?: emptyList()
                },
            search =
                props.firstNotNullOfOrNull {
                    it.searchBlockProperty()?.let { s -> visitSearchBlock(s.searchBlock()) }
                } ?: SearchHintsValue(),
        )
    }

    private fun visitView(od: TTRParser.ObjectDefinitionContext): ViewDef {
        val props = od.viewDef().viewProperty()
        val definitionSqlBlock =
            props.firstNotNullOfOrNull {
                it.definitionSqlProperty()?.embeddedBlock()?.let { e -> walkEmbeddedBlock(e) }
            }
        return ViewDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            columns =
                props.flatMap {
                    it.columnsProperty()?.let { c -> visitColumnDefList(c.columnDefList()) }
                        ?: emptyList()
                },
            definitionSqlBlock = definitionSqlBlock,
            definitionSql = definitionSqlBlock?.let { embeddedValueText(it) },
            search =
                props.firstNotNullOfOrNull {
                    it.searchBlockProperty()?.let { s -> visitSearchBlock(s.searchBlock()) }
                } ?: SearchHintsValue(),
        )
    }

    private fun visitColumnTopLevel(od: TTRParser.ObjectDefinitionContext): ColumnDef =
        buildColumn(od.id().text, defSource(od), od.columnDef().columnProperty())

    private fun visitColumnInline(ctx: TTRParser.ColumnInlineContext): ColumnDef =
        buildColumn(ctx.id().text, location(ctx), ctx.columnDef().columnProperty())

    private fun buildColumn(
        name: String,
        loc: SourceLocation,
        props: List<TTRParser.ColumnPropertyContext>,
    ): ColumnDef {
        val search =
            props.firstNotNullOfOrNull {
                it.searchBlockProperty()?.let { s -> visitSearchBlock(s.searchBlock()) }
            } ?: SearchHintsValue()
        // v2.0.0: there is no top-level `searchable` on a column — the grammar
        // has no `searchableProperty` at column level, so `searchable: true`
        // outside a `search { }` block is rejected syntactically by the parser.
        return ColumnDef(
            name = name,
            source = loc,
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            type = props.firstNotNullOfOrNull { it.typeProperty()?.let { dataType(it.dataType()) } },
            optional =
                props.firstNotNullOfOrNull { it.optionalProperty()?.BOOLEAN_LITERAL()?.let { b -> b.text.toBoolean() } }
                    ?: false,
            isKey =
                props.firstNotNullOfOrNull { it.isKeyProperty()?.BOOLEAN_LITERAL()?.let { b -> b.text.toBoolean() } }
                    ?: false,
            indexed =
                props.firstNotNullOfOrNull { it.indexedProperty()?.BOOLEAN_LITERAL()?.let { b -> b.text.toBoolean() } }
                    ?: false,
            search = search,
        )
    }

    private fun visitIndexTopLevel(od: TTRParser.ObjectDefinitionContext): IndexDef =
        buildIndex(od.id().text, defSource(od), od.indexDef().indexProperty())

    private fun visitIndexInline(ctx: TTRParser.IndexInlineContext): IndexDef =
        buildIndex(ctx.id().text, location(ctx), ctx.indexDef().indexProperty())

    private fun buildIndex(
        name: String,
        loc: SourceLocation,
        props: List<TTRParser.IndexPropertyContext>,
    ): IndexDef =
        IndexDef(
            name = name,
            source = loc,
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            indexType = props.firstNotNullOfOrNull { it.indexTypeProperty()?.indexTypeValue()?.text },
            columns =
                props.firstNotNullOfOrNull { it.columnNamesListProperty()?.let { c -> stringList(c.listOfStrings()) } }
                    ?: emptyList(),
        )

    private fun visitConstraintTopLevel(od: TTRParser.ObjectDefinitionContext): ConstraintDef =
        buildConstraint(od.id().text, defSource(od), od.constraintDef().constraintProperty())

    private fun visitConstraintInline(ctx: TTRParser.ConstraintInlineContext): ConstraintDef =
        buildConstraint(ctx.id().text, location(ctx), ctx.constraintDef().constraintProperty())

    private fun buildConstraint(
        name: String,
        loc: SourceLocation,
        props: List<TTRParser.ConstraintPropertyContext>,
    ): ConstraintDef =
        ConstraintDef(
            name = name,
            source = loc,
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            constraintType =
                props.firstNotNullOfOrNull {
                    it.constraintTypeProperty()?.constraintTypeValue()?.text
                },
            columns =
                props.firstNotNullOfOrNull { it.columnNamesListProperty()?.let { c -> stringList(c.listOfStrings()) } }
                    ?: emptyList(),
        )

    private fun visitFk(od: TTRParser.ObjectDefinitionContext): FkDef {
        val props = od.fkDef().fkProperty()
        return FkDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            from = props.firstNotNullOfOrNull { it.fromProperty()?.let { f -> visitValue(f.value()) } },
            to = props.firstNotNullOfOrNull { it.toProperty()?.let { t -> visitValue(t.value()) } },
        )
    }

    private fun visitProcedure(od: TTRParser.ObjectDefinitionContext): ProcedureDef {
        val props = od.procedureDef().procedureProperty()
        return ProcedureDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            parameters =
                props.flatMap {
                    it.parametersProperty()?.let { p -> visitParameterList(p.parameterDefList()) }
                        ?: emptyList()
                },
            resultColumns =
                props.flatMap {
                    it.resultColumnsProperty()?.let { r -> visitColumnDefList(r.columnDefList()) }
                        ?: emptyList()
                },
        )
    }

    private fun visitEntity(od: TTRParser.ObjectDefinitionContext): EntityDef {
        val props = od.entityDef().entityProperty()
        return EntityDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            labelPlural =
                props.firstNotNullOfOrNull {
                    it.labelPluralProperty()?.STRING_LITERAL()?.let { s ->
                        stringLiteral(s)
                    }
                },
            nameAttribute =
                props.firstNotNullOfOrNull {
                    it
                        .nameAttributeProperty()
                        ?.id()
                        ?.let { makeRef(it) }
                },
            codeAttribute =
                props.firstNotNullOfOrNull {
                    it
                        .codeAttributeProperty()
                        ?.id()
                        ?.let { makeRef(it) }
                },
            aliases =
                props.firstNotNullOfOrNull { it.aliasesProperty()?.let { stringList(it.listOfStrings()) } }
                    ?: emptyList(),
            attributes =
                props.flatMap {
                    it.attributesProperty()?.let { a -> visitAttributeList(a.attributeDefList()) }
                        ?: emptyList()
                },
            roles =
                props.firstNotNullOfOrNull {
                    it.rolesProperty()?.let { r -> idList(r.listOfIds()) }
                } ?: emptyList(),
            displayLabel =
                props.firstNotNullOfOrNull {
                    it.displayLabelProperty()?.let { d -> visitLocalizedString(d.localizedString()) }
                },
            search =
                props.firstNotNullOfOrNull {
                    it.searchBlockProperty()?.let { s -> visitSearchBlock(s.searchBlock()) }
                } ?: SearchHintsValue(),
            binding =
                props.firstNotNullOfOrNull {
                    it.bindingProperty()?.let { m -> visitBindingProperty(m) }
                },
        )
    }

    private fun visitAttributeTopLevel(od: TTRParser.ObjectDefinitionContext): AttributeDef =
        buildAttribute(od.id().text, defSource(od), od.attributeDef().attributeProperty())

    private fun visitAttributeInline(ctx: TTRParser.AttributeInlineContext): AttributeDef =
        buildAttribute(ctx.id().text, location(ctx), ctx.attributeDef().attributeProperty())

    private fun buildAttribute(
        name: String,
        loc: SourceLocation,
        props: List<TTRParser.AttributePropertyContext>,
    ): AttributeDef {
        val search =
            props.firstNotNullOfOrNull {
                it.searchBlockProperty()?.let { s -> visitSearchBlock(s.searchBlock()) }
            } ?: SearchHintsValue()
        // v2.0.0: no top-level `searchable` on an attribute (lives in `search`).
        return AttributeDef(
            name = name,
            source = loc,
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            type = props.firstNotNullOfOrNull { it.typeProperty()?.let { dataType(it.dataType()) } },
            isKey =
                props.firstNotNullOfOrNull { it.isKeyProperty()?.BOOLEAN_LITERAL()?.let { b -> b.text.toBoolean() } }
                    ?: false,
            optional =
                props.firstNotNullOfOrNull { it.optionalProperty()?.BOOLEAN_LITERAL()?.let { b -> b.text.toBoolean() } }
                    ?: false,
            displayLabel =
                props.firstNotNullOfOrNull {
                    it.displayLabelProperty()?.let { d -> visitLocalizedString(d.localizedString()) }
                },
            valueLabels =
                props.firstNotNullOfOrNull {
                    it.valueLabelsProperty()?.let { v -> visitValueLabels(v.valueLabelsBody()) }
                } ?: emptyMap(),
            search = search,
            binding =
                props.firstNotNullOfOrNull {
                    it.bindingProperty()?.let { m -> visitBindingProperty(m) }
                },
        )
    }

    private fun visitRelation(od: TTRParser.ObjectDefinitionContext): RelationDef {
        val props = od.relationDef().relationProperty()
        return RelationDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            from = props.firstNotNullOfOrNull { it.fromProperty()?.let { f -> visitValue(f.value()) } },
            to = props.firstNotNullOfOrNull { it.toProperty()?.let { t -> visitValue(t.value()) } },
            cardinality =
                props.firstNotNullOfOrNull {
                    it.cardinalityProperty()?.let { c ->
                        visitObject(c.object_())
                    }
                },
            join =
                props.firstNotNullOfOrNull { it.joinProperty()?.let { j -> visitListAsValues(j.list()) } }
                    ?: emptyList(),
            search =
                props.firstNotNullOfOrNull {
                    it.searchBlockProperty()?.let { s -> visitSearchBlock(s.searchBlock()) }
                } ?: SearchHintsValue(),
            binding =
                props.firstNotNullOfOrNull {
                    it.bindingProperty()?.let { m -> visitBindingProperty(m) }
                },
        )
    }

    private fun visitEr2dbEntity(od: TTRParser.ObjectDefinitionContext): Er2DbEntityDef {
        val props = od.er2dbEntityDef().er2dbEntityProperty()
        return Er2DbEntityDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            entity =
                props.firstNotNullOfOrNull {
                    it
                        .entityProperty_()
                        ?.id()
                        ?.let { makeRef(it) }
                },
            target = props.firstNotNullOfOrNull { it.targetProperty()?.let { t -> visitTargetValue(t) } },
            whereFilter =
                props.firstNotNullOfOrNull {
                    it.whereFilterProperty()?.let { w ->
                        visitObject(w.object_())
                    }
                },
        )
    }

    private fun visitEr2dbAttribute(od: TTRParser.ObjectDefinitionContext): Er2DbAttributeDef {
        val props = od.er2dbAttributeDef().er2dbAttributeProperty()
        return Er2DbAttributeDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            attribute =
                props.firstNotNullOfOrNull {
                    it
                        .attributeProperty_()
                        ?.id()
                        ?.let { makeRef(it) }
                },
            target = props.firstNotNullOfOrNull { it.targetProperty()?.let { t -> visitTargetValue(t) } },
        )
    }

    private fun visitEr2dbRelation(od: TTRParser.ObjectDefinitionContext): Er2DbRelationDef {
        val props = od.er2dbRelationDef().er2dbRelationProperty()
        return Er2DbRelationDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            relation =
                props.firstNotNullOfOrNull {
                    it
                        .relationProperty_()
                        ?.id()
                        ?.let { makeRef(it) }
                },
            fk =
                props.firstNotNullOfOrNull {
                    it
                        .fkProperty_()
                        ?.id()
                        ?.let { makeRef(it) }
                },
        )
    }

    private fun visitQuery(od: TTRParser.ObjectDefinitionContext): QueryDef {
        val props = od.queryDef().queryProperty()
        val language = props.firstNotNullOfOrNull { it.languageProperty()?.languageValue()?.text }
        val sourceTextBlock =
            props.firstNotNullOfOrNull {
                it.sourceTextProperty()?.embeddedBlock()?.let { e -> walkEmbeddedBlock(e) }
            }

        // `language:` is inferred from the tag and soft-deprecated when a tagged
        // block is present (DESIGN §6); a value disagreeing with the tag is an
        // error in TS. The Kotlin parser empties definitions on any error, so to
        // stay non-blocking (and keep the model usable) both are emitted as
        // warnings here — the value contract and the dump are unaffected.
        if (sourceTextBlock is TaggedBlockValue && language != null) {
            if (language != sourceTextBlock.language) {
                warn(
                    od,
                    "${DiagnosticCode.LanguageTagMismatch}: 'language: $language' disagrees with " +
                        "the block tag '${sourceTextBlock.tag}' (${sourceTextBlock.language})",
                )
            }
            warn(
                od,
                "${DiagnosticCode.DeprecatedLanguageProperty}: 'language' on query is deprecated; " +
                    "it is inferred from the '${sourceTextBlock.tag}' block tag",
            )
        }

        return QueryDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d ->
                        stringForm(d.stringLiteralForm())
                    }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            language = language,
            parameters =
                props.flatMap {
                    it.parametersProperty()?.let { p -> visitParameterList(p.parameterDefList()) }
                        ?: emptyList()
                },
            sourceTextBlock = sourceTextBlock,
            sourceText = sourceTextBlock?.let { embeddedValueText(it) },
            search =
                props.firstNotNullOfOrNull {
                    it.searchBlockProperty()?.let { s -> visitSearchBlock(s.searchBlock()) }
                } ?: SearchHintsValue(),
        )
    }

    // ----- role + er2cnc_role visitors -----

    private fun visitRole(od: TTRParser.ObjectDefinitionContext): RoleDef {
        val props = od.roleDef().roleProperty()
        return RoleDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d -> stringForm(d.stringLiteralForm()) }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            label =
                props.firstNotNullOfOrNull {
                    it.labelProperty()?.let { l -> visitLocalizedString(l.localizedString()) }
                },
            search =
                props.firstNotNullOfOrNull {
                    it.searchBlockProperty()?.let { s -> visitSearchBlock(s.searchBlock()) }
                } ?: SearchHintsValue(),
        )
    }

    private fun visitEr2CncRole(od: TTRParser.ObjectDefinitionContext): Er2CncRoleDef {
        val props = od.er2cncRoleDef().er2cncRoleProperty()
        return Er2CncRoleDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d -> stringForm(d.stringLiteralForm()) }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            entity =
                props.firstNotNullOfOrNull {
                    it
                        .entityProperty_()
                        ?.id()
                        ?.let { makeRef(it) }
                },
            role =
                props.firstNotNullOfOrNull {
                    it
                        .roleProperty_()
                        ?.id()
                        ?.let { makeRef(it) }
                },
        )
    }

    private fun visitDrillMap(od: TTRParser.ObjectDefinitionContext): DrillMapDef {
        val props = od.drillMapDef().drillMapProperty()
        val from =
            props.firstNotNullOfOrNull {
                it
                    .fromProperty()
                    ?.value()
                    ?.id()
                    ?.let { makeRef(it) }
            }
        val to =
            props.firstNotNullOfOrNull {
                it
                    .toProperty()
                    ?.value()
                    ?.id()
                    ?.let { makeRef(it) }
            }
        val argsMap =
            props
                .firstNotNullOfOrNull { it.argsProperty() }
                ?.drillArgsMap()
                ?.drillArgEntry()
                ?.associate { entry ->
                    entry.id().text to (stringForm(entry.stringLiteralForm()) ?: "")
                }
                ?: emptyMap()
        val display =
            props.firstNotNullOfOrNull {
                it.displayProperty()?.let { d -> visitLocalizedString(d.localizedString()) }
            }
        val overrideAuto =
            props
                .firstNotNullOfOrNull {
                    it.overrideProperty()?.BOOLEAN_LITERAL()?.text
                }?.equals("true", ignoreCase = true) ?: false
        return DrillMapDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d -> stringForm(d.stringLiteralForm()) }
                },
            tags =
                props.firstNotNullOfOrNull {
                    it.tagsProperty()?.let { stringList(it.listOfStrings()) }
                } ?: emptyList(),
            from = from,
            to = to,
            args = argsMap,
            display = display,
            overrideAuto = overrideAuto,
        )
    }

    /**
     * v3.0 — `def area <id> { description?, tags?, packages: [...], entities: [...] }`.
     * Mirrors the TS `walkAreaDef`. Members are dotted ids collapsed to strings;
     * per-member source spans are recorded in parallel lists.
     */
    private fun visitArea(od: TTRParser.ObjectDefinitionContext): AreaDef {
        val props = od.areaDef().areaProperty()
        val packagesCtx = props.firstNotNullOfOrNull { it.areaPackagesProperty() }
        val entitiesCtx = props.firstNotNullOfOrNull { it.areaEntitiesProperty() }
        val packageIds = packagesCtx?.id() ?: emptyList()
        val entityIds = entitiesCtx?.id() ?: emptyList()
        return AreaDef(
            name = od.id().text,
            source = defSource(od),
            description =
                props.firstNotNullOfOrNull {
                    it.descriptionProperty()?.let { d -> stringForm(d.stringLiteralForm()) }
                },
            tags =
                props.firstNotNullOfOrNull { it.tagsProperty()?.let { stringList(it.listOfStrings()) } } ?: emptyList(),
            packages = packageIds.map { it.text },
            entities = entityIds.map { it.text },
            packageSources = packageIds.map { location(it) },
            entitySources = entityIds.map { location(it) },
        )
    }

    /**
     * v4.1 — `def world`. Mirrors the TS `walkWorldDef`. engine/executor/storage
     * are nested; manifest entries are transported opaque (T6 β data — MD5).
     */
    private fun visitWorld(od: TTRParser.ObjectDefinitionContext): WorldDef {
        val members = od.worldDef().worldMember()
        var description: String? = null
        var tags: List<String> = emptyList()
        var extends: String? = null
        val engines = mutableListOf<EngineDef>()
        val executors = mutableListOf<ExecutorDef>()
        val storages = mutableListOf<StorageDef>()

        for (m in members) {
            val wp = m.worldProperty()
            if (wp != null) {
                wp.descriptionProperty()?.let { description = stringForm(it.stringLiteralForm()) }
                wp.tagsProperty()?.let { tags = stringList(it.listOfStrings()) }
                wp.extendsProperty()?.let { extends = it.id().text }
                continue
            }
            val memberName = m.id().text
            when {
                m.ENGINE() != null ->
                    engines.add(
                        visitEngineDef(m.engineDef().enginePartProperty(), memberName, location(m)),
                    )
                m.EXECUTOR() != null ->
                    executors.add(
                        visitEngineDef(m.executorDef().enginePartProperty(), memberName, location(m)).let {
                            ExecutorDef(
                                it.name,
                                it.source,
                                it.description,
                                it.tags,
                                it.type,
                                it.version,
                                it.extends,
                                it.manifest,
                            )
                        },
                    )
                m.STORAGE() != null -> storages.add(visitStorageDef(m.storageDef(), memberName, location(m)))
            }
        }

        return WorldDef(
            name = od.id().text,
            source = defSource(od),
            description = description,
            tags = tags,
            extends = extends,
            engines = engines,
            executors = executors,
            storages = storages,
        )
    }

    private fun visitEngineDef(
        props: List<TTRParser.EnginePartPropertyContext>,
        name: String,
        source: SourceLocation,
    ): EngineDef {
        var description: String? = null
        var tags: List<String> = emptyList()
        var type: String? = null
        var version: String? = null
        var extends: String? = null
        val manifest = LinkedHashMap<String, PropertyValue>()
        for (p in props) {
            when {
                p.descriptionProperty() != null -> description = stringForm(p.descriptionProperty().stringLiteralForm())
                p.tagsProperty() != null -> tags = stringList(p.tagsProperty().listOfStrings())
                p.typeProperty() != null -> type = p.typeProperty().dataType().text
                p.versionProperty() != null -> version = stringLiteral(p.versionProperty().STRING_LITERAL())
                p.extendsProperty() != null -> extends = p.extendsProperty().id().text
                p.propertyEntry() != null ->
                    manifest[
                        p
                            .propertyEntry()
                            .key()
                            .id()
                            .text,
                    ] = visitValue(p.propertyEntry().value())
            }
        }
        return EngineDef(name, source, description, tags, type, version, extends, manifest)
    }

    private fun visitStorageDef(
        ctx: TTRParser.StorageDefContext,
        name: String,
        source: SourceLocation,
    ): StorageDef {
        var description: String? = null
        var tags: List<String> = emptyList()
        var type: String? = null
        var extends: String? = null
        var via: String? = null
        var hosts: List<String> = emptyList()
        var staging = false
        val schemas = mutableListOf<WorldSchemaDef>()
        val manifest = LinkedHashMap<String, PropertyValue>()
        for (p in ctx.storageProperty()) {
            when {
                p.descriptionProperty() != null -> description = stringForm(p.descriptionProperty().stringLiteralForm())
                p.tagsProperty() != null -> tags = stringList(p.tagsProperty().listOfStrings())
                p.typeProperty() != null -> type = p.typeProperty().dataType().text
                p.extendsProperty() != null -> extends = p.extendsProperty().id().text
                p.viaProperty() != null -> via = p.viaProperty().id().text
                p.hostsProperty() != null ->
                    hosts =
                        p
                            .hostsProperty()
                            .listOfIds()
                            .id()
                            .map { it.text }
                p.stagingProperty() != null -> staging = p.stagingProperty().BOOLEAN_LITERAL().text == "true"
                p.SCHEMA() != null -> schemas.add(visitWorldSchema(p.worldSchemaDef(), p.id().text, location(p)))
                p.propertyEntry() != null ->
                    manifest[
                        p
                            .propertyEntry()
                            .key()
                            .id()
                            .text,
                    ] = visitValue(p.propertyEntry().value())
            }
        }
        return StorageDef(name, source, description, tags, type, extends, via, hosts, staging, schemas, manifest)
    }

    private fun visitWorldSchema(
        ctx: TTRParser.WorldSchemaDefContext,
        name: String,
        source: SourceLocation,
    ): WorldSchemaDef {
        val fields =
            ctx.worldSchemaField().map { f ->
                WorldSchemaField(name = f.id().text, type = f.dataType().text, source = location(f))
            }
        return WorldSchemaDef(name = name, source = source, fields = fields)
    }

    private fun visitLocalizedString(ctx: TTRParser.LocalizedStringContext?): LocalizedStringValue {
        if (ctx == null) return LocalizedStringValue(emptyMap())
        val entries =
            ctx.localizedEntry().associate { entry ->
                entry.id().text to (stringForm(entry.stringLiteralForm()) ?: "")
            }
        return LocalizedStringValue(entries)
    }

    private fun visitLocalizedStringList(
        ctx: TTRParser.LocalizedStringListContext?,
        propertyName: String,
    ): LocalizedStringListValue {
        if (ctx == null) return LocalizedStringListValue(emptyMap())
        val entries = LinkedHashMap<String, List<String>>()
        for (entry in ctx.localizedStringListEntry()) {
            val lang = entry.id().text
            if (entries.containsKey(lang)) {
                warn(
                    entry,
                    "duplicate language entry '$lang' in $propertyName — last value wins",
                )
            }
            if (lang !in supportedLanguages) {
                val supportedLangs =
                    supportedLanguages.sorted().joinToString(", ")
                warn(
                    entry,
                    "unknown language code '$lang' in $propertyName (supported: $supportedLangs)",
                )
            }
            val values = stringList(entry.listOfStrings())
            if (propertyName == "keywords") {
                values.forEach { token ->
                    if (token.any { it.isWhitespace() }) {
                        warn(
                            entry,
                            "keyword token '$token' contains whitespace — split into separate list entries",
                        )
                    }
                }
            }
            entries[lang] = values
        }
        return LocalizedStringListValue(entries)
    }

    private fun visitSearchBlock(ctx: TTRParser.SearchBlockContext?): SearchHintsValue {
        if (ctx == null) return SearchHintsValue()
        val subs = ctx.searchSubProperty()
        if (subs.isEmpty()) {
            warn(ctx, "empty `search { }` block — remove or populate to take effect")
        }

        subs
            .groupBy { sub ->
                when {
                    sub.keywordsProperty() != null -> "keywords"
                    sub.patternsProperty() != null -> "patterns"
                    sub.descriptionsProperty() != null -> "descriptions"
                    sub.examplesProperty() != null -> "examples"
                    sub.aliasesProperty() != null -> "aliases"
                    sub.searchableProperty() != null -> "searchable"
                    sub.fuzzyProperty() != null -> "fuzzy"
                    else -> "unknown"
                }
            }.filter { (_, group) -> group.size > 1 }
            .forEach { (kind, _) ->
                err(
                    ctx,
                    DiagnosticCode.DuplicateSearchProperty,
                    "'$kind' appears more than once in one search block",
                )
            }

        val patterns =
            subs.firstNotNullOfOrNull {
                it.patternsProperty()?.let { p -> stringList(p.listOfStrings()) }
            } ?: emptyList()
        if (patterns.size != patterns.toSet().size) {
            patterns
                .groupingBy { it }
                .eachCount()
                .filter { (_, c) -> c > 1 }
                .keys
                .forEach { dup -> warn(ctx, "duplicate pattern '$dup' in search.patterns") }
        }
        val searchable =
            subs.firstNotNullOfOrNull {
                it
                    .searchableProperty()
                    ?.BOOLEAN_LITERAL()
                    ?.text
                    ?.toBoolean()
            } ?: false
        val fuzzy =
            subs.firstNotNullOfOrNull {
                it
                    .fuzzyProperty()
                    ?.BOOLEAN_LITERAL()
                    ?.text
                    ?.toBoolean()
            } ?: false
        if (fuzzy && !searchable) {
            warn(ctx, "fuzzy-without-searchable: `fuzzy: true` has no effect unless `searchable: true`")
        }
        return SearchHintsValue(
            keywords =
                subs.firstNotNullOfOrNull {
                    it.keywordsProperty()?.let { k -> visitLocalizedStringList(k.localizedStringList(), "keywords") }
                } ?: LocalizedStringListValue(),
            patterns = patterns,
            descriptions =
                subs.firstNotNullOfOrNull {
                    it.descriptionsProperty()?.let { d ->
                        visitLocalizedStringList(d.localizedStringList(), "descriptions")
                    }
                } ?: LocalizedStringListValue(),
            examples =
                subs.firstNotNullOfOrNull {
                    it.examplesProperty()?.let { e -> stringList(e.listOfStrings()) }
                } ?: emptyList(),
            aliases =
                subs.firstNotNullOfOrNull {
                    it.aliasesProperty()?.let { a -> stringList(a.listOfStrings()) }
                } ?: emptyList(),
            searchable = searchable,
            fuzzy = fuzzy,
        )
    }

    private fun visitValueLabels(ctx: TTRParser.ValueLabelsBodyContext?): Map<String, LocalizedStringValue> {
        if (ctx == null) return emptyMap()
        val out = LinkedHashMap<String, LocalizedStringValue>()
        for (entry in ctx.valueLabelEntry()) {
            val key = stringForm(entry.stringLiteralForm()) ?: continue
            // Last write wins on duplicate keys; the loader surfaces a duplicate-key warning.
            out[key] = visitLocalizedString(entry.localizedString())
        }
        return out
    }

    private fun idList(ctx: TTRParser.ListOfIdsContext?): List<Reference> {
        if (ctx == null) return emptyList()
        return ctx.id().map { makeRef(it) }
    }

    // ----- List visitors -----

    private fun visitColumnDefList(ctx: TTRParser.ColumnDefListContext?): List<ColumnDef> {
        if (ctx == null) return emptyList()
        return ctx.children
            .filterIsInstance<TTRParser.ColumnInlineContext>()
            .map { visitColumnInline(it) }
    }

    private fun visitIndexDefList(ctx: TTRParser.IndexDefListContext?): List<IndexDef> {
        if (ctx == null) return emptyList()
        return ctx.children
            .filterIsInstance<TTRParser.IndexInlineContext>()
            .map { visitIndexInline(it) }
    }

    private fun visitConstraintDefList(ctx: TTRParser.ConstraintDefListContext?): List<ConstraintDef> {
        if (ctx == null) return emptyList()
        return ctx.children
            .filterIsInstance<TTRParser.ConstraintInlineContext>()
            .map { visitConstraintInline(it) }
    }

    private fun visitAttributeList(ctx: TTRParser.AttributeDefListContext?): List<AttributeDef> {
        if (ctx == null) return emptyList()
        return ctx.children
            .filterIsInstance<TTRParser.AttributeInlineContext>()
            .map { visitAttributeInline(it) }
    }

    /**
     * Each parameter is a `{ name: <id>, type: <dataType>, label: "...", direction: <id> }` block
     * (`paramProperty` in the grammar; `direction` only on procedure params). It is materialised as a
     * [PropertyValue.ObjectValue] so the renderer can round-trip it: `name`/`type`/`direction` keep their
     * bare-identifier shape via [PropertyValue.IdValue], `label` is a plain string.
     */
    private fun visitParameterList(ctx: TTRParser.ParameterDefListContext?): List<PropertyValue> {
        if (ctx == null) return emptyList()
        return ctx.children
            .filterIsInstance<TTRParser.ParameterInlineContext>()
            .map { inline ->
                val entries = LinkedHashMap<String, PropertyValue>()
                for (prop in inline.paramProperty()) {
                    when {
                        prop.nameProperty() != null ->
                            entries["name"] = idValue(prop.nameProperty().id().text, prop.nameProperty())
                        prop.typeProperty() != null ->
                            dataType(prop.typeProperty().dataType())?.let { dt ->
                                entries["type"] = idValue(dt.name, prop.typeProperty())
                            }
                        prop.paramLabelProperty() != null ->
                            stringForm(prop.paramLabelProperty().stringLiteralForm())?.let { lbl ->
                                entries["label"] = PropertyValue.StringValue(lbl, location(prop.paramLabelProperty()))
                            }
                        prop.directionProperty() != null ->
                            entries["direction"] = idValue(prop.directionProperty().id().text, prop.directionProperty())
                    }
                }
                PropertyValue.ObjectValue(entries, location(inline))
            }
    }

    // ----- Generic value visitor -----

    private fun visitValue(ctx: TTRParser.ValueContext): PropertyValue =
        when {
            ctx.literal() != null -> visitLiteral(ctx.literal())
            ctx.id() != null -> idValue(ctx.id().text, ctx.id())
            ctx.list() != null -> PropertyValue.ListValue(visitListAsValues(ctx.list()), location(ctx.list()))
            ctx.object_() != null -> visitObject(ctx.object_())
            ctx.functionCall() != null -> visitFunctionCall(ctx.functionCall())
            else -> PropertyValue.NullValue(location(ctx))
        }

    private fun visitLiteral(ctx: TTRParser.LiteralContext): PropertyValue =
        when {
            ctx.NUMBER_LITERAL() != null ->
                PropertyValue.NumberValue(
                    ctx.NUMBER_LITERAL().text.toDouble(),
                    location(ctx),
                )
            ctx.stringLiteralForm() != null -> {
                val form = ctx.stringLiteralForm()
                if (form.TRIPLE_STRING_LITERAL() != null) {
                    PropertyValue.TripleStringValue(tripleStringLiteral(form.TRIPLE_STRING_LITERAL()), location(ctx))
                } else {
                    PropertyValue.StringValue(stringLiteral(form.STRING_LITERAL()), location(ctx))
                }
            }
            ctx.BOOLEAN_LITERAL() != null ->
                PropertyValue.BoolValue(
                    ctx.BOOLEAN_LITERAL().text.toBoolean(),
                    location(ctx),
                )
            ctx.NULL_LITERAL() != null -> PropertyValue.NullValue(location(ctx))
            else -> PropertyValue.NullValue(location(ctx))
        }

    private fun visitListAsValues(ctx: TTRParser.ListContext?): List<PropertyValue> =
        ctx?.value()?.map { visitValue(it) } ?: emptyList()

    // ----- v3.0: inline binding visitors (was v2.1 `mapping:`) -----

    /**
     * Walks `binding: <bareId>` or `binding: { ... }` into the typed
     * [BindingProperty] hierarchy. Mirrors `walkBindingProperty` in the modeler
     * TS walker.
     */
    private fun visitBindingProperty(ctx: TTRParser.BindingPropertyContext): BindingProperty {
        val valueCtx = ctx.bindingValue()
        val bareId = valueCtx.id()
        if (bareId != null) {
            return BindingPropertyBareId(
                id = makeRef(bareId),
                source = location(valueCtx),
            )
        }
        val blockCtx = valueCtx.bindingBlock()
        var target: TargetValue? = null
        var columns: List<BindingColumnEntry> = emptyList()
        var fk: Reference? = null
        for (p in blockCtx.bindingBlockProperty()) {
            p.targetProperty()?.let { tp -> target = visitTargetValue(tp) }
            p.bindingColumnsProperty()?.let { cp ->
                columns = visitBindingColumnMap(cp.bindingColumnMap())
            }
            p.fkProperty_()?.let { fp ->
                fp.id()?.let { fk = makeRef(it) }
            }
        }
        return BindingPropertyBlock(
            target = target,
            columns = columns,
            fk = fk,
            source = location(blockCtx),
        )
    }

    /**
     * Walks `target: { ... }` or the relaxed `target: <bareId>` form. Used both
     * by the inline binding block and by the explicit `def er2db_*` walkers.
     */
    private fun visitTargetValue(ctx: TTRParser.TargetPropertyContext): TargetValue {
        val idCtx = ctx.id()
        if (idCtx != null) {
            return TargetReferenceValue(
                ref = makeRef(idCtx),
                source = location(idCtx),
            )
        }
        return TargetObjectValue(
            obj = visitObject(ctx.object_()),
            source = location(ctx.object_()),
        )
    }

    private fun visitBindingColumnMap(ctx: TTRParser.BindingColumnMapContext?): List<BindingColumnEntry> {
        if (ctx == null) return emptyList()
        return ctx.bindingColumnEntry().map { e ->
            val name = e.id().text
            val v = e.bindingColumnValue()
            val value: BindingColumnValue =
                when {
                    v.id() != null ->
                        BindingColumnBareId(
                            id = makeRef(v.id()),
                            source = location(v),
                        )
                    v.bindingTargetValue() != null -> {
                        // Form (b) — `kód_artiklu: { target: KOD_ZBOZI }` or
                        // `název_artiklu: { target: { column: NAZEV_ZBOZI } }`. Mirror the
                        // modeler-side shape: wrap in a synthetic { target: <inner> } object
                        // so downstream consumers see the same structure as form (c).
                        val mtv = v.bindingTargetValue()
                        val inner: PropertyValue =
                            if (mtv.id() != null) {
                                idValue(mtv.id().text, mtv.id())
                            } else {
                                visitObject(mtv.object_())
                            }
                        BindingColumnObject(
                            obj = PropertyValue.ObjectValue(mapOf("target" to inner), location(v)),
                            source = location(v),
                        )
                    }
                    else ->
                        BindingColumnObject(
                            obj = visitObject(v.object_()),
                            source = location(v),
                        )
                }
            BindingColumnEntry(
                name = name,
                value = value,
                source = location(e),
            )
        }
    }

    private fun visitObject(ctx: TTRParser.Object_Context?): PropertyValue.ObjectValue {
        if (ctx == null) return PropertyValue.ObjectValue(emptyMap(), SourceLocation.UNKNOWN)
        // [PropertyValue.ObjectValue.entries] is a Map (last-write-wins) — see the
        // documented AST-NAMING divergence from TS's ordered `ObjectEntry[]`. A
        // duplicate key therefore collapses (and any reference inside the dropped
        // value would vanish from `collectAllReferences`), so surface it as a
        // warning rather than dropping it silently — matching how duplicate
        // language entries / search properties are handled elsewhere.
        val propEntries = ctx.propertyList()?.propertyEntry() ?: emptyList()
        val entries = LinkedHashMap<String, PropertyValue>()
        for (entry in propEntries) {
            val key = entry.key().id().text
            if (entries.containsKey(key)) {
                warn(entry, "duplicate key '$key' in object — last value wins (earlier value dropped)")
            }
            entries[key] = visitValue(entry.value())
        }
        return PropertyValue.ObjectValue(entries, location(ctx))
    }

    private fun visitFunctionCall(ctx: TTRParser.FunctionCallContext): PropertyValue.FunctionCall =
        PropertyValue.FunctionCall(
            name = ctx.id().text,
            args = ctx.value().map { visitValue(it) },
            source = location(ctx),
        )

    // ----- Type / string helpers -----

    private fun dataType(ctx: TTRParser.DataTypeContext?): DataType? {
        if (ctx == null) return null
        ctx.typeValue()?.let { return DataType(name = it.text) }
        var name: String? = null
        var length: Int? = null
        var precision: Int? = null
        ctx.dataTypeProperty().forEach { p ->
            when {
                p.DATA_TYPE() != null -> name = p.typeValue()?.text
                p.LENGTH() != null -> length = p.NUMBER_LITERAL()?.text?.toIntOrNull()
                p.PRECISION() != null -> precision = p.NUMBER_LITERAL()?.text?.toIntOrNull()
            }
        }
        return DataType(name = name ?: "object", length = length, precision = precision)
    }

    private fun stringForm(ctx: TTRParser.StringLiteralFormContext?): String? {
        if (ctx == null) return null
        ctx.STRING_LITERAL()?.let { return stringLiteral(it) }
        ctx.TRIPLE_STRING_LITERAL()?.let { return tripleStringLiteral(it) }
        // A plain `"""tag␊…"""` whose first line is a bare word lexes as
        // TAGGED_BLOCK_LITERAL (e.g. `"""Ne␊1 = Ano"""`); outside sourceText /
        // definitionSql it is a plain triple-string — the tag word is just text.
        ctx.TAGGED_BLOCK_LITERAL()?.let { return tripleStringLiteral(it) }
        return null
    }

    private fun stringLiteral(node: TerminalNode): String {
        val raw = node.text
        return raw.substring(1, raw.length - 1).unescape()
    }

    private fun tripleStringLiteral(node: TerminalNode): String {
        val raw = node.text
        val inner = raw.substring(3, raw.length - 3)
        return Dedent.applyTextwrapDedent(inner)
    }

    /** The opening fence of a tagged block: `<tag>[ \t]*\r?\n` (the lexer guarantees it). */
    private val taggedOpener = Regex("^([A-Za-z][A-Za-z0-9-]*)([ \t]*\r?\n)")

    /** The single trailing close-fence newline removed in §4 step 6. */
    private val trailingNewline = Regex("\r?\n$")

    /**
     * `sourceText` / `definitionSql` values (embedded-sql DESIGN §2.2, §4). Mirrors
     * the TS `walkEmbeddedBlock`:
     *   - STRING / TRIPLE_STRING → unchanged plain-string behaviour;
     *   - TAGGED_BLOCK → tag-peel → dedent → strip exactly one trailing newline,
     *     resolve the tag via [TAG_REGISTRY] into a [TaggedBlockValue]. An unknown
     *     tag warns and falls back to the extracted text (DESIGN §5).
     */
    private fun walkEmbeddedBlock(ctx: TTRParser.EmbeddedBlockContext): PropertyValue {
        ctx.STRING_LITERAL()?.let {
            return PropertyValue.StringValue(stringLiteral(it), location(ctx))
        }
        ctx.TRIPLE_STRING_LITERAL()?.let {
            return PropertyValue.TripleStringValue(tripleStringLiteral(it), location(ctx))
        }

        val node = ctx.TAGGED_BLOCK_LITERAL()!!
        val loc = location(ctx)
        val inner = node.text.substring(3, node.text.length - 3)
        val opener = taggedOpener.find(inner)!!
        val tag = opener.groupValues[1]
        val body = inner.substring(opener.value.length)
        val dedented = Dedent.applyTextwrapDedentWithIndent(body)
        val value = trailingNewline.replace(dedented.value, "")

        // The tag token sits immediately after the opening `"""`.
        val tagSource =
            SourceLocation(
                file = fileLabel,
                line = loc.line,
                column = loc.column + 3,
                endLine = loc.line,
                endColumn = loc.column + 3 + tag.length,
                offsetStart = loc.offsetStart + 3,
                offsetEnd = loc.offsetStart + 3 + tag.length,
            )

        val entry = resolveTag(tag)
        if (entry == null) {
            warn(ctx, "${DiagnosticCode.UnknownLanguageTag}: Unknown embedded-language tag '$tag'")
            // Stored as raw text (DESIGN §5) — keep the extracted value, drop analysis.
            return PropertyValue.TripleStringValue(value, loc)
        }

        // Body region in file coordinates; the per-line column shift is
        // `indentWidth` (DESIGN §8 uniform source map). Refined further in Phase 2.
        val valueSource =
            SourceLocation(
                file = fileLabel,
                line = loc.line + 1,
                column = dedented.indentWidth,
                endLine = loc.endLine,
                endColumn = maxOf(dedented.indentWidth, loc.endColumn - 3),
                offsetStart = loc.offsetStart + 3 + opener.value.length,
                offsetEnd = loc.offsetEnd - 3,
            )

        return TaggedBlockValue(
            tag = tag,
            language = entry.language,
            dialect = entry.dialect,
            value = value,
            tagSource = tagSource,
            valueSource = valueSource,
            indentWidth = dedented.indentWidth,
            source = loc,
        )
    }

    /** Flattened text of an embedded block value (see [QueryDef.sourceText]). */
    private fun embeddedValueText(v: PropertyValue): String =
        when (v) {
            is PropertyValue.StringValue -> v.raw
            is PropertyValue.TripleStringValue -> v.raw
            is TaggedBlockValue -> v.value
            else -> error("embeddedValueText: unexpected ${v::class.simpleName}")
        }

    private fun stringList(ctx: TTRParser.ListOfStringsContext?): List<String> {
        if (ctx == null) return emptyList()
        return ctx.stringLiteralForm().mapNotNull { stringForm(it) }
    }

    /**
     * `primaryKey` value — a quoted-string list, a bare-id list, or a single bare
     * id (`primaryKey: IDSTRED`). All three collapse to the column-name list,
     * mirroring the TS walker's `walkPrimaryKeyValue`.
     */
    private fun primaryKeyList(ctx: TTRParser.PrimaryKeyValueContext?): List<String> {
        if (ctx == null) return emptyList()
        ctx.listOfStrings()?.let { return stringList(it) }
        ctx.listOfIds()?.let { lst -> return lst.id().map { it.text } }
        ctx.id()?.let { return listOf(it.text) }
        return emptyList()
    }

    /**
     * Source span for a top-level definition. The grammar is
     * `definition : DEF objectDefinition`, so the [od] context starts at the
     * kind keyword (`model`, `entity`, …) and excludes `def`. The canonical TS
     * walker (`walkDefinition` uses `DefinitionContext`) spans the whole
     * `def … ` form, so we read the parent here to match.
     */
    private fun defSource(od: TTRParser.ObjectDefinitionContext): SourceLocation =
        location(od.parent as ParserRuleContext)

    /**
     * Builds a [Reference] from an id (or any single-token) context, carrying the
     * token's own [SourceLocation] — so a collected reference points at the
     * reference itself, not its enclosing def. Mirrors the TS `Reference` shape.
     */
    private fun makeRef(ctx: ParserRuleContext): Reference {
        val path = ctx.text
        return Reference(path, path.split("."), location(ctx))
    }

    /** Builds an [PropertyValue.IdValue], splitting the dotted path into `parts`. */
    private fun idValue(
        path: String,
        ctx: ParserRuleContext,
    ): PropertyValue.IdValue =
        PropertyValue.IdValue(Reference(path, path.split("."), location(ctx)), path.split("."), location(ctx))

    /**
     * D4 source span. `column` is 0-indexed (ANTLR `charPositionInLine`).
     * The multi-token-span invariant: `endColumn = stop.charPositionInLine +
     * stopText.length` — NOT `startColumn + spanLength`.
     */
    private fun location(ctx: ParserRuleContext): SourceLocation {
        val start = ctx.start
        val stop = ctx.stop ?: ctx.start
        val stopText = stop.text ?: ""
        return SourceLocation(
            file = fileLabel,
            line = start.line,
            column = start.charPositionInLine,
            endLine = stop.line,
            endColumn = stop.charPositionInLine + stopText.length,
            offsetStart = start.startIndex,
            offsetEnd = stop.stopIndex + 1,
        )
    }

    private fun String.unescape(): String =
        this
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
}

/** Walker output: schema directive (if any) plus all definitions in source order. */
data class WalkResult(
    val modelDirective: ModelDirective?,
    val definitions: List<Definition>,
    /** Non-blocking semantic warnings emitted during traversal (search feature et al.). */
    val warnings: List<ParseWarning> = emptyList(),
    /** Blocking semantic errors emitted during traversal (duplicate search properties et al.). */
    val errors: List<ParseError> = emptyList(),
    /** The `package <qualifiedName>` declared at the top of the file, if any. */
    val packageName: String? = null,
    /** All `import <qualifiedName> [.*]` statements in file order. */
    val imports: List<ImportStatement> = emptyList(),
)
