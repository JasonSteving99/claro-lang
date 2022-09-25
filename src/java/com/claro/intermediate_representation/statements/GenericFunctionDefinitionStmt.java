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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class GenericFunctionDefinitionStmt extends Stmt {
  private final String functionName;
  private final ImmutableMap<String, ImmutableList<Types.$GenericTypeParam>> requiredContractNamesToGenericArgs;
  private final ImmutableList<String> genericProcedureArgNames;
  private final ImmutableMap<String, TypeProvider> argTypes;
  private final Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes;
  private final TypeProvider outputTypeProvider;
  private final StmtListNode stmtListNode;
  private final Boolean explicitlyAnnotatedBlocking;
  private final Optional<ImmutableList<String>> optionalGenericBlockingOnArgs;

  private boolean alreadyValidatedTypes = false;

  private static HashBasedTable<String, ImmutableMap<Types.$GenericTypeParam, Type>, ProcedureDefinitionStmt>
      monomorphizations = HashBasedTable.create();
  private static final HashMap<String, GenericFunctionDefinitionStmt> genericFunctionDefStmtsByName = Maps.newHashMap();

  public GenericFunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, ImmutableList<Types.$GenericTypeParam>> requiredContractNamesToGenericArgs,
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
    this.outputTypeProvider = outputTypeProvider;
    this.stmtListNode = stmtListNode;
    this.explicitlyAnnotatedBlocking = explicitlyAnnotatedBlocking;
    this.optionalGenericBlockingOnArgs = optionalGenericBlockingOnArgs;

    GenericFunctionDefinitionStmt.genericFunctionDefStmtsByName.put(this.functionName, this);
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

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!this.alreadyValidatedTypes) {
      this.alreadyValidatedTypes = true;

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
        ImmutableList<Types.$GenericTypeParam> requiredContractGenericArgs =
            this.requiredContractNamesToGenericArgs.get(requiredContract);
        if (actualContractType.getTypeParamNames().size() != requiredContractGenericArgs.size()) {
          throw ClaroTypeException.forGenericProcedureRequiresContractImplementationWithWrongNumberOfTypeParams(
              ContractImplementationStmt.getContractTypeString(requiredContract, requiredContractGenericArgs.stream()
                  .map(Type::toString)
                  .collect(ImmutableList.toImmutableList())),
              ContractImplementationStmt.getContractTypeString(requiredContract, actualContractType.getTypeParamNames())
          );
        }

        // Then, validate that all of the required contract type params are in the generic procedures type params list.
        for (Types.$GenericTypeParam typeParamName : requiredContractGenericArgs) {
          Preconditions.checkState(
              this.genericProcedureArgNames.contains(typeParamName.getTypeParamName()),
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

      InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation = true;
      for (String genericArgName : this.genericProcedureArgNames) {
        scopedHeap.putIdentifierValue(
            genericArgName,
            null,
            TypeProvider.ImmediateTypeProvider.of(Types.$GenericTypeParam.forTypeParamName(genericArgName))
        );
      }

      ProcedureDefinitionStmt genericProcedureDefStmt = generateProcedureDefStmt(this.functionName);
      genericProcedureDefStmt.registerProcedureTypeProvider(scopedHeap);
      genericProcedureDefStmt.assertExpectedExprTypes(scopedHeap);

      for (String genericArgName : this.genericProcedureArgNames) {
        scopedHeap.deleteIdentifierValue(genericArgName);
      }
      InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation = false;

      // Finally, we'll put a callback function into the scoped heap that the type checker at the call site
      // can defer to to both setup monomorphization and retrieve the canonicalized procedure name
      // for the sake of codegen to call the correct monomorphization.
      scopedHeap.putIdentifierValue(
          this.functionName,
          genericProcedureDefStmt.resolvedProcedureType,
          (BiFunction<ScopedHeap, ImmutableMap<Types.$GenericTypeParam, Type>, String>)
              this::prepareMonomorphizationForConcreteSignature
      );
    }
  }

  private String prepareMonomorphizationForConcreteSignature(
      ScopedHeap scopedHeap,
      ImmutableMap<Types.$GenericTypeParam, Type> concreteTypeParams) {
    // First thing first, don't waste effort, short-circuit if we've already seen this monomorphization or if this call
    // is being validated w/in the context of type validating a generic procedure definition before concrete types are known.
    if (GenericFunctionDefinitionStmt.monomorphizations.contains(this.functionName, concreteTypeParams)) {
      return GenericFunctionDefinitionStmt.monomorphizations.get(this.functionName, concreteTypeParams).procedureName;
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
    scopedHeap.observeIdentifier(monomorphization.procedureName, null);

    return monomorphization.procedureName;
  }

  private ProcedureDefinitionStmt generateProcedureDefStmt(String canonicalProcedureName) {
    return new ProcedureDefinitionStmt(
        canonicalProcedureName,
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
                                 mapArgNamesToIndex(genericBlockingOnArgs, argTypes.keySet().asList())),
                    Optional.of(this.genericProcedureArgNames),
                    Optional.of(this.requiredContractNamesToGenericArgs)
                ),
        stmtListNode
    );
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource monomorphizationsCodeGen = GeneratedJavaSource.forJavaSourceBody(new StringBuilder());

    // Every time that I do type validation on a new monomorphization, there's a chance that the function definition's
    // body contained another call to a generic function. In that case, it's possible that a new monomorphization for
    // that function was just discovered. So I'll need to keep doing codegen until I know that no more monomorphizations
    // were encountered.
    while (GenericFunctionDefinitionStmt.monomorphizations.size() > 0) {
      // Iterate over a copy of the table that might actually get modified during this loop.
      ImmutableTable<String, ImmutableMap<Types.$GenericTypeParam, Type>, ProcedureDefinitionStmt>
          monomorphizationsCopy = ImmutableTable.copyOf(GenericFunctionDefinitionStmt.monomorphizations);
      // Clear every monomorphization concrete signature map, since we've consumed that now, keeping each map for each
      // generic procedure name so that we're able to actually add entries to it uninterrupted during type validation below.
      GenericFunctionDefinitionStmt.monomorphizations.clear();
      for (String currGenericProcedureName : monomorphizationsCopy.rowKeySet()) {
        ImmutableList<String> currGenericProcedureArgNames =
            GenericFunctionDefinitionStmt.genericFunctionDefStmtsByName.get(currGenericProcedureName).genericProcedureArgNames;
        for (Map.Entry<ImmutableMap<Types.$GenericTypeParam, Type>, ProcedureDefinitionStmt> preparedMonomorphization
            : monomorphizationsCopy.row(currGenericProcedureName).entrySet()) {
          ImmutableMap<Types.$GenericTypeParam, Type> concreteTypeParams = preparedMonomorphization.getKey();
          ProcedureDefinitionStmt monomorphization = preparedMonomorphization.getValue();

          // Now, some initial cleanup based on some unwanted side-effects of the FunctionCallExpr which already
          // puts the generic function's canonicalized name in the scoped heap in order to reuse the rest of its
          // non-generic call flow which marks the called function used...So here I'll remove the existing declaration
          // so that the ProcedureDefinitionStmt we're going to run validation on soon doesn't get tripped up on this.
          scopedHeap.deleteIdentifierValue(monomorphization.procedureName);

          for (int i = 0; i < currGenericProcedureArgNames.size(); i++) {
            String currContractGenericArg = currGenericProcedureArgNames.get(i);
            // This is temporary to this specific monomorphization and needs to be removed.
            scopedHeap.putIdentifierValue(
                currContractGenericArg,
                null,
                TypeProvider.ImmediateTypeProvider.of(
                    concreteTypeParams.get(Types.$GenericTypeParam.forTypeParamName(currContractGenericArg)))
            );
          }

          monomorphization.registerProcedureTypeProvider(scopedHeap);
          try {
            monomorphization.assertExpectedExprTypes(scopedHeap);
          } catch (ClaroTypeException e) {
            throw new RuntimeException(e);
          }
          monomorphizationsCodeGen =
              monomorphizationsCodeGen.createMerged(monomorphization.generateJavaSourceOutput(scopedHeap));

          for (int i = 0; i < currGenericProcedureArgNames.size(); i++) {
            // This is temporary to this specific monomorphization and needs to be removed.
            scopedHeap.deleteIdentifierValue(currGenericProcedureArgNames.get(i));
          }
        }
      }
    }
    return monomorphizationsCodeGen;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return null;
  }
}
