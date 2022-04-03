package com.claro.intermediate_representation.statements;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.*;
import com.claro.runtime_utilities.injector.InjectedKey;
import com.claro.runtime_utilities.injector.Injector;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public abstract class ProcedureDefinitionStmt extends Stmt {

  private static final String HIDDEN_RETURN_TYPE_VARIABLE_FLAG_NAME_FMT_STR = "$%sRETURNS";

  final String procedureName;
  private final Optional<ImmutableMap<String, TypeProvider>> optionalArgTypeProvidersByNameMap;
  private final Optional<ImmutableList<InjectedKey>> optionalInjectedKeysList;
  public final TypeProvider procedureTypeProvider;
  private Optional<ImmutableMap<String, Type>> optionalArgTypesByNameMap;
  private Optional<ImmutableMap<Key, Optional<String>>> optionalInjectedKeysToAliasMap;
  private Types.ProcedureType resolvedProcedureType;
  private boolean isLambdaType;
  private ImmutableSet<String> lambdaScopeCapturedVariables = ImmutableSet.of();

  public ProcedureDefinitionStmt(
      String procedureName,
      ImmutableMap<String, TypeProvider> argTypeProviders,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysList,
      TypeProvider procedureTypeProvider,
      StmtListNode procedureBodyStmtListNode) {
    super(ImmutableList.of(procedureBodyStmtListNode));
    this.procedureName = procedureName;
    this.optionalArgTypeProvidersByNameMap = Optional.of(argTypeProviders);
    this.optionalInjectedKeysList = optionalInjectedKeysList;
    this.procedureTypeProvider = procedureTypeProvider;
  }

  public ProcedureDefinitionStmt(
      String procedureName,
      TypeProvider procedureTypeProvider,
      StmtListNode procedureBodyStmtListNode) {
    this(procedureName, Optional.empty(), procedureTypeProvider, procedureBodyStmtListNode);
  }

  public ProcedureDefinitionStmt(
      String procedureName,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysList,
      TypeProvider procedureTypeProvider,
      StmtListNode procedureBodyStmtListNode) {
    super(ImmutableList.of(procedureBodyStmtListNode));
    this.procedureName = procedureName;
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
        (Types.ProcedureType) this.procedureTypeProvider.resolveType(scopedHeap);
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

    // Finally mark the function declared and initialized within the original calling scope.
    scopedHeap.observeIdentifier(this.procedureName, resolvedProcedureType);
    scopedHeap.initializeIdentifier(this.procedureName);
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
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


    // Now from here step through the function body. Just assert expected types on the StmtListNode.
    ((StmtListNode) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);
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
      javaSourceOutput =
          this.resolvedProcedureType.getJavaNewTypeDefinitionStmt(
              this.procedureName,
              optionalJavaSourceBodyBuilder.orElse(new StringBuilder())
                  .append(procedureBodyGeneratedJavaSource.javaSourceBody())
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
