parser grammar GraphsParser;

options {
    tokenVocab = 'GraphsLexer';
}

start  : (ANY* graph)+ EOF;

graph :
    GRAPH FUNC ID LPAR params RPAR ARROW future L_CURLY nodes+ R_CURLY;

params : (ID COLON any COMMA)* (ID COLON any);

future : FUTURE L_ANGLE any R_ANGLE;

nodes :
    (ROOT | NODE) ID L_ARROW NODE_REF_ID+ SEMICOLON
    // This is a leaf node with no other Node Refs.
  | (ROOT | NODE) ID L_ARROW SEMICOLON;

any : ID | ANY;