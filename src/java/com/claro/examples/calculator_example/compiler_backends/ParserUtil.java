package com.claro.examples.calculator_example.compiler_backends;

import com.claro.examples.calculator_example.CalculatorLexer;
import com.claro.examples.calculator_example.CalculatorParser;

import java.io.StringReader;

public class ParserUtil {

  private static CalculatorLexer createLexer(String input) {
    return new CalculatorLexer(new StringReader(input));
  }

  public static CalculatorParser createParser(String input) {
    return new CalculatorParser(createLexer(input));
  }

}
