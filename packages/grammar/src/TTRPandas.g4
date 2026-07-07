// TTR-pandas — the dataframe fragment dialect (P6 Stage 6.2). Own grammar (C2-g α),
// beside TTR.g4 / TTRP.g4 / TTRSql.g4, read directly by the ANTLR Gradle plugin in
// ttrp-frontend. Kotlin-only (G-b).
//
// "Dataframe-shaped TTR-P" (C2-c β): method chains over the S17 FULL-WORD roster
// `select calc filter join aggregate sort union limit load store display`. Assignment =
// SSA rebind (Q7-γ/C2-c-ii); the last value = the container's single default out
// (C2-c-i). NOT Python — Python-LOOKING for `.ttr.py` highlighting only (H-2), never
// Python-parseable.
//
// S16: skins the ONE PL expression IR (org.tatrman.ttrp.expr.Expression). `==` is the
// single closed dialect synonym for `=` (S9) — folded to the SAME `op.eq` CatalogId,
// pinned by TtrPandasKeywordDriftSpec. Out-of-roster methods, `.apply`/lambdas, IO,
// masks and control flow are NAMED rejects (ttr-pandas.rejects.toml), never bare errors.
//
// Lowercase-only (canonical lexis — this is TTR-P wearing dataframe clothes, not SQL).

grammar TTRPandas;

// =============================================================================
// Parser — statement* finalValue (C2-c-ii)
// =============================================================================

fragmentProgram : statement* finalValue EOF ;

statement       : target=IDENT ASSIGN chain ;
finalValue      : chain ;                                   // last value = default out (C2-c-i)

chain           : head (DOT methodCall)* ;
head            : loadCall | ref ;
ref             : IDENT (DOT IDENT)* ;                       // var / in-port / qname
loadCall        : LOAD LPAREN argList? RPAREN ;

methodCall      : method LPAREN argList? RPAREN ;
method
    : SELECT | CALC | FILTER | JOIN | AGGREGATE | SORT | UNION | LIMIT | LOAD | STORE | DISPLAY
    | bad=IDENT                                              // out-of-roster → TTRP-PD-001 (S17)
    ;

argList         : arg (COMMA arg)* ;
arg             : (name=IDENT COLON)? expr ;                 // named (by:/on:/type:/dir:/nulls:/right:) or positional

// ---- expression grammar — pandas skin over the ONE PL IR (S16, T5-e) -----------
// `==` is the ONE closed synonym for `=` (S9). Ladder mirrors TTRP.g4 / TTRSql.g4.
expr            : orExpr ;
orExpr          : andExpr (OR andExpr)* ;
andExpr         : notExpr (AND notExpr)* ;
notExpr         : NOT notExpr | predicate ;
predicate
    : addExpr (ASSIGN | EQEQ | NEQ | NEQ2 | LT | LTE | GT | GTE) addExpr       # comparePredicate
    | addExpr IS NOT? NULL                                                    # isNullPredicate
    | addExpr NOT? IN LPAREN expr (COMMA expr)* RPAREN                        # inPredicate
    | addExpr NOT? BETWEEN addExpr AND addExpr                               # betweenPredicate
    | addExpr                                                                 # barePredicate
    ;
addExpr         : mulExpr ((PLUS | MINUS) mulExpr)* ;
mulExpr         : unaryExpr ((STAR | SLASH) unaryExpr)* ;
unaryExpr       : MINUS unaryExpr | primary ;
primary
    : literal                                               # litPrimary
    | CAST LPAREN expr AS typeName RPAREN                    # castPrimary
    | CASE (WHEN expr THEN expr)+ (ELSE expr)? END           # casePrimary
    | funcCall                                              # callPrimary
    | chainArg                                              # chainPrimary   // right:/union chain source
    | columnRef                                             # colPrimary
    | LPAREN expr RPAREN                                    # parenPrimary
    ;

// A chain source in arg position: `load(...).filter(...)` / a `.method` chain over a ref.
chainArg        : (loadCall | ref) (DOT methodCall)+ ;
funcCall
    : name=IDENT LPAREN STAR RPAREN                                          # countStar
    | name=IDENT LPAREN (DISTINCT? expr (COMMA expr)*)? RPAREN               # namedCall
    ;
columnRef       : IDENT (DOT IDENT)* ;
typeName        : IDENT (LPAREN NUMBER (COMMA NUMBER)? RPAREN)? ;
literal         : STRING | NUMBER | TRUE | FALSE | NULL ;

// =============================================================================
// Lexer — roster method keywords + expression keywords (shared spellings)
// =============================================================================

SELECT      : 'select' ;
CALC        : 'calc' ;
FILTER      : 'filter' ;
JOIN        : 'join' ;
AGGREGATE   : 'aggregate' ;
SORT        : 'sort' ;
UNION       : 'union' ;
LIMIT       : 'limit' ;
LOAD        : 'load' ;
STORE       : 'store' ;
DISPLAY     : 'display' ;

// Expression keywords (shared spellings → mapped back to CatalogId in the walker).
AND         : 'and' ;
OR          : 'or' ;
NOT         : 'not' ;
IS          : 'is' ;
IN          : 'in' ;
BETWEEN     : 'between' ;
CASE        : 'case' ;
WHEN        : 'when' ;
THEN        : 'then' ;
ELSE        : 'else' ;
END         : 'end' ;
CAST        : 'cast' ;
AS          : 'as' ;
DISTINCT    : 'distinct' ;
NULL        : 'null' ;
TRUE        : 'true' ;
FALSE       : 'false' ;

// Curated REJECT tokens — lexed so TtrPandasRejectScanner can NAME the reject.
LAMBDA      : 'lambda' ;         // PD-002
FOR         : 'for' ;            // PD-005
WHILE       : 'while' ;          // PD-005
IF          : 'if' ;             // PD-005
DEF         : 'def' ;            // PD-005
IMPORT      : 'import' ;         // PD-005

// Operators & punctuation.
ARROW       : '->' ;
EQEQ        : '==' ;             // S9 synonym for `=` (folds to op.eq)
NEQ         : '!=' ;
NEQ2        : '<>' ;
LTE         : '<=' ;
GTE         : '>=' ;
ASSIGN      : '=' ;
LT          : '<' ;
GT          : '>' ;
PLUS        : '+' ;
MINUS       : '-' ;
STAR        : '*' ;
SLASH       : '/' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACKET    : '[' ;              // PD-004 boolean-mask indexing
RBRACKET    : ']' ;
COMMA       : ',' ;
COLON       : ':' ;
DOT         : '.' ;

STRING        : '"' (~["\r\n])* '"' | '\'' (~['\r\n])* '\'' ;

LINE_COMMENT  : '#' ~[\r\n]* -> channel(HIDDEN) ;           // S19 `#` comments
WS            : [ \t\r\n]+ -> skip ;

NUMBER        : [0-9]+ ('.' [0-9]+)? ;
IDENT         : [a-zA-Z_] [a-zA-Z0-9_]* ;
