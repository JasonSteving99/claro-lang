package com.claro.intermediate_representation.statements.contracts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.stream.Collectors;

public class ContractProcedureImplementationStmt extends Stmt {
  final ProcedureDefinitionStmt procedureDefinitionStmt;

  String procedureName;

  private ContractDefinitionStmt contractDefinitionStmt;
  private ImmutableMap<String, Type> concreteTypeParams;
  private String contractTypeString;

  public ContractProcedureImplementationStmt(ProcedureDefinitionStmt procedureImplementation) {
    super(ImmutableList.of());
    this.procedureDefinitionStmt = procedureImplementation;
  }

  public void registerProcedureTypeProvider(
      ScopedHeap scopedHeap, String contractName, ImmutableList<Type> contractTypeParams) {
    // I just need to rename the procedure since the default naming given by the programmer needs to be
    // prefixed with something to indicate which contract implementation it's coming from. Contract functions
    // are in the end of the day essentially top-level functions.
    this.procedureName = this.procedureDefinitionStmt.procedureName;
    this.procedureDefinitionStmt.procedureName =
        getCanonicalProcedureName(contractName, contractTypeParams, this.procedureDefinitionStmt.procedureName);

    // Now, register the procedure definition.
    this.procedureDefinitionStmt.registerProcedureTypeProvider(scopedHeap);
  }

  public static String getCanonicalProcedureName(String contractName, ImmutableList<Type> contractTypeParams, String procedureName) {
    return String.format(
        "$%s%s__%s",
        contractName,
        contractTypeParams.stream()
            .map(Type::toString)
            .collect(Collectors.joining(", ", "::<", ">_")),
        procedureName
    );
  }

  public void assertExpectedExprTypes(
      ScopedHeap scopedHeap,
      String contractTypeString,
      ContractDefinitionStmt contractDefinitionStmt,
      ImmutableMap<String, Type> concreteTypeParams) throws ClaroTypeException {
    this.contractTypeString = contractTypeString;
    this.contractDefinitionStmt = contractDefinitionStmt;
    this.concreteTypeParams = concreteTypeParams;
    assertExpectedExprTypes(scopedHeap);
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    ImmutableMap<String, ContractProcedureSignatureDefinitionStmt> contractSignaturesByProcedureName =
        this.contractDefinitionStmt.declaredContractSignaturesByProcedureName;
    ContractProcedureSignatureDefinitionStmt contractSignature =
        contractSignaturesByProcedureName.get(this.procedureName);

    // Time to finally validate that the signature itself is actually followed.
    Type contractExpectedProcedureSignature =
        contractSignature.getExpectedProcedureTypeForConcreteTypeParams(this.concreteTypeParams);
    if (!contractExpectedProcedureSignature.equals(this.procedureDefinitionStmt.resolvedProcedureType)) {
      throw ClaroTypeException.forContractProcedureImplementationSignatureMismatch(
          this.contractTypeString,
          this.procedureName,
          contractExpectedProcedureSignature,
          this.procedureDefinitionStmt.resolvedProcedureType
      );
    }

    // And, last, actually do type checking on the ProcedureDefinitionStmt.
    this.procedureDefinitionStmt.assertExpectedExprTypes(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return this.procedureDefinitionStmt.generateJavaSourceOutput(scopedHeap);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return null;
  }
}
