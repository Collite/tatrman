import {
  CharStream,
  CommonTokenStream,
  ANTLRErrorListener,
  RecognitionException,
  Recognizer,
  Token,
  ATNSimulator,
} from 'antlr4ng';
import { TTRLexer } from './generated/TTRLexer.js';
import {
  TTRParser,
  DocumentContext,
  DefinitionContext,
  SchemaDirectiveContext,
  ValueContext,
  LiteralContext,
  StringLiteralFormContext,
  EmbeddedBlockContext,
  IdContext,
  ListContext,
  Object_Context,
  FunctionCallContext,
  DataTypeContext,
  LocalizedStringContext,
  LocalizedStringListContext,
  SearchBlockContext,
  ValueLabelsBodyContext,
  ModelDefContext,
  TableDefContext,
  ViewDefContext,
  ColumnDefContext,
  IndexDefContext,
  ConstraintDefContext,
  FkDefContext,
  ProcedureDefContext,
  EntityDefContext,
  AttributeDefContext,
  RelationDefContext,
  Er2dbEntityDefContext,
  Er2dbAttributeDefContext,
  Er2dbRelationDefContext,
  MappingPropertyContext,
  MappingColumnMapContext,
  TargetPropertyContext,
  QueryDefContext,
  RoleDefContext,
  Er2cncRoleDefContext,
  DrillMapDefContext,
  DrillArgsMapContext,
  ColumnDefListContext,
  IndexDefListContext,
  ConstraintDefListContext,
  AttributeDefListContext,
  ParameterDefListContext,
  ListOfStringsContext,
  ListOfIdsContext,
  PrimaryKeyValueContext,
  PackageDeclContext,
  ImportDeclContext,
  GraphBlockContext,
} from './generated/TTRParser.js';
import type {
  SourceLocation,
  Document,
  Definition,
  ParseError,
  ParseResult,
  SchemaDirective,
  PropertyValue,
  StringValue,
  TripleStringValue,
  TaggedBlockValue,
  NumberValue,
  BoolValue,
  NullValue,
  IdValue,
  ListValue,
  ObjectValue,
  ObjectEntry,
  FunctionCallValue,
  Reference,
  LocalizedString,
  LocalizedStringList,
  DataType,
  IndexType,
  ConstraintType,
  QueryLanguage,
  ParameterDirection,
  SearchBlock,
  ValueLabels,
  ParameterDef,
  ModelDef,
  TableDef,
  ViewDef,
  ColumnDef,
  IndexDef,
  ConstraintDef,
  FkDef,
  ProcedureDef,
  EntityDef,
  AttributeDef,
  RelationDef,
  Er2dbEntityDef,
  Er2dbAttributeDef,
  Er2dbRelationDef,
  QueryDef,
  RoleDef,
  Er2cncRoleDef,
  DrillMapDef,
  DrillArgEntry,
  MappingProperty,
  MappingColumnEntry,
  MappingColumnValue,
  PackageDecl,
  ImportDecl,
  GraphBlock,
  GraphLayout,
} from './ast.js';
import { resolveTag } from './tag-registry.js';
import { DiagnosticCode } from './diagnostics.js';
import { RecoveryReportingStrategy } from './recovery.js';
import { attachTrivia } from './cst/attach.js';

class DiagnosticErrorListener implements ANTLRErrorListener {
  private errors: ParseError[];
  private fileLabel: string;

  constructor(errors: ParseError[], fileLabel: string) {
    this.errors = errors;
    this.fileLabel = fileLabel;
  }

  syntaxError(
    _recognizer: Recognizer<ATNSimulator>,
    _offendingSymbol: Token | null,
    line: number,
    charPositionInLine: number,
    msg: string,
    _e: RecognitionException | null
  ): void {
    const symbol = _offendingSymbol;
    this.errors.push({
      code: DiagnosticCode.ParseError,
      message: msg,
      severity: 'error',
      source: {
        file: this.fileLabel,
        line,
        column: charPositionInLine,
        endLine: line,
        endColumn: charPositionInLine + (symbol?.stop ? symbol.stop - symbol.start + 1 : 1),
        offsetStart: symbol?.start ?? 0,
        offsetEnd: symbol ? symbol.stop + 1 : 0,
      },
    });
  }

  reportAmbiguity(): void {}
  reportAttemptingFullContext(): void {}
  reportContextSensitivity(): void {}
}

export function parseString(content: string, fileLabel = '<string>'): ParseResult {
  const inputStream = CharStream.fromString(content);
  const lexer = new TTRLexer(inputStream);
  const tokenStream = new CommonTokenStream(lexer);
  const parser = new TTRParser(tokenStream);

  const errors: ParseError[] = [];

  lexer.removeErrorListeners();
  parser.removeErrorListeners();

  const lexerErrorListener = new DiagnosticErrorListener(errors, fileLabel);
  const parserErrorListener = new DiagnosticErrorListener(errors, fileLabel);

  lexer.addErrorListener(lexerErrorListener);
  parser.addErrorListener(parserErrorListener);

  const recoveryStrategy = new RecoveryReportingStrategy();
  parser.errorHandler = recoveryStrategy;

  try {
    const tree = parser.document();
    const { doc, errors: docErrors } = walkDocument(tree, fileLabel, errors);
    attachTrivia(doc, tokenStream);
    for (const event of recoveryStrategy.recoveryEvents) {
      docErrors.push({
        code: DiagnosticCode.ParseRecoveryInfo,
        message: event.description,
        severity: 'info',
        source: {
          file: fileLabel,
          line: event.line,
          column: event.column,
          endLine: event.line,
          endColumn: event.column + (event.offsetEnd - event.offsetStart),
          offsetStart: event.offsetStart,
          offsetEnd: event.offsetEnd,
        },
      });
    }
    return { ast: doc, errors: docErrors, sourceFile: fileLabel };
  } catch (e) {
    return {
      errors: [
        ...errors,
        {
          message: e instanceof Error ? e.message : 'Unknown parse error',
          severity: 'error',
          source: { file: fileLabel, line: 1, column: 1, endLine: 1, endColumn: 1, offsetStart: 0, offsetEnd: 0 },
        },
      ],
      sourceFile: fileLabel,
    };
  }
}

export async function parseFile(filePath: string): Promise<ParseResult> {
  const fs = await import('fs/promises');
  const content = await fs.readFile(filePath, 'utf-8');
  return parseString(content, filePath);
}

function walkDocument(ctx: DocumentContext, file: string, syntaxErrors: ParseError[]): { doc: Document; errors: ParseError[] } {
  const localErrors: ParseError[] = [...syntaxErrors];

  const packageCtx = ctx.packageDecl();
  const importCtxs = ctx.importDecl();
  const schemaCtx = ctx.schemaDirective();
  const graphCtx = ctx.graphBlock();
  const defContexts = ctx.definition();

  const definitions: Definition[] = defContexts.map((defCtx: DefinitionContext) =>
    walkDefinition(defCtx, file, localErrors)
  );

  if (graphCtx && definitions.length > 0) {
    localErrors.push({
      code: DiagnosticCode.WrongFileKind,
      message: "A file containing 'graph { ... }' must not also contain top-level 'def' definitions.",
      severity: 'error',
      source: makeSourceLocation(graphCtx, file),
    });
  }

  if (file.endsWith('.ttrg') && !graphCtx) {
    localErrors.push({
      code: DiagnosticCode.WrongFileKind,
      message: "A '.ttrg' file must contain a 'graph { ... }' block.",
      severity: 'error',
      source: makeSourceLocation(ctx, file),
    });
  }

  const doc: Document = {
    packageDecl: packageCtx ? walkPackageDecl(packageCtx, file) : undefined,
    imports: importCtxs.map((ic) => walkImportDecl(ic, file)),
    schemaDirective: schemaCtx ? walkSchemaDirective(schemaCtx, file) : undefined,
    graph: graphCtx ? walkGraphBlock(graphCtx, file) : undefined,
    definitions,
    source: makeSourceLocation(ctx, file),
  };

  return { doc, errors: localErrors };
}

function walkSchemaDirective(ctx: SchemaDirectiveContext, file: string): SchemaDirective {
  const schemaCodeCtx = ctx.schemaCode();
  const namespaceCtx = ctx.id();

  let schemaCode = '';
  if (schemaCodeCtx.DB()) schemaCode = 'db';
  else if (schemaCodeCtx.ER()) schemaCode = 'er';
  else if (schemaCodeCtx.MAP()) schemaCode = 'map';
  else if (schemaCodeCtx.QUERY()) schemaCode = 'query';
  else if (schemaCodeCtx.CNC()) schemaCode = 'cnc';

  return {
    schemaCode,
    namespace: namespaceCtx ? namespaceCtx.getText() : undefined,
    source: makeSourceLocation(ctx, file),
  };
}

function walkPackageDecl(ctx: PackageDeclContext, file: string): PackageDecl {
  const qnameCtx = ctx.qualifiedName();
  const idCtx = qnameCtx.id();
  const parts = idCtx.idPart().map((pt) => pt.getText());
  return {
    kind: 'packageDecl',
    name: parts.join('.'),
    parts,
    source: makeSourceLocation(ctx, file),
  };
}

function walkImportDecl(ctx: ImportDeclContext, file: string): ImportDecl {
  const qnameCtx = ctx.qualifiedName();
  const idCtx = qnameCtx.id();
  const parts = idCtx.idPart().map((pt) => pt.getText());
  return {
    kind: 'importDecl',
    target: parts.join('.'),
    targetParts: parts,
    wildcard: ctx.STAR() !== null,
    source: makeSourceLocation(ctx, file),
  };
}

function walkGraphBlock(ctx: GraphBlockContext, file: string): GraphBlock {
  const nameCtx = ctx.id();
  const name = nameCtx ? nameCtx.getText() : '';

  let schema: 'db' | 'er' | 'map' | 'query' | 'cnc' | undefined;
  let description: string | undefined;
  let tags: string[] | undefined;
  let objects: string[] = [];
  let layout: GraphLayout | undefined;

  for (const gp of ctx.graphProperty()) {
    if (gp.graphSchemaProperty()) {
      const sc = gp.graphSchemaProperty()!.schemaCode();
      if (sc.DB()) schema = 'db';
      else if (sc.ER()) schema = 'er';
      else if (sc.MAP()) schema = 'map';
      else if (sc.QUERY()) schema = 'query';
      else if (sc.CNC()) schema = 'cnc';
    }
    if (gp.descriptionProperty()) {
      const parsed = walkStringLiteralForm(gp.descriptionProperty()!.stringLiteralForm()!, file);
      description = parsed.value;
    }
    if (gp.tagsProperty()) {
      tags = walkListOfStrings(gp.tagsProperty()!.listOfStrings()!, file);
    }
    if (gp.graphObjectsProperty()) {
      objects = gp.graphObjectsProperty()!.id().map((idCtx) => {
        const parts = idCtx.idPart().map((pt) => pt.getText());
        return parts.join('.');
      });
    }
    if (gp.graphLayoutProperty()) {
      layout = walkGraphLayout(gp.graphLayoutProperty()!.object_(), file);
    }
  }

  return { kind: 'graphBlock', name, schema, description, tags, objects, layout, source: makeSourceLocation(ctx, file) };
}

function walkGraphLayout(ctx: Object_Context, file: string): GraphLayout {
  let viewport: GraphLayout['viewport'] | undefined;
  const nodes: Record<string, { x: number; y: number }> = {};
  const edges: Record<string, { bendPoints?: [number, number][] }> = {};

  for (const entry of ctx.propertyList()?.propertyEntry() ?? []) {
    const key = entry.key().getText();
    const valueCtx = entry.value();
    if (!valueCtx) continue;

    if (key === 'viewport' && valueCtx.object_()) {
      viewport = walkViewport(valueCtx.object_()!, file);
    } else if (key === 'nodes' && valueCtx.object_()) {
      for (const nodeEntry of valueCtx.object_()!.propertyList()?.propertyEntry() ?? []) {
        const nodeKey = nodeEntry.key().getText();
        const nodeVal = nodeEntry.value()?.object_();
        if (nodeVal) {
          const xEntry = nodeVal.propertyList()?.propertyEntry().find((e) => e.key().getText() === 'x');
          const yEntry = nodeVal.propertyList()?.propertyEntry().find((e) => e.key().getText() === 'y');
          const x = xEntry?.value()?.literal()?.NUMBER_LITERAL() ? Number(xEntry.value()!.literal()!.NUMBER_LITERAL()!.getText()) : 0;
          const y = yEntry?.value()?.literal()?.NUMBER_LITERAL() ? Number(yEntry.value()!.literal()!.NUMBER_LITERAL()!.getText()) : 0;
          nodes[nodeKey] = { x, y };
        }
      }
    } else if (key === 'edges' && valueCtx.object_()) {
      for (const edgeEntry of valueCtx.object_()!.propertyList()?.propertyEntry() ?? []) {
        const edgeKey = edgeEntry.key().getText();
        const edgeVal = edgeEntry.value()?.object_();
        if (!edgeVal) continue;

        const bpEntry = edgeVal.propertyList()?.propertyEntry()
          .find((e) => e.key().getText() === 'bendPoints');
        const bpList  = bpEntry?.value()?.list();
        if (!bpList) {
          edges[edgeKey] = {};
          continue;
        }

        const bendPoints: [number, number][] = [];
        for (const item of bpList.value()) {
          const inner = item.list();
          if (!inner) continue;
          const pair = inner.value();
          if (pair.length !== 2) continue;
          const a = pair[0].literal()?.NUMBER_LITERAL();
          const b = pair[1].literal()?.NUMBER_LITERAL();
          if (a && b) {
            bendPoints.push([Number(a.getText()), Number(b.getText())]);
          }
        }
        edges[edgeKey] = { bendPoints: bendPoints.length > 0 ? bendPoints : undefined };
      }
    }
  }

  return { viewport, nodes, edges };
}

function walkViewport(ctx: Object_Context, _file: string): GraphLayout['viewport'] {
  let zoom = 1.0;
  let panX = 0;
  let panY = 0;
  let displayMode = 'just-names';

  const list = ctx.propertyList();
  if (!list) return { zoom, panX, panY, displayMode };
  for (const entry of list.propertyEntry()) {
    const key = entry.key().getText();
    const val = entry.value();
    if (!val) continue;
    if (key === 'zoom' && val.literal()?.NUMBER_LITERAL()) {
      zoom = Number(val.literal()!.NUMBER_LITERAL()!.getText());
    } else if (key === 'panX' && val.literal()?.NUMBER_LITERAL()) {
      panX = Number(val.literal()!.NUMBER_LITERAL()!.getText());
    } else if (key === 'panY' && val.literal()?.NUMBER_LITERAL()) {
      panY = Number(val.literal()!.NUMBER_LITERAL()!.getText());
    } else if (key === 'displayMode') {
      const idCtx = val.id();
      if (idCtx) displayMode = idCtx.getText();
    }
  }

  return { zoom, panX, panY, displayMode };
}

function walkDefinition(ctx: DefinitionContext, file: string, errors: ParseError[]): Definition {
  const objDef = ctx.objectDefinition();
  const nameCtx = objDef.id();
  const name = nameCtx ? nameCtx.getText() : '';
  const source = makeSourceLocation(ctx, file);

  if (objDef.MODEL()) return walkModelDef(objDef.modelDef()!, name, source, file);
  if (objDef.TABLE()) return walkTableDef(objDef.tableDef()!, name, source, file);
  if (objDef.VIEW()) return walkViewDef(objDef.viewDef()!, name, source, file, errors);
  if (objDef.COLUMN()) return walkColumnDef(objDef.columnDef()!, name, source, file);
  if (objDef.INDEX()) return walkIndexDef(objDef.indexDef()!, name, source, file);
  if (objDef.CONSTRAINT()) return walkConstraintDef(objDef.constraintDef()!, name, source, file);
  if (objDef.FK()) return walkFkDef(objDef.fkDef()!, name, source, file);
  if (objDef.PROCEDURE()) return walkProcedureDef(objDef.procedureDef()!, name, source, file);
  if (objDef.ENTITY()) return walkEntityDef(objDef.entityDef()!, name, source, file);
  if (objDef.ATTRIBUTE()) return walkAttributeDef(objDef.attributeDef()!, name, source, file);
  if (objDef.RELATION()) return walkRelationDef(objDef.relationDef()!, name, source, file);
  if (objDef.ER2DB_ENTITY()) return walkEr2dbEntityDef(objDef.er2dbEntityDef()!, name, source, file);
  if (objDef.ER2DB_ATTRIBUTE()) return walkEr2dbAttributeDef(objDef.er2dbAttributeDef()!, name, source, file);
  if (objDef.ER2DB_RELATION()) return walkEr2dbRelationDef(objDef.er2dbRelationDef()!, name, source, file);
  if (objDef.QUERY()) return walkQueryDef(objDef.queryDef()!, name, source, file, errors);
  if (objDef.ROLE()) return walkRoleDef(objDef.roleDef()!, name, source, file);
  if (objDef.ER2CNC_ROLE()) return walkEr2cncRoleDef(objDef.er2cncRoleDef()!, name, source, file);
  if (objDef.DRILL_MAP()) return walkDrillMapDef(objDef.drillMapDef()!, name, source, file);

  return { kind: 'model', name, source } satisfies ModelDef;
}

// ============================================================================
// Walker for value forms
// ============================================================================

function walkValue(ctx: ValueContext, file: string): PropertyValue {
  if (ctx.literal()) return walkLiteral(ctx.literal()!, file);
  if (ctx.id()) return walkId(ctx.id()!, file);
  if (ctx.list()) return walkList(ctx.list()!, file);
  if (ctx.object_()) return walkObject(ctx.object_()!, file);
  if (ctx.functionCall()) return walkFunctionCall(ctx.functionCall()!, file);
  return { kind: 'null', source: makeSourceLocation(ctx, file) } satisfies NullValue;
}

function walkLiteral(ctx: LiteralContext, file: string): PropertyValue {
  if (ctx.NUMBER_LITERAL()) {
    return {
      kind: 'number',
      value: Number(ctx.NUMBER_LITERAL()!.getText()),
      source: makeSourceLocation(ctx, file),
    } satisfies NumberValue;
  }
  if (ctx.BOOLEAN_LITERAL()) {
    return {
      kind: 'bool',
      value: ctx.BOOLEAN_LITERAL()!.getText() === 'true',
      source: makeSourceLocation(ctx, file),
    } satisfies BoolValue;
  }
  if (ctx.NULL_LITERAL()) {
    return { kind: 'null', source: makeSourceLocation(ctx, file) } satisfies NullValue;
  }
  if (ctx.stringLiteralForm()) {
    return walkStringLiteralForm(ctx.stringLiteralForm()!, file);
  }
  return { kind: 'null', source: makeSourceLocation(ctx, file) } satisfies NullValue;
}

function walkStringLiteralForm(ctx: StringLiteralFormContext, file: string): StringValue | TripleStringValue {
  if (ctx.STRING_LITERAL()) {
    const raw = ctx.STRING_LITERAL()!.getText();
    const value = raw.slice(1, -1).replace(/\\(.)/g, '$1');
    return { kind: 'string', value, source: makeSourceLocation(ctx, file) };
  }
  // Both a plain `"""…"""` and a `"""tag␊…"""` that lexed as TAGGED_BLOCK_LITERAL
  // (because its first line is a bare word, e.g. `"""Ne␊1 = Ano"""`) are read here
  // as a plain triple-string — the tag word is just text. Tag-peeling happens only
  // in `walkEmbeddedBlock` (sourceText / definitionSql).
  const triple = ctx.TRIPLE_STRING_LITERAL() ?? ctx.TAGGED_BLOCK_LITERAL();
  if (triple) {
    const dedented = dedent(triple.getText().slice(3, -3));
    return { kind: 'tripleString', value: dedented, source: makeSourceLocation(ctx, file) };
  }
  return { kind: 'string', value: '', source: makeSourceLocation(ctx, file) };
}

/**
 * `sourceText` / `definitionSql` values (embedded-sql DESIGN §2.2, §4). The
 * grammar routes these through `embeddedBlock`:
 *   - STRING / TRIPLE_STRING → unchanged plain-string behaviour (incl. C5's
 *     trailing newline — triple-strings keep it).
 *   - TAGGED_BLOCK → tag-peel → dedent → strip exactly one trailing newline,
 *     resolve the tag via TAG_REGISTRY into a `TaggedBlockValue`. An unknown tag
 *     emits a diagnostic and falls back to raw text (DESIGN §5).
 */
function walkEmbeddedBlock(
  ctx: EmbeddedBlockContext,
  file: string,
  errors: ParseError[],
): StringValue | TripleStringValue | TaggedBlockValue {
  if (ctx.STRING_LITERAL()) {
    const raw = ctx.STRING_LITERAL()!.getText();
    const value = raw.slice(1, -1).replace(/\\(.)/g, '$1');
    return { kind: 'string', value, source: makeSourceLocation(ctx, file) };
  }
  if (ctx.TRIPLE_STRING_LITERAL()) {
    const raw = ctx.TRIPLE_STRING_LITERAL()!.getText();
    return { kind: 'tripleString', value: dedent(raw.slice(3, -3)), source: makeSourceLocation(ctx, file) };
  }

  const node = ctx.TAGGED_BLOCK_LITERAL()!;
  const loc = makeSourceLocation(ctx, file);
  const inner = node.getText().slice(3, -3);
  // The lexer guarantees `<tag>[ \t]*\r?\n<body>`, so this always matches.
  const opener = /^([A-Za-z][A-Za-z0-9-]*)([ \t]*\r?\n)/.exec(inner)!;
  const tag = opener[1];
  const body = inner.slice(opener[0].length);
  const { value: dedented, indentWidth } = dedentWithIndent(body);
  const value = dedented.replace(/\r?\n$/, ''); // strip exactly one close-fence newline (§4 step 6)

  // The tag token sits immediately after the opening `"""`.
  const tagSource: SourceLocation = {
    file,
    line: loc.line,
    column: loc.column + 3,
    endLine: loc.line,
    endColumn: loc.column + 3 + tag.length,
    offsetStart: loc.offsetStart + 3,
    offsetEnd: loc.offsetStart + 3 + tag.length,
  };

  const entry = resolveTag(tag);
  if (!entry) {
    errors.push({
      code: DiagnosticCode.UnknownLanguageTag,
      message: `Unknown embedded-language tag '${tag}'`,
      severity: 'warning',
      source: tagSource,
    });
    // Stored as raw text (DESIGN §5) — keep the extracted value, drop analysis.
    return { kind: 'tripleString', value, source: loc };
  }

  // Body region in file coordinates; the per-line column shift is `indentWidth`
  // (DESIGN §8 uniform source map). Refined further in Phase 2.
  const valueSource: SourceLocation = {
    file,
    line: loc.line + 1,
    column: indentWidth,
    endLine: loc.endLine,
    endColumn: Math.max(indentWidth, loc.endColumn - 3),
    offsetStart: loc.offsetStart + 3 + opener[0].length,
    offsetEnd: loc.offsetEnd - 3,
  };

  return {
    kind: 'taggedBlock',
    tag,
    language: entry.language,
    dialect: entry.dialect,
    value,
    tagSource,
    valueSource,
    indentWidth,
    source: loc,
  };
}

/**
 * textwrap.dedent (contracts §2.9), additionally returning the common-prefix
 * length removed (= `indentWidth` for the embedded-SQL source map). Mirrors the
 * Kotlin `Dedent.applyTextwrapDedent` so the two parsers agree byte-for-byte.
 */
function dedentWithIndent(text: string): { value: string; indentWidth: number } {
  const withoutLeadingNewline = text.startsWith('\n') ? text.slice(1) : text;
  const lines = withoutLeadingNewline.split('\n');
  let commonPrefix: string | null = null;
  for (const line of lines) {
    if (line.trim().length === 0) continue;
    const leading = line.match(/^[ \t]*/)![0];
    commonPrefix = commonPrefix === null ? leading : longestCommonPrefix(commonPrefix, leading);
    if (commonPrefix.length === 0) break;
  }
  const prefix = commonPrefix ?? '';
  if (prefix.length === 0) return { value: withoutLeadingNewline, indentWidth: 0 };
  const value = lines
    .map((line) => {
      if (line.trim().length === 0) return line.replace(/\s+$/, '');
      return line.startsWith(prefix) ? line.slice(prefix.length) : line;
    })
    .join('\n');
  return { value, indentWidth: prefix.length };
}

function dedent(text: string): string {
  return dedentWithIndent(text).value;
}

function longestCommonPrefix(a: string, b: string): string {
  let i = 0;
  const limit = Math.min(a.length, b.length);
  while (i < limit && a[i] === b[i]) i++;
  return a.slice(0, i);
}

function walkId(ctx: IdContext, file: string): IdValue {
  const parts: string[] = [];
  for (const part of ctx.idPart()) {
    parts.push(part.getText());
  }
  const path = parts.join('.');
  return { kind: 'id', path, parts, source: makeSourceLocation(ctx, file) };
}

function walkList(ctx: ListContext, file: string): ListValue {
  const items: PropertyValue[] = [];
  for (const val of ctx.value()) {
    items.push(walkValue(val, file));
  }
  return { kind: 'list', items, source: makeSourceLocation(ctx, file) };
}

function walkObject(ctx: Object_Context, file: string): ObjectValue {
  const entries: ObjectEntry[] = [];
  const listCtx = ctx.propertyList();
  if (listCtx) {
    for (const entry of listCtx.propertyEntry()) {
      const keyCtx = entry.key();
      const key = keyCtx ? keyCtx.getText() : '';
      const entryValue = entry.value() ? walkValue(entry.value()!, file) : ({ kind: 'null', source: makeSourceLocation(entry, file) } satisfies PropertyValue);
      entries.push({ key, value: entryValue, source: makeSourceLocation(entry, file) });
    }
  }
  return { kind: 'object', entries, source: makeSourceLocation(ctx, file) };
}

function walkFunctionCall(ctx: FunctionCallContext, file: string): FunctionCallValue {
  const nameCtx = ctx.id();
  const name = nameCtx ? nameCtx.getText() : '';
  const args: PropertyValue[] = [];
  for (const val of ctx.value()) {
    args.push(walkValue(val, file));
  }
  return { kind: 'functionCall', name, args, source: makeSourceLocation(ctx, file) };
}

function walkListOfStrings(ctx: ListOfStringsContext, file: string): string[] {
  const result: string[] = [];
  for (const s of ctx.stringLiteralForm()) {
    const val = walkStringLiteralForm(s, file);
    if (val.kind === 'string') result.push(val.value);
    else if (val.kind === 'tripleString') result.push(val.value);
  }
  return result;
}

function walkListOfIds(ctx: ListOfIdsContext, _file: string): string[] {
  const result: string[] = [];
  for (const id of ctx.id()) {
    result.push(id.getText());
  }
  return result;
}

/**
 * `primaryKey` accepts a quoted-string list (legacy), a bare-id list, or a
 * single bare id (`primaryKey: IDSTRED`). All three collapse to the column-name
 * `string[]` — the AST doesn't distinguish the surface form (the formatter
 * re-emits the bare form when each name is a valid identifier).
 */
function walkPrimaryKeyValue(ctx: PrimaryKeyValueContext, file: string): string[] {
  if (ctx.listOfStrings()) return walkListOfStrings(ctx.listOfStrings()!, file);
  if (ctx.listOfIds()) return walkListOfIds(ctx.listOfIds()!, file);
  if (ctx.id()) return [ctx.id()!.getText()];
  return [];
}

// ============================================================================
// Per-kind walker functions: db kinds
// ============================================================================

function walkModelDef(
  ctx: ModelDefContext,
  name: string,
  source: SourceLocation,
  file: string
): ModelDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let version: string | undefined;

  for (const p of ctx.modelProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.versionProperty()) {
      version = p.versionProperty()!.STRING_LITERAL()!.getText().slice(1, -1);
    }
  }

  return { kind: 'model', name, source, description, tags, version };
}

function walkTableDef(
  ctx: TableDefContext,
  name: string,
  source: SourceLocation,
  file: string
): TableDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let primaryKey: string[] | undefined;
  let columns: ColumnDef[] | undefined;
  let indices: IndexDef[] | undefined;
  let constraints: ConstraintDef[] | undefined;
  let search: SearchBlock | undefined;

  for (const p of ctx.tableProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.primaryKeyProperty()) {
      primaryKey = walkPrimaryKeyValue(p.primaryKeyProperty()!.primaryKeyValue()!, file);
    }
    if (p.columnsProperty()) {
      columns = walkColumnDefList(p.columnsProperty()!.columnDefList()!, file);
    }
    if (p.indicesProperty()) {
      indices = walkIndexDefList(p.indicesProperty()!.indexDefList()!, file);
    }
    if (p.constraintsProperty()) {
      constraints = walkConstraintDefList(p.constraintsProperty()!.constraintDefList()!, file);
    }
    if (p.searchBlockProperty()) {
      search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file);
    }
  }

  return { kind: 'table', name, source, description, tags, primaryKey, columns, indices, constraints, search };
}

function walkViewDef(
  ctx: ViewDefContext,
  name: string,
  source: SourceLocation,
  file: string,
  errors: ParseError[]
): ViewDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let columns: ColumnDef[] | undefined;
  let definitionSql: StringValue | TripleStringValue | TaggedBlockValue | undefined;
  let search: SearchBlock | undefined;

  for (const p of ctx.viewProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.columnsProperty()) {
      columns = walkColumnDefList(p.columnsProperty()!.columnDefList()!, file);
    }
    if (p.definitionSqlProperty()) {
      definitionSql = walkEmbeddedBlock(p.definitionSqlProperty()!.embeddedBlock()!, file, errors);
    }
    if (p.searchBlockProperty()) {
      search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file);
    }
  }

  return { kind: 'view', name, source, description, tags, columns, definitionSql, search };
}

function walkColumnDef(
  ctx: ColumnDefContext,
  name: string,
  source: SourceLocation,
  file: string
): ColumnDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let type: DataType | undefined;
  let optional: boolean | undefined;
  let isKey: boolean | undefined;
  let indexed: boolean | undefined;
  let search: SearchBlock | undefined;

  for (const p of ctx.columnProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.typeProperty()) {
      type = walkDataType(p.typeProperty()!.dataType()!, file);
    }
    if (p.optionalProperty()) {
      optional = p.optionalProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
    }
    if (p.isKeyProperty()) {
      isKey = p.isKeyProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
    }
    if (p.indexedProperty()) {
      indexed = p.indexedProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
    }
    if (p.searchBlockProperty()) {
      search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file);
    }
  }

  return { kind: 'column', name, source, description, tags, type, optional, isKey, indexed, search };
}

function walkIndexDef(
  ctx: IndexDefContext,
  name: string,
  source: SourceLocation,
  file: string
): IndexDef {
  let description: StringValue | TripleStringValue | undefined;
  let indexType: IndexType | undefined;
  let columns: string[] | undefined;

  for (const p of ctx.indexProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.indexTypeProperty()) {
      const v = p.indexTypeProperty()!.indexTypeValue();
      if (v.PRIMARY()) indexType = 'primary';
      else if (v.SECONDARY()) indexType = 'secondary';
      else if (v.ORDERED()) indexType = 'ordered';
      else if (v.BTREE()) indexType = 'btree';
      else if (v.FULLTEXT()) indexType = 'fulltext';
    }
    if (p.columnNamesListProperty()) {
      columns = walkListOfStrings(p.columnNamesListProperty()!.listOfStrings()!, file);
    }
  }

  return { kind: 'index', name, source, description, indexType, columns };
}

function walkConstraintDef(
  ctx: ConstraintDefContext,
  name: string,
  source: SourceLocation,
  file: string
): ConstraintDef {
  let description: StringValue | TripleStringValue | undefined;
  let constraintType: ConstraintType | undefined;
  let columns: string[] | undefined;

  for (const p of ctx.constraintProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.constraintTypeProperty()) {
      const v = p.constraintTypeProperty()!.constraintTypeValue();
      if (v.UNIQUE()) constraintType = 'unique';
      else if (v.NOT_NULL()) constraintType = 'notNull';
    }
    if (p.columnNamesListProperty()) {
      columns = walkListOfStrings(p.columnNamesListProperty()!.listOfStrings()!, file);
    }
  }

  return { kind: 'constraint', name, source, description, constraintType, columns };
}

function walkFkDef(
  ctx: FkDefContext,
  name: string,
  source: SourceLocation,
  file: string
): FkDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let from: PropertyValue | undefined;
  let to: PropertyValue | undefined;

  for (const p of ctx.fkProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.fromProperty()?.value()) {
      from = walkValue(p.fromProperty()!.value()!, file);
    }
    if (p.toProperty()?.value()) {
      to = walkValue(p.toProperty()!.value()!, file);
    }
  }

  return { kind: 'fk', name, source, description, tags, from, to };
}

function walkProcedureDef(
  ctx: ProcedureDefContext,
  name: string,
  source: SourceLocation,
  file: string
): ProcedureDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let parameters: ParameterDef[] | undefined;
  let resultColumns: ColumnDef[] | undefined;

  for (const p of ctx.procedureProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.parametersProperty()) {
      parameters = walkParameterDefList(p.parametersProperty()!.parameterDefList()!, file);
    }
    if (p.resultColumnsProperty()) {
      resultColumns = walkColumnDefList(p.resultColumnsProperty()!.columnDefList()!, file);
    }
  }

  return { kind: 'procedure', name, source, description, tags, parameters, resultColumns };
}

// ============================================================================
// Per-kind walker functions: er kinds
// ============================================================================

function walkEntityDef(
  ctx: EntityDefContext,
  name: string,
  source: SourceLocation,
  file: string
): EntityDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let labelPlural: string | undefined;
  let nameAttribute: Reference | undefined;
  let codeAttribute: Reference | undefined;
  let aliases: string[] | undefined;
  let attributes: AttributeDef[] | undefined;
  let roles: string[] | undefined;
  let displayLabel: LocalizedString | undefined;
  let search: SearchBlock | undefined;
  let mapping: MappingProperty | undefined;

  for (const p of ctx.entityProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.labelPluralProperty()) {
      labelPlural = p.labelPluralProperty()!.STRING_LITERAL()!.getText().slice(1, -1);
    }
    if (p.nameAttributeProperty()?.id()) {
      const idCtx = p.nameAttributeProperty()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      nameAttribute = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
    if (p.codeAttributeProperty()?.id()) {
      const idCtx = p.codeAttributeProperty()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      codeAttribute = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
    if (p.aliasesProperty()) {
      aliases = walkListOfStrings(p.aliasesProperty()!.listOfStrings()!, file);
    }
    if (p.attributesProperty()) {
      attributes = walkAttributeDefList(p.attributesProperty()!.attributeDefList()!, file);
    }
    if (p.rolesProperty()) {
      roles = walkListOfIds(p.rolesProperty()!.listOfIds()!, file);
    }
    if (p.displayLabelProperty()) {
      displayLabel = walkLocalizedString(p.displayLabelProperty()!.localizedString()!, file);
    }
    if (p.searchBlockProperty()) {
      search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file);
    }
    if (p.mappingProperty()) {
      mapping = walkMappingProperty(p.mappingProperty()!, file);
    }
  }

  return { kind: 'entity', name, source, description, tags, labelPlural, nameAttribute, codeAttribute, aliases, attributes, roles, displayLabel, search, mapping };
}

function walkAttributeDef(
  ctx: AttributeDefContext,
  name: string,
  source: SourceLocation,
  file: string
): AttributeDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let type: DataType | undefined;
  let isKey: boolean | undefined;
  let optional: boolean | undefined;
  let valueLabels: ValueLabels | undefined;
  let displayLabel: LocalizedString | undefined;
  let search: SearchBlock | undefined;
  let mapping: MappingProperty | undefined;

  for (const p of ctx.attributeProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.typeProperty()) {
      type = walkDataType(p.typeProperty()!.dataType()!, file);
    }
    if (p.isKeyProperty()) {
      isKey = p.isKeyProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
    }
    if (p.optionalProperty()) {
      optional = p.optionalProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
    }
    if (p.valueLabelsProperty()) {
      valueLabels = walkValueLabels(p.valueLabelsProperty()!.valueLabelsBody()!, file);
    }
    if (p.displayLabelProperty()) {
      displayLabel = walkLocalizedString(p.displayLabelProperty()!.localizedString()!, file);
    }
    if (p.searchBlockProperty()) {
      search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file);
    }
    if (p.mappingProperty()) {
      mapping = walkMappingProperty(p.mappingProperty()!, file);
    }
  }

  return { kind: 'attribute', name, source, description, tags, type, isKey, optional, valueLabels, displayLabel, search, mapping };
}

function walkRelationDef(
  ctx: RelationDefContext,
  name: string,
  source: SourceLocation,
  file: string
): RelationDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let from: PropertyValue | undefined;
  let to: PropertyValue | undefined;
  let cardinality: ObjectValue | undefined;
  let join: ListValue | undefined;
  let search: SearchBlock | undefined;
  let mapping: MappingProperty | undefined;

  for (const p of ctx.relationProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.fromProperty()?.value()) {
      from = walkValue(p.fromProperty()!.value()!, file);
    }
    if (p.toProperty()?.value()) {
      to = walkValue(p.toProperty()!.value()!, file);
    }
    if (p.cardinalityProperty()?.object_()) {
      cardinality = walkObject(p.cardinalityProperty()!.object_()!, file);
    }
    if (p.joinProperty()?.list()) {
      join = walkList(p.joinProperty()!.list()!, file);
    }
    if (p.searchBlockProperty()) {
      search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file);
    }
    if (p.mappingProperty()) {
      mapping = walkMappingProperty(p.mappingProperty()!, file);
    }
  }

  return { kind: 'relation', name, source, description, tags, from, to, cardinality, join, search, mapping };
}

// ============================================================================
// Per-kind walker functions: map kinds
// ============================================================================

function walkEr2dbEntityDef(
  ctx: Er2dbEntityDefContext,
  name: string,
  source: SourceLocation,
  file: string
): Er2dbEntityDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let entity: Reference | undefined;
  let target: ObjectValue | Reference | undefined;
  let whereFilter: ObjectValue | undefined;

  for (const p of ctx.er2dbEntityProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.entityProperty_()?.id()) {
      const idCtx = p.entityProperty_()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      entity = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
    if (p.targetProperty()) {
      target = walkTargetValue(p.targetProperty()!, file);
    }
    if (p.whereFilterProperty()?.object_()) {
      whereFilter = walkObject(p.whereFilterProperty()!.object_()!, file);
    }
  }

  return { kind: 'er2dbEntity', name, source, description, tags, entity, target, whereFilter };
}

function walkEr2dbAttributeDef(
  ctx: Er2dbAttributeDefContext,
  name: string,
  source: SourceLocation,
  file: string
): Er2dbAttributeDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let attribute: Reference | undefined;
  let target: ObjectValue | Reference | undefined;

  for (const p of ctx.er2dbAttributeProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.attributeProperty_()?.id()) {
      const idCtx = p.attributeProperty_()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      attribute = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
    if (p.targetProperty()) {
      target = walkTargetValue(p.targetProperty()!, file);
    }
  }

  return { kind: 'er2dbAttribute', name, source, description, tags, attribute, target };
}

function walkEr2dbRelationDef(
  ctx: Er2dbRelationDefContext,
  name: string,
  source: SourceLocation,
  file: string
): Er2dbRelationDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let relation: Reference | undefined;
  let fk: Reference | undefined;

  for (const p of ctx.er2dbRelationProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.relationProperty_()?.id()) {
      const idCtx = p.relationProperty_()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      relation = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
    if (p.fkProperty_()?.id()) {
      const idCtx = p.fkProperty_()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      fk = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
  }

  return { kind: 'er2dbRelation', name, source, description, tags, relation, fk };
}

// ============================================================================
// Per-kind walker functions: query and cnc kinds
// ============================================================================

function walkQueryDef(
  ctx: QueryDefContext,
  name: string,
  source: SourceLocation,
  file: string,
  errors: ParseError[]
): QueryDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let language: QueryLanguage | undefined;
  let parameters: ParameterDef[] | undefined;
  let sourceText: StringValue | TripleStringValue | TaggedBlockValue | undefined;
  let search: SearchBlock | undefined;

  for (const p of ctx.queryProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.languageProperty()) {
      const v = p.languageProperty()!.languageValue();
      if (v.SQL()) language = 'SQL';
      else if (v.TRANSFORMATION_DSL()) language = 'TRANSFORMATION_DSL';
      else if (v.DATAFRAME_DSL()) language = 'DATAFRAME_DSL';
      else if (v.REL_NODE()) language = 'REL_NODE';
    }
    if (p.parametersProperty()) {
      parameters = walkParameterDefList(p.parametersProperty()!.parameterDefList()!, file);
    }
    if (p.sourceTextProperty()) {
      sourceText = walkEmbeddedBlock(p.sourceTextProperty()!.embeddedBlock()!, file, errors);
    }
    if (p.searchBlockProperty()) {
      search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file);
    }
  }

  // `language:` is inferred from the tag and soft-deprecated when a tagged block
  // is present (DESIGN §6); a value disagreeing with the tag is an error.
  if (sourceText?.kind === 'taggedBlock' && language !== undefined) {
    if (language !== sourceText.language) {
      errors.push({
        code: DiagnosticCode.LanguageTagMismatch,
        message: `'language: ${language}' disagrees with the block tag '${sourceText.tag}' (${sourceText.language})`,
        severity: 'error',
        source: sourceText.tagSource,
      });
    }
    errors.push({
      code: DiagnosticCode.DeprecatedLanguageProperty,
      message: `'language' on query is deprecated; it is inferred from the '${sourceText.tag}' block tag`,
      severity: 'warning',
      source: sourceText.tagSource,
    });
  }

  return { kind: 'query', name, source, description, tags, language, parameters, sourceText, search };
}

function walkRoleDef(
  ctx: RoleDefContext,
  name: string,
  source: SourceLocation,
  file: string
): RoleDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let label: LocalizedString | undefined;
  let search: SearchBlock | undefined;

  for (const p of ctx.roleProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.labelProperty()) {
      label = walkLocalizedString(p.labelProperty()!.localizedString()!, file);
    }
    if (p.searchBlockProperty()) {
      search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file);
    }
  }

  return { kind: 'role', name, source, description, tags, label, search };
}

function walkEr2cncRoleDef(
  ctx: Er2cncRoleDefContext,
  name: string,
  source: SourceLocation,
  file: string
): Er2cncRoleDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let entity: Reference | undefined;
  let role: Reference | undefined;

  for (const p of ctx.er2cncRoleProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.entityProperty_()?.id()) {
      const idCtx = p.entityProperty_()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      entity = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
    if (p.roleProperty_()?.id()) {
      const idCtx = p.roleProperty_()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      role = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
  }

  return { kind: 'er2cncRole', name, source, description, tags, entity, role };
}

function walkDrillMapDef(
  ctx: DrillMapDefContext,
  name: string,
  source: SourceLocation,
  file: string
): DrillMapDef {
  let description: StringValue | TripleStringValue | undefined;
  let tags: string[] | undefined;
  let from: Reference | undefined;
  let to: Reference | undefined;
  let args: DrillArgEntry[] = [];
  let display: LocalizedString | undefined;
  let overrideAuto: boolean | undefined;

  for (const p of ctx.drillMapProperty()) {
    if (p.descriptionProperty()) {
      description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
    }
    if (p.tagsProperty()) {
      tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
    }
    if (p.fromProperty()?.value()?.id()) {
      const idCtx = p.fromProperty()!.value()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      from = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
    if (p.toProperty()?.value()?.id()) {
      const idCtx = p.toProperty()!.value()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      to = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
    if (p.argsProperty()) {
      args = walkDrillArgsMap(p.argsProperty()!.drillArgsMap()!, file);
    }
    if (p.displayProperty()) {
      display = walkLocalizedString(p.displayProperty()!.localizedString()!, file);
    }
    if (p.overrideProperty()?.BOOLEAN_LITERAL()) {
      overrideAuto = p.overrideProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
    }
  }

  return {
    kind: 'drillMap',
    name,
    source,
    description,
    tags,
    from,
    to,
    args,
    display,
    overrideAuto,
  };
}

function walkDrillArgsMap(ctx: DrillArgsMapContext, file: string): DrillArgEntry[] {
  const result: DrillArgEntry[] = [];
  for (const entry of ctx.drillArgEntry()) {
    const idCtx = entry.id();
    if (!idCtx) continue;
    const argName = idCtx.idPart().map((pt) => pt.getText()).join('.');
    const value = walkStringLiteralForm(entry.stringLiteralForm()!, file);
    result.push({
      name: argName,
      value,
      source: makeSourceLocation(entry, file),
    });
  }
  return result;
}

// ============================================================================
// Inline def lists
// ============================================================================

function walkColumnDefList(ctx: ColumnDefListContext, file: string): ColumnDef[] {
  const result: ColumnDef[] = [];
  for (const inline of ctx.columnInline()) {
    const nameCtx = inline.id();
    const name = nameCtx ? nameCtx.getText() : '';
    const inlineCtx = inline.columnDef();
    let description: StringValue | TripleStringValue | undefined;
    let tags: string[] | undefined;
    let type: DataType | undefined;
    let optional: boolean | undefined;
    let isKey: boolean | undefined;
    let indexed: boolean | undefined;
    let search: SearchBlock | undefined;

    for (const p of inlineCtx.columnProperty()) {
      if (p.descriptionProperty()) {
        description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
      }
      if (p.tagsProperty()) {
        tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
      }
      if (p.typeProperty()) {
        type = walkDataType(p.typeProperty()!.dataType()!, file);
      }
      if (p.optionalProperty()) {
        optional = p.optionalProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
      }
      if (p.isKeyProperty()) {
        isKey = p.isKeyProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
      }
      if (p.indexedProperty()) {
        indexed = p.indexedProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
      }
      if (p.searchBlockProperty()) {
        search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file);
      }
    }

    result.push({ kind: 'column', name, source: makeSourceLocation(inline, file), description, tags, type, optional, isKey, indexed, search });
  }
  return result;
}

function walkIndexDefList(ctx: IndexDefListContext, file: string): IndexDef[] {
  const result: IndexDef[] = [];
  for (const inline of ctx.indexInline()) {
    const nameCtx = inline.id();
    const name = nameCtx ? nameCtx.getText() : '';
    const inlineCtx = inline.indexDef();
    let description: StringValue | TripleStringValue | undefined;
    let indexType: IndexType | undefined;
    let columns: string[] | undefined;

    for (const p of inlineCtx.indexProperty()) {
      if (p.descriptionProperty()) {
        description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
      }
      if (p.indexTypeProperty()) {
        const v = p.indexTypeProperty()!.indexTypeValue();
        if (v.PRIMARY()) indexType = 'primary';
        else if (v.SECONDARY()) indexType = 'secondary';
        else if (v.ORDERED()) indexType = 'ordered';
        else if (v.BTREE()) indexType = 'btree';
        else if (v.FULLTEXT()) indexType = 'fulltext';
      }
      if (p.columnNamesListProperty()) {
        columns = walkListOfStrings(p.columnNamesListProperty()!.listOfStrings()!, file);
      }
    }

    result.push({ kind: 'index', name, source: makeSourceLocation(inline, file), description, indexType, columns });
  }
  return result;
}

function walkConstraintDefList(ctx: ConstraintDefListContext, file: string): ConstraintDef[] {
  const result: ConstraintDef[] = [];
  for (const inline of ctx.constraintInline()) {
    const nameCtx = inline.id();
    const name = nameCtx ? nameCtx.getText() : '';
    const inlineCtx = inline.constraintDef();
    let description: StringValue | TripleStringValue | undefined;
    let constraintType: ConstraintType | undefined;
    let columns: string[] | undefined;

    for (const p of inlineCtx.constraintProperty()) {
      if (p.descriptionProperty()) {
        description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
      }
      if (p.constraintTypeProperty()) {
        const v = p.constraintTypeProperty()!.constraintTypeValue();
        if (v.UNIQUE()) constraintType = 'unique';
        else if (v.NOT_NULL()) constraintType = 'notNull';
      }
      if (p.columnNamesListProperty()) {
        columns = walkListOfStrings(p.columnNamesListProperty()!.listOfStrings()!, file);
      }
    }

    result.push({ kind: 'constraint', name, source: makeSourceLocation(inline, file), description, constraintType, columns });
  }
  return result;
}

function walkAttributeDefList(ctx: AttributeDefListContext, file: string): AttributeDef[] {
  const result: AttributeDef[] = [];
  for (const inline of ctx.attributeInline()) {
    const nameCtx = inline.id();
    const name = nameCtx ? nameCtx.getText() : '';
    const inlineCtx = inline.attributeDef();
    let description: StringValue | TripleStringValue | undefined;
    let tags: string[] | undefined;
    let type: DataType | undefined;
    let isKey: boolean | undefined;
    let optional: boolean | undefined;
    let valueLabels: ValueLabels | undefined;
    let displayLabel: LocalizedString | undefined;
    let search: SearchBlock | undefined;
    let mapping: MappingProperty | undefined;

    for (const p of inlineCtx.attributeProperty()) {
      if (p.descriptionProperty()) {
        description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
      }
      if (p.tagsProperty()) {
        tags = walkListOfStrings(p.tagsProperty()!.listOfStrings()!, file);
      }
      if (p.typeProperty()) {
        type = walkDataType(p.typeProperty()!.dataType()!, file);
      }
      if (p.isKeyProperty()) {
        isKey = p.isKeyProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
      }
      if (p.optionalProperty()) {
        optional = p.optionalProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
      }
      if (p.valueLabelsProperty()) {
        valueLabels = walkValueLabels(p.valueLabelsProperty()!.valueLabelsBody()!, file);
      }
      if (p.displayLabelProperty()) {
        displayLabel = walkLocalizedString(p.displayLabelProperty()!.localizedString()!, file);
      }
      if (p.searchBlockProperty()) {
        search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file);
      }
      if (p.mappingProperty()) {
        mapping = walkMappingProperty(p.mappingProperty()!, file);
      }
    }

    result.push({ kind: 'attribute', name, source: makeSourceLocation(inline, file), description, tags, type, isKey, optional, valueLabels, displayLabel, search, mapping });
  }
  return result;
}

function walkParameterDefList(ctx: ParameterDefListContext, file: string): ParameterDef[] {
  const result: ParameterDef[] = [];
  for (const inline of ctx.parameterInline()) {
    let name = '';
    let type: DataType | undefined;
    let label: string | undefined;
    let direction: ParameterDirection | undefined;

    for (const p of inline.paramProperty()) {
      if (p.nameProperty()?.id()) {
        name = p.nameProperty()!.id()!.getText();
      }
      if (p.typeProperty()) {
        type = walkDataType(p.typeProperty()!.dataType()!, file);
      }
      if (p.paramLabelProperty()) {
        label = p.paramLabelProperty()!.stringLiteralForm()!.getText().slice(1, -1);
      }
      if (p.directionProperty()?.id()) {
        const d = p.directionProperty()!.id()!.getText().toUpperCase();
        if (d === 'IN' || d === 'OUT' || d === 'INOUT') {
          direction = d;
        }
      }
    }

    result.push({ name, type, label, direction, source: makeSourceLocation(inline, file) });
  }
  return result;
}

// ============================================================================
// dataType and reference detection
// ============================================================================

function walkDataType(ctx: DataTypeContext, file: string): DataType {
  if (ctx.typeValue()) {
    return { kind: 'simple', name: ctx.typeValue()!.getText(), source: makeSourceLocation(ctx, file) };
  }
  let typeName: string | undefined;
  let length: number | undefined;
  let precision: number | undefined;

  for (const p of ctx.dataTypeProperty()) {
    if (p.DATA_TYPE()) {
      typeName = p.typeValue()?.getText();
    }
    if (p.LENGTH()) {
      const lit = p.NUMBER_LITERAL();
      if (lit) length = Number(lit.getText());
    }
    if (p.PRECISION()) {
      const lit = p.NUMBER_LITERAL();
      if (lit) precision = Number(lit.getText());
    }
  }

  if (!typeName) typeName = '';
  return { kind: 'structured', typeName, length, precision, source: makeSourceLocation(ctx, file) };
}

export function extractReference(value: PropertyValue): Reference | null {
  if (value.kind === 'id') {
    return { path: value.path, parts: value.parts, source: value.source };
  }
  return null;
}

// ============================================================================
// LocalizedString and related
// ============================================================================

function walkLocalizedString(ctx: LocalizedStringContext, file: string): LocalizedString {
  const entries: Record<string, string> = {};
  for (const entry of ctx.localizedEntry()) {
    const key = entry.id().getText();
    const val = walkStringLiteralForm(entry.stringLiteralForm()!, file);
    entries[key] = val.value;
  }
  return { kind: 'localizedString', entries, source: makeSourceLocation(ctx, file) };
}

function walkLocalizedStringList(ctx: LocalizedStringListContext, file: string): LocalizedStringList {
  const entries: Record<string, string[]> = {};
  for (const entry of ctx.localizedStringListEntry()) {
    const key = entry.id().getText();
    const val = walkListOfStrings(entry.listOfStrings()!, file);
    entries[key] = val;
  }
  return { kind: 'localizedStringList', entries, source: makeSourceLocation(ctx, file) };
}

function walkSearchBlock(ctx: SearchBlockContext, file: string): SearchBlock {
  let keywords: LocalizedStringList | undefined;
  let patterns: string[] | undefined;
  let descriptions: LocalizedStringList | undefined;
  let examples: string[] | undefined;
  let aliases: string[] | undefined;
  let searchable: boolean | undefined;
  let fuzzy: boolean | undefined;
  const seen = new Map<string, number>();
  const bump = (k: string) => seen.set(k, (seen.get(k) ?? 0) + 1);

  for (const p of ctx.searchSubProperty()) {
    if (p.keywordsProperty()) {
      bump('keywords');
      keywords = walkLocalizedStringList(p.keywordsProperty()!.localizedStringList()!, file);
    }
    if (p.patternsProperty()) {
      bump('patterns');
      patterns = walkListOfStrings(p.patternsProperty()!.listOfStrings()!, file);
    }
    if (p.descriptionsProperty()) {
      bump('descriptions');
      descriptions = walkLocalizedStringList(p.descriptionsProperty()!.localizedStringList()!, file);
    }
    if (p.examplesProperty()) {
      bump('examples');
      examples = walkListOfStrings(p.examplesProperty()!.listOfStrings()!, file);
    }
    if (p.aliasesProperty()) {
      bump('aliases');
      aliases = walkListOfStrings(p.aliasesProperty()!.listOfStrings()!, file);
    }
    if (p.searchableProperty()) {
      bump('searchable');
      searchable = p.searchableProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
    }
    if (p.fuzzyProperty()) {
      bump('fuzzy');
      fuzzy = p.fuzzyProperty()!.BOOLEAN_LITERAL()!.getText() === 'true';
    }
  }

  const duplicateProperties = [...seen.entries()].filter(([, n]) => n > 1).map(([k]) => k);
  return { kind: 'searchBlock', keywords, patterns, descriptions, examples, aliases, searchable, fuzzy, duplicateProperties, source: makeSourceLocation(ctx, file) };
}

function walkValueLabels(ctx: ValueLabelsBodyContext, file: string): ValueLabels {
  const entries: Array<{ key: string; label: LocalizedString; source: SourceLocation }> = [];
  for (const entry of ctx.valueLabelEntry()) {
    const keyVal = entry.stringLiteralForm();
    const key = keyVal ? walkStringLiteralForm(keyVal, file).value : '';
    const label = walkLocalizedString(entry.localizedString()!, file);
    entries.push({ key, label, source: makeSourceLocation(entry, file) });
  }
  return { kind: 'valueLabels', entries, source: makeSourceLocation(ctx, file) };
}

// ============================================================================
// v2.1: inline mapping helpers
// ============================================================================

function walkMappingProperty(ctx: MappingPropertyContext, file: string): MappingProperty {
  const valueCtx = ctx.mappingValue();

  if (valueCtx.id()) {
    const idCtx = valueCtx.id()!;
    const parts = idCtx.idPart().map((pt) => pt.getText());
    const ref: Reference = {
      path: parts.join('.'),
      parts,
      source: makeSourceLocation(idCtx, file),
    };
    return {
      kind: 'bareId',
      id: ref,
      source: makeSourceLocation(valueCtx, file),
    };
  }

  const blockCtx = valueCtx.mappingBlock()!;
  let target: ObjectValue | Reference | undefined;
  let columns: MappingColumnEntry[] | undefined;
  let fk: Reference | undefined;

  for (const p of blockCtx.mappingBlockProperty()) {
    if (p.targetProperty()) {
      target = walkTargetValue(p.targetProperty()!, file);
    }
    if (p.mappingColumnsProperty()) {
      columns = walkMappingColumnMap(p.mappingColumnsProperty()!.mappingColumnMap()!, file);
    }
    if (p.fkProperty_()?.id()) {
      const idCtx = p.fkProperty_()!.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      fk = { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
    }
  }

  return {
    kind: 'block',
    target,
    columns,
    fk,
    source: makeSourceLocation(blockCtx, file),
  };
}

function walkTargetValue(ctx: TargetPropertyContext, file: string): ObjectValue | Reference {
  if (ctx.id()) {
    const idCtx = ctx.id()!;
    const parts = idCtx.idPart().map((pt) => pt.getText());
    return { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) };
  }
  return walkObject(ctx.object_()!, file);
}

function walkMappingColumnMap(ctx: MappingColumnMapContext, file: string): MappingColumnEntry[] {
  const entries: MappingColumnEntry[] = [];
  for (const e of ctx.mappingColumnEntry()) {
    const name = e.id().idPart().map((pt) => pt.getText()).join('.');
    const v = e.mappingColumnValue();
    let value: MappingColumnValue;
    if (v.id()) {
      const idCtx = v.id()!;
      const parts = idCtx.idPart().map((pt) => pt.getText());
      value = {
        kind: 'bareId',
        id: { path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) },
        source: makeSourceLocation(v, file),
      };
    } else if (v.mappingTargetValue()) {
      const mtv = v.mappingTargetValue()!;
      if (mtv.id()) {
        const idCtx = mtv.id()!;
        const parts = idCtx.idPart().map((pt) => pt.getText());
        value = {
          kind: 'object',
          object: { kind: 'object', entries: [{ key: 'target', value: { kind: 'id', path: parts.join('.'), parts, source: makeSourceLocation(idCtx, file) }, source: makeSourceLocation(mtv, file) }], source: makeSourceLocation(mtv, file) },
          source: makeSourceLocation(v, file),
        };
      } else {
        const inner = walkObject(mtv.object_()!, file);
        value = {
          kind: 'object',
          object: { kind: 'object', entries: [{ key: 'target', value: inner, source: makeSourceLocation(mtv, file) }], source: makeSourceLocation(v, file) },
          source: makeSourceLocation(v, file),
        };
      }
    } else {
      value = {
        kind: 'object',
        object: walkObject(v.object_()!, file),
        source: makeSourceLocation(v, file),
      };
    }
    entries.push({ name, value, source: makeSourceLocation(e, file) });
  }
  return entries;
}

// ============================================================================
// Source location helper
// ============================================================================

function makeSourceLocation(
  ctx: { start?: { line: number; column: number; start: number } | null; stop?: { line: number; column: number; start: number; stop: number } | null },
  file: string
): SourceLocation {
  const startToken = ctx.start ?? { line: 1, column: 0, start: 0 };
  const stopToken = ctx.stop ?? { line: startToken.line, column: startToken.column, start: startToken.start, stop: startToken.start - 1 };
  const stopTokenLength = stopToken.stop - stopToken.start + 1;
  return {
    file,
    line: startToken.line,
    column: startToken.column,
    endLine: stopToken.line,
    endColumn: stopToken.column + stopTokenLength,
    offsetStart: startToken.start,
    offsetEnd: stopToken.stop + 1,
  };
}