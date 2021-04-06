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
    // At the program level, validate all types in the entire AST before execution.
    ((StmtListNode) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);

    // Now that we've validated that all types are valid, go to town!
    return new StringBuilder(genJavaMain(this.getChildren().get(0).generateJavaSourceOutput(scopedHeap).toString()));
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // At the program level, validate all types in the entire AST before execution.
    ((StmtListNode) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);

    // Now that we've validated that all types are valid, go to town!
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
        "import java.util.ArrayList;\n" +
        "import java.util.Scanner;\n" +
        "\n\n" +
        "public class %s {\n" +
        // Programs can prompt users for input, they'll read that input using this Scanner over stdin.
        "  private static final Scanner INPUT_SCANNER = new Scanner(System.in);\n\n" +
        "  public static void main(String[] args) {\n" +
        "/*******BEGIN AUTO-GENERATED: DO NOT MODIFY*******/\n" +
        "%s" +
        "/*******END AUTO-GENERATED*******/\n" +
        "/*******BELOW THIS POINT IS THE STANDARD LIBRARY IMPLEMENTATION ESSENTIALLY*******/\n" +
        "  }\n\n" +
        "  private static double promptUserInput(String prompt) {\n" +
        "    System.out.println(prompt);\n" +
        "    return INPUT_SCANNER.nextDouble();\n" +
        "  }\n" +
        "  private static <T> ClaroList<T> initializeList() {\n" +
        "    return new ClaroList<>();\n" +
        "  }\n" +
        "  private static <T> ClaroList<T> initializeList(T ... args) {\n" +
        "    ClaroList<T> arrayList = new ClaroList<>(args.length);\n" +
        "    for (T arg : args) arrayList.add(arg);\n" +
        "    return arrayList;\n" +
        "  }\n" +
        "  private static class ClaroList<T> extends ArrayList<T> {\n" +
        "    public ClaroList() {\n" +
        "      super();\n" +
        "    }\n" +
        "    public ClaroList(int initialSize) {\n" +
        "      super(initialSize);\n" +
        "    }\n" +
        "    // In Claro, this'll end up being a method defined on the Iterable interface.\n" +
        "    public int length() {\n" +
        "      return ClaroList.this.size();\n" +
        "    }\n" +
        "  }\n" +
        "}",
        this.packageString,
        this.generatedClassName,
        java_source_stmt_list
    );
  }
}
