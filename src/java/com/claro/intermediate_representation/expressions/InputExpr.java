package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.Scanner;
import java.util.function.Supplier;

public class InputExpr extends Expr {

  protected static final Scanner INPUT_SCANNER = new Scanner(System.in);
  private final Optional<String> prompt;

  public InputExpr(Optional<String> prompt, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.prompt = prompt;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    return Types.STRING;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    if (this.prompt.isPresent()) {
      return new StringBuilder(String.format("promptUserInput(\"%s\")", this.prompt.get()));
    }
    return new StringBuilder("UserInput.INPUT_SCANNER.nextLine()");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    if (this.prompt.isPresent()) {
      System.out.println(prompt.get());
    }
    return INPUT_SCANNER.nextLine();
  }
}
