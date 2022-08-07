package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.runtime_utilities.injector.InjectedKey;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.Optional;
import java.util.stream.Collectors;


public class FunctionDefinitionStmt extends ProcedureDefinitionStmt {

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, TypeProvider> argTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode,
      boolean explicitlyAnnotatedBlocking) {
    this(
        functionName,
        argTypes,
        Optional.empty(),
        outputTypeProvider,
        stmtListNode,
        explicitlyAnnotatedBlocking
    );
  }

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, TypeProvider> argTypes,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode,
      boolean explicitlyAnnotatedBlocking) {
    super(
        functionName,
        argTypes,
        optionalInjectedKeysTypes,
        (thisProcedureDefinitionStmt) ->
            (scopedHeap) ->
                Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
                    argTypes.values()
                        .stream()
                        .map(t -> t.resolveType(scopedHeap))
                        .collect(ImmutableList.toImmutableList()),
                    outputTypeProvider.resolveType(scopedHeap),
                    BaseType.FUNCTION,
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
                    thisProcedureDefinitionStmt,
                    () ->
                        InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
                            .map(activeProcedureDefinitionStmt ->
                                     ((ProcedureDefinitionStmt) activeProcedureDefinitionStmt).resolvedProcedureType),
                    explicitlyAnnotatedBlocking
                ),
        stmtListNode
    );
  }

  // Use this constructor for a Function declared using a Lambda form so that we can use the
  // custom BaseType that will perform the correct codegen.
  public FunctionDefinitionStmt(
      String functionName,
      BaseType baseType,
      ImmutableMap<String, TypeProvider> argTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode) {
    super(
        functionName,
        argTypes,
        Optional.empty(),
        (thisProcedureDefinitionStmt) ->
            (scopedHeap) ->
                Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
                    argTypes.values()
                        .stream()
                        .map(t -> t.resolveType(scopedHeap))
                        .collect(ImmutableList.toImmutableList()),
                    outputTypeProvider.resolveType(scopedHeap),
                    baseType,
                    Sets.newHashSet(), // no directly used keys.
                    thisProcedureDefinitionStmt,
                    () ->
                        InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
                            .map(activeProcedureDefinitionStmt ->
                                     ((ProcedureDefinitionStmt) activeProcedureDefinitionStmt).resolvedProcedureType),
                    /*explicitlyAnnotatedBlocking=*/ false
                ),
        stmtListNode
    );
  }
}
