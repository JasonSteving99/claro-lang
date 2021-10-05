package com.claro.compiler_backends;

import com.claro.ClaroLexer;
import com.claro.ClaroParser;

import java.io.StringReader;

public class ParserUtil {

  private static ClaroLexer createLexer(String input) {
    return new ClaroLexer(new StringReader(input));
  }

  public static ClaroParser createParser(String input) {
    return new ClaroParser(createLexer(input));
  }

}
