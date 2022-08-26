package com.claro.intermediate_representation.statements;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.runtime_utilities.injector.InjectedKey;
import com.claro.runtime_utilities.injector.Injector;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class ProcedureDefinitionStmt extends Stmt {

  private static final String HIDDEN_RETURN_TYPE_VARIABLE_FLAG_NAME_FMT_STR = "$%sRETURNS";

  public final String procedureName;
  private final Optional<ImmutableMap<String, TypeProvider>> optionalArgTypeProvidersByNameMap;
  private final Optional<ImmutableList<InjectedKey>> optionalInjectedKeysList;
  public final Function<ProcedureDefinitionStmt, TypeProvider> procedureTypeProvider;
  private Optional<ImmutableMap<String, Type>> optionalArgTypesByNameMap;
  private Optional<ImmutableMap<Key, Optional<String>>> optionalInjectedKeysToAliasMap;
  // Allow this to be visible to other nested procedure call nodes so that they can update this current defined
  // procedure with their used injected keys.
  public Types.ProcedureType resolvedProcedureType;
  private boolean isLambdaType;
  private ImmutableSet<String> lambdaScopeCapturedVariables = ImmutableSet.of();
  private boolean alreadyAssertedTypes = false;

  // This field is the fringe that will be used from this node when traversing the top-level procedure call graph.
  public HashSet<String> directTopLevelProcedureDepsSet = Sets.newHashSet();
  public HashMap<String, ImmutableSet<Key>> directTopLevelProcedureDepsToBeFilteredForExplicitUsingBlockKeyBindings =
      Maps.newHashMap();
  private final HashMap<String, Optional<ImmutableSet<Key>>> directTopLevelProcedureDepsToCollectAttributesFromSet =
      Maps.newHashMap();

  // In the case that this procedure definition was actually generic over keyword annotations, we want to actually
  // type check over these concrete variant signatures rather than the generic one.
  private Optional<ImmutableList<ProcedureDefinitionStmt>> optionalConcreteVariantsForKeywordGenericProcedure =
      Optional.empty();

  public ProcedureDefinitionStmt(
      String procedureName,
      ImmutableMap<String, TypeProvider> argTypeProviders,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysList,
      Function<ProcedureDefinitionStmt, TypeProvider> procedureTypeProvider,
      StmtListNode procedureBodyStmtListNode) {
    super(ImmutableList.of(procedureBodyStmtListNode));
    this.procedureName = procedureName;
    this.opaqueData_workaroundToAvoidCircularDepsCausedByExprToStmtBuildTargets = Optional.of(this.procedureName);
    this.optionalArgTypeProvidersByNameMap = Optional.of(argTypeProviders);
    this.optionalInjectedKeysList = optionalInjectedKeysList;
    this.procedureTypeProvider = procedureTypeProvider;
  }

  private ProcedureDefinitionStmt(
      String procedureName,
      Optional<ImmutableMap<String, TypeProvider>> optionalArgTypeProviders,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysList,
      Function<ProcedureDefinitionStmt, TypeProvider> procedureTypeProvider,
      StmtListNode procedureBodyStmtListNode) {
    super(ImmutableList.of(procedureBodyStmtListNode));
    this.procedureName = procedureName;
    this.opaqueData_workaroundToAvoidCircularDepsCausedByExprToStmtBuildTargets = Optional.of(this.procedureName);
    this.optionalArgTypeProvidersByNameMap = optionalArgTypeProviders;
    this.optionalInjectedKeysList = optionalInjectedKeysList;
    this.procedureTypeProvider = procedureTypeProvider;
  }

  public ProcedureDefinitionStmt(
      String procedureName,
      Function<ProcedureDefinitionStmt, TypeProvider> procedureTypeProvider,
      StmtListNode procedureBodyStmtListNode) {
    this(procedureName, Optional.empty(), procedureTypeProvider, procedureBodyStmtListNode);
  }

  public ProcedureDefinitionStmt(
      String procedureName,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysList,
      Function<ProcedureDefinitionStmt, TypeProvider> procedureTypeProvider,
      StmtListNode procedureBodyStmtListNode) {
    super(ImmutableList.of(procedureBodyStmtListNode));
    this.procedureName = procedureName;
    this.opaqueData_workaroundToAvoidCircularDepsCausedByExprToStmtBuildTargets = Optional.of(this.procedureName);
    this.optionalArgTypeProvidersByNameMap = Optional.empty();
    this.optionalInjectedKeysList = optionalInjectedKeysList;
    this.procedureTypeProvider = procedureTypeProvider;
  }

  private static boolean isLambdaType(BaseType procedureBaseType) {
    switch (procedureBaseType) {
      case FUNCTION:
      case PROVIDER_FUNCTION:
      case CONSUMER_FUNCTION:
        return false;
      case LAMBDA_FUNCTION:
      case LAMBDA_PROVIDER_FUNCTION:
      case LAMBDA_CONSUMER_FUNCTION:
        return true;
      default:
        throw new ClaroParserException(
            "Internal Compiler Error: Unsupported procedure type " + procedureBaseType);
    }
  }

  // Register this procedure's type during the procedure resolution phase so that functions may reference each
  // other out of declaration order, to support things like mutual recursion.
  public void registerProcedureTypeProvider(ScopedHeap scopedHeap) {
    // Get the resolved procedure type.
    this.resolvedProcedureType =
        (Types.ProcedureType) this.procedureTypeProvider.apply(this).resolveType(scopedHeap);
    this.isLambdaType = isLambdaType(this.resolvedProcedureType.getPossiblyOverridenBaseType());
    this.optionalArgTypesByNameMap = this.optionalArgTypeProvidersByNameMap
        .map(
            typeProvidersByNameMap ->
                typeProvidersByNameMap.entrySet().stream()
                    .collect(
                        ImmutableMap.toImmutableMap(Map.Entry::getKey, entry -> entry.getValue()
                            .resolveType(scopedHeap))));
    this.optionalInjectedKeysToAliasMap = this.optionalInjectedKeysList
        .map(
            injectedKeys ->
                injectedKeys.stream()
                    .collect(
                        ImmutableMap.toImmutableMap(
                            injectedKey ->
                                new Key(injectedKey.name, injectedKey.typeProvider.resolveType(scopedHeap)),
                            injectedKey -> injectedKey.optionalAlias
                        )));

    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(this.procedureName),
        String.format("Unexpected redeclaration of %s %s.", resolvedProcedureType, this.procedureName)
    );

    // We're going to explicitly validate that the user is correct about their chosen annotation of whether this
    // procedure is blocking.
    this.resolvedProcedureType.getIsBlocking().set(false);

    // Finally mark the function declared and initialized within the original calling scope.
    scopedHeap.observeIdentifier(this.procedureName, resolvedProcedureType);
    scopedHeap.initializeIdentifier(this.procedureName);

    // If we're dealing with a blocking-generic prcedure, then we need to type check for blocking/non-blocking
    // separately and then prevent type checking on this current one.
    if (this.resolvedProcedureType.getAnnotatedBlockingGenericOverArgs().isPresent()) {
      // Make synthetic procedures for handling non-blocking/blocking concrete signatures.

      // Start by converting all of its blocking-generic args to simple non-blocking alts.
      ImmutableList.Builder<Type> nonBlockingVariantArgTypes = ImmutableList.builder();
      ImmutableMap.Builder<String, TypeProvider> nonBlockingVariantArgTypeProvidersByName = ImmutableMap.builder();
      ImmutableList.Builder<Type> blockingVariantArgTypes = ImmutableList.builder();
      ImmutableMap.Builder<String, TypeProvider> blockingVariantArgTypeProvidersByName = ImmutableMap.builder();
      ImmutableList<Map.Entry<String, TypeProvider>> givenTypeProvidersByName =
          this.optionalArgTypeProvidersByNameMap.get().entrySet().asList();
      for (int i = 0; i < this.resolvedProcedureType.getArgTypes().size(); i++) {
        if (this.resolvedProcedureType.getAnnotatedBlockingGenericOverArgs().get().contains(i)) {
          // Generically attempting to accept a concrete blocking variant that the user gave for this arg.
          Types.ProcedureType maybeBlockingArgType =
              (Types.ProcedureType) this.resolvedProcedureType.getArgTypes().get(i);
          Type nonBlockingVariantArgType =
              Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
                  maybeBlockingArgType.getArgTypes(),
                  maybeBlockingArgType.getReturnType(),
                  false
              );
          nonBlockingVariantArgTypes.add(nonBlockingVariantArgType);
          String givenArgName = givenTypeProvidersByName.get(i).getKey();
          nonBlockingVariantArgTypeProvidersByName.put(givenArgName, unused -> nonBlockingVariantArgType);
          Type blockingVariantArgType =
              Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
                  maybeBlockingArgType.getArgTypes(),
                  maybeBlockingArgType.getReturnType(),
                  true
              );
          blockingVariantArgTypes.add(blockingVariantArgType);
          blockingVariantArgTypeProvidersByName.put(givenArgName, unused -> blockingVariantArgType);
        } else {
          nonBlockingVariantArgTypes.add(this.resolvedProcedureType.getArgTypes().get(i));
          nonBlockingVariantArgTypeProvidersByName.put(givenTypeProvidersByName.get(i));
          blockingVariantArgTypes.add(this.resolvedProcedureType.getArgTypes().get(i));
          blockingVariantArgTypeProvidersByName.put(givenTypeProvidersByName.get(i));
        }
      }
      Function<ProcedureDefinitionStmt, Types.ProcedureType> nonBlockingConcreteType;
      Function<ProcedureDefinitionStmt, Types.ProcedureType> blockingConcreteType;
      switch (this.resolvedProcedureType.baseType()) {
        case FUNCTION:
          nonBlockingConcreteType =
              procedureDefinitionStmt ->
                  Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
                      nonBlockingVariantArgTypes.build(),
                      this.resolvedProcedureType.getReturnType(),
                      BaseType.FUNCTION,
                      Sets.newHashSet(this.resolvedProcedureType.getUsedInjectedKeys()),
                      procedureDefinitionStmt,
                      false,
                      Optional.empty()
                  );
          blockingConcreteType =
              procedureDefinitionStmt ->
                  Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
                      blockingVariantArgTypes.build(),
                      this.resolvedProcedureType.getReturnType(),
                      BaseType.FUNCTION,
                      Sets.newHashSet(this.resolvedProcedureType.getUsedInjectedKeys()),
                      procedureDefinitionStmt,
                      true,
                      Optional.empty()
                  );
          break;
        case CONSUMER_FUNCTION:
          nonBlockingConcreteType =
              procedureDefinitionStmt ->
                  Types.ProcedureType.ConsumerType.forConsumerArgTypes(
                      nonBlockingVariantArgTypes.build(),
                      Sets.newHashSet(this.resolvedProcedureType.getUsedInjectedKeys()),
                      procedureDefinitionStmt,
                      false
                  );
          blockingConcreteType =
              procedureDefinitionStmt ->
                  Types.ProcedureType.ConsumerType.forConsumerArgTypes(
                      blockingVariantArgTypes.build(),
                      Sets.newHashSet(this.resolvedProcedureType.getUsedInjectedKeys()),
                      procedureDefinitionStmt,
                      true
                  );
          break;
        default:
          throw new ClaroParserException(
              "Internal Compiler Error! Received a blocking-generic function that is neither a FUNCTION nor CONSUMER_FUNCTION: " +
              this.resolvedProcedureType.baseType());
      }
      // Make a synthetic procedure for handling non-blocking concrete signature.
      ProcedureDefinitionStmt syntheticNonBlockingConcreteSignature =
          new ProcedureDefinitionStmt(
              "$nonBlockingConcreteVariant_" + this.procedureName,
              nonBlockingVariantArgTypeProvidersByName.build(),
              this.optionalInjectedKeysList,
              (procedureDefinitionStmt) -> (_scopedHeap) -> nonBlockingConcreteType.apply(procedureDefinitionStmt),
              (StmtListNode) this.getChildren().get(0)
          );
      syntheticNonBlockingConcreteSignature.registerProcedureTypeProvider(scopedHeap);
      // Make a synthetic procedure for handling blocking concrete signature.
      ProcedureDefinitionStmt syntheticBlockingConcreteSignature =
          new ProcedureDefinitionStmt(
              "$blockingConcreteVariant_" + this.procedureName,
              blockingVariantArgTypeProvidersByName.build(),
              this.optionalInjectedKeysList,
              (procedureDefinitionStmt) -> (_scopedHeap) -> blockingConcreteType.apply(procedureDefinitionStmt),
              (StmtListNode) this.getChildren().get(0)
          );
      syntheticBlockingConcreteSignature.registerProcedureTypeProvider(scopedHeap);
      // Now, keep in mind that though these synthetic concrete variant signatures are in the ScopedHeap,
      // they are not actually in the program's statement list. This is by design so that we can explicitly
      // choose to use these signatures for type validation of the function (to ensure that the generic
      // implementation can be used with each concrete signature) but *not* requiring us to codegen multiple
      // functions since keyword-generics implies that the body of the code does not change.
      this.optionalConcreteVariantsForKeywordGenericProcedure =
          Optional.of(
              ImmutableList.of(
                  syntheticNonBlockingConcreteSignature,
                  syntheticBlockingConcreteSignature
              ));
    }
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!this.alreadyAssertedTypes) {
      // Just be extremely conservative and make sure we don't come back into this since we're recursing
      // over a graph of nodes which is known to legally contain cycles (mutual recursion is legitimate).
      this.alreadyAssertedTypes = true;

      // Short-circuit evaluation of this procedure in case this one is simply a keyword-generic definition.
      // Instead, defer to its concrete signatures.
      if (this.optionalConcreteVariantsForKeywordGenericProcedure.isPresent()) {
        try {
          for (ProcedureDefinitionStmt concreteSignature : this.optionalConcreteVariantsForKeywordGenericProcedure.get()) {
            concreteSignature.assertExpectedExprTypes(scopedHeap);
          }
        } catch (ClaroTypeException e) {
          // If there was some type exception, I need to sanitize the error message so the user doesn't get exposed
          // to the internal details of these synthetic concrete signatures.
          throw new ClaroTypeException(
              e.getMessage().replaceAll("\\$(nonB|b)lockingConcreteVariant_", ""));
        }
        return; // Done after checking concrete signatures.
      }

      // Before I step through the procedure body, I'll need to make sure I set this instance as the
      // currently active ProcedureDefStmt and then save old one to restore.
      Optional<Object> priorActiveProcedureDefStmt =
          InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt;
      InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt = Optional.of(this);
      Optional<Type> priorActiveProcedureResolvedType =
          InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureResolvedType;
      InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureResolvedType =
          Optional.of(this.resolvedProcedureType);

      // Before I do any actual validation of this current ProcedureDefinitionStmt, I actually want to
      // first defer downstream to all directly depended upon (via procedure call) ProcedureDefinitionStmts
      // so that they can update my

      // this::registerTypeProvider should've already been called during the procedure resolution phase, so we
      // can now already assume that this type is registered in this scope.

      // Enter the new scope for this procedure.
      scopedHeap.observeNewScope(
          false,
          isLambdaType ? ScopedHeap.Scope.ScopeType.LAMBDA_SCOPE : ScopedHeap.Scope.ScopeType.FUNCTION_SCOPE
      );

      // Now that we're in the procedure's scope, let's allow ReturnStmts temporarily.
      ReturnStmt.enterProcedureScope(this.procedureName, this.resolvedProcedureType.hasReturnValue());
      String hiddenReturnTypeVariableFlagName = getHiddenReturnTypeVariableFlagName();
      // There's some setup we'll need to do if this procedure expects an output.
      if (this.resolvedProcedureType.hasReturnValue()) {
        // We'll abuse the variable usage checking implementation to validate that we've put a ReturnStmt
        // along every branch of the procedure. So we'll put a hidden variable in the procedure's Scope
        // and then everywhere where a ReturnStmt is located, we'll mark that variable as "initialized".
        // By this approach, we simply need to check if that hidden variable "is initialized" on the last
        // line of the function, and if it's not, then we know there must be a ReturnStmt missing! Damn,
        // sometimes I even impress myself with my laziness...err creativity....
        scopedHeap.observeIdentifier(hiddenReturnTypeVariableFlagName, Types.BOOLEAN);
      }

      // I may need to mark the args as observed identifiers within this new scope.
      Consumer<ImmutableMap<String, Type>> observeAndInitializeIdentifiers =
          identifierTypeMap -> identifierTypeMap.forEach(
              (argName, argType) ->
              {
                scopedHeap.observeIdentifierAllowingHiding(argName, argType);
                scopedHeap.initializeIdentifier(argName);
              }
          );
      if (resolvedProcedureType.hasArgs()) {
        observeAndInitializeIdentifiers.accept(this.optionalArgTypesByNameMap.get());
      }
      // Similarly, in case this procedure has injected dependencies, mark the keys as observed identifiers.
      // But, first validate that all keys are imported with unique local names (or aliases).
      if (optionalInjectedKeysToAliasMap.isPresent()) {
        HashSet<String> uniqueInjectedLocalNames = new HashSet<>(optionalInjectedKeysToAliasMap.get().size());
        HashSet<String> duplicateInjectedLocalNames = new HashSet<>();
        for (Map.Entry<Key, Optional<String>> injectedLocalName : optionalInjectedKeysToAliasMap.get().entrySet()) {
          String localName = injectedLocalName.getValue().orElse(injectedLocalName.getKey().name);
          if (!uniqueInjectedLocalNames.add(localName)) {
            // We ended up finding some conflicting local names.
            duplicateInjectedLocalNames.add(localName);
          }
        }
        if (!duplicateInjectedLocalNames.isEmpty()) {
          throw ClaroTypeException.forDuplicateInjectedLocalNames(
              this.procedureName, this.resolvedProcedureType, duplicateInjectedLocalNames);
        }
      }
      optionalInjectedKeysToAliasMap
          .map(
              keysToAliasMap ->
                  keysToAliasMap.entrySet().stream()
                      .collect(
                          ImmutableMap.toImmutableMap(
                              entry -> entry.getValue().orElse(entry.getKey().name),
                              entry -> entry.getKey().type
                          )))
          .ifPresent(observeAndInitializeIdentifiers);

      // Do setup for any subclasses who need to customize some setup within the function scope for type checking sake.
      this.subclassSetupFunctionBodyScopeCallback(scopedHeap);

      // Now from here step through the function body. Just assert expected types on the StmtListNode.
      ((StmtListNode) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);

      // Do a one time cleanup of any procedure deps that we don't need filtered because they show up both inside
      // and outside of using blocks.
      this.directTopLevelProcedureDepsSet.forEach(
          this.directTopLevelProcedureDepsToBeFilteredForExplicitUsingBlockKeyBindings::remove);

      // In case this was a lambda expression that made reference to implicitly captured variables
      // in the outer scope, we need to find and mark them so that we can handle them during interpretation.
      lambdaScopeCapturedVariables = ImmutableSet.copyOf(scopedHeap.scopeStack.peek().lambdaScopeCapturedVariables);

      // Just before we leave the procedure body, let's make sure that we check for required returns.
      if (this.resolvedProcedureType.hasReturnValue()) {
        if (!scopedHeap.isIdentifierInitialized(hiddenReturnTypeVariableFlagName)) {
          // The hidden variable marking whether or not this procedure returns along every branch is
          // uninitialized meaning that there's guaranteed to be a missing return somewhere.
          throw new ClaroParserException(
              String.format("Missing return in %s %s.", this.resolvedProcedureType, this.procedureName));
        }
        // Just to get the compiler not to yell about our hidden variable being unused, mark it used.
        scopedHeap.markIdentifierUsed(hiddenReturnTypeVariableFlagName);
      }

      // Leave the function body.
      scopedHeap.exitCurrObservedScope(false);
      // Now that we've left the procedure's scope, let's disallow ReturnStmts again.
      ReturnStmt.exitProcedureScope();

      // Restore the prior active ProcedureDefStmt.
      InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt =
          priorActiveProcedureDefStmt;
      InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureResolvedType =
          priorActiveProcedureResolvedType;
    } // End pass through body stmts.

    // Now we are recursively walking the graph of transitive procedure dependencies.
    //
    // !! HERE BE DRAGONS !!:
    // BE VERY CAREFUL EDITING THIS. WE ARE INTENTIONALLY ITERATING OVER COLLECTIONS THAT ARE BEING SIMULTANEOUSLY
    // MUTATED BY DESIGN. THIS MAY BE SOME SPOOKY ACTION AT A DISTANCE BASED ON THE RECURSIVE NATURE OF THIS PROBLEM
    // BUT CONSIDER THIS YOUR WARNING.
    if (!this.directTopLevelProcedureDepsSet.isEmpty()) {
      // I want something stable to iterate over while honoring the mutable set as the actual source of truth.
      ImmutableList<String> initialFringeStateCopyList = ImmutableList.copyOf(this.directTopLevelProcedureDepsSet);
      for (String initialFringeProcedureDep : initialFringeStateCopyList) {
        if (!this.directTopLevelProcedureDepsSet.contains(initialFringeProcedureDep)) {
          // In this case, some dep led to a cycle and we've returned to the start and *should not* go back
          // through validations on it again. Cut the cycle here, when we return at the original call-site,
          // we'll be able to add that dep's used keys to this procedure's used keys.

          // I wish I could `return` instead of `continue` to let the stack unwind (this would be ok for the current
          // procedure) but it would cause the procedure dep that caused the cycle to potentially end up w/ only
          // partial used keys information.
          continue;
        }
        Types.ProcedureType depProcedureDef =
            (Types.ProcedureType) scopedHeap.getValidatedIdentifierType(initialFringeProcedureDep);
        // Make sure that we never recheck this dep. "Mark it visited" by removing it from the dep set.
        // Do this first before kicking of type validation on the dep so that we're ready before a cycle.
        this.directTopLevelProcedureDepsSet.remove(initialFringeProcedureDep);
        // Mark this procedure dep as to-be-processed so that we might potentially use it for lookahead in the case
        // of breaking a cycle.
        this.directTopLevelProcedureDepsToCollectAttributesFromSet.put(initialFringeProcedureDep, Optional.empty());
        // Now, finally do the recursive bit. Kick off type validation on the dep so that we can make sure it
        // collects all of its transitive used deps.
        depProcedureDef.getProcedureDefStmt().assertExpectedExprTypes(scopedHeap);

        // Make sure that we don't redo this work in case we already handled this during a lookahead to break a cycle.
        if (this.directTopLevelProcedureDepsToCollectAttributesFromSet.containsKey(initialFringeProcedureDep)) {
          this.directTopLevelProcedureDepsToCollectAttributesFromSet.remove(initialFringeProcedureDep);
          // Now the *most important thing*!!
          // ---------------------- BEGIN UPDATE FUNCTION ATTRIBUTES! --------------------- //
          // Finally we can trust the dep's transitive used keys!
          this.resolvedProcedureType.getUsedInjectedKeys().addAll(depProcedureDef.getUsedInjectedKeys());
          // And finally update the blocking attribute if this function transitively depends on a blocking op.
          if (depProcedureDef.getIsBlocking().get()) {
            this.resolvedProcedureType.getBlockingProcedureDeps().put(initialFringeProcedureDep, depProcedureDef);
            this.resolvedProcedureType.getIsBlocking().set(true);
          }
          // ---------------------- END UPDATE FUNCTION ATTRIBUTES! --------------------- //
        }
      }
    }
    // We basically need to do the same thing again here, for the deps that were used within a nested using block.
    // Look to the above block for detailed comments explaining the logic.
    if (!this.directTopLevelProcedureDepsToBeFilteredForExplicitUsingBlockKeyBindings.isEmpty()) {
      ImmutableList<String> initialFringeStateCopyList =
          ImmutableList.copyOf(this.directTopLevelProcedureDepsToBeFilteredForExplicitUsingBlockKeyBindings.keySet());
      for (String initialFringeProcedureDep : initialFringeStateCopyList) {
        if (!this.directTopLevelProcedureDepsToBeFilteredForExplicitUsingBlockKeyBindings
            .containsKey(initialFringeProcedureDep)) {
          // cycle case.
          continue;
        }
        Types.ProcedureType depProcedureDef =
            (Types.ProcedureType) scopedHeap.getValidatedIdentifierType(initialFringeProcedureDep);
        ImmutableSet<Key> keysAlreadyProvidedToDep =
            this.directTopLevelProcedureDepsToBeFilteredForExplicitUsingBlockKeyBindings
                .remove(initialFringeProcedureDep);
        // Mark this procedure dep as to-be-processed so that we might potentially use it for lookahead in the case
        // of breaking a cycle.
        this.directTopLevelProcedureDepsToCollectAttributesFromSet.put(
            initialFringeProcedureDep, Optional.of(keysAlreadyProvidedToDep));
        // Now, finally do the recursive bit. Kick off type validation on the dep so that we can make sure it
        // collects all of its transitive used deps.
        depProcedureDef.getProcedureDefStmt().assertExpectedExprTypes(scopedHeap);


        // Make sure that we don't redo this work in case we already handled this during a lookahead to break a cycle.
        if (this.directTopLevelProcedureDepsToCollectAttributesFromSet.containsKey(initialFringeProcedureDep)) {
          this.directTopLevelProcedureDepsToCollectAttributesFromSet.remove(initialFringeProcedureDep);
          // Now the *most important thing*!!
          // ---------------------- BEGIN UPDATE FUNCTION ATTRIBUTES! --------------------- //
          // Finally we can trust the dep's transitive used keys!
          this.resolvedProcedureType.getUsedInjectedKeys()
              .addAll(
                  // Filter out all the keys that were already provided by the nested using block.
                  Sets.difference(depProcedureDef.getUsedInjectedKeys(), keysAlreadyProvidedToDep));
          // And finally update the blocking attribute if this function transitively depends on a blocking op.
          if (depProcedureDef.getIsBlocking().get()) {
            this.resolvedProcedureType.getBlockingProcedureDeps().put(initialFringeProcedureDep, depProcedureDef);
            this.resolvedProcedureType.getIsBlocking().set(true);
          }
          // ---------------------- END UPDATE FUNCTION ATTRIBUTES! --------------------- //
        }
      }
    }
    // Do lookahead cleanup for any procedure dep that we haven't resolved yet that we've reached again as a result of
    // a cycle. Following this cycle will be safe since the procedure that we're going to re-visit will have already
    // checked off any procedure deps that it has already processed so it won't go down that road again.
    ImmutableList<String> copyLookaheadCycleBreakingProcedureDepList =
        ImmutableList.copyOf(this.directTopLevelProcedureDepsToCollectAttributesFromSet.keySet());
    for (String lookaheadCycleBreakingProcedureDep : copyLookaheadCycleBreakingProcedureDepList) {
      Optional<ImmutableSet<Key>> optionalKeysAlreadyProvidedToDep =
          this.directTopLevelProcedureDepsToCollectAttributesFromSet.remove(lookaheadCycleBreakingProcedureDep);
      Types.ProcedureType depProcedureDef =
          (Types.ProcedureType) scopedHeap.getValidatedIdentifierType(lookaheadCycleBreakingProcedureDep);

      // Now, finally do the lookahead recursive bit. Kick off type validation on the dep so that we can make sure it
      // collects all of its transitive used deps.
      depProcedureDef.getProcedureDefStmt().assertExpectedExprTypes(scopedHeap);

      // ---------------------- BEGIN UPDATE FUNCTION ATTRIBUTES! --------------------- //
      // Finally we can trust the dep's transitive used keys!
      this.resolvedProcedureType.getUsedInjectedKeys()
          .addAll(
              optionalKeysAlreadyProvidedToDep.isPresent()
              // Filter out all the keys that were already provided by the nested using block.
              ? Sets.difference(depProcedureDef.getUsedInjectedKeys(), optionalKeysAlreadyProvidedToDep.get())
              : depProcedureDef.getUsedInjectedKeys());
      // And finally update the blocking attributes if this function transitively depends on a blocking op.
      if (depProcedureDef.getIsBlocking().get()) {
        this.resolvedProcedureType.getBlockingProcedureDeps().put(lookaheadCycleBreakingProcedureDep, depProcedureDef);
        this.resolvedProcedureType.getIsBlocking().set(true);
      }
      // ---------------------- END UPDATE FUNCTION ATTRIBUTES! --------------------- //
    }

    // We require that users explicitly annotate procedures that are blocking so that there is no surprise
    // when a dep is rejected as a result of some faraway buried transitive dep using a blocking call.
    // We don't allow graph functions to be annotated blocking, however, so in that case, wait for
    // GraphFunctionDefinitionStmt type checking to throw a more specific error.
    if (!this.resolvedProcedureType.getIsGraph().get() &&
        !this.resolvedProcedureType.getAnnotatedBlockingGenericOverArgs().isPresent()) {
      if (this.resolvedProcedureType.getIsBlocking().get() && !this.resolvedProcedureType.getAnnotatedBlocking()) {
        if (this.procedureName.startsWith("$nonBlockingConcreteVariant_")) {
          // TODO(steving) Make this error message specific to not allowing blocking-generics if no possibility
          // In case this happens to be a synthetic concrete signature variant for a generic type, we want our error
          // message to be applicable to the programmer's written type, not the synthetic one.
          String sanitizedProcedureName = this.procedureName.replaceAll("\\$nonBlockingConcreteVariant_", "");
          throw ClaroTypeException.forInvalidUseOfBlockingGenericsOnBlockingProcedureDefinition(
              sanitizedProcedureName,
              scopedHeap.getValidatedIdentifierType(sanitizedProcedureName),
              this.resolvedProcedureType.getBlockingProcedureDeps()
          );
        } else {
          throw ClaroTypeException.forInvalidBlockingProcedureDefinitionMissingBlockingAnnotation(
              this.procedureName, this.resolvedProcedureType, this.resolvedProcedureType.getBlockingProcedureDeps());
        }
      }
      if (!this.resolvedProcedureType.getIsBlocking().get() && this.resolvedProcedureType.getAnnotatedBlocking()) {
        if (this.procedureName.startsWith("$blockingConcreteVariant_")) {
          // In case this happens to be a synthetic concrete signature variant for a generic type, we want our error
          // message to be applicable to the programmer's written type, not the synthetic one.
          String sanitizedProcedureName = this.procedureName.replaceAll("\\$blockingConcreteVariant_", "");
          throw ClaroTypeException.forInvalidUseOfBlockingGenericsOnNonBlockingProcedureDefinition(
              sanitizedProcedureName,
              scopedHeap.getValidatedIdentifierType(sanitizedProcedureName)
          );
        } else {
          throw ClaroTypeException.forInvalidBlockingAnnotationOnNonBlockingProcedureDefinition(
              this.procedureName, this.resolvedProcedureType);
        }
      }
    }
  }

  // Do setup for any subclasses who need to customize some setup within the function scope for type checking sake.
  protected void subclassSetupFunctionBodyScopeCallback(ScopedHeap scopedHeap) throws ClaroTypeException {
    // By default this should do nothing.
  }

  protected Optional<GeneratedJavaSource> getHelperMethodsJavaSource(ScopedHeap scopedHeap) {
    return Optional.empty();
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    if (this.isLambdaType) {
      scopedHeap.putIdentifierValue(this.procedureName, this.resolvedProcedureType);
    } else {
      // Since procedures are declared in a synthetic top-level scope as the result of an initial AST parsing phase, we
      // don't need to actually declare the procedure here since it's already present in the ScopedHeap, we don't even
      // need to set a value on its entry in the JavaSource version since the implementation will be code-gen'd.
    }

    scopedHeap.enterNewScope(
        isLambdaType ? ScopedHeap.Scope.ScopeType.LAMBDA_SCOPE : ScopedHeap.Scope.ScopeType.FUNCTION_SCOPE
    );

    BiFunction</*isArgsMap*/Boolean, ImmutableMap<String, Type>, StringBuilder> initializeIdentifiers =
        (isArgsMap, identifierTypesByNameMap) -> {
          ImmutableSet<Map.Entry<String, Type>> argTypesByNameEntrySet = identifierTypesByNameMap.entrySet();
          argTypesByNameEntrySet.stream()
              .forEach(stringTypeEntry -> {
                // Since we don't have a value to store in the ScopedHeap we'll manually ack that the identifier is init'd.
                scopedHeap.putIdentifierValue(stringTypeEntry.getKey(), stringTypeEntry.getValue());
                scopedHeap.initializeIdentifier(stringTypeEntry.getKey());
              });
          // We need to gen code for initializing args to be used within the java source function body, since we're
          // constrained to the java source function taking args as `Object... args`.
          StringBuilder javaSourceBodyBuilder = new StringBuilder();
          ImmutableList<Map.Entry<String, Type>> argsEntrySet = argTypesByNameEntrySet.asList();
          for (int i = 0; i < argsEntrySet.size(); i++) {
            String argName = argsEntrySet.get(i).getKey();
            Type argType = argsEntrySet.get(i).getValue();
            String argJavaSourceType = argType.getJavaSourceType();
            javaSourceBodyBuilder.append(
                String.format(
                    "%s %s = (%s) %s",
                    argJavaSourceType,
                    argName,
                    argJavaSourceType,
                    isArgsMap
                    ? String.format("args[%s];\n", i)
                    : String.format(
                        "Injector.bindings.get(new Key(\"%s\", %s));\n",
                        optionalInjectedKeysToAliasMap.get().keySet().asList().get(i).name,
                        argsEntrySet.get(i).getValue().getJavaSourceClaroType()
                    )
                )
            );
          }
          return javaSourceBodyBuilder;
        };

    // Since we're about to immediately execute some java source code gen, we might need to init the local arg variables.
    Optional<StringBuilder> optionalJavaSourceBodyBuilder = Optional.empty();
    if (this.resolvedProcedureType.hasArgs()) {
      optionalJavaSourceBodyBuilder =
          Optional.of(initializeIdentifiers.apply(/*isArgsMap=*/true, this.optionalArgTypesByNameMap.get()));
    }
    // Similarly, we also need to do the same thing for generating initialization code for injected keys.
    if (optionalInjectedKeysToAliasMap.isPresent()) {
      StringBuilder initializeInjectedKeysJavaSource =
          initializeIdentifiers.apply(
              /*isArgsMap=*/
              false,
              optionalInjectedKeysToAliasMap.get().entrySet().stream()
                  .collect(
                      ImmutableMap.toImmutableMap(
                          entry -> entry.getValue().orElse(entry.getKey().name),
                          entry -> entry.getKey().type
                      ))
          );
      if (optionalJavaSourceBodyBuilder.isPresent()) {
        optionalJavaSourceBodyBuilder.get().append(initializeInjectedKeysJavaSource);
      } else {
        optionalJavaSourceBodyBuilder = Optional.of(initializeInjectedKeysJavaSource);
      }
    }

    // There's a StmtListNode to generate code for.
    GeneratedJavaSource procedureBodyGeneratedJavaSource =
        ((StmtListNode) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap);
    String javaSourceOutput;
    if (isLambdaType) {
      javaSourceOutput =
          this.resolvedProcedureType.getJavaNewTypeDefinitionStmtForLambda(
              this.procedureName,
              optionalJavaSourceBodyBuilder.orElse(new StringBuilder())
                  .append(procedureBodyGeneratedJavaSource.javaSourceBody()),
              this.lambdaScopeCapturedVariables
                  .stream()
                  .collect(
                      ImmutableMap.toImmutableMap(
                          id -> id,
                          id -> {
                            return scopedHeap.getIdentifierData(id).type;
                          }
                      ))
          );
    } else {
      // It's possible that we need to generate helper methods for the class we're generating.
      Optional<GeneratedJavaSource> optionalHelperGeneratedJavaSource = getHelperMethodsJavaSource(scopedHeap);
      optionalHelperGeneratedJavaSource.ifPresent(
          helperGeneratedJavaSource -> {
            procedureBodyGeneratedJavaSource.optionalStaticDefinitions()
                .ifPresent(staticDefs -> staticDefs.append(helperGeneratedJavaSource.optionalStaticDefinitions()));
            procedureBodyGeneratedJavaSource.optionalStaticPreambleStmts()
                .ifPresent(
                    staticPreambleDefs ->
                        staticPreambleDefs.append(helperGeneratedJavaSource.optionalStaticPreambleStmts()));
          });

      javaSourceOutput =
          this.resolvedProcedureType.getJavaNewTypeDefinitionStmt(
              this.procedureName,
              optionalJavaSourceBodyBuilder.orElse(new StringBuilder())
                  .append(procedureBodyGeneratedJavaSource.javaSourceBody()),
              optionalHelperGeneratedJavaSource.map(GeneratedJavaSource::javaSourceBody)
          );
    }
    Optional<StringBuilder> optionalStaticDefinitions =
        procedureBodyGeneratedJavaSource.optionalStaticDefinitions();

    scopedHeap.exitCurrScope();

    if (isLambdaType) {
      return GeneratedJavaSource.create(
          new StringBuilder(javaSourceOutput),
          optionalStaticDefinitions.orElse(new StringBuilder()),
          new StringBuilder()
      );
    } else {
      return GeneratedJavaSource.forStaticDefinitionsAndPreamble(
          optionalStaticDefinitions
              .orElse(new StringBuilder())
              .append(javaSourceOutput),
          new StringBuilder(this.resolvedProcedureType.getStaticFunctionReferenceDefinitionStmt(this.procedureName))
      );
    }
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap definitionTimeScopedHeap) {
    // Within this function's new scope we'll need to add nodes to declare+init the arg vars within this scope. Do this
    // in order (which means constructing from the tail, up) for no reason other than sanity if we ever look close.
    //
    // Note that if you look closely and squint this below is actually dynamic code generation in Java. Like...duh cuz
    // this whole thing is code gen...that's what a compiler is...but this feels to be more code gen-y so ~shrug~ lol.
    // I think that's neat ;P.
    //
    // Oh also... Java's lambdas are actual hot garbage... Please God I just want a 3-arg lambda is that so hard?
    final BiFunction<ImmutableMap<String, Type>, ImmutableList<Expr>, Consumer<ScopedHeap>>
        defineIdentifiersConsumerFn =
        (identifierTypesByNameMap, values) -> callTimeScopedHeap -> {
          ImmutableList<Map.Entry<String, Type>> identifierTypes = identifierTypesByNameMap.entrySet().asList();
          for (int i = values.size() - 1; i >= 0; i--) {
            Map.Entry<String, Type> currTailArg = identifierTypes.get(i);
            new DeclarationStmt(
                currTailArg.getKey(),
                TypeProvider.ImmediateTypeProvider.of(currTailArg.getValue()),
                values.get(i),
                true
            ).generateInterpretedOutput(callTimeScopedHeap);
          }
        };

    // If this is a lambda then we need to capture and redeclare variables for all variables referenced
    // in the outer scope at definition time. We don't want lambdas to have access to the outer scope
    // variables themselves, just their definition time *values*.
    ImmutableMap<String, ScopedHeap.IdentifierData> lambdaScopeCapturedVariables =
        this.lambdaScopeCapturedVariables
            .stream()
            .collect(
                ImmutableMap.toImmutableMap(
                    id -> id,
                    id -> definitionTimeScopedHeap.getIdentifierData(id).getShallowCopy()
                ));
    final Consumer<ScopedHeap> defineLambdaCapturedVariables = scopedHeap -> {
      // We need to add declarations to this inner scope at runtime to hide any outer variables
      // and simply reuse their value that they had set at the definition time of this lambda.
      scopedHeap.putCapturedIdentifierData(lambdaScopeCapturedVariables);
    };

    Types.ProcedureType.ProcedureWrapper procedureImplementation =
        this.resolvedProcedureType.new ProcedureWrapper() {
          @Override
          public Object apply(ImmutableList<Expr> args, ScopedHeap callTimeScopedHeap) {
            // First things first, this function needs to operate within a totally new scope. NOTE that when this
            // actually finally EXECUTES, because it depends on the time when the function is finally CALLED rather than
            // this current moment where it's defined, the ScopedHeap very likely has additional identifiers present
            // that would not have been intuitive from the source code's definition order... but this is actually ok,
            // the expected scoping semantics are actually ensured by the type-checking phase's assertions at function
            // definition time rather than at its call site. So this is just a note to anyone who might notice any
            // weirdness if you're the type to open up a debugger and step through data... don't be that person.
            callTimeScopedHeap.enterNewScope(
                isLambdaType ? ScopedHeap.Scope.ScopeType.LAMBDA_SCOPE : ScopedHeap.Scope.ScopeType.FUNCTION_SCOPE
            );

            if (ProcedureDefinitionStmt.this.resolvedProcedureType.hasArgs()) {
              // Execute the arg declarations assigning them to their given values.
              defineIdentifiersConsumerFn.apply(optionalArgTypesByNameMap.get(), args).accept(callTimeScopedHeap);
            }
            optionalInjectedKeysToAliasMap.ifPresent(
                // We need to initialize local variables to hold the injected key values as well.
                injectedKeysTypesMap -> {
                  defineIdentifiersConsumerFn.apply(
                      injectedKeysTypesMap.keySet().stream()
                          .collect(ImmutableMap.toImmutableMap(k -> k.name, k -> k.type)),
                      injectedKeysTypesMap.entrySet().stream()
                          .map(keyEntry -> (Expr) Injector.bindings.get(new Key(keyEntry.getKey().name, keyEntry.getKey().type)))
                          .collect(ImmutableList.toImmutableList())
                  ).accept(callTimeScopedHeap);
                }
            );

            if (isLambdaType) {
              // Define the capture variables in the actual runtime scope to point to the values captured
              // at definition time of this lambda.
              defineLambdaCapturedVariables.accept(callTimeScopedHeap);
            }

            // Now we need to execute the function body StmtListNode given.
            Object returnValue = ((StmtListNode) ProcedureDefinitionStmt.this.getChildren().get(0))
                .generateInterpretedOutput(callTimeScopedHeap);

            // We're done executing this function body now, so we can exit this function's scope.
            callTimeScopedHeap.exitCurrScope();

            return returnValue;
          }
        };

    if (this.isLambdaType) {
      definitionTimeScopedHeap.putIdentifierValue(
          this.procedureName,
          this.resolvedProcedureType,
          procedureImplementation
      );
    } else {
      // Since procedures are declared in a synthetic top-level scope as the result of an initial AST parsing phase, we
      // don't need to actually declare the procedure here since it's already present in the ScopedHeap, we just need
      // to update its entry with a real value containing its implementation.
      definitionTimeScopedHeap.updateIdentifierValue(
          this.procedureName,
          procedureImplementation
      );
    }

    // This is just the function definition (Stmt), not the call-site (Expr), return no value.
    return null;
  }

  private final String getHiddenReturnTypeVariableFlagName() {
    return String.format(HIDDEN_RETURN_TYPE_VARIABLE_FLAG_NAME_FMT_STR, this.procedureName);
  }
}
