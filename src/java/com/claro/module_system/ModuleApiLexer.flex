package com.claro;

import com.google.common.base.Strings;

import java_cup.runtime.Symbol;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Stack;

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

    // This will be used to accumulate all string characters during the STRING state.
    StringBuffer string = new StringBuffer();
    // Because we're going to support format strings over arbitrary expressions, that means we need to support
    // nested format strings within the
    Stack<AtomicReference<Integer>> fmtStrExprBracketCounterStack = new Stack<>();
    // Differing from CLaroLexer.flex for now there's no need disable escaping special chars for now.
    public boolean escapeSpecialChars = true;
    private void appendSpecialCharToString(StringBuffer s, String escapedSpecialChar, char specialChar) {
      if (escapeSpecialChars) {
        s.append(escapedSpecialChar);
      } else {
        s.append(specialChar);
      }
    }

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
%state STRING
%state IGNORE_REMAINING_CHARS_UNTIL_STMT_OR_PAIRED_BRACE_TERMINATOR

%%

// This section contains regular expressions and actions, i.e. Java code, that will be executed when
// the scanner matches the associated regular expression.


// YYINITIAL is the initial state at which the lexer begins scanning.
<YYINITIAL> {

    /* Create a new parser symbol for the lexem. */
    "("                { return symbol(Tokens.LPAR, 0, 1, "("); }
    ")"                { return symbol(Tokens.RPAR, 0, 1, ")"); }
    "{"                {
                         if (!fmtStrExprBracketCounterStack.isEmpty()) {
                           fmtStrExprBracketCounterStack.peek().updateAndGet(i -> i+1);
                         }
                         return symbol(Tokens.LCURLY, 0, 1, "{");
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
                             return symbol(Tokens.RCURLY, 0, 1, "}");
                           }
                         } else {
                           return symbol(Tokens.RCURLY, 0, 1, "}");
                         }
                       }
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
     "HttpService"     { return symbol(Tokens.HTTP_SERVICE, 0, 11, "HttpService"); }
     "HttpClient"      { return symbol(Tokens.HTTP_CLIENT, 0, 10, "HttpClient"); }
     "HttpServer"      { return symbol(Tokens.HTTP_SERVER, 0, 10, "HttpServer"); }

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

// A String is a sequence of any printable characters between quotes.
<STRING> {
    \"                 {
                          yybegin(YYINITIAL);
                          String matchedString = string.toString();
                          string.setLength(0);
                          addToLine("\"");
                          final StringBuilder currentInputLineBuilder = currentInputLine.get();
                          return new Symbol(Tokens.STRING, ++yycolumn - matchedString.length() - 2 , yyline, LexedValue.create(matchedString, () -> currentInputLineBuilder.toString(), matchedString.length() + 2));
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
                         return new Symbol(Tokens.FMT_STRING_PART, yycolumn, yyline, LexedValue.create(fmtStringPart, () -> currentInputLineBuilder.toString(), fmtStringPart.length() + 1));
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

// We have changed the default symbol in the bazel `cup()` rule from "sym" to "Tokens", so we need to
// change how JFlex handles the end of file.
// See http://jflex.de/manual.html#custom-symbol-interface
<<EOF>>                {
                         return symbol(Tokens.EOF);
                       }

/* Catch-all the rest, i.e. unknown character. */
[^]  { handleUnknownToken(); }

