package com.claro;

import com.google.common.base.Strings;

import java_cup.runtime.Symbol;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A simple lexer/parser for basic arithmetic expressions.
 *
 * @author Jason Steving, derived from a simpler example by Régis Décamps
 */

%%


%public
%class ModuleApiLexer
// Use CUP compatibility mode to interface with a CUP parser.
%cup

%unicode

%{
    // Use this for more precise error messaging.
    public String moduleFilename = "module";  // default to be overridden.

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
        Symbol res = new Symbol(type, yycolumn, yyline, LexedValue.create(value, () -> currentInputLineBuilder.toString(), columns));
        yyline += lines;
        yycolumn += columns;
        return res;
    }

    private void handleUnknownToken() {
        String lexed = yytext();
        addToLine(lexed);
        int line = yyline + 1;
        int column = yycolumn;
        yycolumn += lexed.length();
        StringBuilder lineToPointAt = currentInputLine.get();
        ModuleApiParser.errorMessages.push(
            () -> {
              String currStringLineToPointAt = lineToPointAt.toString();
              int trailingWhitespaceStart = currStringLineToPointAt.length();
              // This is just cute for the sake of it....barf...but I'm keeping it lol.
              while (Character.isWhitespace(currStringLineToPointAt.charAt(--trailingWhitespaceStart)));
              System.err.println(
                  moduleFilename + ".claro_module_api:" + line + ": Unexpected token <" + lexed.charAt(0) + ">\n" + currStringLineToPointAt.substring(0, trailingWhitespaceStart + 1));
              System.err.println(Strings.repeat(" ", column) + '^');
            }
        );
        yybegin(IGNORE_REMAINING_CHARS_UNTIL_STMT_OR_PAIRED_BRACE_TERMINATOR);
    }
%}

// A variable identifier. We'll just do uppercase for vars.
Identifier     = ([a-z]|[A-Z])([a-z]|[A-Z]|_|[0-9])*

// A line terminator is a \r (carriage return), \n (line feed), or \r\n. */
LineTerminator = \r|\n|\r\n

/* White space is a line terminator, space, tab, or line feed. */
WhiteSpace     = [ \t\f]

%state LINECOMMENT
%state IGNORE_REMAINING_CHARS_UNTIL_STMT_OR_PAIRED_BRACE_TERMINATOR

%%

// This section contains regular expressions and actions, i.e. Java code, that will be executed when
// the scanner matches the associated regular expression.


// YYINITIAL is the initial state at which the lexer begins scanning.
<YYINITIAL> {

    /* Create a new parser symbol for the lexem. */
    "("                { return symbol(Tokens.LPAR, 0, 1, "("); }
    ")"                { return symbol(Tokens.RPAR, 0, 1, ")"); }
    "{"                { return symbol(Tokens.LCURLY, 0, 1, "{"); }
    "}"                { return symbol(Tokens.RCURLY, 0, 1, "}"); }
    "["                { return symbol(Tokens.LBRACKET, 0, 1, "["); }
    "]"                { return symbol(Tokens.RBRACKET, 0, 1, "]"); }
    "<"                { return symbol(Tokens.L_ANGLE_BRACKET, 0, 1, "<"); }
    ">"                { return symbol(Tokens.R_ANGLE_BRACKET, 0, 1, ">"); }
    "->"               { return symbol(Tokens.ARROW, 0, 2, "->"); }
    ";"                { return symbol(Tokens.SEMICOLON, 0, 1, ";"); }
    ":"                { return symbol(Tokens.COLON, 0, 1, ":"); }
    ","                { return symbol(Tokens.COMMA, 0, 1, ','); }
    "|"                { return symbol(Tokens.BAR, 0, 1, "|"); }

    // Builtin Types.
    "int"              { return symbol(Tokens.INT_TYPE, 0, 3, "int"); }
    "float"            { return symbol(Tokens.FLOAT_TYPE, 0, 5, "float"); }
    "boolean"          { return symbol(Tokens.BOOLEAN_TYPE, 0, 7, "boolean"); }
    "string"           { return symbol(Tokens.STRING_TYPE, 0, 6, "string"); }
    "tuple"            { return symbol(Tokens.TUPLE_TYPE, 0, 5, "tuple"); }
    "oneof"            { return symbol(Tokens.ONEOF, 0, 5, "oneof"); }
    "struct"           { return symbol(Tokens.STRUCT_TYPE, 0, 6, "struct"); }
    "function"         { return symbol(Tokens.FUNCTION_TYPE, 0, 8, "function"); }
    "consumer"         { return symbol(Tokens.CONSUMER_FUNCTION_TYPE, 0, 8, "consumer"); }
    "provider"         { return symbol(Tokens.PROVIDER_FUNCTION_TYPE, 0, 8, "provider"); }

    "alias"            { return symbol(Tokens.ALIAS, 0, 5, "alias"); }
    "newtype"          { return symbol(Tokens.NEWTYPE, 0, 7, "newtype"); }
    "initializers"     { return symbol(Tokens.INITIALIZERS, 0, 12, "initializers"); }
    "unwrappers"       { return symbol(Tokens.UNWRAPPERS, 0, 10, "unwrappers"); }

    // Modifiers go here.
    "mut"              { return symbol(Tokens.MUT, 0, 3, "mut"); }

    // Graph related things go here.
    "future"           { return symbol(Tokens.FUTURE, 0, 6, "future"); }

    "blocking"         { return symbol(Tokens.BLOCKING, 0, 8, "blocking"); }
    "blocking?"        { return symbol(Tokens.MAYBE_BLOCKING, 0, 9, "blocking?"); }

     // Symbols related to builtin HTTP support go here.
     "HttpClient"      { return symbol(Tokens.HTTP_CLIENT, 0, 10, "HttpClient"); }
     "HttpServer"      { return symbol(Tokens.HTTP_SERVER, 0, 10, "HttpServer"); }

    // If the line comment symbol is found, ignore the token and then switch to the LINECOMMENT lexer state.
    "#"                { yycolumn++; addToLine("#"); yybegin(LINECOMMENT); }

    {Identifier}       {
                         String lexed = yytext();
                         return symbol(Tokens.IDENTIFIER, 0, lexed.length(), lexed);
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

// We have changed the default symbol in the bazel `cup()` rule from "sym" to "Tokens", so we need to
// change how JFlex handles the end of file.
// See http://jflex.de/manual.html#custom-symbol-interface
<<EOF>>                {
                         return symbol(Tokens.EOF);
                       }

/* Catch-all the rest, i.e. unknown character. */
[^]  { handleUnknownToken(); }

