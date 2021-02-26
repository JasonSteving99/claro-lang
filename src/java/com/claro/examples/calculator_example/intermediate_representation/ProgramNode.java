package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

public class ProgramNode extends Node {
  private final String packageString, generatedClassName;

  // TODO(steving) package and generatedClassName should probably be injected some cleaner way since this is a Target::JAVA_SOURCE-only artifact.
  public ProgramNode(
      StmtListNode stmtListNode,
      String packageString,
      String generatedClassName) {
    super(ImmutableList.of(stmtListNode));
    this.packageString = packageString;
    this.generatedClassName = generatedClassName;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder compiledJavaSourceOutput =
        new StringBuilder(genJavaMain(this.getChildren().get(0).generateJavaSourceOutput(scopedHeap).toString()));
    return compiledJavaSourceOutput;
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    this.getChildren().get(0).generateInterpretedOutput(scopedHeap);

    // There's no output in the interpreting mode.
    return null;
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
