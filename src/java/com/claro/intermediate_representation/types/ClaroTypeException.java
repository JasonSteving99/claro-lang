package com.claro.intermediate_representation.types;

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
  private static final String UNDECIDED_TYPE_LEAK_ERROR_MESSAGE_FMT_STR =
      "The type of this expression is UNDECIDED at compile-time! You must explicitly cast the Expr to the contextually expected type <%s> to assert this type at compile-time or fix a bug if the contextually expected type isn't applicable.";
  private static final String UNDECIDED_TYPE_LEAK_GENERIC_ERROR_MESSAGE_FMT_STR =
      "The type of this expression is UNDECIDED at compile-time! You must explicitly cast the Expr to the expected type to assert this type at compile-time.";
  private static final String MISSING_TYPE_DECLARATION_FOR_EMPTY_LIST_INITIALIZATION =
      "The type of this empty list is UNDECIDED at compile-time! You must explicitly declare the type of a variable having the empty list `[]` assigned to it to assert this type statically at compile-time.";
  private static final String MISSING_TYPE_DECLARATION_FOR_LAMBDA_INITIALIZATION =
      "The type of this lambda is UNDECIDED at compile-time! You must explicitly declare the type of a variable with a lambda expression assigned to it to assert this type statically at compile-time.";
  private static final String INVALID_CAST_ERROR_MESSAGE_FMT_STR =
      "Invalid cast: Found <%s> which cannot be converted to <%s>.";
  private static final String INVALID_MEMBER_REFERENCE = "Invalid Member Reference: %s has no such member %s.";
  private static final String UNSET_REQUIRED_STRUCT_MEMBER =
      "Builder Missing Required Struct Member: While building %s, required field%s %s need%s to be set before calling build().";
  private static final String WRONG_NUMBER_OF_ARGS_FOR_LAMBDA_DEFINITION =
      "Lambda expression definition contains the incorrect number of args. Expected %s.";

  public ClaroTypeException(String message) {
    super(message);
  }

  public ClaroTypeException(Type actualType, Type expectedType) {
    super(String.format(INVALID_TYPE_ERROR_MESSAGE_FMT_STR, actualType, expectedType));
  }

  public ClaroTypeException(Type actualType, ImmutableSet<?> expectedTypeOptions) {
    super(
        String.format(
            INVALID_TYPE_ONE_OF_ERROR_MESSAGE_FMT_STR,
            actualType,
            Joiner.on(", ").join(expectedTypeOptions)
        )
    );
  }

  public ClaroTypeException(String operatorStr, ImmutableSet<Type> expectedTypeOptions) {
    super(
        String.format(
            INVALID_OPERATOR_OPERANDS_TYPE_ONE_OF_ERROR_MESSAGE_FMT_STR,
            operatorStr,
            Joiner.on(", ").join(expectedTypeOptions)
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

  public static <T> ClaroTypeException forUndecidedTypeLeak() {
    return new ClaroTypeException(UNDECIDED_TYPE_LEAK_GENERIC_ERROR_MESSAGE_FMT_STR);
  }

  public static <T> ClaroTypeException forUndecidedTypeLeak(T contextuallyExpectedType) {
    return new ClaroTypeException(
        String.format(
            UNDECIDED_TYPE_LEAK_ERROR_MESSAGE_FMT_STR,
            contextuallyExpectedType
        )
    );
  }

  public static ClaroTypeException forUndecidedTypeLeak(ImmutableSet<Type> contextuallySupportedExpectedType) {
    return new ClaroTypeException(
        String.format(
            UNDECIDED_TYPE_LEAK_ERROR_MESSAGE_FMT_STR,
            Joiner.on(", ").join(contextuallySupportedExpectedType)
        )
    );
  }

  public static ClaroTypeException forUndecidedTypeLeakEmptyListInitialization() {
    return new ClaroTypeException(MISSING_TYPE_DECLARATION_FOR_EMPTY_LIST_INITIALIZATION);
  }

  public static ClaroTypeException forUndecidedTypeLeakMissingTypeDeclarationForLambdaInitialization() {
    return new ClaroTypeException(MISSING_TYPE_DECLARATION_FOR_LAMBDA_INITIALIZATION);
  }

  public static ClaroTypeException forInvalidCast(Object actualType, Type assertedType) {
    return new ClaroTypeException(
        String.format(
            INVALID_CAST_ERROR_MESSAGE_FMT_STR,
            actualType,
            assertedType
        )
    );
  }

  public static ClaroTypeException forInvalidMemberReference(Type structType, String identifier) {
    return new ClaroTypeException(
        String.format(
            INVALID_MEMBER_REFERENCE,
            structType,
            identifier
        )
    );
  }

  public static ClaroTypeException forUnsetRequiredStructMember(
      Type structType, ImmutableSet<?> unsetFields) {
    boolean plural = unsetFields.size() > 1;
    return new ClaroTypeException(
        String.format(
            UNSET_REQUIRED_STRUCT_MEMBER,
            structType,
            // English is weird.
            plural ? "s" : "",
            Joiner.on(", ").join(unsetFields),
            plural ? "" : "s"
        )
    );
  }

  public static ClaroTypeException forWrongNumberOfArgsForLambdaDefinition(Type expectedType) {
    return new ClaroTypeException(String.format(WRONG_NUMBER_OF_ARGS_FOR_LAMBDA_DEFINITION, expectedType));
  }
}
