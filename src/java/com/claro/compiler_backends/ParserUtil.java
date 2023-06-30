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

  public static ClaroParser createParser(String input, String srcFilename) {
    return createParser(input, srcFilename, /*supportInternalOnlyFeatures*/false, /*escapeSpecialChars*/true);
  }

  public static ClaroParser createParser(
      String input, String srcFilename, boolean supportInternalOnlyFeatures, boolean escapeSpecialChars) {
    ClaroLexer lexer = createLexer(input, escapeSpecialChars);
    lexer.generatedClassName = srcFilename;
    lexer.supportPrivilegedInlineJava = supportInternalOnlyFeatures;
    ClaroParser parser = new ClaroParser(lexer);
    parser.generatedClassName = srcFilename;
    return parser;
  }

}
