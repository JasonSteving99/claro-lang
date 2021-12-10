/*
 * Copyright (C) 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.claro;

import java_cup.runtime.Symbol;

/**
 * A simple lexer/parser for basic arithmetic expressions.
 *
 * @author Jason Steving, derived from a simpler example by Régis Décamps
 */

%%


%public
%class ClaroLexer
// Use CUP compatibility mode to interface with a CUP parser.
%cup

%unicode

%{
    // This will be used to accumulate all string characters during the STRING state.
    StringBuffer string = new StringBuffer();

    /** Creates a new {@link Symbol} of the given type. */
    private Symbol symbol(int type) {
        return new Symbol(type, yyline, yycolumn);
    }

    /** Creates a new {@link Symbol} of the given type and value. */
    private Symbol symbol(int type, Object value) {
        return new Symbol(type, yyline, yycolumn, value);
    }
%}

// A (integer) number is a sequence of digits.
Integer        = [0-9]+

// A (decimal) float is a real number with a decimal value.
Float          = {Integer}\.{Integer}

// A variable identifier. We'll just do uppercase for vars.
Identifier     = ([a-z]|[A-Z])([a-z]|[A-Z]|[0-9])*

// A line terminator is a \r (carriage return), \n (line feed), or \r\n. */
LineTerminator = \r|\n|\r\n

/* White space is a line terminator, space, tab, or line feed. */
WhiteSpace     = {LineTerminator} | [ \t\f]

%state LINECOMMENT
%state STRING

%%

// This section contains regular expressions and actions, i.e. Java code, that will be executed when
// the scanner matches the associated regular expression.


// YYINITIAL is the initial state at which the lexer begins scanning.
<YYINITIAL> {

    /* Create a new parser symbol for the lexem. */
    "+"                { return symbol(Calc.PLUS); }
    "++"               { return symbol(Calc.INCREMENT); }
    "--"               { return symbol(Calc.DECREMENT); }
    "-"                { return symbol(Calc.MINUS); }
    "*"                { return symbol(Calc.MULTIPLY); }
    "^"                { return symbol(Calc.EXPONENTIATE); }
    "/"                { return symbol(Calc.DIVIDE); }
    "("                { return symbol(Calc.LPAR); }
    ")"                { return symbol(Calc.RPAR); }
    "{"                { return symbol(Calc.LCURLY); }
    "}"                { return symbol(Calc.RCURLY); }
    "["                { return symbol(Calc.LBRACKET); }
    "]"                { return symbol(Calc.RBRACKET); }
    "=="               { return symbol(Calc.EQUALS); }
    "!="               { return symbol(Calc.NOT_EQUALS); }
    "<"                { return symbol(Calc.L_ANGLE_BRACKET); }
    ">"                { return symbol(Calc.R_ANGLE_BRACKET); }
    "<="               { return symbol(Calc.LTE); }
    ">="               { return symbol(Calc.GTE); }
    "or"               { return symbol(Calc.OR); }
    "and"              { return symbol(Calc.AND); }
    "not"              { return symbol(Calc.NOT); }
    "->"               { return symbol(Calc.ARROW); }
    "true"             { return symbol(Calc.TRUE); }
    "false"            { return symbol(Calc.FALSE); }
    "var"              { return symbol(Calc.VAR); }
    "="                { return symbol(Calc.ASSIGNMENT); }
    ";"                { return symbol(Calc.SEMICOLON); }
    ":"                { return symbol(Calc.COLON); }
    ","                { return symbol(Calc.COMMA); }
    "."                { return symbol(Calc.DOT); }
    "|"                { return symbol(Calc.BAR); }
    "if"               { return symbol(Calc.IF); }
    "else"             { return symbol(Calc.ELSE); }
    "while"            { return symbol(Calc.WHILE); }
    "return"           { return symbol(Calc.RETURN); }

    // Builtin functions are currently processed at the grammar level.. maybe there's a better generalized way.
    "log_"             { return symbol(Calc.LOG_PREFIX); }
    "print"            { return symbol(Calc.PRINT); }
    "numeric_bool"     { return symbol(Calc.NUMERIC_BOOL); }
    "input"            { return symbol(Calc.INPUT); }
    "len"              { return symbol(Calc.LEN); }
    "type"             { return symbol(Calc.TYPE); }

    // DEBUGGING keywords that should be removed when we want a real release...
    "$dumpscope"       { return symbol(Calc.DEBUG_DUMP_SCOPE); }

    // Builtin Types.
    "int"              { return symbol(Calc.INT_TYPE); }
    "float"            { return symbol(Calc.FLOAT_TYPE); }
    "boolean"          { return symbol(Calc.BOOLEAN_TYPE); }
    "string"           { return symbol(Calc.STRING_TYPE); }
    "tuple"            { return symbol(Calc.TUPLE_TYPE); }
    "struct"           { return symbol(Calc.STRUCT_TYPE); }
    "function"         { return symbol(Calc.FUNCTION_TYPE); }
    "consumer"         { return symbol(Calc.CONSUMER_FUNCTION_TYPE); }
    "provider"         { return symbol(Calc.PROVIDER_FUNCTION_TYPE); }

    // Builders are builtin at the language level.
    "builder"          { return symbol(Calc.BUILDER); }
    ".build()"            { return symbol(Calc.DOTBUILD); }

    // Modifiers go here.
    "immutable"         { return symbol(Calc.IMMUTABLE); }

    \"                 {
                         // There may have already been another string accumulated into this buffer.
                         // In that case we need to clear the buffer to start processing this.
                         string.setLength(0);
                         yybegin(STRING);
                       }

    // If the line comment symbol is found, ignore the token and then switch to the LINECOMMENT lexer state.
    "#"                { yybegin(LINECOMMENT); }

    // If an integer is found, return the token INTEGER that represents an integer and the value of
    // the integer that is held in the string yytext
    {Integer}          { return symbol(Calc.INTEGER, Integer.parseInt(yytext())); }

    // If float is found, return the token FLOAT that represents a float and the value of
    // the float that is held in the string yytext
    {Float}            { return symbol(Calc.FLOAT, Double.parseDouble(yytext())); }

    {Identifier}       { return symbol(Calc.IDENTIFIER, yytext()); }

    /* Don't do anything if whitespace is found */
    {WhiteSpace}       { /* do nothing with space */ }
}

// A comment that goes all the way from the symbol '#' to the end of the line or EOF.
<LINECOMMENT> {
    {LineTerminator}   { yybegin(YYINITIAL); }
    .                  { /* Ignore everything in the rest of the commented line. */ }
}

// A String is a sequence of any printable characters between quotes.
<STRING> {
    \"                 {
                          yybegin(YYINITIAL);
                          return symbol(Calc.STRING, string.toString());
                       }
    [^\n\r\"\\]+       { string.append( yytext() ); }
    \\t                { string.append('\t'); }
    \\n                { string.append('\n'); }

    \\r                { string.append('\r'); }
    \\\"               { string.append('\"'); }
    \\                 { string.append('\\'); }
}

// We have changed the default symbol in the bazel `cup()` rule from "sym" to "Calc", so we need to
// change how JFlex handles the end of file.
// See http://jflex.de/manual.html#custom-symbol-interface
<<EOF>>                { return symbol(Calc.EOF); }

/* Catch-all the rest, i.e. unknown character. */
[^]  { throw new ClaroParserException("Illegal character <" + yytext() + ">"); }
