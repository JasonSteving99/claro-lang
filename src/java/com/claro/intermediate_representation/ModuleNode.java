package com.claro.intermediate_representation;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.NewTypeDefStmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Stack;

public class ModuleNode {
  public final ImmutableList<ContractProcedureSignatureDefinitionStmt> exportedSignatures;
  public final ImmutableList<NewTypeDefStmt> exportedNewTypeDefs;
  private final String moduleName;
  private final String javaPackage;
  public final Stack<String> errorMessages = new Stack<>();

  private ImmutableMap<String, Types.ProcedureType> moduleExportedProcedureSignatureTypes = null;

  public ModuleNode(
      ImmutableList<ContractProcedureSignatureDefinitionStmt> exportedSignatures,
      ImmutableList<NewTypeDefStmt> exportedNewTypeDefs,
      String moduleName,
      String javaPackage) {
    this.exportedSignatures = exportedSignatures;
    this.exportedNewTypeDefs = exportedNewTypeDefs;
    this.moduleName = moduleName;
    this.javaPackage = javaPackage;
  }

  public ImmutableMap<String, Types.ProcedureType> getExportedProcedureSignatureTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Turns out there are a couple places where this gets used. I'm just going to simplify my life by caching the result.
    if (this.moduleExportedProcedureSignatureTypes == null) {
      // TODO(steving) Consider factoring out the core functionality from the ContractProcedureSignatureDefinitionStmt so
      //   that it doesn't actually require masquerading the Module as though it's a ContractDefitionStmt.
      // Unfortunately, just for the sake of integrating w/ the existing ContractProcedureSignatureDefinitionStmt's
      // expectations of being used w/in the context of a ContractDefinitionStmt, I need to artificially masquerade as
      // though this Module definition is actually a ContractDefinitionStmt.
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractName = this.moduleName + "$MODULE$";
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractGenericTypeParamNames =
          this.exportedSignatures.stream().map(sig -> sig.procedureName).collect(ImmutableList.toImmutableList());
      for (ContractProcedureSignatureDefinitionStmt exportedSignature : this.exportedSignatures) {
        exportedSignature.assertExpectedExprTypes(scopedHeap);
      }
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractName = null;
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractGenericTypeParamNames = null;

      this.moduleExportedProcedureSignatureTypes =
          this.exportedSignatures.stream()
              .collect(ImmutableMap.toImmutableMap(
                  sig -> sig.procedureName,
                  sig -> sig.getExpectedProcedureTypeForConcreteTypeParams(ImmutableMap.of())
              ));
    }
    return this.moduleExportedProcedureSignatureTypes;
  }

  public boolean assertExpectedProceduresActuallyExported(ScopedHeap scopedHeap) throws ClaroTypeException {
    boolean errorsFound = false;
    for (Map.Entry<String, Types.ProcedureType> expectedExportedProcedureEntry :
        getExportedProcedureSignatureTypes(scopedHeap).entrySet()) {
      String exportedProcedureName = expectedExportedProcedureEntry.getKey();
      Types.ProcedureType expectedExportedProcedureType = expectedExportedProcedureEntry.getValue();
      if (!scopedHeap.isIdentifierDeclared(expectedExportedProcedureEntry.getKey())) {
        errorsFound = true;
        logError(
            ClaroTypeException.forModuleExportedProcedureNotDefinedInModuleImplFiles(
                exportedProcedureName, expectedExportedProcedureType));
        continue; // There's nothing more to do with this invalid exported signature.
      }
      Type actualIdentifierType = scopedHeap.getValidatedIdentifierType(exportedProcedureName);
      if (!actualIdentifierType.equals(expectedExportedProcedureType)) {
        errorsFound = true;
        logError(
            ClaroTypeException.forModuleExportedProcedureNameBoundToIncorrectImplementationType(
                exportedProcedureName, expectedExportedProcedureType, actualIdentifierType
            )
        );
        continue;
      }
      // TODO(steving) TESTING!!! I NEED A WAY TO DISTINGUISH BTWN FIRST-CLASS FUNCTION VARS AND THE ACTUAL FUNCTION
      //    DEFINITION. I WOULD WANT TO REJECT BINDINGS TO FIRST-CLASS FUNCTION VARS B/C ANY TOP-LEVEL STMTS ACTUALLY
      //    WON'T BE EVALUATED.
    }
    return errorsFound;
  }

  // TODO(steving) TESTING!!! EXTEND THE MODULE PARSER TO ACTUALLY COLLECT LINE INFO FOR EACH SIGNATURE.
  private void logError(ClaroTypeException e) {
    this.errorMessages.push(String.format("%s.claro_module: %s", this.moduleName, e.getMessage()));
  }

  @AutoValue
  public static abstract class ModuleApiStmtsBuilder {
    public abstract ImmutableList.Builder<ContractProcedureSignatureDefinitionStmt> getProcedureSignaturesBuilder();

    public abstract ImmutableList.Builder<NewTypeDefStmt> getNewTypeDefStmtsBuilder();

    public static ModuleApiStmtsBuilder create() {
      return new AutoValue_ModuleNode_ModuleApiStmtsBuilder(ImmutableList.builder(), ImmutableList.builder());
    }
  }
}
