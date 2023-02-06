package com.claro.intermediate_representation.statements.contracts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class ContractProcedureImplementationStmt extends Stmt {
  // Note: this can be either a GenericProcedureDefinitionStmt or a ProcedureDefinitionStmt but Java doesn't let me
  // express that w/o inheritance...
  Stmt procedureDefinitionStmt;
  private BiFunction<String, ScopedHeap, Stmt> updateGenericFunctionDefinitionCanonicalName;

  String procedureName;
  String canonicalProcedureName;

  private ContractDefinitionStmt contractDefinitionStmt;
  private ImmutableMap<String, Type> concreteTypeParams;
  private String contractTypeString;
  public Type resolvedProcedureType;

  public ContractProcedureImplementationStmt(ProcedureDefinitionStmt procedureImplementation) {
    super(ImmutableList.of());
    this.procedureDefinitionStmt = procedureImplementation;
  }

  public ContractProcedureImplementationStmt(
      String genericFunctionName,
      BiFunction<String, ScopedHeap, Stmt> updateGenericFunctionDefinitionCanonicalName) {
    super(ImmutableList.of());
    this.procedureName = genericFunctionName;
    this.updateGenericFunctionDefinitionCanonicalName = updateGenericFunctionDefinitionCanonicalName;
  }

  public void registerProcedureTypeProvider(
      ScopedHeap scopedHeap, String contractName, ImmutableList<Type> contractTypeParams) {
    if (this.procedureDefinitionStmt != null) {
      // I just need to rename the procedure since the default naming given by the programmer needs to be
      // prefixed with something to indicate which contract implementation it's coming from. Contract functions
      // are in the end of the day essentially top-level functions.
      ProcedureDefinitionStmt thisProcedureDefStmt = (ProcedureDefinitionStmt) this.procedureDefinitionStmt;
      this.procedureName = thisProcedureDefStmt.procedureName;
      this.canonicalProcedureName =
          getCanonicalProcedureName(contractName, contractTypeParams, thisProcedureDefStmt.procedureName);
      thisProcedureDefStmt.procedureName = this.canonicalProcedureName;

      // Now, register the procedure definition.
      thisProcedureDefStmt.registerProcedureTypeProvider(scopedHeap);
    } else { // Generic procedure.
      this.canonicalProcedureName = getCanonicalProcedureName(contractName, contractTypeParams, this.procedureName);
      this.procedureDefinitionStmt =
          this.updateGenericFunctionDefinitionCanonicalName.apply(this.canonicalProcedureName, scopedHeap);

      // Since this generic procedure will potentially be codegen'd out of order, we need to mark this procedure as
      // belonging to a Contract impl, so that the GenericFunctionDefStmt node knows to set aside the monomorphization
      // codegen's for this node to inline with the contract impl when ready.
      InternalStaticStateUtil.ContractDefinitionStmt_genericContractImplProceduresCanonicalNames
          .add(this.canonicalProcedureName);
    }
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
    Types.ProcedureType contractExpectedProcedureSignature =
        contractSignature.getExpectedProcedureTypeForConcreteTypeParams(this.concreteTypeParams);
    try {
      HashMap<Type, Type> genericToConcreteTypeMappings = Maps.newHashMap();
      this.concreteTypeParams.forEach(
          (name, concreteType) ->
              genericToConcreteTypeMappings.put(Types.$GenericTypeParam.forTypeParamName(name), concreteType));
      Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.of(genericToConcreteTypeMappings);
      Type structurallyMatchedProcedureType =
          StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
              genericToConcreteTypeMappings,
              contractExpectedProcedureSignature,
              scopedHeap.getValidatedIdentifierType(this.canonicalProcedureName)
          );
      // Since the above structural type checking doesn't actually validate the complete signature, check that
      // explicitly here to pick up any blocking annotation(s).
      Boolean foundBlockingAnnotation = ((Types.ProcedureType) structurallyMatchedProcedureType).getAnnotatedBlocking();
      if (!Objects.equals(foundBlockingAnnotation, contractExpectedProcedureSignature.getAnnotatedBlocking())) {
        throw new ClaroTypeException("Contract Procedure Impl Signature Missing Required Blocking Annotation!");
      }
    } catch (ClaroTypeException ignored) {
      throw ClaroTypeException.forContractProcedureImplementationSignatureMismatch(
          this.contractTypeString,
          this.procedureName,
          contractExpectedProcedureSignature,
          scopedHeap.getValidatedIdentifierType(this.canonicalProcedureName)
      );
    } finally {
      Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.empty();
    }


    // And, last, actually do type checking on the ProcedureDefinitionStmt.
    this.procedureDefinitionStmt.assertExpectedExprTypes(scopedHeap);

    // Need to hold the resolved procedure type from the underlying procedure def stmt.
    if (this.procedureDefinitionStmt instanceof ProcedureDefinitionStmt) {
      this.resolvedProcedureType = ((ProcedureDefinitionStmt) this.procedureDefinitionStmt).resolvedProcedureType;
    } else {
      // This should just be a reference to the underlying GenericProcedureDefinitionStmt, but the build deps are
      // preventing me from directly referencing this type. Bazel, you're killing me.
      this.resolvedProcedureType = scopedHeap.getValidatedIdentifierType(this.canonicalProcedureName);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // In the case that this Contract procedure was actually Generic, then the codegen phase for generic funcs may have
    // already run and set monomorphizations for this contract procedure implementation. Check for that, and if any
    // are found, then just produce the Java source for those.
    if (InternalStaticStateUtil.GenericProcedureDefinitionStmt_alreadyCodegenedContractProcedureMonomorphizations
        .containsRow(this.canonicalProcedureName)) {
      final GeneratedJavaSource[] monomorphizationsCodegen =
          {GeneratedJavaSource.forJavaSourceBody(new StringBuilder())};
      InternalStaticStateUtil.GenericProcedureDefinitionStmt_alreadyCodegenedContractProcedureMonomorphizations
          .values().forEach(monoCodegen -> {
            monomorphizationsCodegen[0] = monomorphizationsCodegen[0].createMerged((GeneratedJavaSource) monoCodegen);
          });
      return monomorphizationsCodegen[0];
    }
    return this.procedureDefinitionStmt.generateJavaSourceOutput(scopedHeap);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return null;
  }

  public void updateProcedureDefName(String updatedProcedureName) {
    if (this.updateGenericFunctionDefinitionCanonicalName != null) {
      // Here we simply want to update the name of the underlying procedures for the GenericProcedureDefinitionStmt.
      Object unused = this.updateGenericFunctionDefinitionCanonicalName.apply(updatedProcedureName, null);
    } else {
      ((ProcedureDefinitionStmt) this.procedureDefinitionStmt).procedureName = updatedProcedureName;
    }
  }
}
