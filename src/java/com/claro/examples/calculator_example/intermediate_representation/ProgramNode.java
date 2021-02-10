package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.CalculatorParserException;
import com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.HashSet;

public class ProgramNode extends Node {
  private final String packageString, generatedClassName;

  // TODO(steving) package and generatedClassName should probably be injected some cleaner way since this is a Target::JAVA_SOURCE-only artifact.
  public ProgramNode(
      StmtListNode stmtListNode,
      HashSet<String> symbolSet,
      HashSet<String> usedSymbolSet,
      boolean checkUnused,
      String packageString,
      String generatedClassName) {
    super(ImmutableList.of(stmtListNode));

    // TODO(steving) This should probably be pulled out to be something that gets processed top-down outside of constructing the AST.
    if (checkUnused) {
      symbolSet.removeAll(usedSymbolSet);
      if (symbolSet.size() > 0) {
        throw new CalculatorParserException(
            String.format("Warning! The following declared symbols are unused! %s", symbolSet)
        );
      }
    }

    this.packageString = packageString;
    this.generatedClassName = generatedClassName;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(genJavaMain(this.getChildren().get(0).generateJavaSourceOutput().toString()));
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    return this.getChildren().get(0).generateInterpretedOutput(heap);
  }

  /**
   * In some ways this hardcoded class is basically a standard library for this language.
   */
  private String genJavaMain(String java_source_stmt_list) {
    return String.format(
        "%s" +
        "import java.util.Scanner;\n\n\n" +
        "public class %s {\n" +
        // Programs can prompt users for input, they'll read that input using this Scanner over stdin.
        "  private static final Scanner INPUT_SCANNER = new Scanner(System.in);\n\n" +
        "  public static void main(String[] args) {\n" +
        "/*******AUTO-GENERATED DO NOT MODIFY*******/\n" +
        "%s" +
        "/*******END AUTO-GENERATED*******/\n" +
        "  }\n\n" +
        "  private static double promptUserInput(String prompt) {\n" +
        "    System.out.println(prompt);\n" +
        "    return INPUT_SCANNER.nextDouble();\n" +
        "  }\n" +
        "}",
        this.packageString,
        this.generatedClassName,
        java_source_stmt_list
    );
  }
}
