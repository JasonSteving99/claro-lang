package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
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
  protected Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    return Types.STRING;
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
