package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.user_defined_impls.$UserDefinedType;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class UnwrapUserDefinedTypeExpr extends Expr {
  private final Expr expr;
  public Type validatedUnwrappedType;

  public UnwrapUserDefinedTypeExpr(Expr expr, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.expr = expr;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Type validatedExprType = this.expr.getValidatedExprType(scopedHeap);
    if (!validatedExprType.baseType().equals(BaseType.USER_DEFINED_TYPE)) {
      if (validatedExprType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
        this.expr.logTypeError(ClaroTypeException.forInvalidUnwrapOfGenericType(validatedExprType));
      } else {
        this.expr.logTypeError(ClaroTypeException.forInvalidUnwrapOfBuiltinType(validatedExprType));
      }
      return Types.UNKNOWABLE;
    }

    Types.UserDefinedType validatedUserDefinedType = (Types.UserDefinedType) validatedExprType;

    // Interestingly, this node is different than most other nodes in that it should only validate the legality of
    // whether this procedure can use this unwrapper *once* since in the case it's being used within a Generic Procedure
    // Def, the monomorphized procedure names won't be registered as legal "unwrappers".
    if (InternalStaticStateUtil.UnwrappersBlockStmt_unwrappersByUnwrappedType.containsKey(validatedUserDefinedType.getTypeName())
        && (!InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()
            ||
            (!((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.get()).procedureName.contains("$$MONOMORPHIZATION")
             && !InternalStaticStateUtil.UnwrappersBlockStmt_unwrappersByUnwrappedType
                .get(validatedUserDefinedType.getTypeName())
                .contains(((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.get()).procedureName)))) {
      // Actually, it turns out this is an illegal reference to the auto-generated default constructor outside of one
      // of the procedures defined within the `initializers` block.
      // Technically though the types check, so let's log the error and continue to find more errors.
      this.logTypeError(
          ClaroTypeException.forIllegalUseOfUserDefinedTypeDefaultUnwrapperOutsideOfUnwrapperProcedures(
              validatedExprType,
              InternalStaticStateUtil.UnwrappersBlockStmt_unwrappersByUnwrappedType.get(validatedUserDefinedType.getTypeName())
          ));
    }

    if (!validatedUserDefinedType.parameterizedTypeArgs().isEmpty()) {
      // In this case we actually should be parameterizing the wrapped type with this instance's concrete type params.
      HashMap<Type, Type> genericTypeParamMap = Maps.newHashMap();
      IntStream.range(0, validatedUserDefinedType.parameterizedTypeArgs().size())
          .boxed()
          .forEach(
              i ->
                  genericTypeParamMap.put(
                      Types.$GenericTypeParam.forTypeParamName(
                          Types.UserDefinedType.$typeParamNames.get(validatedUserDefinedType.getTypeName()).get(i)),
                      validatedUserDefinedType.parameterizedTypeArgs().get(i.toString())
                  ));
      Type genericWrappedType = scopedHeap.getValidatedIdentifierType(
          ((Types.UserDefinedType) validatedExprType).getTypeName() + "$wrappedType");
      this.validatedUnwrappedType =
          StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
              genericTypeParamMap,
              genericWrappedType,
              genericWrappedType,
              /*inferConcreteTypes=*/true
          );
      return this.validatedUnwrappedType;
    }

    this.validatedUnwrappedType = scopedHeap.getValidatedIdentifierType(
        ((Types.UserDefinedType) validatedExprType).getTypeName() + "$wrappedType");
    return this.validatedUnwrappedType;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = expr.generateJavaSourceOutput(scopedHeap);
    res.javaSourceBody().append(".wrappedValue)");
    return GeneratedJavaSource.forJavaSourceBody(
            // Unfortunately need to cast the wrapped value because in certain circumstances, even though the
            // $UserDefinedType runtime repr is parameterized, codegen interplays poorly w/ Java's limited type inference.
            new StringBuilder("((")
                .append(this.validatedUnwrappedType.getJavaSourceType())
                .append(") "))
        .createMerged(res);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return (($UserDefinedType) expr.generateInterpretedOutput(scopedHeap)).wrappedValue;
  }
}
