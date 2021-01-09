package com.claro.examples.calculator_example;
  
import java.io.StringReader;
import java.util.Scanner;
  
public class CalculatorMain {
  public static void main(String[] args) throws Exception {
    System.out.println("Enter your formula:");
    Scanner scan = new Scanner(System.in);
    String inputFormula = scan.nextLine();
    CalculatorParser parser = createParser(inputFormula);
    System.out.println("= " + parser.parse().value);
  }

  private static CalculatorLexer createLexer(String input) {
    return new CalculatorLexer(new StringReader(input));
  }
 
  private static CalculatorParser createParser(String input) {
    return new CalculatorParser(createLexer(input));
  }
}
