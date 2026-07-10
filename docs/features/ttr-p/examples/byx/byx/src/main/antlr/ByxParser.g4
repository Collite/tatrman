parser grammar ByxParser;


options { tokenVocab=ByxLexer; }
//import AlteryxExpressionParser;
//import ByxLexer;

boraFile : (lines+=line)+ ;

line      : statement DOT? (NEWLINE | EOF) ;

statement : select
          | rename
          | retype
          | filter
          | negative_filter
          | summarize
          | formula
//          | join                # joinStatement
//          | input               # inputStatement
//          | output              # outputStatement
          ;


select     : SELECT (ONLY)? THE? (column_word)? column_list_with_renames
           | TAKE ONLY? THE? column_word? column_list_with_renames
           ;

retype     : RETYPE column_word? var_ref (AS|TO) Type=type
           | CHANGE THE? TYPE OF var_ref TO Type=type
           | CONVERT THE? TYPE? OF? var_ref TO Type=type
           ;

filter     : SELECT (ONLY)? THE? record_ref? where_word? logical_expression
           | FILTER (FOR)? record_ref? where_word? logical_expression
           | TAKE ONLY? THE? record_ref? where_word? logical_expression
           | KEEP ONLY? THE? record_ref? where_word? logical_expression
//           | logical_expression    //This will be possible, but will not be inlcuded in the (suggesting) grammar
           ;

negative_filter
           : (DELETE|REMOVE) THE? record_ref? where_word? logical_expression
           ;


rename     : RENAME THE? (column_word)? column_list_with_renames            # renameList
           | CHANGE THE? NAME OF var_ref TO new_name                        # renamePair
           ;

formula    : CREATE (NEW)? (column_word|FORMULA)? expression_list_with_renames
           | COMPUTE (NEW)? (column_word|FORMULA)? expression_list_with_renames
           | CALCULATE (NEW)? (column_word|FORMULA)? expression_list_with_renames
           ;

summarize  : (CREATE? NEW? SUMMARY)                                         # emptySummarize
           | (SUMMARIZE ref_word?)                                          # emptySummarize
           | CREATE? NEW? SUMMARY? summarize_content                        # nonEmptySummarize
           ;

summarize_content : summarize_group_by summarize_summary                    # summarizeBothGroupFirst
                  | summarize_summary summarize_group_by                    # summarizeBothSummaryFirst
                  | summarize_group_by                                      # summarizeJustGroupBy
                  | summarize_summary                                       # summarizeJustSummary
                  ;

summarize_group_by : (GROUP | GROUP BY | BY) expression_list_with_renames
                   ;

summarize_summary  : SUMMARIZE expression_list_with_renames
                   ;

//column_list : Cols+=var_ref (list_separator Cols+=var_ref)*
//            ;

expression_with_rename
            : expression (AS|TO)? new_name                #renamedExpr
            | expression                                  #justExpr
            ;

expression_list_with_renames
            : Exprs+=expression_with_rename (list_separator Exprs+=expression_with_rename)*
            ;

column_with_rename
            : var_ref (AS|TO)? new_name                   #renamedVar
            | var_ref                                     #justVar
            ;

column_list_with_renames
            : Cols+=column_with_rename (list_separator Cols+=column_with_rename)*
            ;

new_name    : ID                                          #newID
            | BRACKETID                                   #newID
            ;

record_ref  : row_word
            | record_word
            | ref_word
            ;

type        : T_STRING | T_TEXT | T_NUMBER | T_DECIMAL | T_FLOAT | T_DOUBLE | T_INT | T_INTEGER | T_DATE | T_TIME | T_DATETIME ;

where_word  : WHERE
            | WHICH
            | THAT
            | WITH
            ;

column_word : COLUMN
            | COLUMNS
            | COL
            | COLS
            ;

row_word    : ROW
            | ROWS
            ;
record_word : RECORD
            | RECORDS
            ;

ref_word    : THIS
            | THESE
            | THOSE
            | THAT
            | IT
            ;

have_word   : HAVE
            | HAS
            ;

is_word     : IS
            | ARE
            ;

start_word  : BEGINNING
            | STARTING
            | LEFT
            | FIRST
            ;

end_word    : ENDING
            | LAST
            | RIGHT
            ;

char_word   : CHARACTER
            | CHARACTERS
            | CHAR
            | CHARS
            ;


// -----------------------------------------------------------------------------------------
//                          EXPRESSION PARSER
// -----------------------------------------------------------------------------------------



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



