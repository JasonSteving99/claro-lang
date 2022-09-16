package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.contracts.ContractImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureImplementationStmt;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public class ContractFunctionCallExpr extends FunctionCallExpr {
  private final String contractName;
  private final ImmutableList<TypeProvider> contractConcreteTypes;
  private String referencedContractImplName;

  public ContractFunctionCallExpr(
      String contractName,
      ImmutableList<TypeProvider> contractConcreteTypes,
      String functionName,
      ImmutableList<Expr> args, Supplier<String> currentLine,
      int currentLineNumber, int startCol, int endCol) {
    super(
        // For now, we'll just masquerade as function name since we haven't resolved types yet. But we'll need to
        // canonicalize the name in a moment after type validation has gotten under way and types are known.
        functionName, args, currentLine, currentLineNumber, startCol, endCol);

    this.contractName = contractName;
    this.contractConcreteTypes = contractConcreteTypes;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Validate that the concrete type params are valid and make note of them.
    ImmutableList<Type> concreteTypes = this.contractConcreteTypes.stream()
        .map(typeProvider -> typeProvider.resolveType(scopedHeap))
        .collect(ImmutableList.toImmutableList());

    // Validate that the contract name is valid.
    if (!scopedHeap.isIdentifierDeclared(this.contractName)) {
      throw ClaroTypeException.forReferencingUnknownContract(this.contractName, concreteTypes);
    }
    Type contractType = scopedHeap.getValidatedIdentifierType(this.contractName);
    if (!contractType.baseType().equals(BaseType.$CONTRACT)) {
      throw new ClaroTypeException(scopedHeap.getValidatedIdentifierType(this.contractName), BaseType.$CONTRACT);
    }

    // Set the canonical implementation name so that this can be referenced later simply by type.
    ImmutableList<String> concreteTypeStrings =
        concreteTypes.stream()
            .map(Type::toString)
            .collect(ImmutableList.toImmutableList());

    // Check that there are the correct number of type params.
    if (((Types.$Contract) contractType).getTypeParamNames().size() != concreteTypes.size()) {
      throw ClaroTypeException.forContractReferenceWithWrongNumberOfTypeParams(
          ContractImplementationStmt.getContractTypeString(this.contractName, concreteTypeStrings),
          ContractImplementationStmt.getContractTypeString(this.contractName, ((Types.$Contract) contractType).getTypeParamNames())
      );
    }
    // Validate that the referenced procedure name is even actually in the referenced contract.
    if (!((Types.$Contract) contractType).getProcedureNames().contains(this.name)) {
      throw ClaroTypeException.forContractReferenceUndefinedProcedure(this.contractName, concreteTypeStrings, this.name);
    }

    // We can now resolve the contract's concrete types so that we can canonicalize the function call name.
    this.name = ContractProcedureImplementationStmt.getCanonicalProcedureName(
        this.contractName,
        concreteTypes,
        this.name
    );

    // Before leaving, just hold onto the corresponding contract's implementation name for codegen.
    this.referencedContractImplName =
        (String) scopedHeap.getIdentifierValue(
            ContractImplementationStmt.getContractTypeString(this.contractName, concreteTypeStrings));
    return super.getValidatedExprType(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(this.referencedContractImplName).append('.'));
    return res.createMerged(super.generateJavaSourceOutput(scopedHeap));
  }
}
