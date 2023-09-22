package com.claro;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.stdlib.StdLibModuleUtil;
import com.google.common.base.Strings;

import java_cup.runtime.Symbol;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Lexer for the fully fledged Claro programming language.
 *
 * @author Jason Steving
 */

%%


%public
%class ClaroLexer
// Use CUP compatibility mode to interface with a CUP parser.
%cup

%unicode

%{
    // Use this for more precise error messaging.
    public String generatedClassName = "CompiledClaroProgram";  // default to be overridden.

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
        Symbol res = new Symbol(type, yycolumn, yyline, LexedValue.create(value, () -> currentInputLineBuilder.toString(), columns));
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

    // This will be used to signal whether or not the PRIVILEGED_INLINE_JAVA token should actually be supported or not.
    // This allows me to create a mechanism that will only enable this unsafe feature for special .claro_internal files
    // that are used to implement the stdlib.
    public boolean supportPrivilegedInlineJava = false;

    private void handleUnknownToken() {
        String lexed = yytext();
        addToLine(lexed);
        int line = yyline + 1;
        int column = yycolumn;
        yycolumn += lexed.length();
        StringBuilder lineToPointAt = currentInputLine.get();
        ClaroParser.errorMessages.push(
            () -> {
              String currStringLineToPointAt = lineToPointAt.toString();
              int trailingWhitespaceStart = currStringLineToPointAt.length();
              // This is just cute for the sake of it....barf...but I'm keeping it lol.
              while (Character.isWhitespace(currStringLineToPointAt.charAt(--trailingWhitespaceStart)));
              System.err.println(
                  generatedClassName + ".claro:" + line + ": Unexpected token <" + lexed.charAt(0) + ">\n" + currStringLineToPointAt.substring(0, trailingWhitespaceStart + 1));
              System.err.println(Strings.repeat(" ", column) + '^');
            }
        );
        yybegin(IGNORE_REMAINING_CHARS_UNTIL_STMT_OR_PAIRED_BRACE_TERMINATOR);
    }
%}

// A (integer) number is a sequence of digits.
Integer        = [0-9]+

// A (decimal) float is a real number with a decimal value.
Float          = {Integer}\.{Integer}

// A variable identifier. We'll just do uppercase for vars.
Identifier     = ([a-z]|[A-Z])([a-z]|[A-Z]|_|[0-9])*

// An identifier found within an explicitly defined scope. This is explicitly defined in order to avoid running into a
// situation where the parser's single token lookahead would be insufficient to distinguish between various uses of
// scoped symbols e.g. `std::Error(std::Nothing)` parsed incorrectly as a syntax error before this change.
ScopedIdentifier = {Identifier}::{Identifier}

// An identifier that explicitly references a contract procedure from some contract defined in a dep module. Follows the
// scoping as follows:
//    <dep module name>::<contract name>::<procedure name>
DepModuleContractProcedure = {Identifier}::{Identifier}::{Identifier}

// A line terminator is a \r (carriage return), \n (line feed), or \r\n. */
LineTerminator = \r|\n|\r\n

/* White space is a line terminator, space, tab, or line feed. */
WhiteSpace     = [ \t\f]

PrivilegedInlineJava = [^]*\$\$END_JAVA

%state LINECOMMENT
%state STRING
%state PRIVILEGED_INLINE_JAVA
%state IGNORE_REMAINING_CHARS_UNTIL_STMT_OR_PAIRED_BRACE_TERMINATOR

%%

// This section contains regular expressions and actions, i.e. Java code, that will be executed when
// the scanner matches the associated regular expression.


// YYINITIAL is the initial state at which the lexer begins scanning.
<YYINITIAL> {

    /* Create a new parser symbol for the lexem. */
    "+"                { return symbol(Tokens.PLUS, 0, 1, '+'); }
    "++"               { return symbol(Tokens.INCREMENT, 0, 2, "++"); }
    "--"               { return symbol(Tokens.DECREMENT, 0, 2, "--"); }
    "-"                { return symbol(Tokens.MINUS, 0, 1, "-"); }
    "*"                { return symbol(Tokens.MULTIPLY, 0, 1, "*"); }
    "**"                { return symbol(Tokens.EXPONENTIATE, 0, 2, "**"); }
    "/"                { return symbol(Tokens.DIVIDE, 0, 1, "/");}
    "%"                { return symbol(Tokens.MODULUS, 0, 1, "%");}
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
    "=="               { return symbol(Tokens.EQUALS, 0, 2, "=="); }
    "!="               { return symbol(Tokens.NOT_EQUALS, 0, 2, "!="); }
    "<"                { return symbol(Tokens.L_ANGLE_BRACKET, 0, 1, "<"); }
    ">"                { return symbol(Tokens.R_ANGLE_BRACKET, 0, 1, ">"); }
    "<="               { return symbol(Tokens.LTE, 0, 2, "<="); }
    ">="               { return symbol(Tokens.GTE, 0, 2, ">="); }
    "or"               { return symbol(Tokens.OR, 0, 2, "or"); }
    "and"              { return symbol(Tokens.AND, 0, 3, "and"); }
    "not"              { return symbol(Tokens.NOT, 0, 3, "not"); }
    "->"               { return symbol(Tokens.ARROW, 0, 2, "->"); }
    "|>"               { return symbol(Tokens.PIPE_ARROW, 0, 2, "|>"); }
    "=>"               { return symbol(Tokens.IMPLICATION_ARROW, 0, 2, "=>"); }
    "true"             { return symbol(Tokens.TRUE, 0, 4, true); }
    "false"            { return symbol(Tokens.FALSE, 0, 5, false); }
    "var"              { return symbol(Tokens.VAR, 0, 3, "var"); }
    "="                { return symbol(Tokens.ASSIGNMENT, 0, 1, "="); }
    ";"                { return symbol(Tokens.SEMICOLON, 0, 1, ";"); }
    ":"                { return symbol(Tokens.COLON, 0, 1, ":"); }
    ","                { return symbol(Tokens.COMMA, 0, 1, ','); }
    "."                { return symbol(Tokens.DOT, 0, 1, "."); }
    "|"                { return symbol(Tokens.BAR, 0, 1, "|"); }
    "if"               { return symbol(Tokens.IF, 0, 2, "if"); }
    "else"             { return symbol(Tokens.ELSE, 0, 4, "else"); }
    "match"            { return symbol(Tokens.MATCH, 0, 5, "match"); }
    "case"             { return symbol(Tokens.CASE, 0, 4, "case"); }
    "while"            { return symbol(Tokens.WHILE, 0, 5, "while"); }
    "for"              { return symbol(Tokens.FOR, 0, 3, "for"); }
    "repeat"           { return symbol(Tokens.REPEAT, 0, 6, "repeat"); }
    "break"            { return symbol(Tokens.BREAK, 0, 5, "break"); }
    "continue"         { return symbol(Tokens.CONTINUE, 0, 8, "continue"); }
    "where"            { return symbol(Tokens.WHERE, 0, 5, "where"); }
    "return"           { return symbol(Tokens.RETURN, 0, 6, "return"); }
    "?="               { return symbol(Tokens.QUESTION_MARK_ASSIGNMENT, 0, 2, "?="); }

    // Builtin functions are currently processed at the grammar level.. maybe there's a better generalized way.
    "log_"             { return symbol(Tokens.LOG_PREFIX, 0, 4, "log_"); }
    "print"            { return symbol(Tokens.PRINT, 0, 5, "print"); }
    "numeric_bool"     { return symbol(Tokens.NUMERIC_BOOL, 0, 12, "numeric_bool"); }
    "input"            { return symbol(Tokens.INPUT, 0, 5, "input"); }
    "isInputReady"     { return symbol(Tokens.IS_INPUT_READY, 0, 12, "isInputReady"); }
    "len"              { return symbol(Tokens.LEN, 0, 3, "len"); }
    "type"             { return symbol(Tokens.TYPE, 0, 4, "type"); }
    "remove"           { return symbol(Tokens.REMOVE, 0, 6, "remove"); }
    "in"               { return symbol(Tokens.IN, 0, 2, "in"); }
    "instanceof"       { return symbol(Tokens.INSTANCEOF, 0, 10, "instanceof"); }
    "copy"             { return symbol(Tokens.COPY, 0, 4, "copy"); }
    "fromJson"         { return symbol(Tokens.FROM_JSON, 0, 8, "fromJson"); }
    "sleep"            { return symbol(Tokens.SLEEP, 0, 5, "sleep"); }

    // DEBUGGING keywords that should be removed when we want a real release...
    "$dumpscope"       { return symbol(Tokens.DEBUG_DUMP_SCOPE, 0, 10, "$dumpscope"); }

    // This is an internal-only feature, reserved for implementing the stdlib.
    "$$BEGIN_JAVA\n"  { if (supportPrivilegedInlineJava) {
                          yyline++;
                          yycolumn=0;
                          yybegin(PRIVILEGED_INLINE_JAVA);
                        } else {
                          handleUnknownToken();
                        }
                      }

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
    "lambda"           { return symbol(Tokens.LAMBDA, 0, 6, "lambda"); }
    "alias"            { return symbol(Tokens.ALIAS, 0, 5, "alias"); }
    "atom"             { return symbol(Tokens.ATOM, 0, 4, "atom"); }
    "newtype"          { return symbol(Tokens.NEWTYPE, 0, 7, "newtype"); }
    "unwrap"           { return symbol(Tokens.UNWRAP, 0, 6, "unwrap"); }
    "initializers"     { return symbol(Tokens.INITIALIZERS, 0, 12, "initializers"); }
    "unwrappers"       { return symbol(Tokens.UNWRAPPERS, 0, 10, "unwrappers"); }

    // Modifiers go here.
    "mut"              { return symbol(Tokens.MUT, 0, 3, "mut"); }

    // Module related bindings go here.
    "module"           { return symbol(Tokens.MODULE, 0, 6, "module"); }
    "bind"             { return symbol(Tokens.BIND, 0, 4, "bind"); }
    "to"               { return symbol(Tokens.TO, 0, 2, "to"); }
    "as"               { return symbol(Tokens.AS, 0, 2, "as"); }
    "using"            { return symbol(Tokens.USING, 0, 5, "using"); }

    "cast"             { return symbol(Tokens.CAST, 0, 4, "cast"); }

    // Graph related things go here.
    "future"           { return symbol(Tokens.FUTURE, 0, 6, "future"); }
    "graph"            { return symbol(Tokens.GRAPH, 0, 5, "graph"); }
    "root"             { return symbol(Tokens.ROOT, 0, 4, "root"); }
    "node"             { return symbol(Tokens.NODE, 0, 4, "node"); }
    "blocking"         { return symbol(Tokens.BLOCKING, 0, 8, "blocking"); }
    "blocking?"        { return symbol(Tokens.MAYBE_BLOCKING, 0, 9, "blocking?"); }
    "<-"               { return symbol(Tokens.LEFT_ARROW, 0, 2, "<-"); }
    "@"                { return symbol(Tokens.AT, 0, 1, "@"); }
    "<-|"              { return symbol(Tokens.BLOCKING_GET, 0, 3, "<-|"); }

    // This up arrow is used for the pipe chain backreference term.
    "^"                { return symbol(Tokens.UP_ARROW, 0, 1, "^"); }

    // Contract tokens.
    "contract"         { return symbol(Tokens.CONTRACT, 0, 8, "contract"); }
    "implement"        { return symbol(Tokens.IMPLEMENT, 0, 9, "implement"); }
    "requires"         { return symbol(Tokens.REQUIRES, 0, 8, "requires"); }

    "_"                { return symbol(Tokens.UNDERSCORE, 0, 1, "_"); }

     // Symbols related to builtin HTTP support go here.
     // TODO(steving) The Http related types should all also require http:: namespacing.
     // TODO(steving) This `http` module should be completely reimplemented as a proper claro_module_internal() target once possible.
     "HttpService"     { return symbol(Tokens.HTTP_SERVICE, 0, 11, "HttpService"); }
     "HttpClient"      { return symbol(Tokens.HTTP_CLIENT, 0, 10, "HttpClient"); }
     "http::getHttpClient"   {
         StdLibModuleUtil.validateRequiredOptionalStdlibModuleDepIsPresentAndMarkUsedIfSo("http");
         return symbol(Tokens.GET_HTTP_CLIENT, 0, 19, "http::getHttpClient");
     }
     "http::getBasicHttpServerForPort"  {
         StdLibModuleUtil.validateRequiredOptionalStdlibModuleDepIsPresentAndMarkUsedIfSo("http");
         return symbol(Tokens.GET_BASIC_HTTP_SERVER_FOR_PORT, 0, 31, "http::getBasicHttpServerForPort");
     }
     // This is a major hack that simply allows the detection of the synthetic http optional stdlib module for which extra java deps will need to be added to the build.
     "http::startServerAndAwaitShutdown"  {
         StdLibModuleUtil.validateRequiredOptionalStdlibModuleDepIsPresentAndMarkUsedIfSo("http");
         return symbol(Tokens.IDENTIFIER, 0, 33, "com.claro.runtime_utilities.http.$ClaroHttpServer.startServerAndAwaitShutdown");
     }
     "HttpResponse"      { return symbol(Tokens.HTTP_RESPONSE, 0, 12, "HttpResponse"); }
     "HttpServer"      { return symbol(Tokens.HTTP_SERVER, 0, 10, "HttpServer"); }
     "endpoint_handlers" { return symbol(Tokens.ENDPOINT_HANDLERS, 0, 17, "endpoint_handlers"); }

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
                         return symbol(Tokens.INTEGER, 0, lexed.length(), Integer.parseInt(lexed));
                       }

    // If float is found, return the token FLOAT that represents a float and the value of
    // the float that is held in the string yytext
    {Float}            {
                         String lexed = yytext();
                         return symbol(Tokens.FLOAT, 0, lexed.length(), Double.parseDouble(lexed));
                       }

    {Identifier}       {
                         String lexed = yytext();
                         return symbol(Tokens.IDENTIFIER, 0, lexed.length(), lexed);
                       }
    {ScopedIdentifier} {
                         String lexed = yytext();
                         String[] split = lexed.split("::");
                         final StringBuilder currentInputLineBuilder = currentInputLine.get();
                         return symbol(
                             Tokens.SCOPED_IDENTIFIER,
                             0,
                             lexed.length(),
                             ScopedIdentifier.forScopeNameAndIdentifier(
                                 split[0], split[1], () -> currentInputLineBuilder.toString(), yyline, yycolumn)
                         );
                       }
    {DepModuleContractProcedure} {
                         String lexed = yytext();
                         String[] split = lexed.split("::");
                         final StringBuilder currentInputLineBuilder = currentInputLine.get();
                         return symbol(
                             Tokens.SCOPED_IDENTIFIER,
                             0,
                             lexed.length(),
                             ScopedIdentifier.forScopeNameAndIdentifier(
                                 String.format(
                                     "%s$%s",
                                     split[1],
                                     ScopedHeap.getDefiningModuleDisambiguator(Optional.of(split[0]))),
                                 split[2],
                                 () -> currentInputLineBuilder.toString(), yyline, yycolumn)
                         );
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

<PRIVILEGED_INLINE_JAVA> {
    [^]                    {
                              // Collect everything into the currentInputLine just for somewhere to keep it.
                              addToLine(yytext());
                           }
    "$$END_JAVA\n"           {
                             yybegin(YYINITIAL);
                             String lexed = currentInputLine.get().toString();
                             // Just want to make sure line numbers are still tracked properly.
                             int lines = 0;
                             int i = 0;
                             while (i < lexed.length()) {
                               if (lexed.charAt(i++) == '\n') {
                                 lines++;
                               }
                             }
                             yyline += lines;
                             yycolumn = 0;
                             currentInputLine.set(new StringBuilder());
                             return new Symbol(Tokens.PRIVILEGED_INLINE_JAVA, yycolumn, yyline - 1, LexedValue.create(lexed, () -> lexed, lexed.length()));
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
                         return symbol(Tokens.EOF);
                       }

/* Catch-all the rest, i.e. unknown character. */
[^]  { handleUnknownToken(); }

