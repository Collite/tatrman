// TTR-P canonical grammar — seed (P0 Stage 0.1). Real productions land in P1 Stage 1.1.
// @grammar-version: 0.1  (TTR-P spec version is an integer cut via docs/grammar-master/ — S6)
grammar TTRP;
document      : statement* EOF ;
statement     : ID ;                                  // placeholder — replaced in Stage 1.1
LINE_COMMENT  : '//' ~[\r\n]* -> channel(HIDDEN) ;
BLOCK_COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;
ID            : [a-zA-Z_] [a-zA-Z0-9_]* ;
WS            : [ \t\r\n]+ -> skip ;
