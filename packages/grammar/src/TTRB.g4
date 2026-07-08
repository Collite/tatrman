// TTR-B — the natural-language fragment dialect (P7 Stage 7.1). Own grammar (C2-g α),
// beside TTR.g4 / TTRP.g4 / TTRSql.g4 / TTRPandas.g4, read directly by the ANTLR Gradle
// plugin in ttrp-frontend. Kotlin-only (G-b).
//
// "English-sentence-shaped TTR-P" (C4-a = α): one sentence per statement, each sentence
// decomposes to node(s) of the standard set (C2 regime inherited wholesale). The C4-b
// verb roster: Load, Keep/Take/Select, Remove/Delete, Rename, Convert/Retype,
// Create/Compute, Summarize, Join, Sort, Combine/Append, Store, Show/Display. Anaphora
// (C4-b-i): `that`/`this`/`it` and the implicit subject = the previous sentence's out.
// NOT NLP, never fuzzy (P2) — every synonym is a closed grammar alternative.
//
// S16: skins the ONE PL expression IR (org.tatrman.ttrp.expr.Expression). Verbose
// comparisons ("is more than", "is not empty", "is one of") fold to the SAME CatalogId
// ids as canonical TTR-P (KeywordTable / CatalogId), pinned by TtrbKeywordDriftSpec; the
// closed synonym set lives in ttr-b.synonyms.toml (C4-c = β). Canonical operators (`>`,
// `=`, …) remain valid and may mix with verbose forms in one predicate.
//
// English-only (S20). `#` line comments (S19); no `//` or `/* */` (they reach the reject
// path, TtrbRejectScanner). caseInsensitive: sentence-initial capitals are convention.
//
// GRAMMAR-PROTOTYPE LEFTOVERS DECIDED HERE (close 12-nl-options.md §Leftover):
//   · Ref-word roster (C4-b-i): v1 keeps `that | this | it` only. Byx's plural
//     `these/those` are DROPPED — a TTR-B pipeline value is a single table, so a plural
//     anaphor has no distinct referent (P2: one deterministic antecedent = the prev out).
//   · Binding form: v1 uses `… as <name>` ONLY. Byx's `call it <name>` standalone shape
//     is DROPPED for v1 (one closed binding spelling; `as` already carries Load/Join/
//     Compute/Show naming). Reconsider both in a v1.x NL sweep.

grammar TTRB;

options { caseInsensitive = true; }

// =============================================================================
// Parser — sentence+ (C4-a); one statement per sentence, `.` terminated
// =============================================================================

fragmentProgram : sentence+ EOF ;

sentence : statement DOT ;

statement
    : loadStmt          # loadSentence
    | limitStmt         # limitSentence      // before keep* — `Keep … first n rows` disambiguates on FIRST
    | keepColumnsStmt   # keepColumnsSentence
    | keepExceptStmt    # keepExceptSentence
    | filterStmt        # filterSentence
    | renameStmt        # renameSentence
    | convertStmt       # convertSentence
    | computeStmt       # computeSentence
    | summarizeStmt     # summarizeSentence
    | joinStmt          # joinSentence
    | sortStmt          # sortSentence
    | combineStmt       # combineSentence
    | storeStmt         # storeSentence
    | showStmt          # showSentence
    ;

// ---- statements (C4-b roster) --------------------------------------------------

// `Load [from] file "…" [with schema <ref>] [as <name>].` / `Load [from] <ref> [as <name>].`
loadStmt
    : LOAD FROM? fileSource (WITH SCHEMA schema=qname)? (AS name=IDENT)?    # loadFile
    | LOAD FROM? source=qname (AS name=IDENT)?                             # loadModel
    ;
fileSource : FILE str ;

// `Keep/Take/Select only the columns a, b [as c].`
keepColumnsStmt : keepVerb ONLY? THE? columnWord colRenameList ;

// `Keep all columns except a, b.`
keepExceptStmt  : keepVerb ALL? THE? columnWord? exceptWord colList ;

// `Keep/Filter [only] [the] rows where <expr>.` / `Remove/Delete [the] rows where <expr>.`
filterStmt
    : keepVerb ONLY? THE? rowWord? whereWord? boolExpr        # keepFilter
    | FILTER FOR? THE? rowWord? whereWord? boolExpr           # keepFilter
    | (REMOVE | DELETE) THE? rowWord? whereWord? boolExpr     # removeFilter
    ;

// `Rename a to b.` / `Rename the columns a as b, c as d.`
renameStmt : RENAME THE? columnWord? renamePair (COMMA renamePair)* ;
renamePair : from=IDENT (AS | TO) to=IDENT ;

// `Convert/Retype a to <type>.`
convertStmt : (CONVERT | RETYPE) THE? W_TYPE? OF? col=IDENT (AS | TO) typeName ;

// `Create/Compute [new column] <expr> as <name>.`
computeStmt : (CREATE | COMPUTE | CALCULATE) NEW? columnWord? expr AS name=IDENT ;

// `Summarize sum of amount as total [, …] by/grouped by region.`
summarizeStmt : SUMMARIZE aggItem (COMMA aggItem)* groupBy groupKey (COMMA groupKey)* ;
aggItem  : func=IDENT OF arg=IDENT (AS name=IDENT)? ;
groupBy  : BY | (GROUP | GROUPED) BY ;
groupKey : IDENT ;

// `Join that/it/<name> with <name> on <expr> [as <name>].`
joinStmt : JOIN joinLeft WITH right=qname ON boolExpr (AS name=IDENT)? ;
joinLeft : refWord | qname ;

// `Sort [the rows] by a [descending] [, …].`
sortStmt : SORT refWord? (THE? rowWord)? BY sortKey (COMMA sortKey)* ;
sortKey  : col=IDENT (ASC | ASCENDING | DESC | DESCENDING)? ;

// `Keep [only] the first <n> rows.`
limitStmt : KEEP? ONLY? THE? FIRST count=NUMBER rowWord ;

// `Combine/Append that with <name>.`
combineStmt : (COMBINE | APPEND | UNION) joinLeft WITH right=qname ;

// `Store that/[the] result to file "…" / <ref>.`
storeStmt : STORE storeSource TO (fileSource | dest=qname) ;
storeSource : refWord | THE? (RESULT | RESULTS) | qname ;

// `Show/Display [me] [the] result [as <name>].`
showStmt : (SHOW | DISPLAY) ME? THE? (RESULT | RESULTS)? refWord? (AS name=IDENT)? ;

// ---- helper word classes (C4-b-ii = α: full synonym breadth + noise words) ------

keepVerb   : KEEP | TAKE | SELECT ;
columnWord : COLUMN | COLUMNS | COL | COLS | FIELD | FIELDS ;
rowWord    : ROW | ROWS | RECORD | RECORDS | LINE | LINES ;
whereWord  : WHERE | WHICH | THAT | WITH ;
exceptWord : EXCEPT | BUT | EXCLUDING | EXCLUDE ;
refWord    : THAT | THIS | IT ;

colList        : IDENT (COMMA IDENT)* ;
colRenameList  : colRename (COMMA colRename)* ;
colRename      : IDENT (AS IDENT)? ;

// ---- expression grammar — verbose skin over the ONE PL IR (S16, T5-e) ----------
// Ladder mirrors TTRP.g4 / TTRSql.g4: or < and < not < predicate < additive <
// multiplicative < unary < primary. Verbose comparators are closed alternatives
// (C4-c); canonical operators (`>` `=` …) remain valid and may mix.
boolExpr : orExpr ;
orExpr   : andExpr (OR andExpr)* ;
andExpr  : notExpr (AND notExpr)* ;
notExpr  : NOT notExpr | predicate ;
predicate
    : addExpr IS NOT? EMPTY                                       # emptyPredicate      // is [not] empty
    | addExpr IS ONE OF LPAREN expr (COMMA expr)* RPAREN          # oneOfPredicate      // is one of (…)
    | addExpr IS NOT? BETWEEN addExpr AND addExpr                 # betweenPredicate
    | addExpr comparator addExpr                                  # comparePredicate
    | addExpr                                                     # barePredicate
    ;

// Verbose + canonical comparators (closed set; 1:1 with ttr-b.synonyms.toml). Each form
// is its own sub-rule so the fold reads which matched (labels can't hold `|` in ANTLR).
comparator
    : symbolOp
    | verboseGe | verboseLe | verboseGt | verboseLt | verboseNe | verboseEq
    ;
symbolOp  : EQ | NEQ | NEQ2 | LT | LTE | GT | GTE ;
verboseGt : IS W_MORE THAN | IS BIGGER THAN | IS LARGER THAN | IS HIGHER THAN | COMES AFTER ;
verboseLt : IS LESS THAN | IS FEWER THAN | IS LOWER THAN | IS SMALLER THAN | COMES BEFORE ;
verboseLe : IS NOT W_MORE THAN | COMES NOT AFTER ;
verboseGe : IS NOT LESS THAN | COMES NOT BEFORE ;
verboseNe : IS NOT EQUAL TO | DOES NOT EQUAL | IS NOT ;
verboseEq : IS EQUAL TO | EQUALS | IS THE SAME AS | IS ;

expr     : addExpr ;
addExpr  : mulExpr ((PLUS | MINUS) mulExpr)* ;
mulExpr  : unaryExpr ((STAR | SLASH) unaryExpr)* ;
unaryExpr : MINUS unaryExpr | primary ;
primary
    : literal                                               # litPrimary
    | funcCall                                              # callPrimary
    | dottedRef                                             # colPrimary
    | LPAREN expr RPAREN                                    # parenPrimary
    ;
funcCall  : name=IDENT LPAREN (expr (COMMA expr)*)? RPAREN ;
dottedRef : IDENT (DOT IDENT)* ;
qname     : IDENT (DOT IDENT)* ;
typeName  : IDENT (LPAREN NUMBER (COMMA NUMBER)? RPAREN)? ;
literal   : str | NUMBER | TRUE | FALSE ;
str       : STRING | CHAR_STRING ;

// =============================================================================
// Lexer — English roster keywords + verbose comparison words + shared operators
// =============================================================================

// Verbs.
LOAD        : 'load' ;
KEEP        : 'keep' ;
TAKE        : 'take' ;
SELECT      : 'select' ;
FILTER      : 'filter' ;
REMOVE      : 'remove' ;
DELETE      : 'delete' ;
RENAME      : 'rename' ;
CONVERT     : 'convert' ;
RETYPE      : 'retype' ;
CREATE      : 'create' ;
COMPUTE     : 'compute' ;
CALCULATE   : 'calculate' ;
SUMMARIZE   : 'summarize' ;
JOIN        : 'join' ;
SORT        : 'sort' ;
COMBINE     : 'combine' ;
APPEND      : 'append' ;
UNION       : 'union' ;
STORE       : 'store' ;
SHOW        : 'show' ;
DISPLAY     : 'display' ;

// Helper / noise words.
COLUMN      : 'column' ;
COLUMNS     : 'columns' ;
COL         : 'col' ;
COLS        : 'cols' ;
FIELD       : 'field' ;
FIELDS      : 'fields' ;
ROW         : 'row' ;
ROWS        : 'rows' ;
RECORD      : 'record' ;
RECORDS     : 'records' ;
LINE        : 'line' ;
LINES       : 'lines' ;
ONLY        : 'only' ;
ALL         : 'all' ;
THE         : 'the' ;
ME          : 'me' ;
NEW         : 'new' ;
FROM        : 'from' ;
WITH        : 'with' ;
ON          : 'on' ;
BY          : 'by' ;
OF          : 'of' ;
TO          : 'to' ;
AS          : 'as' ;
FOR         : 'for' ;
WHERE       : 'where' ;
WHICH       : 'which' ;
THAT        : 'that' ;
THIS        : 'this' ;
IT          : 'it' ;
EXCEPT      : 'except' ;
BUT         : 'but' ;
EXCLUDING   : 'excluding' ;
EXCLUDE     : 'exclude' ;
GROUP       : 'group' ;
GROUPED     : 'grouped' ;
FIRST       : 'first' ;
RESULT      : 'result' ;
RESULTS     : 'results' ;
W_TYPE      : 'type' ;
FILE        : 'file' ;
SCHEMA      : 'schema' ;
ASC         : 'asc' ;
ASCENDING   : 'ascending' ;
DESC        : 'desc' ;
DESCENDING  : 'descending' ;

// Verbose comparison words (C4-c; shared spellings fold to CatalogId).
IS          : 'is' ;
NOT         : 'not' ;
AND         : 'and' ;
OR          : 'or' ;
W_MORE      : 'more' ;
LESS        : 'less' ;
THAN        : 'than' ;
BIGGER      : 'bigger' ;
LARGER      : 'larger' ;
HIGHER      : 'higher' ;
LOWER       : 'lower' ;
SMALLER     : 'smaller' ;
FEWER       : 'fewer' ;
BEFORE      : 'before' ;
AFTER       : 'after' ;
COMES       : 'comes' ;
EQUAL       : 'equal' ;
EQUALS      : 'equals' ;
SAME        : 'same' ;
DOES        : 'does' ;
EMPTY       : 'empty' ;
ONE         : 'one' ;
BETWEEN     : 'between' ;

// Literals-as-keywords.
TRUE        : 'true' ;
FALSE       : 'false' ;

// Operators & punctuation (multi-char before single-char for maximal munch).
EQEQ        : '==' ;                 // S9 reject → TTRP-EQ-001 (scanner)
NEQ         : '<>' ;
NEQ2        : '!=' ;
LTE         : '<=' ;
GTE         : '>=' ;
EQ          : '=' ;
LT          : '<' ;
GT          : '>' ;
PLUS        : '+' ;
MINUS       : '-' ;
STAR        : '*' ;
SLASH       : '/' ;                  // `//` = two SLASH ⇒ TTRP-B-005 (scanner)
LPAREN      : '(' ;
RPAREN      : ')' ;
COMMA       : ',' ;
DOT         : '.' ;

STRING        : '"' (~["\r\n])* '"' ;
CHAR_STRING   : '\'' (~['\r\n])* '\'' ;

LINE_COMMENT  : '#' ~[\r\n]* -> channel(HIDDEN) ;   // S19 `#` comments
WS            : [ \t\r\n]+ -> skip ;

NUMBER        : [0-9]+ ('.' [0-9]+)? ;
IDENT         : [a-z_] [a-z0-9_]* ;   // caseInsensitive folds A-Z into a-z (avoids the dup-range warning)

// Catch-all LAST: any other char (e.g. a non-ASCII letter, S20) becomes an UNMATCHED
// token the reject scanner names (TTRP-B-006) instead of a bare lexer error.
UNMATCHED     : . ;
