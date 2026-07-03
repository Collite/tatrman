parser grammar ByxParser;


options { tokenVocab=ByxLexer; }
//import AlteryxExpressionParser;

boraFile : (lines+=line)+ ;

line      : statement DOT? (NEWLINE | EOF) ;

statement : select
          | negative_select
          | rename
          | retype
          | filter
          | negative_filter
          | summarize
          | formula
          | join
          | input
          | output
          | browse
          ;


select     : SELECT (ONLY)? THE? (column_word)? column_list_with_renames
           | TAKE ONLY? THE? column_word? column_list_with_renames
           | KEEP ONLY? THE? column_word? column_list_with_renames
           | RETURN ONLY? THE? column_word? column_list_with_renames
           ;

negative_select
           : SELECT (ALL)? THE? (column_word)? except_word column_list_with_renames
           | TAKE ALL? THE? column_word? except_word column_list_with_renames
           | KEEP ALL? THE? column_word? except_word column_list_with_renames
           | RETURN ALL? THE? column_word? except_word column_list_with_renames
           ;
retype     : RETYPE column_word? var_ref (AS|TO) Type=type
           | CHANGE THE? TYPE OF var_ref TO Type=type
           | CONVERT THE? TYPE? OF? var_ref TO Type=type
           ;

filter     : SELECT (ONLY)? THE? record_ref? where_word? logical_expression
           | FILTER (FOR)? record_ref? where_word? logical_expression
           | TAKE ONLY? THE? record_ref? where_word? logical_expression
           | KEEP ONLY? THE? record_ref? where_word? logical_expression
           | RETURN ONLY? THE? record_ref? where_word? logical_expression
//           | logical_expression    //This will be possible, but will not be inlcuded in the (suggesting) grammar
           ;

negative_filter
           : (DELETE|REMOVE) THE? (record_ref)? where_word? logical_expression
           | SELECT (ALL)? THE? record_ref? except_word where_word? logical_expression
           | TAKE ALL? THE? record_ref? except_word where_word? logical_expression
           | KEEP ALL? THE? record_ref? except_word where_word? logical_expression
           | RETURN ALL? THE? record_ref? except_word where_word? logical_expression
           ;


rename     : RENAME THE? (column_word)? column_list_with_renames                             # renameList
           | CHANGE THE? NAME OF var_ref TO new_name                                         # renamePair
           ;

formula    : CREATE (NEW)? (column_word|FORMULA)? expression_list_with_renames
           | COMPUTE (NEW)? (column_word|FORMULA)? expression_list_with_renames
           | CALCULATE (NEW)? (column_word|FORMULA)? expression_list_with_renames
           ;

summarize  : (CREATE? NEW? SUMMARY)                                                          # emptySummarize
           | (SUMMARIZE ref_word?)                                                           # emptySummarize
           | SUMMARIZE Summary=expression_list_with_renames                                  # summarizeJustSummary
           | SUMMARIZE (ref_word | Summary = expression_list_with_renames)
                       (BY | GROUP | GROUP BY) Grouping = expression_list_with_renames       # summarizeBothSummaryFirst
           | GROUP ref_word? BY? Grouping = expression_list_with_renames                     # summarizeJustGroupBy
           | GROUP ref_word? BY? Grouping = expression_list_with_renames
                       SUMMARIZE ref_word? Summary = expression_list_with_renames            # summarizeBothGroupFirst
           | CREATE? NEW? SUMMARY FOR? Summary = expression_list_with_renames
                       (BY | GROUP | GROUP BY) Grouping = expression_list_with_renames       # summarizeBothSummaryFirst
           | CREATE? NEW? SUMMARY (BY | GROUP | GROUP BY) Grouping = expression_list_with_renames
                        (FOR | SUMMARIZE)? Summary = expression_list_with_renames            # summarizeBothGroupFirst
           ;

join       : JOIN (THAT)?                                # emptyJoin
           | JOIN (THAT)? join_on                        # joinWithCondition
           ;

join_on    : ON column_word? logical_expression;
//list of logical expressions will be handled in nlp

input      : input_word FROM? file_ref  alias            #renamedFileInput
           | input_word FROM? file_ref                   #justFileInput
           | input_word FROM? db_ref alias               #renamedDbInput
           | input_word FROM? db_ref                     #justDbInput
           ;

output     : output_word ref_word TO? (file_ref | db_ref)
           ;

browse     : BROWSE (THROUGH | THRU)? THE? (RESULT | RESULTS)?
           | (SHOW | DISPLAY) ME? THE? (RESULT | RESULTS)?
           ;



//column_list : Cols+=var_ref (list_separator Cols+=var_ref)*
//            ;

expression_with_rename
            : expression alias                            #renamedExpr
            | expression                                  #justExpr
            ;

expression_list_with_renames
            : Exprs+=expression_with_rename (list_separator Exprs+=expression_with_rename)*
            ;

column_with_rename
            : var_ref alias                               #renamedVar
            | var_ref                                     #justVar
            ;

column_list_with_renames
            : Cols+=column_with_rename (list_separator Cols+=column_with_rename)*
            ;

alias       : (AS|TO)? new_name
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


except_word : EXCEPT
            | BUT
            | EXCLUDING
            | EXCLUDE
            ;

column_word : COLUMN
            | COLUMNS
            | COL
            | COLS
            | FIELD
            | FIELDS
            ;

row_word    : ROW
            | ROWS
            | LINE
            | LINES
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

input_word  : INPUT
            | LOAD
            ;

output_word : OUTPUT
            | STORE
            ;

db_word     : DB
            | DATABASE
            | ORACLE
            | SNOWFLAKE
            | POSTGRES
            | POSTGRESQL
            | MYSQL
            | MSSQL
            | MS_SQL
            | TABLE
            | VIEW
            ;

file_word   : FILE
            | EXCEL
            | CSV
            | YXDB
            ;

file_ref    : file_word win_path                                             # fileRefByPath
            | file_word DOUBLE_QUOTED_STRING                                 # fileRefByString
            ;

db_ref      : db_word var_ref                                                # dbRefByVar
            | db_word DOUBLE_QUOTED_STRING                                   # dbRefByString
            ;


win_path    : (Drive=win_drive? BACK)? (Path+=win_path_element BACK)* FileName=win_path_element DOT FileType=file_type
            ;

win_path_element : Base=ID ( Separators+=(DOT | MINUS) Ids+=ID )*
            ;

win_drive   : ID COLON
            ;

file_type   : CSV | YXDB | XLS | XLSX ;

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
           | DOLLARID                                            #dollarReference
           | VarRefs+=var_ref (DOT VarRefs+=var_ref)+            #recursiveVarReference
           ;

function_call : FunctionCategory=function_qualifier? Function=ID LPAR ExprList=expression_list? RPAR   #funcCall
           ;

function_qualifier : (file_word | db_word) DOT;

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



