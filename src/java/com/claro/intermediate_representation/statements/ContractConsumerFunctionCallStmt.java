package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.procedures.functions.ContractFunctionCallExpr;
import com.claro.intermediate_representation.statements.contracts.ContractDefinitionStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class ContractConsumerFunctionCallStmt extends ConsumerFunctionCallStmt {
  private final String contractName;
  private String referencedContractImplName;
  private ImmutableList<Type> resolvedContractConcreteTypes;
  private Types.$Contract resolvedContractType;
  private String originalName;

  public ContractConsumerFunctionCallStmt(
      String contractName,
      String consumerName,
      ImmutableList<Expr> args) {
    // For now, we'll just masquerade as function name since we haven't resolved types yet. But we'll need to
    // canonicalize the name in a moment after type validation has gotten under way and types are known.
    super(consumerName, args);

    this.contractName = contractName;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
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
        contractDefinitionStmt.declaredContractSignaturesByProcedureName.get(this.consumerName);

    this.assertExpectedExprTypesInternal(
        contractDefinitionStmt,
        contractProcedureSignatureDefinitionStmt,
        scopedHeap
    );

    // Return type validation to the default state where Generic type params must be strictly checked for equality.
    Expr.validatingContractProcCallWithinGenericProc = false;
  }

  private void assertExpectedExprTypesInternal(
      ContractDefinitionStmt contractDefinitionStmt,
      ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt,
      ScopedHeap scopedHeap) throws ClaroTypeException {
    AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.consumerName);
    AtomicReference<String> originalName_OUT_PARAM = new AtomicReference<>(this.originalName);
    AtomicReference<String> referencedContractImplName_OUT_PARAM =
        new AtomicReference<>(this.referencedContractImplName);
    AtomicReference<ImmutableList<Type>> resolvedContractConcreteTypes_OUT_PARAM =
        new AtomicReference<>(this.resolvedContractConcreteTypes);
    boolean revertNameAfterTypeValidation;
    try {
      revertNameAfterTypeValidation =
          ContractFunctionCallExpr.getValidatedTypeInternal(
              this.contractName,
              this.resolvedContractType,
              contractDefinitionStmt,
              contractProcedureSignatureDefinitionStmt,
              this.argExprs,
              Optional.empty(),
              scopedHeap,
              resolvedContractConcreteTypes_OUT_PARAM,
              procedureName_OUT_PARAM,
              originalName_OUT_PARAM,
              referencedContractImplName_OUT_PARAM
          );
    } finally {
      this.consumerName = procedureName_OUT_PARAM.get();
      this.originalName = originalName_OUT_PARAM.get();
      this.referencedContractImplName = referencedContractImplName_OUT_PARAM.get();
      this.resolvedContractConcreteTypes = resolvedContractConcreteTypes_OUT_PARAM.get();
    }
    // This final step defers validation of the actual types passed as args.
    super.assertExpectedExprTypes(scopedHeap);
    if (revertNameAfterTypeValidation) {
      this.consumerName = originalName_OUT_PARAM.get();
    }
  }

  private void resolveContractType(ScopedHeap scopedHeap) throws ClaroTypeException {
    AtomicReference<Types.$Contract> resolvedContractType_OUT_PARAM =
        new AtomicReference<>(this.resolvedContractType);
    AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.consumerName);
    AtomicReference<String> originalName_OUT_PARAM = new AtomicReference<>(this.originalName);
    try {
      ContractFunctionCallExpr.resolveContractType(
          this.contractName, scopedHeap, resolvedContractType_OUT_PARAM, procedureName_OUT_PARAM, originalName_OUT_PARAM);
    } finally {
      // To simulate "out params" no matter what happens in this call, the side effects must occur.
      this.resolvedContractType = resolvedContractType_OUT_PARAM.get();
      this.consumerName = procedureName_OUT_PARAM.get();
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
    this.consumerName = this.originalName;

    return res;
  }
}
