lexer grammar ByxLexer;

channels { COMMENT_CH, WHITESPACE_CH }
options {
caseInsensitive=true;
}

// Comment
COMMENT            : '//' ~( '\r' | '\n' )* -> channel(COMMENT_CH) ;
LONG_COMMENT       : '/*' .*? '*/' -> channel(COMMENT_CH) ;

// Whitespace
NEWLINE            : ('\r\n' | 'r' | '\n') -> channel(WHITESPACE_CH) ;
WS                 : [\t ]+ -> channel(WHITESPACE_CH) ;

// Keywords : operation verbs
SELECT             : 'select' ;
SUMMARIZE          : 'summarize' ;
FILTER             : 'filter' ;
JOIN               : 'join' ;
CREATE             : 'create' ;
INPUT              : 'input' ;
OUTPUT             : 'output' ;
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

// Keywords : helpers
COLUMN             : 'column' ;
COLUMNS            : 'columns' ;
COL                :  'col' ;
COLS               : 'cols';
ROW                : 'row' ;
ROWS               : 'rows' ;
RECORD             : 'record' ;
RECORDS            : 'records';
FORMULA            : 'formula';
NEW                : 'new';
AS                 : 'as' ;
ONLY               : 'only';
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

// Keywords : constants
TRUE               : 'true';
FALSE              : 'false';
TODAY              : 'today';

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


// Identifiers
ID                 : LETTER (LETTER|DIGIT)*
                   | UNDERSCORE+ (LETTER | DIGIT)*
                   ;
BRACKETID          : LBRACKET [a-z0-9 ._-]* RBRACKET ;

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

LETTER : [a-z$_] ;

UNMATCHED          : . ;
