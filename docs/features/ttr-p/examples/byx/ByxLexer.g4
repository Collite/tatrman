lexer grammar ByxLexer;

channels { COMMENT_CH, WHITESPACE_CH }
options {
caseInsensitive=true;
}

// Comment
COMMENT            : '//' ~( '\r' | '\n' )* -> channel(COMMENT_CH) ;
LONG_COMMENT       : '/*' .*? '*/' -> channel(COMMENT_CH) ;

// Whitespace
//NEWLINE            : ('\r\n' | '\r' | '\n') -> channel(WHITESPACE_CH) ;
NEWLINE            : ('\r\n' | '\r' | '\n') ;
WS                 : [\t ]+ -> channel(WHITESPACE_CH) ;

// Keywords : primary operation verbs
SELECT             : 'select' ;
SUMMARIZE          : 'summarize' ;
FILTER             : 'filter' ;
JOIN               : 'join' ;
CREATE             : 'create' ;
INPUT              : 'input' ;
OUTPUT             : 'output' ;
LOAD               : 'load';
STORE              : 'store';
RENAME             : 'rename';
CHANGE             : 'change';
RETYPE             : 'retype';
CONVERT            : 'convert';
COMPUTE            : 'compute';
CALCULATE          : 'calculate';
DELETE             : 'delete';
REMOVE             : 'remove';
KEEP               : 'keep';
TAKE               : 'take';
LOOKUP             : 'lookup';
INSERT             : 'insert';
APPEND             : 'append';
UNION              : 'union';
REPLACE            : 'replace';
BROWSE             : 'browse';
SHOW               : 'show';
DISPLAY            : 'display';
READ               : 'read';
WRITE              : 'write';
REWRITE            : 'rewrite';
SAVE               : 'save';
RETURN             : 'return';


// Keywords : helpers
COLUMN             : 'column' ;
COLUMNS            : 'columns' ;
COL                : 'col' ;
COLS               : 'cols';
FIELD              : 'field';
FIELDS             : 'fields';
ROW                : 'row' ;
ROWS               : 'rows' ;
LINE               : 'line';
LINES              : 'lines';
RECORD             : 'record' ;
RECORDS            : 'records';
FORMULA            : 'formula';
NEW                : 'new';
AS                 : 'as' ;
ONLY               : 'only';
ALL                : 'all';
SUMMARY            : 'summary';
GROUP              : 'group';
BY                 : 'by';
OF                 : 'of';
WHERE              : 'where' ;
FOR                : 'for';
BEGINNING          : 'beginning' ;
STARTING           : 'starting';
FIRST              : 'first' ;
LEFT               : 'left' ;
ENDING             : 'ending' ;
LAST               : 'last' ;
RIGHT              : 'right' ;
CHARACTERS         : 'characters';
CHARS              : 'chars' ;
CHARACTER          : 'character';
CHAR               : 'char' ;
IS                 : 'is' ;
ARE                : 'are' ;
EXCEPT             : 'except';
BUT                : 'but';
EXCLUDE            : 'exclude';
EXCLUDING          : 'excluding';
THAN               : 'than' ;
HAVE               : 'have' ;
HAS                : 'has' ;
TO                 : 'to';
COMES              : 'comes';
COME               : 'come';
THE                : 'the';
NAME               : 'name';
TYPE               : 'type';
THOSE              : 'those';
THIS               : 'this' ;
THESE              : 'these';
THAT               : 'that';
WHICH              : 'which';
WITH               : 'with';
IT                 : 'it';
ON                 : 'on';
FROM               : 'from';
RESULT             : 'result';
RESULTS            : 'results';
THROUGH            : 'through';
THRU               : 'thru';
ME                 : 'me';

//Keywords: Types
T_STRING           : 'string';
T_TEXT             : 'text';
T_NUMBER           : 'number' ;
T_DECIMAL          : 'decimal';
T_FLOAT            : 'float';
T_DOUBLE           : 'double';
T_INT              : 'int' ;
T_INTEGER          : 'integer';
T_DATE             : 'date';
T_TIME             : 'time';
T_DATETIME         : 'datetime';

// Keywords : functions
// NO ! Functions will be recognized separately, including parameters and type system
W_AND              : 'and';
W_OR               : 'or';
W_NOT              : 'not';
EXTRACT            : 'extract';

// Keywords : constants
TRUE               : 'true';
FALSE              : 'false';

IN : 'in' ;
IF : 'if' ;
THEN : 'then' ;
ELSEIF : 'elseif' ;
ELSE : 'else' ;
ENDIF : 'endif' ;

// Operators
PLUS               : '+' ;
MINUS              : '-' ;
TIMES              : '*' ;
DIV                : '/' ;
EQ                 : '=' ;
ASSIGN             : ':=';
COLON              : ':' ;
LPAR               : '(' ;
RPAR               : ')' ;
LBRACKET           : '[' ;
RBRACKET           : ']' ;
ARROW              : '->' ;

DOT : '.';
ELLIPSIS : '...';
COMMA : ',';
SEMICOLON : ';';
POWER : '**';
OR : '|';
BIG_OR: '||';
XOR : '^';
AND: '&';
BIG_AND: '&&';
LEFT_SHIFT : '<<';
RIGHT_SHIFT : '>>';
MOD : '%';
IDIV : '//';
NOT : '!';
TILDE : '~';
LBRACE : '{' ;
RBRACE : '}' ;
LT : '<';
GT : '>';
DOUBLE_EQ : '==';
GE : '>=';
LE : '<=';
NEQ: '<>';
NOT_EQ : '!=';
AT : '@';
UNDERSCORE : '_';
DOLLAR : '$' ;
BACK : '\\';

//word comparisons
W_EQUAL     : 'equal' ;
W_EQUALS    : 'equals' ;
W_SAME      : 'same';
W_DURING    : 'during' ;
W_PRECISELY : 'precisely' ;
W_LESS      : 'less';
W_FEWER     : 'fewer';
W_SMALLER   : 'smaller';
W_BEFORE    : 'before';
W_UP_TO     : 'up to';
W_GREATER   : 'greater';
W_BIGGER    : 'bigger';
W_HIGHER    : 'higher';
W_MORE      : 'more';
W_LARGER    : 'larger';
W_AFTER     : 'after' ;
W_LOWER     : 'lower';
W_SINCE     : 'since';
W_OLDER     : 'older';
W_YOUNGER   : 'younger';
W_BETWEEN   : 'between';
W_WITHIN    : 'within';


// Files and DBs
FILE        : 'file';
EXCEL       : 'excel';
ALTERYX     : 'alteryx';
CSV         : 'csv';
YXDB        : 'yxdb';
XLSX        : 'xlsx';
XLS         : 'xls';

DB          : 'db';
DATABASE    : 'database';
TABLE       : 'table';
VIEW        : 'view';
ORACLE      : 'oracle';
SNOWFLAKE   : 'snowflake';
POSTGRES    : 'postgres';
POSTGRESQL  : 'postgresql';
MYSQL       : 'mysql';
MSSQL       : 'mssql';
MS_SQL      : 'ms sql';


// Identifiers
ID                 : LETTER (LETTER|DIGIT)*
                   | UNDERSCORE+ (LETTER | DIGIT)*
                   ;

BRACKETID          : LBRACKET [a-z0-9 ._-]* RBRACKET ;

DOLLARID           : DOLLAR INTEGER ;

// Literals

INTEGER : DIGIT+ ;

DECIMAL
   : ( INTEGER DOT | INTEGER DOT INTEGER | DOT INTEGER ) EXPONENT_NUM_PART?
   | INTEGER EXPONENT_NUM_PART
   ;

EXPONENT_NUM_PART
   : [e] [-+]? INTEGER
   ;

fragment ESCAPED_DOUBLE_QUOTE : '\\"';
DOUBLE_QUOTED_STRING :   '"' ( ESCAPED_DOUBLE_QUOTE | ~('\n'|'\r') )*? '"';
fragment ESCAPED_SINGLE_QUOTE : '\\\'';
SINGLE_QUOTED_STRING :   '\'' ( ESCAPED_SINGLE_QUOTE | ~('\n'|'\r') )*? '\'';



DIGIT : [0-9] ;

SINGLE_QUOTE : '\'' ;
DOUBLE_QUOTE : '"' ;

LETTER : [a-z_] ;

UNMATCHED          : . ;
