package com.claro.intermediate_representation;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.statements.AtomDefinitionStmt;
import com.claro.intermediate_representation.statements.HttpServiceDefStmt;
import com.claro.intermediate_representation.statements.contracts.ContractDefinitionStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.AliasStmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.NewTypeDefStmt;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.*;

public class ModuleNode {
  public final ImmutableList<ContractProcedureSignatureDefinitionStmt> exportedSignatures;
  public final ImmutableList<AliasStmt> exportedAliasDefs;
  public final ImmutableList<AtomDefinitionStmt> exportedAtomDefs;
  public final ImmutableList<NewTypeDefStmt> exportedNewTypeDefs;
  public final ImmutableMap<IdentifierReferenceTerm, ImmutableList<ContractProcedureSignatureDefinitionStmt>>
      initializersBlocks;
  public final ImmutableMap<IdentifierReferenceTerm, ImmutableList<ContractProcedureSignatureDefinitionStmt>>
      unwrappersBlocks;
  public final ImmutableList<ContractDefinitionStmt> exportedContractDefs;
  public final ImmutableSet<String> depModulesTransitiveTypeExports;
  private final String moduleName;
  public final ImmutableList<HttpServiceDefStmt> exportedHttpServiceDefs;
  private final String uniqueModuleName;
  public final Stack<String> errorMessages = new Stack<>();

  private ImmutableMap<String, Types.ProcedureType> moduleExportedProcedureSignatureTypes = null;

  public ModuleNode(
      ImmutableList<ContractProcedureSignatureDefinitionStmt> exportedSignatures,
      ImmutableList<AliasStmt> exportedAliasDefs,
      ImmutableList<AtomDefinitionStmt> exportedAtomDefs,
      ImmutableList<NewTypeDefStmt> exportedNewTypeDefs,
      ImmutableMap<IdentifierReferenceTerm, ImmutableList<ContractProcedureSignatureDefinitionStmt>> initializersBlocks,
      ImmutableMap<IdentifierReferenceTerm, ImmutableList<ContractProcedureSignatureDefinitionStmt>> unwrappersBlocks,
      ImmutableList<ContractDefinitionStmt> exportedContractDefs,
      ImmutableList<HttpServiceDefStmt> exportedHttpServiceDefs,
      ImmutableSet<String> depModulesTransitiveTypeExports,
      String moduleName,
      String uniqueModuleName) {
    this.exportedSignatures = exportedSignatures;
    this.exportedAliasDefs = exportedAliasDefs;
    this.exportedAtomDefs = exportedAtomDefs;
    this.exportedNewTypeDefs = exportedNewTypeDefs;
    this.initializersBlocks = initializersBlocks;
    this.unwrappersBlocks = unwrappersBlocks;
    this.exportedContractDefs = exportedContractDefs;
    this.depModulesTransitiveTypeExports = depModulesTransitiveTypeExports;
    this.moduleName = moduleName;
    this.exportedHttpServiceDefs = exportedHttpServiceDefs;
    this.uniqueModuleName = uniqueModuleName;
  }

  public void registerExportedTypeDefs(ScopedHeap scopedHeap) {
    for (AtomDefinitionStmt exportedAtomDef : this.exportedAtomDefs) {
      exportedAtomDef.registerType(scopedHeap);
    }
    for (NewTypeDefStmt exportedNewTypeDef : this.exportedNewTypeDefs) {
      exportedNewTypeDef.registerTypeProvider(scopedHeap);
    }
    for (AliasStmt exportedAliasDef : this.exportedAliasDefs) {
      exportedAliasDef.registerTypeProvider(scopedHeap);
    }
    for (HttpServiceDefStmt exportedHttpServiceDefStmt : this.exportedHttpServiceDefs) {
      exportedHttpServiceDefStmt.registerTypeProvider(scopedHeap);
    }
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
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractGenericTypeParamNames = ImmutableList.of();
      // Collect all the exported procedure signatures together.
      ImmutableList.Builder<ContractProcedureSignatureDefinitionStmt> allExportedSignaturesBuilder =
          ImmutableList.builder();
      // First validate all of the top-level exported procedures.
      for (ContractProcedureSignatureDefinitionStmt exportedSignature : this.exportedSignatures) {
        // Make sure that the ContractProcedureSignatureDefinitionStmt knows not to go renaming this procedure.
        exportedSignature.shouldNormalizeProcedureNameForContractDefinition = false;
        exportedSignature.assertExpectedExprTypes(scopedHeap);
        allExportedSignaturesBuilder.add(exportedSignature);
      }
      // Then validate all procedures within initializers blocks.
      for (ImmutableList<ContractProcedureSignatureDefinitionStmt> exportedInitializers : this.initializersBlocks.values()) {
        for (ContractProcedureSignatureDefinitionStmt exportedSignature : exportedInitializers) {
          exportedSignature.assertExpectedExprTypes(scopedHeap);
          allExportedSignaturesBuilder.add(exportedSignature);
        }
      }
      // Then validate all procedures within unwrappers blocks.
      for (ImmutableList<ContractProcedureSignatureDefinitionStmt> exportedUnwrappers : this.unwrappersBlocks.values()) {
        for (ContractProcedureSignatureDefinitionStmt exportedSignature : exportedUnwrappers) {
          exportedSignature.assertExpectedExprTypes(scopedHeap);
          allExportedSignaturesBuilder.add(exportedSignature);
        }
      }
      ImmutableMap.Builder<String, Types.ProcedureType> moduleExportedProcedureSignatureTypesBuilder =
          ImmutableMap.builder();
      // Then validate all synthetic procedures defined by HttpServiceDefs.
      for (HttpServiceDefStmt httpServiceDefStmt : this.exportedHttpServiceDefs) {
        httpServiceDefStmt.registerHttpProcedureTypeProviders(scopedHeap);
        httpServiceDefStmt.assertExpectedExprTypes(scopedHeap);
        moduleExportedProcedureSignatureTypesBuilder.putAll(
            httpServiceDefStmt.syntheticEndpointProcedures.stream()
                .collect(ImmutableMap.toImmutableMap(
                    procedureDefinitionStmt -> procedureDefinitionStmt.procedureName,
                    procedureDefinitionStmt -> procedureDefinitionStmt.resolvedProcedureType
                )));
      }
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractName = null;
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractGenericTypeParamNames = null;

      moduleExportedProcedureSignatureTypesBuilder.putAll(
          allExportedSignaturesBuilder.build().stream()
              .collect(ImmutableMap.toImmutableMap(
                  sig -> sig.procedureName,
                  sig -> sig.getExpectedProcedureTypeForConcreteTypeParams(ImmutableMap.of())
              )));

      this.moduleExportedProcedureSignatureTypes = moduleExportedProcedureSignatureTypesBuilder.build();
    }
    return this.moduleExportedProcedureSignatureTypes;
  }

  public void assertInitializersAndUnwrappersBlocksAreDefinedOnTypesExportedByThisModule(ScopedHeap scopedHeap) {
    for (IdentifierReferenceTerm initializedTypeName : this.initializersBlocks.keySet()) {
      assertInitializersOrUnwrappersBlockIsDefinedOnTypesExportedByThisModule(scopedHeap, initializedTypeName, /*initializers=*/true);
    }
    for (IdentifierReferenceTerm unwrappedTypeName : this.unwrappersBlocks.keySet()) {
      assertInitializersOrUnwrappersBlockIsDefinedOnTypesExportedByThisModule(scopedHeap, unwrappedTypeName, /*initializers=*/false);
    }
  }

  private static void assertInitializersOrUnwrappersBlockIsDefinedOnTypesExportedByThisModule(
      ScopedHeap scopedHeap, IdentifierReferenceTerm initializedTypeIdentfier, boolean initializers) {
    String initializedTypeName = initializedTypeIdentfier.identifier;
    if (!scopedHeap.isIdentifierDeclared(initializedTypeName)) {
      initializedTypeIdentfier.logTypeError(
          ClaroTypeException.forIllegalInitializersBlockReferencingUndeclaredInitializedType(
              initializedTypeName, initializers));
    }
    Type validatedInitializedType =
        TypeProvider.Util.getTypeByName(initializedTypeName, true).resolveType(scopedHeap);
    if (!validatedInitializedType.baseType().equals(BaseType.USER_DEFINED_TYPE)) {
      initializedTypeIdentfier.logTypeError(
          ClaroTypeException.forIllegalInitializersBlockReferencingNonUserDefinedType(
              initializedTypeName, validatedInitializedType, initializers));
    }
    if (((Types.UserDefinedType) validatedInitializedType).getTypeName().contains("$DEP_MODULE$")) {
      // I need to ensure that any initializers/unwrappers blocks are strictly referencing real UserDefinedTypes that
      // were defined by *THIS* Module. Modules are not allowed to define these blocks for types defined by dep modules
      // as this would be very problematic for Claro's overall incremental compilation story, where individually
      // compiling a Claro module would be insufficient to determine that a module passes all type checks (you'd have to
      // do a secondary check at the claro_binary() that walks the module graph to ensure that if
      // initializers/unwrappers were defined somewhere unbeknownst to some other modules, we still emitted errors about
      // illegal uses of default constructors or the unwrap() function). This constraint is comparable to Rust's
      // "orphan rules" for traits.
      initializedTypeIdentfier.logTypeError(
          ClaroTypeException.forIllegalExportedInitializersBlockReferencingTypeFromDepModule(initializers));
    }
  }

  public boolean assertExpectedProceduresActuallyExported(ScopedHeap scopedHeap) throws ClaroTypeException {
    boolean errorsFound = false;

    // We're going to use a fresh scoped heap when we get the exported procedures from this module because we don't want
    // to worry about conflicting definitions of the signatures in the actual src files scoped heap. We only need this
    //  as a temporary to allow the extraction of exported procedure type signatures.
    ScopedHeap syntheticModuleAPIScopedHeap = new ScopedHeap();
    syntheticModuleAPIScopedHeap.enterNewScope();
    // Setup this synthetic scoped heap with the types that are declared in this module.
    this.registerExportedTypeDefs(syntheticModuleAPIScopedHeap);
    // Register all user-defined-types exported by all the dep modules in the synthetic scoped heap so that the
    // procedure signatures may reference dep module exported types.
    for (Map.Entry<String, SerializedClaroModule.ExportedTypeDefinitions> depModuleExportedTypes :
        ScopedHeap.currProgramDepModuleExportedTypes.entrySet()) {
      String depUniqueModuleName =
          ScopedHeap.getDefiningModuleDisambiguator(Optional.of(depModuleExportedTypes.getKey()));
      // Register all alias defs.
      for (Map.Entry<String, TypeProtos.TypeProto> exportedAliasDef :
          depModuleExportedTypes.getValue().getExportedAliasDefsByNameMap().entrySet()) {
        syntheticModuleAPIScopedHeap.putIdentifierValueAsTypeDef(
            String.format("%s$%s", exportedAliasDef.getKey(), depUniqueModuleName),
            Types.parseTypeProto(exportedAliasDef.getValue()),
            null
        );
      }
      // Register all newtype defs.
      for (Map.Entry<String, SerializedClaroModule.ExportedTypeDefinitions.NewTypeDef> exportedType :
          depModuleExportedTypes.getValue().getExportedNewtypeDefsByNameMap().entrySet()) {
        syntheticModuleAPIScopedHeap.putIdentifierValueAsTypeDef(
            String.format("%s$%s", exportedType.getKey(), depUniqueModuleName),
            Types.parseTypeProto(
                TypeProtos.TypeProto.newBuilder()
                    .setUserDefinedType(exportedType.getValue().getUserDefinedType())
                    .build()),
            null
        );
      }
    }

    // Register any AtomDefinitionStmts found in the module.
    InternalStaticStateUtil.AtomDefinition_CACHE_INDEX_BY_MODULE_AND_ATOM_NAME.build().cellSet().stream()
        .filter(c -> !c.getRowKey().equals(ScopedHeap.getDefiningModuleDisambiguator(Optional.empty())))
        .forEach(c -> {
          String atomName = c.getColumnKey();
          syntheticModuleAPIScopedHeap.putIdentifierValueAsTypeDef(
              atomName,
              scopedHeap.getValidatedIdentifierType(atomName),
              null
          );
          syntheticModuleAPIScopedHeap.initializeIdentifier(atomName);
        });

    for (Map.Entry<String, Types.ProcedureType> expectedExportedProcedureEntry :
        getExportedProcedureSignatureTypes(syntheticModuleAPIScopedHeap).entrySet()) {
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
      if (!(actualIdentifierType.equals(expectedExportedProcedureType)
            && ((Types.ProcedureType) actualIdentifierType).allTransitivelyRequiredContractNamesToGenericArgs.get()
                .equals(expectedExportedProcedureType.allTransitivelyRequiredContractNamesToGenericArgs.get())
            &&
            Objects.equals(
                ((Types.ProcedureType) actualIdentifierType).getAnnotatedBlocking(),
                expectedExportedProcedureType.getAnnotatedBlocking()
            )
      )) {
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

  public void assertDepModulesTransitiveTypeExportsActuallyExported() {
    Set<String> nonExportedDeps =
        Sets.difference(this.depModulesTransitiveTypeExports, ScopedHeap.transitiveExportedDepModules);
    // Stdlib modules are privileged to not require export based on the implication that the modules are
    // all implicitly depended on by all claro_module() targets.
    nonExportedDeps = Sets.difference(nonExportedDeps, ScopedHeap.stdlibDepModules);
    if (!nonExportedDeps.isEmpty()) {
      logError(ClaroTypeException.forModuleAPIReferencesTypeFromTransitiveDepModuleNotExplicitlyExplicitlyExported(nonExportedDeps));
    }
    Set<String> unnecessarilyExportedDeps =
        Sets.difference(ScopedHeap.transitiveExportedDepModules, this.depModulesTransitiveTypeExports);
    if (!unnecessarilyExportedDeps.isEmpty()) {
      logError(ClaroTypeException.forUnnecessaryExportedDepModule(unnecessarilyExportedDeps));
    }
  }

  @AutoValue
  public static abstract class ModuleApiStmtsBuilder {
    public abstract ImmutableList.Builder<ContractProcedureSignatureDefinitionStmt> getProcedureSignaturesBuilder();

    public abstract ImmutableList.Builder<AliasStmt> getAliasDefStmtsBuilder();

    public abstract ImmutableList.Builder<AtomDefinitionStmt> getAtomDefStmtsBuilder();

    public abstract ImmutableList.Builder<NewTypeDefStmt> getNewTypeDefStmtsBuilder();

    public abstract ImmutableMap.Builder<IdentifierReferenceTerm, ImmutableList<ContractProcedureSignatureDefinitionStmt>>
    getInitializersBlocksByTypeName();

    public abstract ImmutableMap.Builder<IdentifierReferenceTerm, ImmutableList<ContractProcedureSignatureDefinitionStmt>>
    getUnwrappersBlocksByTypeName();

    public abstract ImmutableList.Builder<ContractDefinitionStmt> getContractDefsBuilder();

    public abstract ImmutableList.Builder<HttpServiceDefStmt> getHttpServiceDefsBuilder();

    public static ModuleApiStmtsBuilder create() {
      return new AutoValue_ModuleNode_ModuleApiStmtsBuilder(
          ImmutableList.builder(), ImmutableList.builder(), ImmutableList.builder(), ImmutableList.builder(), ImmutableMap.builder(), ImmutableMap.builder(), ImmutableList.builder(), ImmutableList.builder());
    }
  }
}
