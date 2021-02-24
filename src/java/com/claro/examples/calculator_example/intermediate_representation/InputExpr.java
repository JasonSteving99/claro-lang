package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

import java.util.Scanner;

public class InputExpr extends Expr {

  private static final Scanner INPUT_SCANNER = new Scanner(System.in);
  private final String prompt;

  public InputExpr(String prompt) {
    super(ImmutableList.of());
    this.prompt = prompt;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(String.format("promptUserInput(\"%s\")", this.prompt));
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    System.out.println(prompt);
    return INPUT_SCANNER.nextDouble();
  }
}
