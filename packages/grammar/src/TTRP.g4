// TTR-P canonical grammar — the C3-converged canonical surface (P1 Stage 1.1).
// @grammar-version: 0.1  (TTR-P spec version is an integer cut via docs/grammar-master/ — S6)
//
// Kotlin-only generation (G-b): this .g4 is read directly by the ANTLR Gradle
// plugin in ttrp-frontend; there is no antlr-ng/TS target and no TextMate grammar.
//
// Design notes (see docs/ttr-p/design/05-canonical-dsl-options.md §"The converged
// hero rendering" and contracts §3):
//   · γ-hybrid statements: chains (`a -> filter(…) -> sort(…)`) + SSA assignment,
//     freely mixed; precedence `=` < `->` < call (C3-a/C3-a-iv).
//   · `->` is ONE token for chains AND program-level wiring (C3-b).
//   · Chain elements are op-calls or references — NOT arbitrary expressions.
//     Expressions live only inside op arguments / config entries. This is what
//     keeps statement-level `=` (assignment) unambiguous vs expression `=`
//     (equality, S9): the two never meet in the same grammar position.
//   · Named-only multi-in; union list form — enforced in the walker, not here
//     (C3-c/S11 → TTRP-PRS-003/004).
//   · Closed containers, program-level wiring (C3-d-iii); keyword control
//     `after`/`with`, `finishes with` reserved (C3-e/F-b → TTRP-CTL-001).
//   · Two error ports `err`/`rejects` (C3-f); tagged blocks lexed opaque (C3-g).
//   · Reserved port names (`in out err rejects true false else`) stay lexable as
//     identifier/idPart fragments; reservation is a WALKER check (S10 → PRS-005).

grammar TTRP;

// =============================================================================
// Parser
// =============================================================================

document        : statement* EOF ;

statement
    : usesWorld
    | importDecl
    | schemaDecl                 // program-level `def schema <name> { col: type, … }` (D-c, Stage 1.3)
    | containerDecl
    | controlBlock
    | programHeader              // parses ONLY to name the S12 rejection (walker → TTRP-PRS-002)
    | controlStmt                // b after a / a with b / a finishes with b
    | bindingOrChain
    | cubeletStmt                // MD cubelet statements (D20–D24) — listed AFTER bindingOrChain so
                                 // a chain-viable `x = a` stays an Assignment; dispatch is semantic (R24)
    ;

usesWorld       : USES WORLD STRING ;
importDecl      : IMPORT qname (DOT STAR)? ;
programHeader   : PROGRAM identifier ;
schemaDecl      : DEF SCHEMA identifier LBRACE (schemaField (COMMA schemaField)*)? RBRACE ;

// C3-a-iv precedence: `=` < `->` < call — encoded by rule nesting, not token
// precedence. Assignment vs bare chain is decided by the token AFTER the leading
// identifier (`=` ⇒ assignment; `(` / `.` / `->` / end ⇒ chain), so there is no
// ambiguity: a chainElem never begins `identifier =`.
bindingOrChain
    : identifier ASSIGN chain    # assignment          // SSA reassignment legal (Q7-γ)
    | chain                      # chainStmt           // incl. program-level wiring
    ;

chain           : chainElem (ARROW chainElem)* ;       // one token for chains AND wiring (C3-b)
chainElem       : opCall | dottedRef ;                 // dottedRef = variable | node.port | qname
                                                        // (which one is a RESOLUTION question — D-b)

opCall
    : identifier LPAREN argList? RPAREN configBlock?
    | identifier configBlock                            // wide-op block form: aggregate { … }
    ;
argList         : arg (COMMA arg)* ;
arg             : (identifier COLON)? argValue ;        // named canonical; bare positional allowed
                                                        // ONLY as a single-in op's source or a union
                                                        // list element — enforced in the walker
                                                        // (TTRP-PRS-003/004), NOT the grammar.
argValue        : RELATION qname | schemaLiteral | expr ;

configBlock     : LBRACE configEntry* RBRACE ;
configEntry
    : GROUP BY identifier (COMMA identifier)*
    | identifier ASSIGN expr
    ;

controlStmt     : identifier AFTER identifier                    # afterFs      // FS
                | identifier WITH identifier                     # withSs       // SS
                | identifier FINISHES WITH identifier            # finishesFf   // reserved → TTRP-CTL-001
                ;
controlBlock    : CONTROL LBRACE controlStmt* RBRACE ;

// MD cubelet statements (contracts §1.2, D20–D24). LHS dispatches semantics (R24, S5C):
// an mdPath LHS is a slice; a bare identifier is a cubelet/virtual/fresh target. The parser
// stays mechanical — the four operators, an expr RHS, and an optional free-form `with` object.
cubeletStmt     : cubeletLhs op=(ASSIGN | ASSIGN_MAT | PLUS_ASSIGN | MINUS_ASSIGN) expr withClause? ;
cubeletLhs      : mdPath | identifier ;                          // mdPath first: a dotted LHS is a path
withClause      : WITH mdObject ;                                // keys (shape/table/journal) checked semantically (R27)
mdObject        : LBRACE (mdObjectEntry (COMMA mdObjectEntry)*)? RBRACE ;
mdObjectEntry   : identifier COLON mdObjectValue ;
mdObjectValue   : qname | STRING | numericLiteral | TRUE | FALSE ;

containerDecl   : CONTAINER identifier portSig? TARGET qname
                  ( LBRACE statement* RBRACE | TAGGED_BLOCK ) ;   // closed containers (C3-d-iii)
portSig         : LPAREN portDecl (COMMA portDecl)* RPAREN ;
portDecl        : (IN | OUT | ERR) identifier ;                  // hero writes `err rejects` — an
                                                                 // err-kind port NAMED rejects (R4;
                                                                 // typing revisited in Stage 2.1).

// Inline schema literal (D-c, Stage 1.3): `schema: { col: type, … }`. Column types
// reuse the Stage 1.2 `typeName` production (S23 spellings, e.g. `decimal(12,2)`),
// shared with `def schema` via `schemaField`.
schemaLiteral   : LBRACE (schemaField (COMMA schemaField)*)? RBRACE ;
schemaField     : identifier COLON typeName ;

qname           : identifier (DOT identifier)* ;
dottedRef       : identifier (DOT idPart)* ;

// ---- expression grammar (STAGE 1.2 — the ONE PL expression layer, T5-e) --------
// Precedence ladder: or < and < not < predicate < additive < multiplicative <
// unary < primary. `=` IS the equality operator inside expression context (S9),
// context-separated from statement binding by grammar position; `==` stays lexable
// (EQEQ) and is rejected by the walker (→ TTRP-EQ-001).
//
// `between … and …` sits at the predicate level with `addExpr` operands: this
// resolves the `and` overlap with boolean `andExpr` structurally — no predicates,
// no backtracking — because `between addExpr AND addExpr` is a fixed shape ANTLR's
// ALL(*) commits to before the higher `andExpr` loop can see the `and`.
expr            : orExpr ;
orExpr          : andExpr (OR andExpr)* ;
andExpr         : notExpr (AND notExpr)* ;
notExpr         : NOT notExpr | predicate ;
predicate       : addExpr ( (ASSIGN | EQEQ | NEQ | LT | LTE | GT | GTE) addExpr
                          | IS NOT? NULL
                          | NOT? IN LPAREN expr (COMMA expr)* RPAREN
                          | BETWEEN addExpr AND addExpr
                          )? ;
addExpr         : mulExpr ((PLUS | MINUS) mulExpr)* ;
mulExpr         : unaryExpr ((STAR | SLASH) unaryExpr)* ;
unaryExpr       : MINUS unaryExpr | primary ;
primary
    : literal
    | castExpr
    | caseExpr
    | functionCall
    | dottedRef                                             // column or port.column (C3-a-iv-4)
    | mdPath                                                // MD dot-path (D14) — LAST: only catches
                                                            // chains dottedRef can't (numeric/quoted/
                                                            // set/range/star). `floatLiteral` wins via
                                                            // `literal` above (R1: float before path).
    | LPAREN expr RPAREN
    ;
castExpr        : CAST LPAREN expr AS typeName RPAREN ;      // explicit cast only (B-T5)
caseExpr        : CASE (WHEN expr THEN expr)+ (ELSE expr)? END ;
functionCall    : identifier LPAREN (DISTINCT? expr (COMMA expr)*)? RPAREN ;  // distinct → AggregateCall only
typeName        : identifier (LPAREN INT (COMMA INT)? RPAREN)? ;              // decimal(12,2) — S23 spellings
literal         : STRING | CHAR_STRING | numericLiteral | TRUE | FALSE | NULL ;

// MD dot-path float/path split (contracts §1.2/§1.3, D14). `floatLiteral` is exactly one of the
// three float shapes; anything else dotted is an `mdPath`. Reconstruction to a raw number string
// (WS-insignificant, R2) keeps `12.5`-style literals byte-identical (zero behaviour change).
numericLiteral  : floatLiteral | INT ;
floatLiteral    : INT DOT INT                               // 12.5, 2025.06
                | DOT INT                                   // .25
                | INT DOT                                   // 25.
                ;
mdPath          : pathComponent (DOT pathComponent)+ ;
pathComponent   : identifier                                // member, level, measure, agg, cubelet
                | INT                                        // numeric member (2025, 06)
                | STRING                                     // quoted member: "Kaufland K123"
                | LBRACE pathAtom (COMMA pathAtom)* RBRACE   // set (D15: braces compulsory)
                | pathAtom DOTDOT pathAtom                   // range: 2024..2026
                | STAR                                       // free dimension (bound to an attr — R7)
                ;
pathAtom        : identifier | INT | STRING ;
// -------------------------------------------------------------------------------

// Soft keywords: structural words that must still be usable as names. `in/out/err`
// are port-kind keywords but also legal port names / refs (`prep.err`) / the
// PRS-005 reject subject (`err = …`); `by` is both `group by` and the `by:` arg
// name. `true/false/null` are literals but also dotted-ref parts (`b.true`).
// `schema` is the S23 inline-schema keyword but also a legal arg name (`schema: s`).
// `distinct` is the canonical `distinct()` op name (GraphBuilder, S3.5) but also
// `functionCall`'s SQL-style DISTINCT? modifier (`count(distinct x)`) — same
// dual-use pattern; the two never collide (DISTINCT? there is a fixed pre-`expr`
// position, distinct() here is `identifier LPAREN …` — ANTLR's context disambiguates).
identifier      : IDENT | IN | OUT | ERR | BY | SCHEMA | DISTINCT ;
idPart          : identifier | TRUE | FALSE | NULL ;

// =============================================================================
// Lexer
// =============================================================================

// Structural keywords (declared before IDENT so they win maximal-munch ties).
USES        : 'uses' ;
WORLD       : 'world' ;
IMPORT      : 'import' ;
PROGRAM     : 'program' ;
CONTAINER   : 'container' ;
DEF         : 'def' ;
TARGET      : 'target' ;
CONTROL     : 'control' ;
AFTER       : 'after' ;
WITH        : 'with' ;
FINISHES    : 'finishes' ;
GROUP       : 'group' ;
BY          : 'by' ;
RELATION    : 'relation' ;
SCHEMA      : 'schema' ;
IN          : 'in' ;
OUT         : 'out' ;
ERR         : 'err' ;

// Expression keywords.
AND         : 'and' ;
OR          : 'or' ;
NOT         : 'not' ;
IS          : 'is' ;
NULL        : 'null' ;
TRUE        : 'true' ;
FALSE       : 'false' ;
BETWEEN     : 'between' ;
CASE        : 'case' ;
WHEN        : 'when' ;
THEN        : 'then' ;
ELSE        : 'else' ;
END         : 'end' ;
CAST        : 'cast' ;
AS          : 'as' ;
DISTINCT    : 'distinct' ;

// Operators & punctuation (multi-char before single-char for maximal munch).
ARROW        : '->' ;
EQEQ         : '==' ;
NEQ          : '<>' ;
LTE          : '<=' ;
GTE          : '>=' ;
DOTDOT       : '..' ;    // MD range (declared before DOT — maximal munch keeps `..` one token)
ASSIGN_MAT   : ':=' ;    // materialize (D22 — before COLON)
PLUS_ASSIGN  : '+=' ;    // merge (D23)
MINUS_ASSIGN : '-=' ;    // delete (D24)
ASSIGN       : '=' ;
LT           : '<' ;
GT           : '>' ;
PLUS         : '+' ;
MINUS        : '-' ;
STAR         : '*' ;
SLASH        : '/' ;
LPAREN       : '(' ;
RPAREN       : ')' ;
LBRACE       : '{' ;
RBRACE       : '}' ;
COMMA        : ',' ;
COLON        : ':' ;
DOT          : '.' ;

// Tagged blocks (C2-f: interiors byte-preserved — captured verbatim, no dedent).
// Single-token, non-greedy `.*?`; declared BEFORE STRING so the `"""` tag wins the
// equal-length tie (same trick as TTR.g4's TAGGED_BLOCK_LITERAL).
TAGGED_BLOCK  : '"""' [a-zA-Z] [a-zA-Z0-9-]* [ \t]* '\r'? '\n' .*? '"""' ;
STRING        : '"' (~["\r\n])* '"' ;
CHAR_STRING   : '\'' (~['\r\n])* '\'' ;

LINE_COMMENT  : '//' ~[\r\n]* -> channel(HIDDEN) ;
BLOCK_COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;
WS            : [ \t\r\n]+ -> skip ;

INT           : [0-9]+ ;              // MD dot-path (D14): NUMBER split into INT + `floatLiteral`
                                      // parser rule so `2025.06` can be a float OR a path by context.
IDENT         : [a-zA-Z_] [a-zA-Z0-9_]* ;
