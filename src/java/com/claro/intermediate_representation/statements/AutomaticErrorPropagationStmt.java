package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.Node.GeneratedJavaSource;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.Optional;

public class AutomaticErrorPropagationStmt {
  private static long autoCheckedVariableCount = 0;
  private final long uniqueId;
  private final Optional<Type> optionalAssertedNonErrorType;
  private final Expr returnExpr;


  private Type validatedExprType;
  private Optional<Type> narrowedToConcreteType = Optional.empty();
  private ImmutableList<Type> possibleErrorTypes;

  public AutomaticErrorPropagationStmt(Optional<Type> optionalAssertedNonErrorType, Expr returnExpr) {
    this.optionalAssertedNonErrorType = optionalAssertedNonErrorType;
    this.returnExpr = returnExpr;

    // Make sure that every instantiation of this class triggers an increment to this so that we ensure we have unique
    // generated var names at codegen time.
    this.uniqueId = AutomaticErrorPropagationStmt.autoCheckedVariableCount++;
  }

  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Type validatedExprType = this.returnExpr.getValidatedExprType(scopedHeap);
    // Checked type has to be a oneof type to do automatic error propagation (otherwise programmer should just use
    // explicit return).
    if (!validatedExprType.baseType().equals(BaseType.ONEOF)) {
      // Log and move on nicely.
      this.returnExpr.logTypeError(ClaroTypeException.forIllegalAutomaticErrorPropagation(validatedExprType));
      // We haven't actually done any error propagation, so this can just return the original type (for the sake of
      // moving on with type checking) because essentially in this case what I *want* the programmer to do is to simply
      // delete the `?`.
      return validatedExprType;
    }
    Types.OneofType validatedOneofType = (Types.OneofType) validatedExprType;

    // Ok, so we have a oneof, but it must have at least one variant that's an `Error<T>` and one non-error variant.
    if (validatedOneofType.getVariantTypes().stream().noneMatch(ClaroRuntimeUtilities::isErrorType)
        || validatedOneofType.getVariantTypes().stream().allMatch(ClaroRuntimeUtilities::isErrorType)) {
      this.returnExpr.logTypeError(ClaroTypeException.forIllegalAutomaticErrorPropagation(validatedOneofType));
      return validatedOneofType;
    }

    // Finally, I need to ensure that this operator is only being used within a Procedure body that returns the error
    // type(s) that this oneof contains.
    if (!InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureResolvedType.isPresent()
        ||
        !((Types.ProcedureType) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureResolvedType.get()).hasReturnValue()) {
      this.returnExpr.logTypeError(ClaroTypeException.forIllegalAutomaticErrorPropagationOutsideOfProcedureBody());
      return validatedOneofType;
    }
    Type activeProcedureReturnType =
        ((Types.ProcedureType) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureResolvedType.get())
            .getReturnType();
    if (activeProcedureReturnType.baseType().equals(BaseType.ONEOF)) {
      // If the return type is a oneof, then all of the error types have to be represented in the oneof.
      if (validatedOneofType.getVariantTypes().stream()
          .filter(ClaroRuntimeUtilities::isErrorType)
          .anyMatch(t -> !((Types.OneofType) activeProcedureReturnType).getVariantTypes().contains(t))) {
        this.returnExpr.logTypeError(ClaroTypeException.forIllegalAutomaticErrorPropagationForUnsupportedReturnType(validatedOneofType, activeProcedureReturnType));
        return validatedOneofType;
      }
    } else if (
        validatedOneofType.getVariantTypes().stream().filter(ClaroRuntimeUtilities::isErrorType).count() > 1
        || !validatedOneofType.getVariantTypes().stream()
            .filter(ClaroRuntimeUtilities::isErrorType)
            .findFirst().get()
            .equals(activeProcedureReturnType)) {
      // If there's only a concrete type accepted, then there must only be a single possible error type, and it must
      // match the return type exactly.
      this.returnExpr.logTypeError(ClaroTypeException.forIllegalAutomaticErrorPropagationForUnsupportedReturnType(validatedOneofType, activeProcedureReturnType));
      return validatedOneofType;
    }

    // Great, so they used the operator properly. I need to now factor out all of the Error variants to produce the
    // resulting type of applying this operator. Split out the error type(s) from the non-error type(s).
    this.validatedExprType = validatedOneofType;
    this.possibleErrorTypes =
        validatedOneofType.getVariantTypes().stream()
            .filter(ClaroRuntimeUtilities::isErrorType)
            .collect(ImmutableList.toImmutableList());

    if (validatedOneofType.getVariantTypes().stream()
            .filter(t -> !ClaroRuntimeUtilities.isErrorType(t))
            .count() == 1) {
      // If there's only a single non-error case, then the resulting type is actually not even a oneof, it's just a
      // simple concrete type.
      this.narrowedToConcreteType = Optional.of(
          validatedOneofType.getVariantTypes().stream()
              .filter(t -> !ClaroRuntimeUtilities.isErrorType(t))
              .findFirst()
              .get()
      );
      // If type assertion is requested, then check that now.
      if (this.optionalAssertedNonErrorType.isPresent()
          && !this.optionalAssertedNonErrorType.get().equals(this.narrowedToConcreteType.get())) {
        this.returnExpr.logTypeError(
            new ClaroTypeException(this.narrowedToConcreteType.get(), this.optionalAssertedNonErrorType.get()));
      }
      return this.narrowedToConcreteType.get();
    }
    // There is more than one non-error variant, so the resulting type is still a oneof.
    Type res = Types.OneofType.forVariantTypes(
        validatedOneofType.getVariantTypes().stream()
            .filter(t -> !ClaroRuntimeUtilities.isErrorType(t))
            .collect(ImmutableList.toImmutableList()));

    if (this.optionalAssertedNonErrorType.isPresent()) {
      try {
        StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
            Maps.newHashMap(),
            this.optionalAssertedNonErrorType.get(),
            res
        );
      } catch (ClaroTypeException e) {
        this.returnExpr.logTypeError(e);
      }
    }

    return res;
  }

  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // First, do codegen for the underlying expr, so we can hold onto its static codegen at the end.
    GeneratedJavaSource exprGenJavaSource = this.returnExpr.generateJavaSourceOutput(scopedHeap);
    // First I need to prefix the current stmt with a synthetic stmt setting up a variable
    String syntheticVariableName = String.format("$errorPropagationCheckedVar_%s", this.uniqueId);
    StringBuilder prefixErrorCheckJavaSourceCodegen = new StringBuilder()
        .append(this.validatedExprType.getJavaSourceType())
        .append(" ")
        .append(syntheticVariableName)
        .append(" = ")
        .append(exprGenJavaSource.javaSourceBody().toString())
        .append(";\n");
    // Already consumed this javaSourceBody, empty it now.
    exprGenJavaSource.javaSourceBody().setLength(0);

    prefixErrorCheckJavaSourceCodegen
        .append("if ((")
        .append(syntheticVariableName)
        .append(" instanceof ClaroTypeImplementation) && ")
        .append("ClaroRuntimeUtilities.isErrorType(((ClaroTypeImplementation) ")
        .append(syntheticVariableName)
        .append(").getClaroType())) {\n")
        .append("\treturn ");
    if (this.possibleErrorTypes.size() == 1) {
      // Not an exactly applicable condition, but good enough. If there's only a single known error variant, then cast
      // to that specific type on the way out. This covers the case that this function's return type is itself expecting
      // this specific concrete error type.
      prefixErrorCheckJavaSourceCodegen.append('(')
          .append(this.possibleErrorTypes.get(0).getJavaSourceType())
          .append(") ");
    }
    prefixErrorCheckJavaSourceCodegen
        .append(syntheticVariableName)
        .append(";\n")
        .append("}\n");
    Stmt.addGeneratedJavaSourceStmtBeforeCurrentStmt(prefixErrorCheckJavaSourceCodegen.toString());


    if (this.narrowedToConcreteType.isPresent()) {
      // I'm transitioning from a oneof type, to a concrete type, so I require a cast.
      GeneratedJavaSource res =
          GeneratedJavaSource.forJavaSourceBody(new StringBuilder("(/*NARROWED UNDER AUTOMATIC ERROR PROPAGATION*/("));
      res.javaSourceBody()
          .append(this.narrowedToConcreteType.get().getJavaSourceType())
          .append(") ");
      res.javaSourceBody()
          .append(syntheticVariableName)
          .append(")");
      return res.createMerged(exprGenJavaSource);
    }
    return GeneratedJavaSource.forJavaSourceBody(new StringBuilder(syntheticVariableName))
        .createMerged(exprGenJavaSource);
  }

  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    throw new RuntimeException("Internal Compiler Error: Automatic Error Propagation via `?` operator is not yet supported in the interpreted backend.");
  }
}
