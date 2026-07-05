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
    | containerDecl
    | controlBlock
    | programHeader              // parses ONLY to name the S12 rejection (walker → TTRP-PRS-002)
    | controlStmt                // b after a / a with b / a finishes with b
    | bindingOrChain
    ;

usesWorld       : USES WORLD STRING ;
importDecl      : IMPORT qname (DOT STAR)? ;
programHeader   : PROGRAM identifier ;

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

containerDecl   : CONTAINER identifier portSig? TARGET qname
                  ( LBRACE statement* RBRACE | TAGGED_BLOCK ) ;   // closed containers (C3-d-iii)
portSig         : LPAREN portDecl (COMMA portDecl)* RPAREN ;
portDecl        : (IN | OUT | ERR) identifier ;                  // hero writes `err rejects` — an
                                                                 // err-kind port NAMED rejects (R4;
                                                                 // typing revisited in Stage 2.1).

// Inline schema literal placeholder — becomes real in Stage 1.3 (D-c). Kept
// minimal here (types are bare identifiers); no golden fixture exercises it yet.
schemaLiteral   : LBRACE (identifier COLON identifier (COMMA identifier COLON identifier)*)? RBRACE ;

qname           : identifier (DOT identifier)* ;
dottedRef       : identifier (DOT idPart)* ;

// ---- provisional expression grammar (STAGE-1.2 REPLACES this whole block) -----
// Precedence ladder or < and < not < comparison < additive < multiplicative <
// unary < primary. `==` is parsed on purpose and rejected by the walker (S9 →
// TTRP-EQ-001); `=` is the one equality operator here (context-separated from
// statement binding by grammar position).
expr            : orExpr ;
orExpr          : andExpr (OR andExpr)* ;
andExpr         : notExpr (AND notExpr)* ;
notExpr         : NOT notExpr | comparison ;
comparison      : additive ( (ASSIGN | EQEQ | NEQ | LT | LTE | GT | GTE) additive
                            | IS NOT? NULL )? ;
additive        : multiplicative ((PLUS | MINUS) multiplicative)* ;
multiplicative  : unary ((STAR | SLASH) unary)* ;
unary           : MINUS unary | primary ;
primary
    : literal
    | functionCall
    | dottedRef
    | LPAREN expr RPAREN
    ;
functionCall    : identifier LPAREN (expr (COMMA expr)*)? RPAREN ;
literal         : STRING | CHAR_STRING | NUMBER | TRUE | FALSE | NULL ;
// -------------------------------------------------------------------------------

// Soft keywords: structural words that must still be usable as names. `in/out/err`
// are port-kind keywords but also legal port names / refs (`prep.err`) / the
// PRS-005 reject subject (`err = …`); `by` is both `group by` and the `by:` arg
// name. `true/false/null` are literals but also dotted-ref parts (`b.true`).
identifier      : IDENT | IN | OUT | ERR | BY ;
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
TARGET      : 'target' ;
CONTROL     : 'control' ;
AFTER       : 'after' ;
WITH        : 'with' ;
FINISHES    : 'finishes' ;
GROUP       : 'group' ;
BY          : 'by' ;
RELATION    : 'relation' ;
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

// Operators & punctuation (multi-char before single-char for maximal munch).
ARROW       : '->' ;
EQEQ        : '==' ;
NEQ         : '<>' ;
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
LBRACE      : '{' ;
RBRACE      : '}' ;
COMMA       : ',' ;
COLON       : ':' ;
DOT         : '.' ;

// Tagged blocks (C2-f: interiors byte-preserved — captured verbatim, no dedent).
// Single-token, non-greedy `.*?`; declared BEFORE STRING so the `"""` tag wins the
// equal-length tie (same trick as TTR.g4's TAGGED_BLOCK_LITERAL).
TAGGED_BLOCK  : '"""' [a-zA-Z] [a-zA-Z0-9-]* [ \t]* '\r'? '\n' .*? '"""' ;
STRING        : '"' (~["\r\n])* '"' ;
CHAR_STRING   : '\'' (~['\r\n])* '\'' ;

LINE_COMMENT  : '//' ~[\r\n]* -> channel(HIDDEN) ;
BLOCK_COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;
WS            : [ \t\r\n]+ -> skip ;

NUMBER        : [0-9]+ ('.' [0-9]+)? ;
IDENT         : [a-zA-Z_] [a-zA-Z0-9_]* ;
