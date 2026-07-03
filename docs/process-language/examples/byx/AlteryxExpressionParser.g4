parser grammar AlteryxExpressionParser;

options { tokenVocab=ByxLexer; }

expression
   :  UnaryOperator=(PLUS | MINUS) Expr=expression                              #unary
   |  LPAR Expr=expression RPAR                                                 #unary
   |  Left=expression BinaryOperator=(TIMES | DIV) Right=expression             #binary
   |  Left=expression BinaryOperator=(PLUS | MINUS) Right=expression            #binary
   |  IF Cond=expression THEN ResultTrue=expression optionalElses ENDIF         #if
   |  term                                                                      #exprTerminal
   ;

optionalElses
   : ELSEIF Condition=logical_expression THEN ResultTrue=expression optionalElses    #elseIf
   | ELSE ResultFalse=expression                                                     #else
   ;

term
   :  constant
   |  var_ref
   |  function_call
   ;

constant
   : INTEGER                                                     #const
   | DECIMAL                                                     #const
   | SINGLE_QUOTED_STRING                                        #const
   | DOUBLE_QUOTED_STRING                                        #const
   | TRUE                                                        #const
   | FALSE                                                       #const
   ;


var_ref    : ID                                                  #varReference
           | BRACKETID                                           #varReference
           | VarRefs+=var_ref (DOT VarRefs+=var_ref)+            #recursiveVarReference
           ;

function_call : Function=ID LPAR ExprList=expression_list? RPAR   #funcCall
           ;

expression_list : Params+=expression (list_separator Params+=expression)*
           ;

list_separator : COMMA | SEMICOLON | W_AND
           ;

logical_expression : Left=logical_expression BinaryOperator=(AND|BIG_AND|W_AND) Right=logical_expression # logBinary
           | Left=logical_expression BinaryOperator=(OR|BIG_OR|W_OR) Right=logical_expression            # logBinary
           | UnaryOperator=(W_NOT | NOT) LogExpr=logical_expression                                      # logUnary
           | LPAR LogExpr=logical_expression RPAR                                                        # logUnary
           | Left=expression BinaryOperator=(EQ | DOUBLE_EQ | NOT_EQ | NEQ
                            | LT | LE | GT | GE ) Right=expression                                       # comparison
           | Left=expression BinaryOperator=verbose_comparison Right=expression                          # verboseComparison
           | Expr=expression (IN|verbose_in) LPAR ExprList=expression_list? RPAR                         # in
           | term                                                                                        # logExprTerminal
           ;


verbose_comparison
           : verbose_eq              #verbEq
           | verbose_neq             #verbNeq
           | verbose_lt              #verbLt
           | verbose_le              #verbLe
           | verbose_gt              #verbGt
           | verbose_ge              #verbGe
           ;

verbose_eq : (IS|ARE)? (W_EQUAL|W_EQUALS) TO?
           | (IS|ARE)? W_SAME AS
           | (IS|ARE)
           | (IS|ARE)? W_PRECISELY
           ;
verbose_neq: (IS|ARE)? (W_NOT|NOT) (W_EQUAL|W_EQUALS|EQ|DOUBLE_EQ) TO?
           | (IS|ARE)? W_NOT
           ;
verbose_lt : (IS|ARE|HAVE|HAS)? W_LESS THAN?
           | (IS|ARE|HAVE|HAS)? W_FEWER THAN?
           | (IS|ARE)? (W_LOWER | W_SMALLER) THAN?
           | (COMES|COME)? W_BEFORE
           ;
verbose_gt : (IS|HAS)? W_MORE THAN?
           | (IS|HAS)? (W_BIGGER|W_LARGER) THAN?
           | (IS|HAS)? W_HIGHER THAN?
           | (COMES|COME)? W_AFTER
           ;
verbose_le : verbose_lt W_OR verbose_eq
           | (IS|ARE|HAVE|HAS)? W_NOT W_MORE THAN?
           | (IS|ARE|HAVE|HAS)? W_NOT (W_BIGGER|W_LARGER) THAN?
           | (IS|ARE|HAVE|HAS)? W_NOT W_HIGHER THAN?
           | (COMES|COME)? W_NOT W_AFTER
           ;
verbose_ge : verbose_gt W_OR verbose_eq
           | (IS|ARE|HAVE|HAS)? W_NOT W_LESS THAN?
           | (IS|ARE|HAVE|HAS)? W_NOT W_FEWER THAN?
           | (IS|ARE)? W_NOT (W_LOWER | W_SMALLER) THAN?
           | (COMES|COME)? W_NOT W_BEFORE
           ;
verbose_in : (IS|ARE)? (W_BETWEEN | W_WITHIN)
           ;



