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
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

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
    // Because we're going to support format strings over arbitrary expressions, that means we need to support
    // nested format strings within the
    Stack<AtomicReference<Integer>> fmtStrExprBracketCounterStack = new Stack<>();

    // Until I find a more efficient way to do this, let's bring the entire file contents into memory in order to give
    // useful error messages that can point at the line. We'll only need to track one at a time because we'll hand off
    // the entire string builder reference to the parser when we're passing off symbols, so we won't reuse the same
    // instance across lines, we'll instantiate and reference a new one. This is our way of doling out lines to only
    // consumers that need that line.
    AtomicReference<StringBuilder> currentInputLine = new AtomicReference<>(new StringBuilder());
    private void addToLine(Object input) {
      currentInputLine.get().append(input);
    }

    /** Creates a new {@link Symbol} of the given type. */
    private Symbol symbol(int type) {
        return new Symbol(type, yycolumn, yyline);
    }

    /** Creates a new {@link Symbol} of the given type and value. */
    private <T> Symbol symbol(int type, int lines, int columns, T value) {
        addToLine(value);
        final StringBuilder currentInputLineBuilder = currentInputLine.get();
        Symbol res = new Symbol(type, yycolumn, yyline, new LexedValue<T>(value, () -> currentInputLineBuilder.toString(), columns));
        yyline += lines;
        yycolumn += columns;
        return res;
    }

    public boolean escapeSpecialChars = false;
    private void appendSpecialCharToString(StringBuffer s, String escapedSpecialChar, char specialChar) {
      if (escapeSpecialChars) {
        s.append(escapedSpecialChar);
      } else {
        s.append(specialChar);
      }
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
WhiteSpace     = [ \t\f]

%state LINECOMMENT
%state STRING
%state IGNORE_REMAINING_CHARS_UNTIL_STMT_OR_PAIRED_BRACE_TERMINATOR

%%

// This section contains regular expressions and actions, i.e. Java code, that will be executed when
// the scanner matches the associated regular expression.


// YYINITIAL is the initial state at which the lexer begins scanning.
<YYINITIAL> {

    /* Create a new parser symbol for the lexem. */
    "+"                { return symbol(Calc.PLUS, 0, 1, '+'); }
    "++"               { return symbol(Calc.INCREMENT, 0, 2, "++"); }
    "--"               { return symbol(Calc.DECREMENT, 0, 2, "--"); }
    "-"                { return symbol(Calc.MINUS, 0, 1, "-"); }
    "*"                { return symbol(Calc.MULTIPLY, 0, 1, "*"); }
    "**"                { return symbol(Calc.EXPONENTIATE, 0, 2, "**"); }
    "/"                { return symbol(Calc.DIVIDE, 0, 1, "/");}
    "("                { return symbol(Calc.LPAR, 0, 1, "("); }
    ")"                { return symbol(Calc.RPAR, 0, 1, ")"); }
    "{"                {
                         if (!fmtStrExprBracketCounterStack.isEmpty()) {
                           fmtStrExprBracketCounterStack.peek().updateAndGet(i -> i+1);
                         }
                         return symbol(Calc.LCURLY, 0, 1, "{");
                       }
    "}"                {
                         if (!fmtStrExprBracketCounterStack.isEmpty()) {
                           if (fmtStrExprBracketCounterStack.peek().get() == 0) {
                             // Pop the stack because we just finished lexing the current fmt str expr.
                             fmtStrExprBracketCounterStack.pop();
                             // Resume the string lexing state since all we've done is finish grabbing a single format expr.
                             yybegin(STRING);
                             yycolumn++;
                             addToLine("}");
                           } else {
                             fmtStrExprBracketCounterStack.peek().updateAndGet(i -> i-1);
                             return symbol(Calc.RCURLY, 0, 1, "}");
                           }
                         } else {
                           return symbol(Calc.RCURLY, 0, 1, "}");
                         }
                       }
    "["                { return symbol(Calc.LBRACKET, 0, 1, "["); }
    "]"                { return symbol(Calc.RBRACKET, 0, 1, "]"); }
    "=="               { return symbol(Calc.EQUALS, 0, 2, "=="); }
    "!="               { return symbol(Calc.NOT_EQUALS, 0, 2, "!="); }
    "<"                { return symbol(Calc.L_ANGLE_BRACKET, 0, 1, "<"); }
    ">"                { return symbol(Calc.R_ANGLE_BRACKET, 0, 1, ">"); }
    "<="               { return symbol(Calc.LTE, 0, 2, "<="); }
    ">="               { return symbol(Calc.GTE, 0, 2, ">="); }
    "or"               { return symbol(Calc.OR, 0, 2, "or"); }
    "and"              { return symbol(Calc.AND, 0, 3, "and"); }
    "not"              { return symbol(Calc.NOT, 0, 3, "not"); }
    "->"               { return symbol(Calc.ARROW, 0, 2, "->"); }
    "|>"               { return symbol(Calc.PIPE_ARROW, 0, 2, "|>"); }
    "true"             { return symbol(Calc.TRUE, 0, 4, true); }
    "false"            { return symbol(Calc.FALSE, 0, 5, false); }
    "var"              { return symbol(Calc.VAR, 0, 3, "var"); }
    "="                { return symbol(Calc.ASSIGNMENT, 0, 1, "="); }
    ";"                { return symbol(Calc.SEMICOLON, 0, 1, ";"); }
    ":"                { return symbol(Calc.COLON, 0, 1, ":"); }
    ","                { return symbol(Calc.COMMA, 0, 1, ','); }
    "."                { return symbol(Calc.DOT, 0, 1, "."); }
    "|"                { return symbol(Calc.BAR, 0, 1, "|"); }
    "if"               { return symbol(Calc.IF, 0, 2, "if"); }
    "else"             { return symbol(Calc.ELSE, 0, 4, "else"); }
    "while"            { return symbol(Calc.WHILE, 0, 5, "while"); }
    "return"           { return symbol(Calc.RETURN, 0, 6, "return"); }

    // Builtin functions are currently processed at the grammar level.. maybe there's a better generalized way.
    "log_"             { return symbol(Calc.LOG_PREFIX, 0, 4, "log_"); }
    "print"            { return symbol(Calc.PRINT, 0, 5, "print"); }
    "numeric_bool"     { return symbol(Calc.NUMERIC_BOOL, 0, 12, "numeric_bool"); }
    "input"            { return symbol(Calc.INPUT, 0, 5, "input"); }
    "len"              { return symbol(Calc.LEN, 0, 3, "len"); }
    "type"             { return symbol(Calc.TYPE, 0, 4, "type"); }
    "append"           { return symbol(Calc.APPEND, 0, 6, "append"); }

    // DEBUGGING keywords that should be removed when we want a real release...
    "$dumpscope"       { return symbol(Calc.DEBUG_DUMP_SCOPE, 0, 10, "$dumpscope"); }

    // Builtin Types.
    "int"              { return symbol(Calc.INT_TYPE, 0, 3, "int"); }
    "float"            { return symbol(Calc.FLOAT_TYPE, 0, 5, "float"); }
    "boolean"          { return symbol(Calc.BOOLEAN_TYPE, 0, 7, "boolean"); }
    "string"           { return symbol(Calc.STRING_TYPE, 0, 6, "string"); }
    "tuple"            { return symbol(Calc.TUPLE_TYPE, 0, 5, "tuple"); }
    "struct"           { return symbol(Calc.STRUCT_TYPE, 0, 6, "struct"); }
    "function"         { return symbol(Calc.FUNCTION_TYPE, 0, 8, "function"); }
    "consumer"         { return symbol(Calc.CONSUMER_FUNCTION_TYPE, 0, 8, "consumer"); }
    "provider"         { return symbol(Calc.PROVIDER_FUNCTION_TYPE, 0, 8, "provider"); }
    "lambda"           { return symbol(Calc.LAMBDA, 0, 6, "lambda"); }
    "alias"            { return symbol(Calc.ALIAS, 0, 5, "alias"); }

    // Builders are builtin at the language level.
    "builder"          { return symbol(Calc.BUILDER, 0, 7, "builder"); }
    ".build()"         { return symbol(Calc.DOTBUILD, 0, 8, ".build()"); }

    // Modifiers go here.
    "immutable"        { return symbol(Calc.IMMUTABLE, 0, 9, "immutable"); }

    // Module related bindings go here.
    "module"           { return symbol(Calc.MODULE, 0, 6, "module"); }
    "bind"             { return symbol(Calc.BIND, 0, 4, "bind"); }
    "to"               { return symbol(Calc.TO, 0, 2, "to"); }
    "as"               { return symbol(Calc.AS, 0, 2, "as"); }
    "using"            { return symbol(Calc.USING, 0, 5, "using"); }

    // Graph related things go here.
    "future"           { return symbol(Calc.FUTURE, 0, 4, "future"); }
    "graph"            { return symbol(Calc.GRAPH, 0, 4, "graph"); }
    "root"             { return symbol(Calc.ROOT, 0, 4, "root"); }
    "node"             { return symbol(Calc.NODE, 0, 4, "node"); }
    "blocking"         { return symbol(Calc.BLOCKING, 0, 8, "blocking"); }
    "blocking?"        { return symbol(Calc.MAYBE_BLOCKING, 0, 9, "blocking?"); }
    "<-"               { return symbol(Calc.LEFT_ARROW, 0, 2, "<-"); }
    "@"                { return symbol(Calc.AT, 0, 1, "@"); }
    "<-|"              { return symbol(Calc.BLOCKING_GET, 0, 3, "<-|"); }

    // This up arrow is used for the pipe chain backreference term.
    "^"                { return symbol(Calc.UP_ARROW, 0, 1, "^"); }

    // Contract tokens.
    "contract"         { return symbol(Calc.CONTRACT, 0, 8, "contract"); }
    "implement"        { return symbol(Calc.IMPLEMENT, 0, 9, "implement"); }
    "requires"         { return symbol(Calc.REQUIRES, 0, 8, "requires"); }
    "::"               { return symbol(Calc.COLON_COLON, 0, 2, "::"); }

    "_"                { return symbol(Calc.UNDERSCORE, 0, 1, "_"); }

    \"                 {
                         // There may have already been another string accumulated into this buffer.
                         // In that case we need to clear the buffer to start processing this.
                         string.setLength(0);
                         yycolumn++;
                         addToLine("\"");
                         yybegin(STRING);
                       }

    // If the line comment symbol is found, ignore the token and then switch to the LINECOMMENT lexer state.
    "#"                { yycolumn++; addToLine("#"); yybegin(LINECOMMENT); }

    // If an integer is found, return the token INTEGER that represents an integer and the value of
    // the integer that is held in the string yytext
    {Integer}          {
                         String lexed = yytext();
                         return symbol(Calc.INTEGER, 0, lexed.length(), Integer.parseInt(lexed));
                       }

    // If float is found, return the token FLOAT that represents a float and the value of
    // the float that is held in the string yytext
    {Float}            {
                         String lexed = yytext();
                         return symbol(Calc.FLOAT, 0, lexed.length(), Double.parseDouble(lexed));
                       }

    {Identifier}       {
                         String lexed = yytext();
                         return symbol(Calc.IDENTIFIER, 0, lexed.length(), lexed);
                       }

    /* Don't do anything if whitespace is found */
    {WhiteSpace}       {
                         String parsed = yytext();
                         yycolumn += parsed.length();
                         addToLine(parsed);
                       }

    {LineTerminator}   { yyline++; yycolumn = 0; addToLine(yytext()); currentInputLine.set(new StringBuilder()); }
}

// A comment that goes all the way from the symbol '#' to the end of the line or EOF.
<LINECOMMENT> {
    {LineTerminator}   { yybegin(YYINITIAL); yyline++; yycolumn = 0; addToLine(yytext()); currentInputLine.set(new StringBuilder()); }
    .                  { addToLine(yytext()); /* Ignore everything in the rest of the commented line. */ }
}

// A String is a sequence of any printable characters between quotes.
<STRING> {
    \"                 {
                          yybegin(YYINITIAL);
                          String matchedString = string.toString();
                          string.setLength(0);
                          addToLine("\"");
                          final StringBuilder currentInputLineBuilder = currentInputLine.get();
                          return new Symbol(Calc.STRING, ++yycolumn - matchedString.length() - 2 , yyline, new LexedValue(matchedString, () -> currentInputLineBuilder.toString(), matchedString.length() + 2));
                       }
    [^\n\r\"\\{]+      {
                         String parsed = yytext();
                         yycolumn += parsed.length();
                         string.append(parsed);
                         addToLine(parsed);
                       }
    \\t                { appendSpecialCharToString(string, "\\t", '\t'); yycolumn += 2; addToLine(yytext()); }
    \\n                { appendSpecialCharToString(string, "\\n", '\n'); yycolumn += 2; addToLine(yytext()); }

    \\r                { appendSpecialCharToString(string, "\\r", '\r'); yycolumn += 2; addToLine(yytext()); }
    \\\"               { appendSpecialCharToString(string, "\\\"", '\"'); yycolumn += 2; addToLine(yytext());}
    \\                 { appendSpecialCharToString(string, "\\", '\\'); yycolumn += 2; addToLine(yytext()); }
    \\\{               { string.append('{'); yycolumn += 2; addToLine(yytext()); }
    "{"                {
                         // We're now going to start lexing a (possibly nested) fmt str expr, so we push a 0 bracket
                         // count onto the stack so that we can start tracking when we are done lexing the expr.
                         fmtStrExprBracketCounterStack.push(new AtomicReference<>(0));
                         // We now are going to start lexing for an arbitrary expr, which means that we need to move
                         // JFlex back to the base lexing state. Lexing will return to this STRING state once the
                         // closing '}' is found.
                         yybegin(YYINITIAL);
                         // Turns out that whatever we've currently seen so far was actually a FMT_STRING_PART rather
                         // than a STRING, so we need to consume the StringBuilder and return the FMT_STRING_PART.
                         String fmtStringPart = string.toString();
                         string.setLength(0);
                         yycolumn++;
                         addToLine("{");
                         final StringBuilder currentInputLineBuilder = currentInputLine.get();
                         return new Symbol(Calc.FMT_STRING_PART, yycolumn, yyline, new LexedValue<String>(fmtStringPart, () -> currentInputLineBuilder.toString(), fmtStringPart.length() + 1));
                       }
}

// This lexing state is literally just a mimimum-effort approach to trying to give more useful errors. Instead of just
// stopping lexing early or simply ignoring the illegal char, we'll try to consume the rest of the current statement
// or paired braces in order to give more useful errors on other statements as soon as possible.
<IGNORE_REMAINING_CHARS_UNTIL_STMT_OR_PAIRED_BRACE_TERMINATOR> {
    ";"                { yycolumn++; yybegin(YYINITIAL); addToLine(";"); }
    "}"                { yycolumn++; yybegin(YYINITIAL); addToLine("}"); }
    ")"                { yycolumn++; yybegin(YYINITIAL); addToLine(")"); }
    {LineTerminator}   { yyline++; yycolumn = 0; addToLine(yytext()); currentInputLine.set(new StringBuilder()); }
    [^]                { yycolumn++; addToLine(yytext()); /* Swallow all other chars. */ }
}

// We have changed the default symbol in the bazel `cup()` rule from "sym" to "Calc", so we need to
// change how JFlex handles the end of file.
// See http://jflex.de/manual.html#custom-symbol-interface
<<EOF>>                {
                         if (string.length() > 0) {
                           string.setLength(0);
                           ClaroParser.errorMessages.push(
                               () ->
                                 System.err.println(
                                    String.format(
                                        fmtStrExprBracketCounterStack.isEmpty()
                                        ? "Error:%s: Reached EOF while parsing string literal, you're probably missing a closing quote."
                                        : "Error:%s: Reached EOF while parsing format string expression literal, you're probably missing a closing brace.",
                                        yyline
                                    )
                                 )
                           );
                         }
                         return symbol(Calc.EOF);
                       }

/* Catch-all the rest, i.e. unknown character. */
[^]  {
        yybegin(IGNORE_REMAINING_CHARS_UNTIL_STMT_OR_PAIRED_BRACE_TERMINATOR);
        String lexed = yytext();
        addToLine(lexed);
        ClaroParser.errorMessages.push(
            () ->
              System.err.println(
                  "Illegal character <" + lexed + "> found at line: " + (yyline + 1) + " column: " + ++yycolumn));
     }

