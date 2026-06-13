// =============================================================================
// TTR (Tatrman) grammar
//
// @grammar-version: 2.2
//
// Version scheme: X.Y — X is a breaking/major change, Y is additive
// (syntactic sugar, new optional constructs, bug fixes). Bump the marker
// above when changing the grammar; the prebuild script extracts it into
// @modeler/grammar's exported TTR_GRAMMAR_VERSION. See CHANGELOG.md for
// history.
//
// Changes vs v1 (promoted to 2.0 with v1.1 work):
//   1. New top-level constructs: `package <qualifiedName>`, `import <qualifiedName>[]`,
//      `graph <id> { ... }`.
//   2. New lexer tokens: PACKAGE, IMPORT, GRAPH, OBJECTS, LAYOUT, STAR.
//   3. New parser rules: packageDecl, importDecl, graphBlock, graphProperty,
//      graphSchemaProperty, graphObjectsProperty, graphLayoutProperty, qualifiedName.
//   4. Updated document rule to accept the new constructs.
//   5. Extended idPart to include the new keywords so they remain usable as
//      cross-reference components.
//
// Changes in 2.2 (additive):
//   - New top-level construct: `def drill_map <id> { from, to, args, display?, override? }`.
//     Models "click this row → run that pattern". Consumers (ai-platform's new-Golem)
//     surface drill maps to the FE; modeler treats them like any other def for
//     parse/diagnose purposes.
//   - New lexer tokens: DRILL_MAP, ARGS, DISPLAY, OVERRIDE.
//   - New parser rules: drillMapDef, drillMapProperty, argsProperty, displayProperty,
//     overrideProperty, drillArgEntry.
//   - Extended idPart to include the new keywords.
// =============================================================================

grammar TTR;

// ----- Top level -----

document
  : packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF
  ;

packageDecl
  : PACKAGE qualifiedName
  ;

importDecl
  : IMPORT qualifiedName (DOT STAR)?
  ;

graphBlock
  : GRAPH id LBRACE (graphProperty (COMMA? graphProperty)* COMMA?)? RBRACE
  ;

graphProperty
  : graphSchemaProperty
  | descriptionProperty
  | tagsProperty
  | graphObjectsProperty
  | graphLayoutProperty
  ;

graphSchemaProperty   : SCHEMA propSep? schemaCode ;
graphObjectsProperty  : OBJECTS propSep? LBRACK ( id (COMMA id)* )? COMMA? RBRACK ;
graphLayoutProperty   : LAYOUT propSep? object_ ;

qualifiedName
  : id
  ;

schemaDirective
  : SCHEMA schemaCode ( NAMESPACE id )?
  ;

schemaCode
  : DB | ER | MAP | QUERY | CNC
  ;

definition
  : DEF objectDefinition
  ;

objectDefinition
  : MODEL          id  modelDef
  | TABLE          id  tableDef
  | VIEW           id  viewDef
  | COLUMN         id  columnDef
  | INDEX          id  indexDef
  | CONSTRAINT     id  constraintDef
  | FK             id  fkDef
  | PROCEDURE      id  procedureDef
  | ENTITY         id  entityDef
  | ATTRIBUTE      id  attributeDef
  | RELATION       id  relationDef
  | ER2DB_ENTITY   id  er2dbEntityDef
  | ER2DB_ATTRIBUTE id er2dbAttributeDef
  | ER2DB_RELATION id  er2dbRelationDef
  | QUERY          id  queryDef
  | ROLE           id  roleDef            // Phase 2.2 — cnc.role.*
  | ER2CNC_ROLE    id  er2cncRoleDef      // Phase 2.2 — er2cnc_role.*
  | DRILL_MAP      id  drillMapDef        // v2.2 — drill mapping between two patterns
  ;

// ----- Object bodies -----
// Each *Def is a brace-enclosed list of properties. Comma-optional.

modelDef         : LBRACE (modelProperty         (COMMA? modelProperty)*         COMMA?)? RBRACE ;
tableDef         : LBRACE (tableProperty         (COMMA? tableProperty)*         COMMA?)? RBRACE ;
viewDef          : LBRACE (viewProperty          (COMMA? viewProperty)*          COMMA?)? RBRACE ;
columnDef        : LBRACE (columnProperty        (COMMA? columnProperty)*        COMMA?)? RBRACE ;
indexDef         : LBRACE (indexProperty         (COMMA? indexProperty)*         COMMA?)? RBRACE ;
constraintDef    : LBRACE (constraintProperty    (COMMA? constraintProperty)*    COMMA?)? RBRACE ;
fkDef            : LBRACE (fkProperty            (COMMA? fkProperty)*            COMMA?)? RBRACE ;
procedureDef     : LBRACE (procedureProperty     (COMMA? procedureProperty)*     COMMA?)? RBRACE ;
entityDef        : LBRACE (entityProperty        (COMMA? entityProperty)*        COMMA?)? RBRACE ;
attributeDef     : LBRACE (attributeProperty     (COMMA? attributeProperty)*     COMMA?)? RBRACE ;
relationDef      : LBRACE (relationProperty      (COMMA? relationProperty)*      COMMA?)? RBRACE ;
er2dbEntityDef   : LBRACE (er2dbEntityProperty   (COMMA? er2dbEntityProperty)*   COMMA?)? RBRACE ;
er2dbAttributeDef: LBRACE (er2dbAttributeProperty(COMMA? er2dbAttributeProperty)* COMMA?)? RBRACE ;
er2dbRelationDef : LBRACE (er2dbRelationProperty (COMMA? er2dbRelationProperty)* COMMA?)? RBRACE ;
queryDef         : LBRACE (queryProperty         (COMMA? queryProperty)*         COMMA?)? RBRACE ;
roleDef          : LBRACE (roleProperty          (COMMA? roleProperty)*          COMMA?)? RBRACE ;
er2cncRoleDef    : LBRACE (er2cncRoleProperty    (COMMA? er2cncRoleProperty)*    COMMA?)? RBRACE ;
drillMapDef      : LBRACE (drillMapProperty      (COMMA? drillMapProperty)*      COMMA?)? RBRACE ;

// ----- Per-kind valid properties -----

modelProperty            : descriptionProperty | tagsProperty | versionProperty ;

tableProperty            : descriptionProperty | tagsProperty | primaryKeyProperty | columnsProperty | indicesProperty | constraintsProperty | searchBlockProperty ;

viewProperty             : descriptionProperty | tagsProperty | columnsProperty | definitionSqlProperty | searchBlockProperty ;

columnProperty           : descriptionProperty | tagsProperty | typeProperty | optionalProperty | isKeyProperty | indexedProperty | searchBlockProperty ;

indexProperty            : descriptionProperty | indexTypeProperty | columnNamesListProperty ;

constraintProperty       : descriptionProperty | constraintTypeProperty | columnNamesListProperty ;

fkProperty               : descriptionProperty | tagsProperty | fromProperty | toProperty ;

procedureProperty        : descriptionProperty | tagsProperty | parametersProperty | resultColumnsProperty ;

entityProperty           : descriptionProperty | tagsProperty | labelPluralProperty | nameAttributeProperty | codeAttributeProperty | aliasesProperty | attributesProperty | rolesProperty | displayLabelProperty | searchBlockProperty | mappingProperty ;

attributeProperty        : descriptionProperty | tagsProperty | typeProperty | isKeyProperty | optionalProperty | valueLabelsProperty | displayLabelProperty | searchBlockProperty | mappingProperty ;

relationProperty         : descriptionProperty | tagsProperty | fromProperty | toProperty | cardinalityProperty | joinProperty | searchBlockProperty | mappingProperty ;

er2dbEntityProperty      : descriptionProperty | tagsProperty | entityProperty_ | targetProperty | whereFilterProperty ;

er2dbAttributeProperty   : descriptionProperty | tagsProperty | attributeProperty_ | targetProperty ;

er2dbRelationProperty    : descriptionProperty | tagsProperty | relationProperty_ | fkProperty_ ;

queryProperty            : descriptionProperty | tagsProperty | languageProperty | parametersProperty | sourceTextProperty | searchBlockProperty ;

roleProperty             : descriptionProperty | tagsProperty | labelProperty | searchBlockProperty ;

er2cncRoleProperty       : descriptionProperty | tagsProperty | entityProperty_ | roleProperty_ ;

drillMapProperty         : descriptionProperty | tagsProperty | fromProperty | toProperty | argsProperty | displayProperty | overrideProperty ;

// A query / procedure parameter: { name: <id>, type: <dataType>, label: "...", direction: <id> }.
// `label` here is a plain display string (unlike `roleProperty`'s localised `labelProperty`).
// `direction` (IN | OUT | INOUT) only appears on procedure parameters.
paramProperty            : nameProperty | typeProperty | paramLabelProperty | directionProperty ;

// ----- Property productions -----

descriptionProperty       : DESCRIPTION       propSep? stringLiteralForm ;
tagsProperty              : TAGS              propSep? listOfStrings ;
versionProperty           : VERSION           propSep? STRING_LITERAL ;
primaryKeyProperty        : PRIMARY_KEY       propSep? listOfStrings ;
columnsProperty           : COLUMNS           propSep? columnDefList ;
indicesProperty           : INDICES           propSep? indexDefList ;
constraintsProperty       : CONSTRAINTS       propSep? constraintDefList ;
definitionSqlProperty     : DEFINITION_SQL    propSep? embeddedBlock ;
typeProperty              : DATA_TYPE         propSep? dataType ;
optionalProperty          : OPTIONAL          propSep? BOOLEAN_LITERAL ;
isKeyProperty             : IS_KEY            propSep? BOOLEAN_LITERAL ;
searchableProperty        : SEARCHABLE        propSep? BOOLEAN_LITERAL ;
indexedProperty           : INDEXED           propSep? BOOLEAN_LITERAL ;
indexTypeProperty         : DATA_TYPE         propSep? indexTypeValue ;
constraintTypeProperty    : DATA_TYPE         propSep? constraintTypeValue ;
columnNamesListProperty   : COLUMNS           propSep? listOfStrings ;
fromProperty              : FROM              propSep? value ;
toProperty                : TO                propSep? value ;
parametersProperty        : PARAMETERS        propSep? parameterDefList ;
resultColumnsProperty     : RESULT_COLUMNS    propSep? columnDefList ;
labelPluralProperty       : LABEL_PLURAL      propSep? STRING_LITERAL ;
nameAttributeProperty     : NAME_ATTRIBUTE    propSep? id ;
nameProperty              : NAME              propSep? id ;
codeAttributeProperty     : CODE_ATTRIBUTE    propSep? id ;
aliasesProperty           : ALIASES           propSep? listOfStrings ;
attributesProperty        : ATTRIBUTES        propSep? attributeDefList ;
cardinalityProperty       : CARDINALITY       propSep? object_ ;
joinProperty              : JOIN              propSep? list ;
entityProperty_           : ENTITY            propSep? id ;
attributeProperty_        : ATTRIBUTE         propSep? id ;
relationProperty_         : RELATION          propSep? id ;
fkProperty_               : FK                propSep? id ;
targetProperty            : TARGET            propSep? ( object_ | id ) ;
whereFilterProperty       : WHERE_FILTER      propSep? object_ ;
languageProperty          : LANGUAGE          propSep? languageValue ;
sourceTextProperty        : SOURCE_TEXT       propSep? embeddedBlock ;

// Phase 2.2 — localised string blocks: { cs: "...", en: "..." } shape, used for
// role labels, entity / attribute display_label, and value_labels values.
labelProperty             : LABEL             propSep? localizedString ;
paramLabelProperty        : LABEL             propSep? stringLiteralForm ;
directionProperty         : DIRECTION         propSep? id ;
displayLabelProperty      : DISPLAY_LABEL     propSep? localizedString ;
rolesProperty             : ROLES             propSep? listOfIds ;
valueLabelsProperty       : VALUE_LABELS      propSep? valueLabelsBody ;
roleProperty_             : ROLE              propSep? id ;

// Search feature — `search { keywords {...} patterns [...] descriptions {...} examples [...] aliases [...] searchable: true, fuzzy: true }`
searchBlockProperty       : SEARCH            propSep? searchBlock ;
keywordsProperty          : KEYWORDS          propSep? localizedStringList ;
patternsProperty          : PATTERNS          propSep? listOfStrings ;
descriptionsProperty      : DESCRIPTIONS      propSep? localizedStringList ;
examplesProperty          : EXAMPLES          propSep? listOfStrings ;
fuzzyProperty             : FUZZY             propSep? BOOLEAN_LITERAL ;

// v2.2 — drill map properties.
//
// `args` is a map of <target-parameter-name> → <source-column-name-or-literal>;
// keys are bare identifiers (parameter names on the `to` pattern) and values are
// string literals (column names from `from`'s result projection, or literals).
// `display` is a localised string for the user-facing drill chip label.
// `override` (default false) suppresses auto-derived drills with the same target.
argsProperty          : ARGS              propSep? drillArgsMap ;
displayProperty       : DISPLAY           propSep? localizedString ;
overrideProperty      : OVERRIDE          propSep? BOOLEAN_LITERAL ;

drillArgsMap          : LBRACE ( drillArgEntry ( COMMA? drillArgEntry )* COMMA? )? RBRACE ;
drillArgEntry         : id propSep? stringLiteralForm ;

// v2.1 — inline mapping (syntactic sugar for def er2db_*).
mappingProperty       : MAPPING propSep? mappingValue ;

mappingValue
    : id
    | mappingBlock
    ;

mappingBlock
    : LBRACE ( mappingBlockProperty ( COMMA? mappingBlockProperty )* COMMA? )? RBRACE
    ;

mappingBlockProperty
    : targetProperty
    | mappingColumnsProperty
    | fkProperty_
    ;

mappingColumnsProperty
    : COLUMNS propSep? mappingColumnMap
    ;

mappingColumnMap
    : LBRACE ( mappingColumnEntry ( COMMA? mappingColumnEntry )* COMMA? )? RBRACE
    ;

mappingColumnEntry
    : id propSep? mappingColumnValue
    ;

mappingColumnValue
    : id
    | LBRACE TARGET propSep? mappingTargetValue RBRACE
    | object_
    ;

mappingTargetValue
    : id
    | object_
    ;

// ----- Inline def lists -----

columnDefList     : LBRACK ( columnInline COMMA? )* RBRACK | columnInline ;
columnInline      : DEF COLUMN id columnDef ;

indexDefList      : LBRACK ( indexInline COMMA? )* RBRACK | indexInline ;
indexInline       : DEF INDEX id indexDef ;

constraintDefList : LBRACK ( constraintInline COMMA? )* RBRACK | constraintInline ;
constraintInline  : DEF CONSTRAINT id constraintDef ;

attributeDefList  : LBRACK ( attributeInline COMMA? )* RBRACK | attributeInline ;
attributeInline   : DEF ATTRIBUTE id attributeDef ;

parameterDefList  : LBRACK ( parameterInline COMMA? )* RBRACK | parameterInline ;
parameterInline   : LBRACE ( paramProperty COMMA? )* RBRACE ;

// ----- Type values -----

dataType
  : typeValue
  | LBRACE dataTypeProperty (COMMA? dataTypeProperty)* COMMA? RBRACE
  ;

dataTypeProperty
  : DATA_TYPE propSep? typeValue
  | LENGTH    propSep? NUMBER_LITERAL
  | PRECISION propSep? NUMBER_LITERAL
  ;

typeValue
  : TEXT | INT | FLOAT | BOOL | DATETIME
  | STRING | BOOLEAN | NUMBER | INTEGER | DOUBLE
  | OBJECT | LIST
  | CHAR | VARCHAR | DECIMAL | DATE | TIMESTAMP
  | id
  ;

indexTypeValue       : PRIMARY | SECONDARY | ORDERED | BTREE | FULLTEXT ;
constraintTypeValue  : UNIQUE | NOT_NULL ;
languageValue        : SQL | TRANSFORMATION_DSL | DATAFRAME_DSL | REL_NODE ;

// ----- Generic value forms -----

propSep : COLON | EQUALS ;

value
  : literal
  | id
  | list
  | object_
  | functionCall
  ;

literal
  : NUMBER_LITERAL
  | stringLiteralForm
  | BOOLEAN_LITERAL
  | NULL_LITERAL
  ;

stringLiteralForm
  : STRING_LITERAL
  | TRIPLE_STRING_LITERAL
  ;

// Embedded foreign-language source (embedded-sql DESIGN §2.2). Used ONLY by the
// sourceText / definitionSql properties. A TAGGED_BLOCK_LITERAL anywhere else is
// a parse error (it is not in any other production) — intended.
embeddedBlock
  : TAGGED_BLOCK_LITERAL
  | TRIPLE_STRING_LITERAL
  | STRING_LITERAL
  ;

list
  : LBRACK ( value (COMMA value)* )? COMMA? RBRACK
  ;

listOfStrings
  : LBRACK ( stringLiteralForm ( COMMA stringLiteralForm )* )? COMMA? RBRACK
  ;

// Phase 2.2 — bracketed list of bare ids (used by `roles: [fact, dimension]`).
listOfIds
  : LBRACK ( id ( COMMA id )* )? COMMA? RBRACK
  ;

// Phase 2.2 — `{ cs: "...", en: "...", de: "..." }` block. Keys are bare
// language tags (BCP-47); values are string literals. Empty block is valid.
localizedString
  : LBRACE ( localizedEntry ( COMMA? localizedEntry )* COMMA? )? RBRACE
  ;

localizedEntry
  : id propSep? stringLiteralForm
  ;

// Phase 2.2 — `value_labels { "1": { cs: "Aktivní", en: "Active" }, "2": { ... } }`
valueLabelsBody
  : LBRACE ( valueLabelEntry ( COMMA? valueLabelEntry )* COMMA? )? RBRACE
  ;

valueLabelEntry
  : stringLiteralForm propSep? localizedString
  ;

// Search feature — `{ cs: ["...", "..."], en: ["..."] }` block. Mirrors localizedString
// but each language's value is a list of strings instead of a single string. Empty
// block is valid (a parse-time warning is emitted by the loader's validator).
localizedStringList
  : LBRACE ( localizedStringListEntry ( COMMA? localizedStringListEntry )* COMMA? )? RBRACE
  ;

localizedStringListEntry
  : id propSep? listOfStrings
  ;

// Search feature — body of a `search { ... }` block.
searchBlock
  : LBRACE ( searchSubProperty ( COMMA? searchSubProperty )* COMMA? )? RBRACE
  ;

searchSubProperty
  : keywordsProperty
  | patternsProperty
  | descriptionsProperty
  | examplesProperty
  | aliasesProperty
  | searchableProperty
  | fuzzyProperty
  ;

object_
  : LBRACE propertyList? RBRACE
  ;

propertyList
  : propertyEntry (COMMA? propertyEntry)* COMMA?
  ;

propertyEntry
  : key propSep? value
  ;

key
  : id
  ;

functionCall
  : id LPAREN ( value (COMMA value)* )? RPAREN
  ;

// ----- Identifiers -----
//
// Cross-references (dotted ids) may contain schema-code or object-kind keywords
// as positional components — e.g. `er.entity.objednavka`, `cnc.role.fact`,
// `db.dbo.customers`. ANTLR otherwise tokenises those as their respective
// keyword tokens; we accept them as IDENT-equivalents here so dotted refs work.
//
// `idPart` is the union; `id` is one or more dotted parts.

id : idPart ( DOT idPart )* ;

idPart
  : IDENT
  | DB | ER | MAP | QUERY | CNC                          // schema codes
  | ROLE | ER2CNC_ROLE                                   // Phase 2.2 kinds
  | TABLE | VIEW | COLUMN | INDEX | CONSTRAINT
  | FK | PROCEDURE | ENTITY | ATTRIBUTE | RELATION
  | ER2DB_ENTITY | ER2DB_ATTRIBUTE | ER2DB_RELATION
  | MODEL
  | NAME | LABEL | DIRECTION                              // common as identifiers / object keys (e.g. `def column name`)
  | FROM | TO                                            // allowed as object property keys (e.g. cardinality, join pairs)
  | PACKAGE | IMPORT | GRAPH                              // v1.1 new top-level keywords
  | OBJECTS | LAYOUT                                      // v1.1 graph body keywords
  | MAPPING                                         // v2.1
  | DRILL_MAP | ARGS | DISPLAY | OVERRIDE          // v2.2
  ;

// =============================================================================
// Lexer
// =============================================================================

DEF        : 'def' ;
SCHEMA     : 'schema' ;
NAMESPACE  : 'namespace' ;

PACKAGE    : 'package' ;    // v1.1
IMPORT     : 'import' ;     // v1.1
GRAPH      : 'graph' ;      // v1.1
OBJECTS    : 'objects' ;    // v1.1 graph body
LAYOUT     : 'layout' ;     // v1.1 graph body
MAPPING    : 'mapping' ;    // v2.1

DB    : 'db' ;
ER    : 'er' ;
MAP   : 'map' ;
CNC   : 'cnc' ;                        // Phase 2.2 — conceptual schema

MODEL            : 'model' ;
TABLE            : 'table' ;
VIEW             : 'view' ;
COLUMN           : 'column' ;
INDEX            : 'index' ;
CONSTRAINT       : 'constraint' ;
FK               : 'fk' ;
PROCEDURE        : 'procedure' ;
ENTITY           : 'entity' ;
ATTRIBUTE        : 'attribute' ;
RELATION         : 'relation' ;
ER2DB_ENTITY     : 'er2db_entity' ;
ER2DB_ATTRIBUTE  : 'er2db_attribute' ;
ER2DB_RELATION   : 'er2db_relation' ;
QUERY            : 'query' ;
ROLE             : 'role' ;            // Phase 2.2
ER2CNC_ROLE      : 'er2cnc_role' ;     // Phase 2.2
DRILL_MAP        : 'drill_map' ;       // v2.2
ARGS             : 'args' ;            // v2.2 — drill map: target param → source col/literal
DISPLAY          : 'display' ;         // v2.2 — drill map: localised chip label
OVERRIDE         : 'override' ;        // v2.2 — drill map: suppress auto-derived drill with same target

DESCRIPTION       : 'description' ;
TAGS              : 'tags' ;
VERSION           : 'version' ;
PRIMARY_KEY       : 'primaryKey' ;
COLUMNS           : 'columns' ;
INDICES           : 'indices' ;
CONSTRAINTS       : 'constraints' ;
ATTRIBUTES        : 'attributes' ;
PARAMETERS        : 'parameters' ;
RESULT_COLUMNS    : 'resultColumns' ;
DEFINITION_SQL    : 'definitionSql' ;
DATA_TYPE         : 'type' ;
OPTIONAL          : 'optional' ;
IS_KEY            : 'isKey' ;
SEARCHABLE        : 'searchable' ;
INDEXED           : 'indexed' ;
LABEL_PLURAL      : 'labelPlural' ;
NAME_ATTRIBUTE    : 'nameAttribute' ;
CODE_ATTRIBUTE    : 'codeAttribute' ;
ALIASES           : 'aliases' ;
CARDINALITY       : 'cardinality' ;
JOIN              : 'join' ;
TARGET            : 'target' ;
WHERE_FILTER      : 'whereFilter' ;
LANGUAGE          : 'language' ;
SOURCE_TEXT       : 'sourceText' ;
LENGTH            : 'length' ;
PRECISION         : 'precision' ;

// Phase 2.2 — localised text + role properties.
LABEL             : 'label' ;
NAME              : 'name' ;
DIRECTION         : 'direction' ;
DISPLAY_LABEL     : 'displayLabel' ;
VALUE_LABELS      : 'valueLabels' ;
ROLES             : 'roles' ;

// Search feature — `search { keywords {...} patterns [...] descriptions {...} examples [...] aliases [...] searchable: true, fuzzy: true }`.
// `aliases` reuses the existing ALIASES token. `description` (single) and `descriptions` (list) are
// distinct lexemes — ANTLR longest-match handles disambiguation.
SEARCH            : 'search' ;
KEYWORDS          : 'keywords' ;
PATTERNS          : 'patterns' ;
DESCRIPTIONS      : 'descriptions' ;
EXAMPLES          : 'examples' ;
FUZZY             : 'fuzzy' ;

FROM : 'from' ;
TO   : 'to' ;

TEXT      : 'text' ;
INT       : 'int' ;
FLOAT     : 'float' ;
BOOL      : 'bool' ;
DATETIME  : 'datetime' ;
STRING    : 'string' ;
BOOLEAN   : 'boolean' ;
NUMBER    : 'number' ;
INTEGER   : 'integer' ;
DOUBLE    : 'double' ;
OBJECT    : 'object' ;
LIST      : 'list' ;
CHAR      : 'char' ;
VARCHAR   : 'varchar' ;
DECIMAL   : 'decimal' ;
DATE      : 'date' ;
TIMESTAMP : 'timestamp' ;

PRIMARY   : 'primary' ;
SECONDARY : 'secondary' ;
ORDERED   : 'ordered' ;
BTREE     : 'btree' ;
FULLTEXT  : 'fulltext' ;
UNIQUE    : 'unique' ;
NOT_NULL  : 'notNull' ;

SQL                : 'SQL' ;
TRANSFORMATION_DSL : 'TRANSFORMATION_DSL' ;
DATAFRAME_DSL      : 'DATAFRAME_DSL' ;
REL_NODE           : 'REL_NODE' ;

// Punctuation / operators
EQUALS : '=' ;       // property separator
COLON  : ':' ;
COMMA  : ',' ;
LBRACE : '{' ;
RBRACE : '}' ;
LBRACK : '[' ;
RBRACK : ']' ;
LPAREN : '(' ;
RPAREN : ')' ;
DOT    : '.' ;
STAR   : '*' ;    // v1.1 wildcard for `import x.y.*`

// Literals
NULL_LITERAL          : 'null' ;
BOOLEAN_LITERAL       : 'true' | 'false' ;
NUMBER_LITERAL        : '-'? [0-9]+ ( '.' [0-9]+ )? ( [eE] [+-]? [0-9]+ )? ;
// Tagged embedded-language block: """<tag>\n ... """ (embedded-sql DESIGN §2.1).
// MUST be declared before TRIPLE_STRING_LITERAL: both can match the same span,
// and ANTLR breaks equal-length ties by declaration order, so the tagged form
// wins when a tag + newline is present on the opener line.
TAGGED_BLOCK_LITERAL  : '"""' [a-zA-Z] [a-zA-Z0-9-]* [ \t]* '\r'? '\n' .*? '"""' ;
TRIPLE_STRING_LITERAL : '"""' .*? '"""' ;
STRING_LITERAL        : '"' ( ~["\\\r\n] | '\\' . )* '"' ;

// Identifiers (declared after keywords so keywords win)
// Includes Czech/Latin Extended letters (á, č, ď, é, ě, í, ľ, ň, ó, ř, š, ť, ú, ů, ý, ž, etc.)
IDENT : [a-zA-Z\u00C0-\u024F_][a-zA-Z0-9_\u00C0-\u024F]* ;

// Comments route to the hidden channel so the CST/trivia layer can read them
// (parse-equivalent: the parser still ignores hidden-channel tokens). WS stays
// skipped — the formatter re-derives whitespace (design §10.1).
LINE_COMMENT  : '//' ~[\r\n]* -> channel(HIDDEN) ;
BLOCK_COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;
WS            : [ \t\r\n]+   -> skip ;
