import { parseFile } from './walker.js';
import type { ParseResult } from './ast.js';

export { parseString, parseFile } from './walker.js';
export { DiagnosticCode } from './diagnostics.js';
export { TAG_REGISTRY, resolveTag } from './tag-registry.js';
export type { TagEntry } from './tag-registry.js';
export { sqlPosToFile, fileToSqlOffset } from './sql-position.js';
export type { SqlTokenPos } from './sql-position.js';
export { attachTrivia } from './cst/attach.js';
export type { Trivia, TriviaKind } from './cst/trivia.js';
export type {
  Document,
  Definition,
  SchemaDirective,
  SourceLocation,
  ParseError,
  ParseResult,
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
  PropertyValue,
  StringValue,
  TripleStringValue,
  TaggedBlockValue,
  SqlDialect,
  LanguageKind,
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
  SimpleDataType,
  StructuredDataType,
  IndexType,
  ConstraintType,
  QueryLanguage,
  ParameterDirection,
  SearchBlock,
  ValueLabels,
  ParameterDef,
  MappingProperty,
  MappingPropertyBareId,
  MappingPropertyBlock,
  MappingColumnEntry,
  MappingColumnValue,
  ImportDecl,
  PackageDecl,
  GraphBlock,
  GraphLayout,
} from './ast.js';

export async function parseDirectory(rootPath: string, recursive = true): Promise<ParseResult[]> {
  const fs = await import('fs/promises');

  const results: ParseResult[] = [];

  async function walk(dir: string): Promise<void> {
    let entries;
    try {
      entries = await fs.readdir(dir, { withFileTypes: true });
    } catch {
      return;
    }

    for (const entry of entries) {
      if (entry.isDirectory()) {
        if (entry.name === '.modeler') continue;
        if (entry.name === 'node_modules') continue;
        if (entry.name === '.git') continue;
        if (recursive) {
          await walk(dir + '/' + entry.name);
        }
      } else if (entry.isFile() && entry.name.endsWith('.ttr')) {
        const result = await parseFile(dir + '/' + entry.name);
        results.push(result);
      }
    }
  }

  await walk(rootPath);
  return results;
}