parser grammar HelloParser;

options {
    tokenVocab = 'HelloLexer';
}

start  : (var_decl | var_assignment | print)* EOF;

expr :
    ID
  | literals
  | expr PLUS expr
  | expr MINUS expr;

print : PRINT LPAR expr RPAR SEMICOLON;

var_decl : VAR ID COLON type SEMICOLON;
var_assignment : ID EQ expr SEMICOLON;

type : INT | FLOAT;

literals : int_literal | float_literal;
int_literal : DIGITS;
float_literal : DIGITS DOT DIGITS;