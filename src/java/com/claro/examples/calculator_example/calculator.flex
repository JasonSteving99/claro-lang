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

package com.claro.examples.calculator_example;

import java_cup.runtime.Symbol;

/**
 * A simple lexer/parser for basic arithmetic expressions.
 *
 * @author Régis Décamps
 */

%%


%public
%class CalculatorLexer
// Use CUP compatibility mode to interface with a CUP parser.
%cup

%unicode

%{
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
Integer         = [0-9]+

// A line terminator is a \r (carriage return), \n (line feed), or \r\n. */
LineTerminator = \r|\n|\r\n

/* White space is a line terminator, space, tab, or line feed. */
WhiteSpace     = {LineTerminator} | [ \t\f]


%%

// This section contains regular expressions and actions, i.e. Java code, that will be executed when
// the scanner matches the associated regular expression.


// YYINITIAL is the initial state at which the lexer begins scanning.
<YYINITIAL> {

    /* Create a new parser symbol for the lexem. */
    "+"                { return symbol(Calc.PLUS); }
    "-"                { return symbol(Calc.MINUS); }
    "*"                { return symbol(Calc.MULTIPLY); }
    "^"                { return symbol(Calc.EXPONENTIATE); }
    "/"                { return symbol(Calc.DIVIDE); }
    "("                { return symbol(Calc.LPAR); }
    ")"                { return symbol(Calc.RPAR); }

    "log_"             { return symbol(Calc.LOG_PREFIX); }

    // If an integer is found, return the token INTEGER that represents an integer and the value of
    // the integer that is held in the string yytext
    {Integer}           { return symbol(Calc.INTEGER, Integer.parseInt(yytext())); }

    /* Don't do anything if whitespace is found */
    {WhiteSpace}       { /* do nothing with space */ }
}

// We have changed the default symbol in the bazel `cup()` rule from "sym" to "Calc", so we need to
// change how JFlex handles the end of file.
// See http://jflex.de/manual.html#custom-symbol-interface
<<EOF>>                { return symbol(Calc.EOF); }

/* Catch-all the rest, i.e. unknown character. */
[^]  { throw new CalculatorParserException("Illegal character <" + yytext() + ">"); }
