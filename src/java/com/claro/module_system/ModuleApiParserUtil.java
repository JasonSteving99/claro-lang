package com.claro.module_system;

import com.claro.ModuleApiLexer;
import com.claro.ModuleApiParser;

import java.io.StringReader;
import java.util.Scanner;

public class ModuleApiParserUtil {
  public static ModuleApiParser getParserForModuleApiFileOnStdIn() {
    Scanner scan = new Scanner(System.in);

    StringBuilder inputModuleApi = new StringBuilder();
    while (scan.hasNextLine()) {
      inputModuleApi.append(scan.nextLine());
      // Scanner is being stupid and dropping all the newlines... so this may give an extra compared to what's in the
      // source file, but who cares, the grammar will handle it.
      inputModuleApi.append("\n");
    }

    return createParser(inputModuleApi.toString(), "TestModule");
  }

  public static ModuleApiLexer createLexer(String input) {
    ModuleApiLexer lexer = new ModuleApiLexer(new StringReader(input));
    return lexer;
  }

  public static ModuleApiParser createParser(String input, String srcFilename) {
    ModuleApiLexer lexer = createLexer(input);
    lexer.moduleFilename = srcFilename;
    ModuleApiParser parser = new ModuleApiParser(lexer);
    parser.moduleName = srcFilename;
    return parser;
  }
}
