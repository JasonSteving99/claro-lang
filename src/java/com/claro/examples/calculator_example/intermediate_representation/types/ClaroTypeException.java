package com.claro.examples.calculator_example.intermediate_representation.types;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

public class ClaroTypeException extends Exception {

  private static final String INVALID_TYPE_ERROR_MESSAGE_FMT_STR = "Invalid type: found <%s>, but expected <%s>.";
  private static final String INVALID_BASE_TYPE_ERROR_MESSAGE_FMT_STR =
      "Invalid type: found <%s>, but expected to have the BaseType <%s>.";
  private static final String INVALID_TYPE_ONE_OF_ERROR_MESSAGE_FMT_STR =
      "Invalid type: found <%s>, but expected one of (<%s>).";
  private static final String INVALID_OPERATOR_OPERANDS_TYPE_ONE_OF_ERROR_MESSAGE_FMT_STR =
      "Internal Compiler Error: Operator `<%s>` expects one of (<%s>) for operands.";

  public ClaroTypeException(Type actualType, Type expectedType) {
    super(String.format(INVALID_TYPE_ERROR_MESSAGE_FMT_STR, actualType, expectedType));
  }

  public ClaroTypeException(Type actualType, ImmutableSet<Type> expectedTypeOptions) {
    super(
        String.format(
            INVALID_TYPE_ONE_OF_ERROR_MESSAGE_FMT_STR,
            actualType,
            Joiner.on(",").join(expectedTypeOptions)
        )
    );
  }

  public ClaroTypeException(String operatorStr, ImmutableSet<Type> expectedTypeOptions) {
    super(
        String.format(
            INVALID_OPERATOR_OPERANDS_TYPE_ONE_OF_ERROR_MESSAGE_FMT_STR,
            operatorStr,
            Joiner.on(",").join(expectedTypeOptions)
        )
    );
  }

  public ClaroTypeException(Type actualExprType, BaseType expectedBaseType) {
    super(
        String.format(
            INVALID_BASE_TYPE_ERROR_MESSAGE_FMT_STR,
            actualExprType,
            expectedBaseType
        )
    );
  }
}
