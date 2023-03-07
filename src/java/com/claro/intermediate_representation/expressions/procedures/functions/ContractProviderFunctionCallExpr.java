package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.contracts.ContractDefinitionStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ContractProviderFunctionCallExpr extends ProviderFunctionCallExpr {
  private final String contractName;
  private String referencedContractImplName;
  private ImmutableList<Type> resolvedContractConcreteTypes;
  private Types.$Contract resolvedContractType;
  private String originalName;
  private Type assertedOutputType;

  public ContractProviderFunctionCallExpr(
      String contractName,
      String providerName,
      Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    // For now, we'll just masquerade as function name since we haven't resolved types yet. But we'll need to
    // canonicalize the name in a moment after type validation has gotten under way and types are known.
    super(providerName, currentLine, currentLineNumber, startCol, endCol);

    this.contractName = contractName;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type assertedOutputType) throws ClaroTypeException {
    // First things first, reset to original state because type checking dictates codegen, and this node may be type
    // checked multiple times in the case that it gets called within a generic procedure (hence, it'll at least be
    // checked once for the generic procedure validation pass and once for any concrete monomorphization type checking).
    {
      referencedContractImplName = null;
      resolvedContractConcreteTypes = null;
      resolvedContractType = null;
      originalName = null;
    }

    this.resolveContractType(scopedHeap);

    // Before we do any type checking, we essentially want to disable worrying about exact matches on generic
    // type params. For a contract, we simply would want to know that the contract is listed in the requirements
    // but for its generic argument positions it can be passed literally any type (since the existence of that
    // contract impl will actually be validated at the callsite to the generic function, not here).
    Expr.validatingContractProcCallWithinGenericProc = true;

    ContractDefinitionStmt contractDefinitionStmt =
        (ContractDefinitionStmt) scopedHeap.getIdentifierValue(this.contractName);
    ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt =
        contractDefinitionStmt.declaredContractSignaturesByProcedureName.get(this.functionName);

    // Infer all types from the return type.
    HashMap<Type, Type> inferredConcreteTypes = Maps.newHashMap();
    // Let FunctionCallExpr that we're about to defer to handle throwing the exception if this was called over some
    // consumer that doesn't even have an output type.
    if (contractProcedureSignatureDefinitionStmt.resolvedOutputType.isPresent()) {
      // Need to infer the output type first to preserve the semantic that the contextual types provide better type
      // checking on the args necessary to get that desired output type.
      try {
        StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
            inferredConcreteTypes,
            contractProcedureSignatureDefinitionStmt.resolvedOutputType.get().toType(),
            assertedOutputType
        );
      } catch (ClaroTypeException ignored) {
        // We want cleaner error messages that indicate the type mismatch more clearly.
        throw new ClaroTypeException(
            assertedOutputType,
            contractProcedureSignatureDefinitionStmt.resolvedOutputType.get().toType()
        );
      }
      this.resolvedContractConcreteTypes = contractDefinitionStmt.typeParamNames.stream()
          .map(n -> inferredConcreteTypes.get(Types.$GenericTypeParam.forTypeParamName(n)))
          .collect(ImmutableList.toImmutableList());
    }

    this.assertedOutputType = assertedOutputType;
    super.assertExpectedExprType(scopedHeap, assertedOutputType);

    // Return type validation to the default state where Generic type params must be strictly checked for equality.
    Expr.validatingContractProcCallWithinGenericProc = false;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    ContractDefinitionStmt contractDefinitionStmt =
        (ContractDefinitionStmt) scopedHeap.getIdentifierValue(this.contractName);
    ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt =
        contractDefinitionStmt.declaredContractSignaturesByProcedureName.get(this.functionName);

    // It's invalid to call a Contract Provider without a contextually asserted type output type.
    if (this.assertedOutputType == null) {
      // In this case the program is ambiguous when the return type isn't asserted. The output type of this particular
      // Contract procedure call happens to be composed around a Contract type param that is NOT present in one of the
      // other args, therefore making Contract Impl inference via the arg types alone impossible.
      throw ClaroTypeException.forContractProcedureCallWithoutRequiredContextualOutputTypeAssertion(
          this.contractName,
          contractDefinitionStmt.typeParamNames,
          this.functionName,
          contractProcedureSignatureDefinitionStmt.resolvedOutputType.get().toType()
      );
    }

    Type res = this.getValidatedTypeInternal(
        contractDefinitionStmt, contractProcedureSignatureDefinitionStmt, scopedHeap);

    // In case we actually would return a generic type here (bc we're still within a generic type validation phase), we
    // can just go ahead and assume it safe to return the asserted type. This is because we know that when the Generic
    // Procedure that this contract function call is made within is actually called, the correct monomorphization for
    // this contract will be selected since the generic procedure's contract requirements will have been validated.
    if (res.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
      return this.assertedOutputType;
    }
    return res;
  }

  private Type getValidatedTypeInternal(
      ContractDefinitionStmt contractDefinitionStmt,
      ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt,
      ScopedHeap scopedHeap) throws ClaroTypeException {
    AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.functionName);
    AtomicReference<String> originalName_OUT_PARAM = new AtomicReference<>(this.originalName);
    AtomicReference<String> referencedContractImplName_OUT_PARAM =
        new AtomicReference<>(this.referencedContractImplName);
    AtomicReference<ImmutableList<Type>> resolvedContractConcreteTypes_OUT_PARAM =
        new AtomicReference<>(this.resolvedContractConcreteTypes);
    try {
      ContractFunctionCallExpr.getValidatedTypeInternal(
          this.contractName,
          this.resolvedContractType,
          contractDefinitionStmt,
          contractProcedureSignatureDefinitionStmt,
          ImmutableList.of(),
          /*alreadyAssertedOutputTypes=*/ true,
          Optional.of(this::logTypeError),
          scopedHeap,
          resolvedContractConcreteTypes_OUT_PARAM,
          procedureName_OUT_PARAM,
          originalName_OUT_PARAM,
          referencedContractImplName_OUT_PARAM,
          new AtomicBoolean(false) // Providers can't possibly support dynamic dispatch.
      );
    } finally {
      this.functionName = procedureName_OUT_PARAM.get();
      this.originalName = originalName_OUT_PARAM.get();
      this.referencedContractImplName = referencedContractImplName_OUT_PARAM.get();
      this.resolvedContractConcreteTypes = resolvedContractConcreteTypes_OUT_PARAM.get();
    }
    // This final step defers validation of the actual types passed as args.
    Type res = super.getValidatedExprType(scopedHeap);
    if (InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation) {
      this.functionName = originalName_OUT_PARAM.get();
    }
    return res;
  }

  private void resolveContractType(ScopedHeap scopedHeap) throws ClaroTypeException {
    AtomicReference<Types.$Contract> resolvedContractType_OUT_PARAM =
        new AtomicReference<>(this.resolvedContractType);
    AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.functionName);
    AtomicReference<String> originalName_OUT_PARAM = new AtomicReference<>(this.originalName);
    try {
      ContractFunctionCallExpr.resolveContractType(
          this.contractName, scopedHeap, resolvedContractType_OUT_PARAM, procedureName_OUT_PARAM, originalName_OUT_PARAM);
    } finally {
      // To simulate "out params" no matter what happens in this call, the side effects must occur.
      this.resolvedContractType = resolvedContractType_OUT_PARAM.get();
      this.functionName = procedureName_OUT_PARAM.get();
      this.originalName = originalName_OUT_PARAM.get();
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(this.referencedContractImplName).append('.'));

    // In order to avoid using names that are way too long for Java, we're going to hash all names within this
    // contract implementation. I won't worry about maintaining the old names here, because these variables should
    // never be referenced anymore after codegen.
    super.hashNameForCodegen = true;
    res = res.createMerged(super.generateJavaSourceOutput(scopedHeap));

    // This node will be potentially reused assuming that it is called within a Generic function that gets
    // monomorphized as that process will reuse the exact same nodes over multiple sets of types. So reset
    // the name now.
    this.functionName = this.originalName;

    return res;
  }
}
