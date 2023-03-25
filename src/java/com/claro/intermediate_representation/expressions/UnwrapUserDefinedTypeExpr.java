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
    if (InternalStaticStateUtil.InitializersBlockStmt_unwrappersByUnwrappedType.containsKey(validatedUserDefinedType.getTypeName())
        && !(InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()
             && InternalStaticStateUtil.InitializersBlockStmt_unwrappersByUnwrappedType
                 .get(validatedUserDefinedType.getTypeName())
                 .contains(((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.get()).procedureName))) {
      // Actually, it turns out this is an illegal reference to the auto-generated default constructor outside of one
      // of the procedures defined within the `initializers` block.
      // Technically though the types check, so let's log the error and continue to find more errors.
      this.logTypeError(
          ClaroTypeException.forIllegalUseOfUserDefinedTypeDefaultUnwrapperOutsideOfUnwrapperProcedures(
              validatedExprType,
              InternalStaticStateUtil.InitializersBlockStmt_unwrappersByUnwrappedType.get(validatedUserDefinedType.getTypeName())
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
      return StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
          genericTypeParamMap,
          genericWrappedType,
          genericWrappedType,
          /*inferConcreteTypes=*/true
      );
    }

    return scopedHeap.getValidatedIdentifierType(
        ((Types.UserDefinedType) validatedExprType).getTypeName() + "$wrappedType");
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = expr.generateJavaSourceOutput(scopedHeap);
    res.javaSourceBody().append(".wrappedValue");
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return (($UserDefinedType) expr.generateInterpretedOutput(scopedHeap)).wrappedValue;
  }
}
