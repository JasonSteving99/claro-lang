lexer grammar GraphsLexer;

ANY_OUTSIDE_GRAPH: .+? -> channel(HIDDEN);
GRAPH : 'graph' -> pushMode(Graph);

mode Graph;

DOUBLE_QUOTE : '"' -> pushMode(String);
FUNC : 'function';
PROV : 'provider';
CONS : 'consumer';
LPAR : '(';
RPAR : ')';
COLON : ':';
COMMA : ',';
SEMICOLON : ';';
ARROW : '->';
L_ANGLE : '<';
R_ANGLE : '>';
L_CURLY : '{';
R_CURLY : '}' -> popMode;
FUTURE : 'future';
NODE : 'node';
ROOT : 'root';
L_ARROW : '<-' -> pushMode(NodeBody);

ID : ([a-z]|[A-Z])([a-z]|[A-Z]|'_'|[0-9])*;
WS : [ \t\r\n]+ -> channel(HIDDEN);
COMMENT : '#' ~( '\n' | '\r' )* -> channel(HIDDEN);
ANY : .+?;

/*******************************************************************************
* String MODE BEGIN
*******************************************************************************/
mode String;

TEXT: ~[\\"]+ ; // Anything but quote gets collected.
DOUBLE_QUOTE_IN_STRING: '"' -> type(DOUBLE_QUOTE), popMode;
/*******************************************************************************
* String MODE END
*******************************************************************************/

mode NodeBody;

NODE_REF : '@' -> pushMode(NodeRef), channel(HIDDEN);
DOUBLE_QUOTE_IN_NODE_BODY : '"' -> pushMode(String), channel(HIDDEN);
COMMENT_IN_NODE_BODY : COMMENT -> channel(HIDDEN);
ANY_IN_NODE_BODY : ~[@;]+ -> type(ANY), channel(HIDDEN);
SEMICOLON_IN_NODE_BODY : ';' -> type(SEMICOLON), popMode;

mode NodeRef;

NODE_REF_ID : ID -> popMode ;