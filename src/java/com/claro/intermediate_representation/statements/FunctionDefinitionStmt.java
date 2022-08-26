package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.runtime_utilities.injector.InjectedKey;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Optional;
import java.util.stream.Collectors;


public class FunctionDefinitionStmt extends ProcedureDefinitionStmt {

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, TypeProvider> argTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode,
      Boolean explicitlyAnnotatedBlocking,
      Optional<ImmutableList<String>> optionalGenericBlockingOn) {
    this(
        functionName,
        argTypes,
        Optional.empty(),
        outputTypeProvider,
        stmtListNode,
        explicitlyAnnotatedBlocking,
        optionalGenericBlockingOn
    );
  }

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, TypeProvider> argTypes,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode,
      Boolean explicitlyAnnotatedBlocking) {
    this(
        functionName,
        argTypes,
        optionalInjectedKeysTypes,
        outputTypeProvider,
        stmtListNode,
        explicitlyAnnotatedBlocking,
        Optional.empty()
    );
  }

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, TypeProvider> argTypes,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode,
      Boolean explicitlyAnnotatedBlocking,
      Optional<ImmutableList<String>> optionalGenericBlockingOnArgs) {
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
                    explicitlyAnnotatedBlocking,
                    optionalGenericBlockingOnArgs
                        .map(genericBlockingOnArgs ->
                                 mapArgNamesToIndex(genericBlockingOnArgs, argTypes.keySet().asList()))
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
                    /*explicitlyAnnotatedBlocking=*/ false,
                    Optional.empty()
                ),
        stmtListNode
    );
  }

  private static ImmutableSet<Integer> mapArgNamesToIndex(ImmutableList<String> argNames, ImmutableList<String> args) {
    ImmutableSet.Builder<Integer> res = ImmutableSet.builder();
    for (int i = 0; i < args.size(); i++) {
      if (argNames.contains(args.get(i))) {
        res.add(i);
      }
    }
    return res.build();
  }
}
