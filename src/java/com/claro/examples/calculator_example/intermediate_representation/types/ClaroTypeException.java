package com.claro.examples.calculator_example.intermediate_representation.types;

public class ClaroTypeException extends RuntimeException {

  private static final String INVALID_TYPE_ERROR_MESSAGE_FMT_STR = "Invalid type: expected <%s>, but found <%s>.";

  public ClaroTypeException(Type expectedType, Type actualType) {
    super(String.format(INVALID_TYPE_ERROR_MESSAGE_FMT_STR, expectedType, actualType));
  }
}
