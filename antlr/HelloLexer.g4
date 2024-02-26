lexer grammar HelloLexer;

INT : 'int';
FLOAT : 'float';
PRINT : 'print';

PLUS : '+';
MINUS : '-';
LPAR : '(';
RPAR : ')';
SEMICOLON : ';';
COLON : ':';
VAR : 'var';
EQ : '=';
DOT : '.';
DOUBLE_QUOTE : '"' -> pushMode(String);

ID : [a-z]+;
WS : [ \t\r\n]+ -> channel(HIDDEN);
COMMENT : '#' ~( '\n' | '\r' )* -> channel(HIDDEN);
DIGITS : [0-9]+;

/*******************************************************************************
* String MODE BEGIN
*******************************************************************************/
mode String;

TEXT: ~[\\"]+ ; // Anything but quote gets collected.
DOUBLE_QUOTE_IN_STRING: '"' -> type(DOUBLE_QUOTE), popMode;
/*******************************************************************************
* String MODE END
*******************************************************************************/
