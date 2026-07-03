parser grammar VerboseExpressionParser;

options { tokenVocab=ByxLexer; }
import AlteryxExpressionParser;

verbose_left
    : TAKE? ONLY? Count=INTEGER (BEGINNING)? CHARACTERS OF Text=expression
    | BEGINNING Count=INTEGER (CHARACTERS)? OF Text=expression
    ;
verbose_right
    : TAKE? ONLY? Count=INTEGER LAST CHARACTERS OF Text=expression
    | LAST Count=INTEGER (CHARACTERS)? OF Text=expression
    ;

verbose_func
    : Function=ID OF Expression=expression
    ;



