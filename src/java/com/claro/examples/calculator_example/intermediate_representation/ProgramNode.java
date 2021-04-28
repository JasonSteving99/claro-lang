package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
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

  // TODO(steving) This method needs to be refactored and have lots of its logic lifted up out into the callers which
  // TODO(steving) are the actual CompilerBackend's. Most of what's going on here is legit not an AST node's responsibility.
  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // At the program level, validate all types in the entire AST before execution.
    try {
      ((StmtListNode) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);
    } catch (ClaroTypeException e) {
      // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
      // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
      // use in the execution stage.
      throw new RuntimeException(e);
    }

    // Manually exit the last observed scope which is the global scope, since nothing else will trigger its exit.
    // BUT, because we need the scope to not be thrown away in the REPL case (since in that case we aren't actually
    // exiting the scope, we're just temporarily bouncing out, with the ScopedHeap as the source of continuity between
    // REPL stmts...) we won't do this if it's the repl case. This only loses us "unused" checking, which is disabled in
    // the REPL anyways.
    if (scopedHeap.checkUnused) {
      // Finalize the type-checking phase.
      scopedHeap.exitCurrScope();
      // Now prepare for interpreted execution/javasource generation phase.
      scopedHeap.enterNewScope();
    }

    // Now that we've validated that all types are valid, go to town in a fresh scope!
    StringBuilder res =
        new StringBuilder(genJavaMain(this.getChildren().get(0).generateJavaSourceOutput(scopedHeap).toString()));

    // Just for completeness sake, we'll want to exit this global scope as well just in case there are important checks
    // that get run at that time at the last moment before we give the all good signal.
    scopedHeap.exitCurrScope();

    return res;
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // At the program level, validate all types in the entire AST before execution.
    try {
      ((StmtListNode) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);
    } catch (ClaroTypeException e) {
      // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
      // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
      // use in the execution stage.
      throw new RuntimeException(e);
    }

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
        "  private static String promptUserInput(String prompt) {\n" +
        "    System.out.println(prompt);\n" +
        "    return INPUT_SCANNER.nextLine();\n" +
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
