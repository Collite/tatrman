#!/usr/bin/env node
"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseGrammar = parseGrammar;
exports.tokenToScope = tokenToScope;
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
// The sibling .js is emitted by `pnpm run build-generator`. Do not edit the .js by hand.
function getScriptDir() {
    if (process.argv[1]) {
        return path_1.default.dirname(path_1.default.resolve(process.argv[1]));
    }
    return '<unknown-script-dir>';
}
const __dirname_script = getScriptDir();
const monorepoRoot = path_1.default.resolve(__dirname_script, '..', '..', '..');
const GRAMMAR_PATH = path_1.default.join(monorepoRoot, 'packages', 'grammar', 'src', 'TTR.g4');
const OUTPUT_PATH = path_1.default.join(__dirname_script, '..', 'syntaxes', 'ttr.tmLanguage.json');
function parseGrammar(g4Content) {
    const tokens = [];
    const lexerRuleRegex = /^([A-Z_][A-Z0-9_]*)\s*:\s*(.+?)\s*;/gm;
    let match;
    while ((match = lexerRuleRegex.exec(g4Content)) !== null) {
        const name = match[1];
        const rhs = match[2];
        const alternatives = rhs.split(/\s*\|\s*/);
        for (const alt of alternatives) {
            const trimmed = alt.trim();
            if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
                tokens.push({ name, literal: trimmed.slice(1, -1) });
            }
        }
    }
    return tokens;
}
function tokenToScope(name, literal) {
    switch (name) {
        // v1.1 top-level keywords
        case 'PACKAGE': return 'keyword.control.package.ttr';
        case 'IMPORT': return 'keyword.control.import.ttr';
        case 'GRAPH': return 'keyword.declaration.graph.ttr';
        // v1.1 graph body keywords
        case 'OBJECTS': return 'keyword.other.property.ttr';
        case 'LAYOUT': return 'keyword.other.property.ttr';
        // v2.3 .ttrd domain keywords
        case 'DOMAIN': return 'keyword.declaration.domain.ttr';
        case 'PACKAGES': return 'keyword.other.packages.ttr';
        case 'ENTITIES': return 'keyword.other.entities.ttr';
        case 'DEF': return 'keyword.control.def.ttr';
        case 'SCHEMA': return 'keyword.control.def.ttr';
        case 'NAMESPACE': return 'keyword.control.def.ttr';
        case 'DB': return 'keyword.other.schema.ttr';
        case 'ER': return 'keyword.other.schema.ttr';
        case 'BINDING': return 'keyword.other.schema.ttr';
        case 'CNC': return 'keyword.other.schema.ttr';
        case 'QUERY': return 'keyword.other.schema.ttr';
        case 'MODEL': return 'keyword.other.kind.ttr';
        case 'TABLE': return 'keyword.other.kind.ttr';
        case 'VIEW': return 'keyword.other.kind.ttr';
        case 'COLUMN': return 'keyword.other.kind.ttr';
        case 'INDEX': return 'keyword.other.kind.ttr';
        case 'CONSTRAINT': return 'keyword.other.kind.ttr';
        case 'FK': return 'keyword.other.kind.ttr';
        case 'PROCEDURE': return 'keyword.other.kind.ttr';
        case 'ENTITY': return 'keyword.other.kind.ttr';
        case 'ATTRIBUTE': return 'keyword.other.kind.ttr';
        case 'RELATION': return 'keyword.other.kind.ttr';
        case 'ER2DB_ENTITY': return 'keyword.other.kind.ttr';
        case 'ER2DB_ATTRIBUTE': return 'keyword.other.kind.ttr';
        case 'ER2DB_RELATION': return 'keyword.other.kind.ttr';
        case 'ROLE': return 'keyword.other.kind.ttr';
        case 'ER2CNC_ROLE': return 'keyword.other.kind.ttr';
        case 'DRILL_MAP': return 'keyword.other.kind.ttr';
        case 'ARGS': return 'keyword.other.property.ttr';
        case 'DISPLAY': return 'keyword.other.property.ttr';
        case 'OVERRIDE': return 'keyword.other.property.ttr';
        case 'DESCRIPTION': return 'keyword.other.property.ttr';
        case 'TAGS': return 'keyword.other.property.ttr';
        case 'VERSION': return 'keyword.other.property.ttr';
        case 'PRIMARY_KEY': return 'keyword.other.property.ttr';
        case 'COLUMNS': return 'keyword.other.property.ttr';
        case 'INDICES': return 'keyword.other.property.ttr';
        case 'CONSTRAINTS': return 'keyword.other.property.ttr';
        case 'ATTRIBUTES': return 'keyword.other.property.ttr';
        case 'PARAMETERS': return 'keyword.other.property.ttr';
        case 'RESULT_COLUMNS': return 'keyword.other.property.ttr';
        case 'DEFINITION_SQL': return 'keyword.other.property.ttr';
        case 'DATA_TYPE': return 'keyword.other.property.ttr';
        case 'OPTIONAL': return 'keyword.other.property.ttr';
        case 'IS_KEY': return 'keyword.other.property.ttr';
        case 'SEARCHABLE': return 'keyword.other.property.ttr';
        case 'INDEXED': return 'keyword.other.property.ttr';
        case 'LABEL_PLURAL': return 'keyword.other.property.ttr';
        case 'NAME_ATTRIBUTE': return 'keyword.other.property.ttr';
        case 'CODE_ATTRIBUTE': return 'keyword.other.property.ttr';
        case 'ALIASES': return 'keyword.other.property.ttr';
        case 'CARDINALITY': return 'keyword.other.property.ttr';
        case 'JOIN': return 'keyword.other.property.ttr';
        case 'TARGET': return 'keyword.other.property.ttr';
        case 'WHERE_FILTER': return 'keyword.other.property.ttr';
        case 'LANGUAGE': return 'keyword.other.property.ttr';
        case 'SOURCE_TEXT': return 'keyword.other.property.ttr';
        case 'LENGTH': return 'keyword.other.property.ttr';
        case 'PRECISION': return 'keyword.other.property.ttr';
        case 'LABEL': return 'keyword.other.property.ttr';
        case 'NAME': return 'keyword.other.property.ttr';
        case 'DIRECTION': return 'keyword.other.property.ttr';
        case 'DISPLAY_LABEL': return 'keyword.other.property.ttr';
        case 'VALUE_LABELS': return 'keyword.other.property.ttr';
        case 'ROLES': return 'keyword.other.property.ttr';
        case 'SEARCH': return 'keyword.other.property.ttr';
        case 'KEYWORDS': return 'keyword.other.property.ttr';
        case 'PATTERNS': return 'keyword.other.property.ttr';
        case 'DESCRIPTIONS': return 'keyword.other.property.ttr';
        case 'EXAMPLES': return 'keyword.other.property.ttr';
        case 'FUZZY': return 'keyword.other.property.ttr';
        case 'MAPPING': return 'keyword.other.property.ttr';
        case 'FROM': return 'keyword.other.property.ttr';
        case 'TO': return 'keyword.other.property.ttr';
        case 'TEXT': return 'support.type.primitive.ttr';
        case 'INT': return 'support.type.primitive.ttr';
        case 'FLOAT': return 'support.type.primitive.ttr';
        case 'BOOL': return 'support.type.primitive.ttr';
        case 'DATETIME': return 'support.type.primitive.ttr';
        case 'STRING': return 'support.type.primitive.ttr';
        case 'BOOLEAN': return 'support.type.primitive.ttr';
        case 'NUMBER': return 'support.type.primitive.ttr';
        case 'INTEGER': return 'support.type.primitive.ttr';
        case 'DOUBLE': return 'support.type.primitive.ttr';
        case 'OBJECT': return 'support.type.primitive.ttr';
        case 'LIST': return 'support.type.primitive.ttr';
        case 'CHAR': return 'support.type.primitive.ttr';
        case 'VARCHAR': return 'support.type.primitive.ttr';
        case 'DECIMAL': return 'support.type.primitive.ttr';
        case 'DATE': return 'support.type.primitive.ttr';
        case 'TIMESTAMP': return 'support.type.primitive.ttr';
        case 'PRIMARY': return 'constant.language.indextype.ttr';
        case 'SECONDARY': return 'constant.language.indextype.ttr';
        case 'ORDERED': return 'constant.language.indextype.ttr';
        case 'BTREE': return 'constant.language.indextype.ttr';
        case 'FULLTEXT': return 'constant.language.indextype.ttr';
        case 'UNIQUE': return 'constant.language.constrainttype.ttr';
        case 'NOT_NULL': return 'constant.language.constrainttype.ttr';
        case 'SQL': return 'constant.language.querylang.ttr';
        case 'TRANSFORMATION_DSL': return 'constant.language.querylang.ttr';
        case 'DATAFRAME_DSL': return 'constant.language.querylang.ttr';
        case 'REL_NODE': return 'constant.language.querylang.ttr';
        case 'BOOLEAN_LITERAL': return 'constant.language.ttr';
        case 'NUMBER_LITERAL': return null;
        case 'TRIPLE_STRING_LITERAL': return null;
        case 'STRING_LITERAL': return null;
        case 'IDENT': return null;
        case 'EQUALS': return 'punctuation.separator.ttr';
        case 'COLON': return 'punctuation.separator.ttr';
        case 'COMMA': return 'punctuation.separator.ttr';
        case 'LBRACE': return 'punctuation.section.braces.ttr';
        case 'RBRACE': return 'punctuation.section.braces.ttr';
        case 'LBRACK': return 'punctuation.section.brackets.ttr';
        case 'RBRACK': return 'punctuation.section.brackets.ttr';
        case 'LPAREN': return 'punctuation.section.parens.ttr';
        case 'RPAREN': return 'punctuation.section.parens.ttr';
        case 'DOT': return 'punctuation.separator.ttr';
        default: return null;
    }
}
function escapeRegex(s) {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
function buildGrammar(tokens) {
    const byScope = new Map();
    const rules = [];
    const scopeMap = {
        'keyword.control.def.ttr': [],
        'keyword.control.package.ttr': [],
        'keyword.control.import.ttr': [],
        'keyword.declaration.graph.ttr': [],
        'keyword.declaration.domain.ttr': [],
        'keyword.other.packages.ttr': [],
        'keyword.other.entities.ttr': [],
        'keyword.other.schema.ttr': [],
        'keyword.other.kind.ttr': [],
        'keyword.other.property.ttr': [],
        'support.type.primitive.ttr': [],
        'constant.language.ttr': [],
        'constant.language.indextype.ttr': [],
        'constant.language.constrainttype.ttr': [],
        'constant.language.querylang.ttr': [],
        'punctuation.separator.ttr': [],
        'punctuation.section.braces.ttr': [],
        'punctuation.section.brackets.ttr': [],
        'punctuation.section.parens.ttr': [],
    };
    for (const token of tokens) {
        const scope = tokenToScope(token.name, token.literal);
        if (!scope)
            continue;
        if (!scopeMap[scope])
            scopeMap[scope] = [];
        scopeMap[scope].push(escapeRegex(token.literal));
    }
    const keywordScopes = [
        'keyword.control.def.ttr',
        'keyword.control.package.ttr',
        'keyword.control.import.ttr',
        'keyword.declaration.graph.ttr',
        'keyword.declaration.domain.ttr',
        'keyword.other.packages.ttr',
        'keyword.other.entities.ttr',
        'keyword.other.schema.ttr',
        'keyword.other.kind.ttr',
        'keyword.other.property.ttr',
    ];
    const keywordsRepo = [];
    for (const scope of keywordScopes) {
        const rules = scopeMap[scope];
        if (rules.length > 0) {
            const combined = rules.join('|');
            keywordsRepo.push({ scope, match: '\\b(' + combined + ')\\b' });
        }
    }
    const grammar = {
        name: 'TTR',
        fileTypes: ['ttr'],
        scopeName: 'source.ttr',
        patterns: [
            { include: '#comments' },
            { include: '#strings' },
            { include: '#numbers' },
            { include: '#keywords' },
            { include: '#operators' },
        ],
        repository: {
            comments: {
                patterns: [
                    { name: 'comment.line.double-slash.ttr', match: '//.*$' },
                    { name: 'comment.block.ttr', begin: '/\\*', end: '\\*/' },
                ],
            },
            strings: {
                patterns: [
                    {
                        name: 'string.quoted.triple.ttr',
                        begin: '"""',
                        end: '"""',
                    },
                    {
                        name: 'string.quoted.double.ttr',
                        begin: '"',
                        end: '"(?=[^\\\\])',
                        patterns: [
                            { name: 'constant.character.escape.ttr', match: '\\\\.' },
                        ],
                    },
                ],
            },
            numbers: {
                patterns: [
                    {
                        name: 'constant.numeric.ttr',
                        match: '-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?',
                    },
                ],
            },
            keywords: {
                patterns: keywordScopes.map(scope => ({ include: '#' + scope.replace(/\./g, '_') })),
            },
            ...Object.fromEntries(keywordScopes.map(scope => {
                const key = scope.replace(/\./g, '_');
                const rules = scopeMap[scope] ?? [];
                return [key, { patterns: [{ name: scope, match: '\\b(' + rules.join('|') + ')\\b' }] }];
            })),
            operators: {
                patterns: [
                    { name: 'punctuation.separator.ttr', match: '=' },
                    { name: 'punctuation.separator.ttr', match: ':' },
                    { name: 'punctuation.separator.ttr', match: ',' },
                    { name: 'punctuation.separator.ttr', match: '\\.' },
                    { name: 'punctuation.section.braces.ttr', match: '\\{' },
                    { name: 'punctuation.section.braces.ttr', match: '\\}' },
                    { name: 'punctuation.section.brackets.ttr', match: '\\[' },
                    { name: 'punctuation.section.brackets.ttr', match: '\\]' },
                    { name: 'punctuation.section.parens.ttr', match: '\\(' },
                    { name: 'punctuation.section.parens.ttr', match: '\\)' },
                ],
            },
        },
    };
    return grammar;
}
function main() {
    const g4Content = fs_1.default.readFileSync(GRAMMAR_PATH, 'utf-8');
    const tokens = parseGrammar(g4Content);
    const grammar = buildGrammar(tokens);
    fs_1.default.writeFileSync(OUTPUT_PATH, JSON.stringify(grammar, null, 2));
    console.log(`Generated TextMate grammar (${tokens.length} tokens) to ${OUTPUT_PATH}`);
}
const invokedAsScript = !!process.argv[1] && path_1.default.resolve(process.argv[1]).endsWith('generate-tm-grammar.js');
if (invokedAsScript)
    main();
