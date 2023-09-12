package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.contracts.ContractImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureImplementationStmt;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.runtime_utilities.injector.InjectedKey;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.common.hash.Hashing;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class GenericFunctionDefinitionStmt extends Stmt {
  public String functionName;
  private final ImmutableListMultimap<String, ImmutableList<Type>> requiredContractNamesToGenericArgs;
  private final ImmutableList<String> genericProcedureArgNames;
  private final ImmutableMap<String, TypeProvider> argTypes;
  private final Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes;
  private final Optional<TypeProvider> outputTypeProvider;
  private final StmtListNode stmtListNode;
  private final Boolean explicitlyAnnotatedBlocking;
  private final Optional<ImmutableList<String>> optionalGenericBlockingOnArgs;

  public ProcedureDefinitionStmt genericProcedureDefStmt;

  private boolean alreadyValidatedTypes = false;

  public static final HashBasedTable<String, ImmutableMap<Type, Type>, ProcedureDefinitionStmt>
      monomorphizations = HashBasedTable.create();
  public static final HashBasedTable<String, ImmutableMap<Type, Type>, String>
      alreadyCodegendMonomorphizations = HashBasedTable.create();
  public static final HashMap<String, GenericFunctionDefinitionStmt> genericFunctionDefStmtsByName = Maps.newHashMap();


  public GenericFunctionDefinitionStmt( // FUNCTION
                                        String functionName,
                                        ImmutableListMultimap<String, ImmutableList<Type>> requiredContractNamesToGenericArgs,
                                        ImmutableList<String> genericProcedureArgNames,
                                        ImmutableMap<String, TypeProvider> argTypes,
                                        Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
                                        TypeProvider outputTypeProvider,
                                        StmtListNode stmtListNode,
                                        Boolean explicitlyAnnotatedBlocking,
                                        Optional<ImmutableList<String>> optionalGenericBlockingOnArgs) {
    super(ImmutableList.of());

    this.functionName = functionName;
    this.requiredContractNamesToGenericArgs = requiredContractNamesToGenericArgs;
    this.genericProcedureArgNames = genericProcedureArgNames;
    this.argTypes = argTypes;
    this.optionalInjectedKeysTypes = optionalInjectedKeysTypes;
    this.outputTypeProvider = Optional.of(outputTypeProvider);
    this.stmtListNode = stmtListNode;
    this.explicitlyAnnotatedBlocking = explicitlyAnnotatedBlocking;
    this.optionalGenericBlockingOnArgs = optionalGenericBlockingOnArgs;
  }

  public GenericFunctionDefinitionStmt( // CONSUMER
                                        String functionName,
                                        ImmutableListMultimap<String, ImmutableList<Type>> requiredContractNamesToGenericArgs,
                                        ImmutableList<String> genericProcedureArgNames,
                                        ImmutableMap<String, TypeProvider> argTypes,
                                        Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
                                        StmtListNode stmtListNode,
                                        Boolean explicitlyAnnotatedBlocking,
                                        Optional<ImmutableList<String>> optionalGenericBlockingOnArgs) {
    super(ImmutableList.of());

    this.functionName = functionName;
    this.requiredContractNamesToGenericArgs = requiredContractNamesToGenericArgs;
    this.genericProcedureArgNames = genericProcedureArgNames;
    this.argTypes = argTypes;
    this.optionalInjectedKeysTypes = optionalInjectedKeysTypes;
    this.outputTypeProvider = Optional.empty();
    this.stmtListNode = stmtListNode;
    this.explicitlyAnnotatedBlocking = explicitlyAnnotatedBlocking;
    this.optionalGenericBlockingOnArgs = optionalGenericBlockingOnArgs;
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

  public void registerGenericProcedureTypeProvider(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!this.alreadyValidatedTypes) {
      this.alreadyValidatedTypes = true;

      // Register this instance under its canonicalized name for lookup in later codegen.
      GenericFunctionDefinitionStmt.genericFunctionDefStmtsByName.put(this.functionName, this);

      // Make sure that the procedure name isn't already in use.
      if (scopedHeap.isIdentifierDeclared(this.functionName)) {
        throw ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.functionName);
      }

      // Then, validate that none of the generic type param names are in use.
      for (String typeParamName : this.genericProcedureArgNames) {
        Preconditions.checkState(
            !scopedHeap.isIdentifierDeclared(typeParamName),
            String.format(
                "Generic parameter name `%s` already in use for %s<%s>.",
                typeParamName,
                this.functionName,
                String.join(", ", this.genericProcedureArgNames)
            )
        );
      }

      // The entire goal of this GenericFunctionDefinitionStmt node is literally just to validate
      // that "Monomorphization" will be possible when we come across calls to this procedure.
      // That is, we need to go through with type checking for this procedure def over its generic types.
      // So, we'll know that the given procedure definition will end up being valid across all possible
      // inputs that this procedure can accept. We know which inputs the procedure can accept based
      // on the following factors:
      //   1. The required Contract implementations.
      //   TODO(steving) More coming.. For more details check the feature ideation doc:
      //         https://docs.google.com/document/d/1JvRXy-UwPjEAzVTCAtmVgBzj-tIEfVwIq6bOa3xGTRk/edit#heading=h.8x7278wze2r2

      for (String requiredContract : this.requiredContractNamesToGenericArgs.keySet()) {
        // First thing, check that the named contract even exists.
        if (!scopedHeap.isIdentifierDeclared(requiredContract)) {
          throw ClaroTypeException.forGenericProcedureRequiresUnknownContract(requiredContract, this.functionName);
        }
        Types.$Contract actualContractType = (Types.$Contract) scopedHeap.getValidatedIdentifierType(requiredContract);

        // Check that there are the correct number of type params.
        for (ImmutableList<Type> requiredContractGenericArgs
            : this.requiredContractNamesToGenericArgs.get(requiredContract)) {
          if (actualContractType.getTypeParamNames().size() != requiredContractGenericArgs.size()) {
            throw ClaroTypeException.forGenericProcedureRequiresContractImplementationWithWrongNumberOfTypeParams(
                ContractImplementationStmt.getContractTypeString(requiredContract, requiredContractGenericArgs.stream()
                    .map(Type::toString)
                    .collect(ImmutableList.toImmutableList())),
                ContractImplementationStmt.getContractTypeString(requiredContract, actualContractType.getTypeParamNames())
            );
          }

          // Then, validate that all of the required contract type params are in the generic procedures type params list.
          for (Type typeParamName : requiredContractGenericArgs) {
            Preconditions.checkState(
                this.genericProcedureArgNames.contains(((Types.$GenericTypeParam) typeParamName).getTypeParamName()),
                String.format(
                    "Generic parameter name `%s` required by %s<%s> not included in %s<%s>.",
                    typeParamName,
                    requiredContract,
                    requiredContractGenericArgs.stream().map(Type::toString).collect(Collectors.joining(", ")),
                    this.functionName,
                    String.join(", ", this.genericProcedureArgNames)
                )
            );
          }
        }
      }

      for (String genericArgName : this.genericProcedureArgNames) {
        scopedHeap.putIdentifierValue(
            genericArgName,
            Types.$GenericTypeParam.forTypeParamName(genericArgName),
            null
        );
        scopedHeap.markIdentifierAsTypeDefinition(genericArgName);
      }
      // Define the ProcedureDefinitionStmt to defer to from here on out for the generic, non-monomorphized variant.
      this.genericProcedureDefStmt = generateProcedureDefStmt(this.functionName);
      this.genericProcedureDefStmt.registerProcedureTypeProvider(scopedHeap);
      for (String genericArgName : this.genericProcedureArgNames) {
        scopedHeap.deleteIdentifierValue(genericArgName);
      }

      // Finally, we'll put a callback function into the scoped heap that the type checker at the call site
      // can defer to to both setup monomorphization and retrieve the canonicalized procedure name
      // for the sake of codegen to call the correct monomorphization.
      scopedHeap.putIdentifierValueAtLevel(
          this.functionName,
          genericProcedureDefStmt.resolvedProcedureType,
          (BiFunction<ScopedHeap, ImmutableMap<Type, Type>, String>) this::prepareMonomorphizationForConcreteSignature,
          0
      );
    }
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (InternalStaticStateUtil.GnericProcedureDefinitionStmt_doneWithGenericProcedureTypeValidationPhase) {
      // TODO(steving) Currently I have no better way of preventing the ProgramNode from doing type validation
      //  on this node type twice. So short-circuiting here.
      return;
    }
    InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation = true;
    for (String genericArgName : this.genericProcedureArgNames) {
      scopedHeap.putIdentifierValue(
          genericArgName,
          Types.$GenericTypeParam.forTypeParamName(genericArgName),
          null
      );
      scopedHeap.markIdentifierAsTypeDefinition(genericArgName);
    }

    this.genericProcedureDefStmt.assertExpectedExprTypes(scopedHeap);

    for (String genericArgName : this.genericProcedureArgNames) {
      scopedHeap.deleteIdentifierValue(genericArgName);
    }
    InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation = false;
  }

  private String prepareMonomorphizationForConcreteSignature(
      ScopedHeap scopedHeap,
      ImmutableMap<Type, Type> concreteTypeParams) {
    // First thing first, don't waste effort, short-circuit if we've already seen this monomorphization or if this call
    // is being validated w/in the context of type validating a generic procedure definition before concrete types are known.
    if (GenericFunctionDefinitionStmt.monomorphizations.contains(this.functionName, concreteTypeParams)) {
      return GenericFunctionDefinitionStmt.monomorphizations.get(this.functionName, concreteTypeParams).procedureName;
    }
    if (GenericFunctionDefinitionStmt.alreadyCodegendMonomorphizations.contains(this.functionName, concreteTypeParams)) {
      return GenericFunctionDefinitionStmt.alreadyCodegendMonomorphizations.get(this.functionName, concreteTypeParams);
    }
    if (InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation) {
      return this.functionName;
    }

    ImmutableList.Builder<Type> orderedConcreteTypeParams = ImmutableList.builder();
    for (int i = 0; i < this.genericProcedureArgNames.size(); i++) {
      orderedConcreteTypeParams.add(
          concreteTypeParams.get(Types.$GenericTypeParam.forTypeParamName(this.genericProcedureArgNames.get(i))));
    }

    ProcedureDefinitionStmt monomorphization =
        generateProcedureDefStmt(
            ContractProcedureImplementationStmt.getCanonicalProcedureName(
                /*contractName=*/"$MONOMORPHIZATION", orderedConcreteTypeParams.build(), this.functionName));

    GenericFunctionDefinitionStmt.monomorphizations.put(this.functionName, concreteTypeParams, monomorphization);
    // The type shouldn't ever be used, so don't bother computing the concrete function type, just set it to the generic
    // signature type for simplicity. However, put the monomorphization at the top-level scope along with other function
    // definitions so that it persists across calls, and is not ephemeral.
    scopedHeap.putIdentifierValueAtLevel(
        monomorphization.procedureName,
        scopedHeap.getValidatedIdentifierType(this.functionName),
        null,
        /*scopeLevel=*/0
    );

    // Publish the fact that this monomorphization will be codegen'd. This is specifically useful for the sake of
    // codegen'ing Contract Dynamic Dispatch switch functions which need to know which types they should be codegening
    // for.
    InternalStaticStateUtil.GenericProcedureDefinitionStmt_monomorphizationsByGenericProcedureCanonName
        .put(this.functionName, concreteTypeParams, monomorphization.procedureName);
    return monomorphization.procedureName;
  }

  private ProcedureDefinitionStmt generateProcedureDefStmt(String canonicalProcedureName) {
    if (outputTypeProvider.isPresent()) {
      if (!this.argTypes.isEmpty()) { // FUNCTION
        return new ProcedureDefinitionStmt(
            canonicalProcedureName,
            argTypes,
            optionalInjectedKeysTypes,
            (unusedThisProcedureDefinitionStmt) ->
                (scopedHeap) ->
                    Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
                        argTypes.values()
                            .stream()
                            .map(t -> t.resolveType(scopedHeap))
                            .collect(ImmutableList.toImmutableList()),
                        outputTypeProvider.get().resolveType(scopedHeap),
                        BaseType.FUNCTION,
                        optionalInjectedKeysTypes
                            .map(
                                injectedKeysTypes ->
                                    injectedKeysTypes.stream()
                                        .map(
                                            injectedKey ->
                                                Key.create(injectedKey.getName(), injectedKey.getTypeProvider()
                                                    .resolveType(scopedHeap)))
                                        .collect(Collectors.toSet())
                            )
                            .orElse(Sets.newHashSet()),
                        // In the case of Generic procedures, we need to ensure that the recursive dep procedure type
                        // validation goes first through the GenericFunctionDefinitionStmt rather than skipping straight
                        // to the encapsulated ProcedureDefinitionStmt since this would miss the necessary setup process
                        // needed for generic procedures before type checking could be handled successfully in
                        // ProcedureDefinitionStmt.
                        GenericFunctionDefinitionStmt.this,
                        explicitlyAnnotatedBlocking,
                        optionalGenericBlockingOnArgs
                            .map(genericBlockingOnArgs ->
                                     mapArgNamesToIndex(genericBlockingOnArgs, argTypes.keySet().asList())),
                        Optional.of(this.genericProcedureArgNames),
                        Optional.of(this.requiredContractNamesToGenericArgs)
                    ),
            stmtListNode
        );
      } else { // PROVIDER
        return new ProcedureDefinitionStmt(
            canonicalProcedureName,
            argTypes,
            optionalInjectedKeysTypes,
            (unusedThisProcedureDefinitionStmt) ->
                (scopedHeap) ->
                    Types.ProcedureType.ProviderType.forReturnType(
                        outputTypeProvider.get().resolveType(scopedHeap),
                        BaseType.PROVIDER_FUNCTION,
                        optionalInjectedKeysTypes
                            .map(
                                injectedKeysTypes ->
                                    injectedKeysTypes.stream()
                                        .map(
                                            injectedKey ->
                                                Key.create(injectedKey.getName(), injectedKey.getTypeProvider()
                                                    .resolveType(scopedHeap)))
                                        .collect(Collectors.toSet())
                            )
                            .orElse(Sets.newHashSet()),
                        // In the case of Generic procedures, we need to ensure that the recursive dep procedure type
                        // validation goes first through the GenericFunctionDefinitionStmt rather than skipping straight
                        // to the encapsulated ProcedureDefinitionStmt since this would miss the necessary setup process
                        // needed for generic procedures before type checking could be handled successfully in
                        // ProcedureDefinitionStmt.
                        GenericFunctionDefinitionStmt.this,
                        explicitlyAnnotatedBlocking,
                        Optional.of(this.genericProcedureArgNames),
                        Optional.of(this.requiredContractNamesToGenericArgs)
                    ),
            stmtListNode
        );
      }
    } else { // CONSUMER
      return new ProcedureDefinitionStmt(
          canonicalProcedureName,
          argTypes,
          optionalInjectedKeysTypes,
          (unusedThisProcedureDefinitionStmt) ->
              (scopedHeap) ->
                  Types.ProcedureType.ConsumerType.forConsumerArgTypes(
                      argTypes.values()
                          .stream()
                          .map(t -> t.resolveType(scopedHeap))
                          .collect(ImmutableList.toImmutableList()),
                      BaseType.CONSUMER_FUNCTION,
                      optionalInjectedKeysTypes
                          .map(
                              injectedKeysTypes ->
                                  injectedKeysTypes.stream()
                                      .map(
                                          injectedKey ->
                                              Key.create(injectedKey.getName(), injectedKey.getTypeProvider()
                                                  .resolveType(scopedHeap)))
                                      .collect(Collectors.toSet())
                          )
                          .orElse(Sets.newHashSet()),
                      // In the case of Generic procedures, we need to ensure that the recursive dep procedure type
                      // validation goes first through the GenericFunctionDefinitionStmt rather than skipping straight
                      // to the encapsulated ProcedureDefinitionStmt since this would miss the necessary setup process
                      // needed for generic procedures before type checking could be handled successfully in
                      // ProcedureDefinitionStmt.
                      GenericFunctionDefinitionStmt.this,
                      explicitlyAnnotatedBlocking,
                      optionalGenericBlockingOnArgs
                          .map(genericBlockingOnArgs ->
                                   mapArgNamesToIndex(genericBlockingOnArgs, argTypes.keySet().asList())),
                      Optional.of(this.genericProcedureArgNames),
                      Optional.of(this.requiredContractNamesToGenericArgs)
                  ),
          stmtListNode
      );
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource monomorphizationsCodeGen = GeneratedJavaSource.forJavaSourceBody(new StringBuilder());

    // Since we've already done type validation on this generic function before, let's skip unused checks
    // since we know anything that would show up now would be a complete red-herring if it didn't already
    // show up before.
    boolean originalCheckUnused = scopedHeap.checkUnused;
    scopedHeap.checkUnused = false;

    // Every time that I do type validation on a new monomorphization, there's a chance that the function definition's
    // body contained another call to a generic function. In that case, it's possible that a new monomorphization for
    // that function was just discovered. So I'll need to keep doing codegen until I know that no more monomorphizations
    // were encountered.
    while (GenericFunctionDefinitionStmt.monomorphizations.size() > 0) {
      // Iterate over a copy of the table that might actually get modified during this loop.
      ImmutableTable<String, ImmutableMap<Type, Type>, ProcedureDefinitionStmt>
          monomorphizationsCopy = ImmutableTable.copyOf(GenericFunctionDefinitionStmt.monomorphizations);
      // Clear every monomorphization concrete signature map, since we've consumed that now, keeping each map for each
      // generic procedure name so that we're able to actually add entries to it uninterrupted during type validation below.
      GenericFunctionDefinitionStmt.monomorphizations.clear();
      for (String currGenericProcedureName : monomorphizationsCopy.rowKeySet()) {
        for (Map.Entry<ImmutableMap<Type, Type>, ProcedureDefinitionStmt> preparedMonomorphization
            : monomorphizationsCopy.row(currGenericProcedureName).entrySet()) {
          ImmutableMap<Type, Type> concreteTypeParams = preparedMonomorphization.getKey();

          // This is technically a recursive function, so we need to ensure that we haven't reached a monomorphization
          // that's already been codegen'd.
          if (GenericFunctionDefinitionStmt.alreadyCodegendMonomorphizations
              .contains(currGenericProcedureName, concreteTypeParams)) {
            continue;
          }

          // Do codegen for the current monomorphization and merge it into the running codegen for the current set of
          // generic procedures.
          monomorphizationsCodeGen =
              monomorphizationsCodeGen.createMerged(
                  getMonomorphizationCodeGen(
                      scopedHeap,
                      currGenericProcedureName,
                      preparedMonomorphization.getKey(),
                      preparedMonomorphization.getValue()
                  ));
        }
      }
    }
    scopedHeap.checkUnused = originalCheckUnused;
    return monomorphizationsCodeGen;
  }

  public static GeneratedJavaSource getMonomorphizationCodeGen(
      ScopedHeap scopedHeap,
      String currGenericProcedureName,
      ImmutableMap<Type, Type> concreteTypeParams,
      ProcedureDefinitionStmt monomorphization) {
    GeneratedJavaSource monomorphizationsCodeGen;

    // Now, some initial cleanup based on some unwanted side-effects of the FunctionCallExpr which already
    // puts the generic function's canonicalized name in the scoped heap in order to reuse the rest of its
    // non-generic call flow which marks the called function used...So here I'll remove the existing declaration
    // so that the ProcedureDefinitionStmt we're going to run validation on soon doesn't get tripped up on this.
    scopedHeap.deleteIdentifierValue(monomorphization.procedureName);

    ImmutableList<String> currGenericProcedureArgNames =
        GenericFunctionDefinitionStmt.genericFunctionDefStmtsByName.get(currGenericProcedureName).genericProcedureArgNames;

    for (int i = 0; i < currGenericProcedureArgNames.size(); i++) {
      String currContractGenericArg = currGenericProcedureArgNames.get(i);
      // This is temporary to this specific monomorphization and needs to be removed.
      scopedHeap.putIdentifierValue(
          currContractGenericArg,
          concreteTypeParams.get(Types.$GenericTypeParam.forTypeParamName(currContractGenericArg)),
          null
      );
      scopedHeap.markIdentifierAsTypeDefinition(currContractGenericArg);
    }

    monomorphization.registerProcedureTypeProvider(scopedHeap);
    try {
      monomorphization.assertExpectedExprTypes(scopedHeap);
    } catch (ClaroTypeException e) {
      throw new RuntimeException(e);
    }
    // At the last second, right before doing codegen, I want to actually make codegen simplify the
    // monomorphization's name into something shorter, but still avoiding collisions with any other identifiers.
    // For this, I'll use sha256 hashing. The reason is that Java has started to complain about some generated
    // class names being too long (e.g. one was >550 chars for a monomorphization over a complex data structure).
    String originalMonomorphizationName = monomorphization.procedureName;
    // It's possible that this generic procedure was defined w/in a Contract impl, in which case we can't just
    // codegen directly into the top-level, this GeneratedJavaSource needs to be set aside to be included inline
    // with the Contract impl's codegen.
    if (!InternalStaticStateUtil.ContractDefinitionStmt_genericContractImplProceduresCanonicalNames
        .contains(currGenericProcedureName)) {
      monomorphization.procedureName = currGenericProcedureName + "__" + Hashing.sha256()
          .hashUnencodedChars(monomorphization.procedureName)
          .toString();
      monomorphizationsCodeGen = monomorphization.generateJavaSourceOutput(scopedHeap);
    } else {
      // Need to drop the "$ContractName::<Concrete,Types>___" prefix to make it callable.
      monomorphization.procedureName =
          // TODO(steving) This substring approach is fairly hacky...is there a better solution?
          currGenericProcedureName.substring(currGenericProcedureName.indexOf("___") + 3)
          + "__" + Hashing.sha256()
              .hashUnencodedChars(monomorphization.procedureName)
              .toString();
      GeneratedJavaSource currMonomorphizationCodeGen = monomorphization.generateJavaSourceOutput(scopedHeap);
      InternalStaticStateUtil.GenericProcedureDefinitionStmt_alreadyCodegenedContractProcedureMonomorphizations
          .put(currGenericProcedureName, concreteTypeParams, currMonomorphizationCodeGen);

      // Intentionally not returning any actual monomorphization for this contract procedure.
      monomorphizationsCodeGen = GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
    }
    // We only need the hash name during codegen, we'll keep the readable name during internal evaluation to make
    // debugging easier.
    monomorphization.procedureName = originalMonomorphizationName;

    for (int i = 0; i < currGenericProcedureArgNames.size(); i++) {
      // This is temporary to this specific monomorphization and needs to be removed.
      scopedHeap.deleteIdentifierValue(currGenericProcedureArgNames.get(i));
    }

    // Mark this as already monomorphized so that if we run across it again in another generic function we can
    // skip monomorphizing it again.
    alreadyCodegendMonomorphizations.put(currGenericProcedureName, concreteTypeParams, monomorphization.procedureName);
    return monomorphizationsCodeGen;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return null;
  }
}
