package com.claro.compiler_backends;

import com.claro.ClaroLexer;
import com.claro.ClaroParser;

import java.io.StringReader;

public class ParserUtil {

  private static ClaroLexer createLexer(String input, boolean escapeSpecialChars) {
    ClaroLexer lexer = new ClaroLexer(new StringReader(input));
    lexer.escapeSpecialChars = escapeSpecialChars;
    return lexer;
  }

  public static ClaroParser createParser(String input) {
    return createParser(input, true);
  }

  public static ClaroParser createParser(String input, boolean escapeSpecialChars) {
    return new ClaroParser(createLexer(input, escapeSpecialChars));
  }

}
