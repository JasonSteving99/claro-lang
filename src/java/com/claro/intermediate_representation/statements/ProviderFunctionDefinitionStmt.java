package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.runtime_utilities.injector.InjectedKey;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.Optional;
import java.util.stream.Collectors;


public class ProviderFunctionDefinitionStmt extends ProcedureDefinitionStmt {

  public ProviderFunctionDefinitionStmt(
      String providerName,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode) {
    this(providerName, Optional.empty(), outputTypeProvider, stmtListNode);
  }

  public ProviderFunctionDefinitionStmt(
      String providerName,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode) {
    super(
        providerName,
        optionalInjectedKeysTypes,
        (procedureDefinitionStmt) ->
            (scopedHeap) ->
                Types.ProcedureType.ProviderType.forReturnType(
                    outputTypeProvider.resolveType(scopedHeap),
                    optionalInjectedKeysTypes
                        .map(
                            injectedKeysTypes ->
                                injectedKeysTypes.stream()
                                    .map(
                                        injectedKey ->
                                            new Key(injectedKey.name, injectedKey.typeProvider.resolveType(scopedHeap)))
                                    .collect(Collectors.toSet())
                        )
                        .orElse(Sets.newHashSet()),
                    procedureDefinitionStmt,
                    () ->
                        ProcedureDefinitionStmt.optionalActiveProcedureDefinitionStmt
                            .map(activeProcedureDefinitionStmt -> activeProcedureDefinitionStmt.resolvedProcedureType)
                ),
        stmtListNode
    );
  }

  // Use this constructor for a Provider function declared using a Lambda form so that we can use the
  // custom BaseType that will perform the correct codegen.
  public ProviderFunctionDefinitionStmt(
      String providerName,
      BaseType baseType,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode) {
    super(
        providerName,
        (procedureDefinitionStmt) ->
            (scopedHeap) ->
                Types.ProcedureType.ProviderType.forReturnType(
                    outputTypeProvider.resolveType(scopedHeap),
                    baseType,
                    Sets.newHashSet(),
                    procedureDefinitionStmt,
                    () ->
                        ProcedureDefinitionStmt.optionalActiveProcedureDefinitionStmt
                            .map(activeProcedureDefinitionStmt -> activeProcedureDefinitionStmt.resolvedProcedureType)
                ),
        stmtListNode
    );
  }
}
