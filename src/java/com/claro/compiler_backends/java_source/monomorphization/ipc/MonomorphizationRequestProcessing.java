package com.claro.compiler_backends.java_source.monomorphization.ipc;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.compiler_backends.java_source.JavaSourceCompilerBackend;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages.MonomorphizationRequest;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.statements.GenericFunctionDefinitionStmt;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MonomorphizationRequestProcessing {
  public static ImmutableMap<String, GenericFunctionDefinitionStmt> genericFunctionDefinitionStmtsByName = null;
  private static final HashSet<String> alreadyTypeCheckedGenericFunctionDefinitionStmts = Sets.newHashSet();

  public static String handleMonomorphizationRequest(String base64EncodedMonomorphizationRequest) {
    MonomorphizationRequest monomorphizationRequest;
    try {
      try {
        monomorphizationRequest = MonomorphizationRequest.parseFrom(
            BaseEncoding.base64Url().decode(base64EncodedMonomorphizationRequest));
      } catch (InvalidProtocolBufferException e) {
        throw new RuntimeException("Internal Compiler Error! Failed to parse MonomorphizationRequest!", e);
      }

      // First things first, this monomorphization may need its GenericFunctionDefinition type checked as setup in case
      // this is the first monomorphization request coming in for this particular procedure.
      if (!alreadyTypeCheckedGenericFunctionDefinitionStmts.contains(monomorphizationRequest.getProcedureName())) {
        genericFunctionDefinitionStmtsByName.get(monomorphizationRequest.getProcedureName())
            .assertExpectedExprTypes(JavaSourceCompilerBackend.scopedHeap);
        // Ensure that this doesn't get type checked all over again for no reason.
        alreadyTypeCheckedGenericFunctionDefinitionStmts.add(monomorphizationRequest.getProcedureName());
      }

      return BaseEncoding.base64Url().encode(
          IPCMessages.MonomorphizationResponse.newBuilder()
              .addAllLocalModuleMonomorphizations(
                  getLocalMonomorphizationsForMonomorphizationRequest(monomorphizationRequest))
              .addAllTransitiveDepModuleMonomorphizationRequests(
                  InternalStaticStateUtil.JavaSourceCompilerBackend_depModuleGenericMonomoprhizationsNeeded.entries()
                      .stream()
                      .map(e ->
                               IPCMessages.MonomorphizationResponse.TransitiveDepModuleMonomorphizationRequest
                                   .newBuilder()
                                   .setUniqueModuleName(
                                       ScopedHeap.getDefiningModuleDisambiguator(Optional.of(e.getKey())))
                                   .setMonomorphizationRequest(e.getValue())
                                   .build())
                      .collect(Collectors.toList()))
              .build().toByteArray());
    } catch (Exception e) {
      // If there's any sort of exception during the actual compilation logic itself, I really need some way to diagnose
      // that in the main coordinator process as debugging the dep module processes is a painful process. So, instead,
      // I'll format an error message here and convey the problem to the coordinator via a proper error field in the
      // MonomorphizationResponse, leaving everything else unset. The coordinator should then check for errors before
      // proceeding.
      return BaseEncoding.base64Url().encode(
          IPCMessages.MonomorphizationResponse.newBuilder()
              .setOptionalErrorMessage(
                  "Internal Compiler Error! Exception thrown during MonomorphizationRequest handling: "
                  + e.getMessage() + "\n\t" + Joiner.on("\n\t").join(e.getStackTrace())
                  + "\n\tCaused by:\n\t" + Optional.ofNullable(e.getCause())
                      .map(cause -> Joiner.on("\n\t").join(cause.getStackTrace())).orElse("N/a"))
              .build().toByteArray());
    }
  }

  @SuppressWarnings("unchecked")
  public static ImmutableList<IPCMessages.MonomorphizationResponse.Monomorphization>
  getLocalMonomorphizationsForMonomorphizationRequest(
      MonomorphizationRequest monomorphizationRequest) {
    ImmutableList<String> requestedProcedureGenericTypeParamNames =
        ((Types.ProcedureType) JavaSourceCompilerBackend.scopedHeap
            .getValidatedIdentifierType(monomorphizationRequest.getProcedureName()))
            .getGenericProcedureArgNames().get();
    ImmutableMap<Type, Type> concreteTypeParams =
        IntStream.range(0, monomorphizationRequest.getConcreteTypeParamsCount()).boxed().collect(
            ImmutableMap.toImmutableMap(
                i -> Types.$GenericTypeParam.forTypeParamName(requestedProcedureGenericTypeParamNames.get(i)),
                i -> Types.parseTypeProto(monomorphizationRequest.getConcreteTypeParams(i))
            ));
    ((BiFunction<ScopedHeap, ImmutableMap<Type, Type>, String>)
         JavaSourceCompilerBackend.scopedHeap.getIdentifierValue(monomorphizationRequest.getProcedureName()))
        .apply(
            JavaSourceCompilerBackend.scopedHeap,
            concreteTypeParams
        );

    // Before invoking the codegen, in order to allow for any user-defined types whose definition may not already be
    // known by this compilation unit's dep subgraph, I need to do some preliminary setup of metadata associated with
    // any user-defined types used as concrete type params in this monomorphization request.
    for (MonomorphizationRequest.UserDefinedTypeMetadata userDefinedTypeMetadata :
        monomorphizationRequest.getUserDefinedTypeConcreteTypeParamsMetadataList()) {
      TypeProtos.UserDefinedType userDefinedType = userDefinedTypeMetadata.getType();
      String disambiguatedIdentifier =
          String.format("%s$%s", userDefinedType.getTypeName(), userDefinedType.getDefiningModuleDisambiguator());
      // If this type has already been observed either b/c it was defined w/in this compilation unit's dep subgraph, or
      // b/c it has already been seen in a prior MonomorphizationRequest, just move on, nothing to see here.
      if (Types.UserDefinedType.$resolvedWrappedTypes.containsKey(disambiguatedIdentifier)) {
        continue;
      }
      if (userDefinedType.getParameterizedTypesCount() > 0) {
        Types.UserDefinedType.$typeParamNames.put(
            disambiguatedIdentifier, ImmutableList.copyOf(userDefinedTypeMetadata.getTypeParamNamesList()));
      }
      Types.UserDefinedType.$resolvedWrappedTypes.put(
          disambiguatedIdentifier,
          Types.parseTypeProto(userDefinedTypeMetadata.getWrappedType())
      );
    }

    // Before invoking the codegen, in order to allow for a generic procedure requiring some contract impl to be called
    // over some contract implementation that is not known by this module in isolation (it comes from some module
    // "above" it in the dependency tree), I need to do some preliminary setup of the required contract impls used in
    // this monomorphization request. This may "overwrite" some already-registered contract implementations, but they
    // will be the same, so I'm not going to bother checking first.
    for (IPCMessages.ExportedContractImplementation contractImpl : monomorphizationRequest.getRequiredContractImplementationsList()) {
      JavaSourceCompilerBackend.registerExportedContractImplementation(
          contractImpl.getImplementedContractName(),
          contractImpl.getContractImplDefiningModuleDescriptor().getProjectPackage(),
          contractImpl.getContractImplDefiningModuleDescriptor().getUniqueModuleName(),
          contractImpl.getConcreteTypeParamsList()
              .stream()
              .map(Types::parseTypeProto)
              .collect(ImmutableList.toImmutableList()),
          contractImpl.getConcreteSignaturesList().stream()
              .collect(ImmutableMap.toImmutableMap(
                  IPCMessages.ExportedContractImplementation.Procedure::getName,
                  MonomorphizationRequestProcessing::getProcedureTypeFromProto
              )),
          JavaSourceCompilerBackend.scopedHeap
      );
      // Now, this dep module monomorphization subprocess has been handed a contract implementation that possibly came
      // from a module that hasn't been registered yet, so just for the sake of it being accessible, register it. Don't
      // bother checking if it was already registered, b/c this would just rewrite the same data.
      ScopedHeap.currProgramDepModules.put(
          // There's no user-defined name for this module, so just use it's full unique module name. This also avoids
          // any concerns of accidentally overwriting some other module.
          contractImpl.getContractImplDefiningModuleDescriptor().getUniqueModuleName(),
          // Mark it used because we're literally using it right now.
          /*isUsed=*/true,
          SerializedClaroModule.UniqueModuleDescriptor.newBuilder()
              .setProjectPackage(contractImpl.getContractImplDefiningModuleDescriptor().getProjectPackage())
              .setUniqueModuleName(contractImpl.getContractImplDefiningModuleDescriptor().getUniqueModuleName())
              .build()
      );
    }

    ImmutableList.Builder<IPCMessages.MonomorphizationResponse.Monomorphization> resBuilder = ImmutableList.builder();
    // Request codegen for just the specifically monomorphization requested. This may have resulted in transitive
    // dep monomorphizations being necessary and they must be handled explicitly following this.
    Node.GeneratedJavaSource monoCodegen =
        GenericFunctionDefinitionStmt.getMonomorphizationCodeGen(
            JavaSourceCompilerBackend.scopedHeap,
            monomorphizationRequest.getProcedureName(),
            concreteTypeParams,
            GenericFunctionDefinitionStmt.monomorphizations.get(monomorphizationRequest.getProcedureName(), concreteTypeParams)
        );
    resBuilder.add(
        IPCMessages.MonomorphizationResponse.Monomorphization.newBuilder()
            .setMonomorphizationRequest(monomorphizationRequest)
            .setMonomorphizationCodegen(
                monoCodegen.optionalStaticPreambleStmts().get().toString() +
                monoCodegen.optionalStaticDefinitions().get().toString()
            ).build()
    );

    // Now, handle any transitive local monomorphizations that were discovered as a result of monomorphizing the
    // requested generic procedure.
    while (!GenericFunctionDefinitionStmt.monomorphizations.isEmpty()) {
      // Iterate over a copy of the table that might actually get modified during this loop.
      ImmutableTable<String, ImmutableMap<Type, Type>, ProcedureDefinitionStmt>
          monomorphizationsCopy = ImmutableTable.copyOf(GenericFunctionDefinitionStmt.monomorphizations);
      // Clear every monomorphization concrete signature map, since we've consumed that now, keeping each map for each
      // generic procedure name so that we're able to actually add entries to it uninterrupted during type validation below.
      GenericFunctionDefinitionStmt.monomorphizations.clear();
      for (String currGenericProcedureName : monomorphizationsCopy.rowKeySet()) {
        for (Map.Entry<ImmutableMap<Type, Type>, ProcedureDefinitionStmt> preparedMonomorphization
            : monomorphizationsCopy.row(currGenericProcedureName).entrySet()) {
          concreteTypeParams = preparedMonomorphization.getKey();

          // This is, in a way, a recursive algorithm, so we need to ensure that we haven't reached a monomorphization
          // that's already been codegen'd.
          if (GenericFunctionDefinitionStmt.alreadyCodegendMonomorphizations
              .contains(currGenericProcedureName, concreteTypeParams)) {
            continue;
          }

          monoCodegen =
              GenericFunctionDefinitionStmt.getMonomorphizationCodeGen(
                  JavaSourceCompilerBackend.scopedHeap,
                  currGenericProcedureName,
                  preparedMonomorphization.getKey(),
                  preparedMonomorphization.getValue()
              );
          resBuilder.add(
              IPCMessages.MonomorphizationResponse.Monomorphization.newBuilder()
                  .setMonomorphizationRequest(
                      createMonomorphizationRequest(
                          currGenericProcedureName,
                          preparedMonomorphization.getKey().values().stream()
                              .map(Type::toProto)
                              .collect(ImmutableList.toImmutableList())
                      ))
                  .setMonomorphizationCodegen(
                      monoCodegen.optionalStaticPreambleStmts().get().toString() +
                      monoCodegen.optionalStaticDefinitions().get().toString()
                  ).build()
          );
        }
      }
    }

    return resBuilder.build();
  }

  private static Types.ProcedureType getProcedureTypeFromProto(
      IPCMessages.ExportedContractImplementation.Procedure procedureProto) {
    TypeProtos.TypeProto procedureTypeProto;
    switch (procedureProto.getProcedureTypeCase()) {
      case FUNCTION:
        procedureTypeProto = TypeProtos.TypeProto.newBuilder().setFunction(procedureProto.getFunction()).build();
        break;
      case CONSUMER:
        procedureTypeProto = TypeProtos.TypeProto.newBuilder().setConsumer(procedureProto.getConsumer()).build();
        break;
      case PROVIDER:
        procedureTypeProto = TypeProtos.TypeProto.newBuilder().setProvider(procedureProto.getProvider()).build();
        break;
      default:
        throw new RuntimeException("Internal Compiler Error! Encountered unexpected procedure type while parsing newtype definition from .claro_module.");
    }
    return (Types.ProcedureType) Types.parseTypeProto(procedureTypeProto);
  }

  private static MonomorphizationRequest createMonomorphizationRequest(
      String currGenericProcedureName, ImmutableList<TypeProtos.TypeProto> concreteTypeParamProtos) {
    return MonomorphizationRequest.newBuilder()
        .setProcedureName(currGenericProcedureName)
        .addAllConcreteTypeParams(concreteTypeParamProtos)
        // Can set metadata even for types that potentially came from the coordinator rather than
        // this current dep module, because the only way this transitive procedure is called w/ them
        // is if the types were originally received from the coordinator in which case they're
        // already setup in Types.UserDefinedType's static state to be re-referenced here.
        .addAllUserDefinedTypeConcreteTypeParamsMetadata(
            concreteTypeParamProtos.stream().filter(TypeProtos.TypeProto::hasUserDefinedType)
                .map(t -> {
                  String disambiguatedIdentifier =
                      String.format(
                          "%s$%s",
                          t.getUserDefinedType().getTypeName(),
                          t.getUserDefinedType().getDefiningModuleDisambiguator()
                      );
                  return MonomorphizationRequest.UserDefinedTypeMetadata.newBuilder()
                      .setType(t.getUserDefinedType())
                      .addAllTypeParamNames(
                          Optional.ofNullable(Types.UserDefinedType.$typeParamNames.get(disambiguatedIdentifier))
                              .orElse(ImmutableList.of()))
                      .setWrappedType(
                          Types.UserDefinedType.$resolvedWrappedTypes.get(disambiguatedIdentifier)
                              .toProto())
                      .build();
                })
                .collect(Collectors.toList()))
        .build();
  }
}
