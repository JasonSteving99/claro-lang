package com.claro.intermediate_representation.types;

import com.claro.runtime_utilities.injector.Key;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ClaroTypeException extends Exception {

  private static final String UNEXPECTED_IDENTIFIER_REDECLARATION =
      "Unexpected redeclaration of identifier <%s>.";
  private static final String INVALID_TYPE_ERROR_MESSAGE_FMT_STR =
      "Invalid type:\n\tFound:\n\t\t%s\n\tExpected:\n\t\t%s";
  private static final String INVALID_SUBSCRIPT_FOR_NON_COLLECTION_TYPE =
      "Invalid Subscript: The collection subscript operator must only be used on collection types.";
  private static final String INVALID_BASE_TYPE_ERROR_MESSAGE_FMT_STR =
      "Invalid type: found <%s>, but expected to have the BaseType <%s>.";
  private static final String INVALID_TYPE_ONE_OF_ERROR_MESSAGE_FMT_STR =
      "Invalid type: found <%s>, but expected one of (<%s>).";
  private static final String INVALID_OPERATOR_OPERANDS_TYPE_ONE_OF_ERROR_MESSAGE_FMT_STR =
      "Internal Compiler Error: Operator `<%s>` expects one of (<%s>) for operands.";
  private static final String UNDECIDED_TYPE_LEAK_ERROR_MESSAGE_FMT_STR =
      "The type of this expression is UNDECIDED at compile-time! You must explicitly cast the Expr to the contextually expected type <%s> to assert this type at compile-time or fix a bug if the contextually expected type isn't applicable.";
  private static final String UNDECIDED_TYPE_LEAK_GENERIC_ERROR_MESSAGE_FMT_STR =
      "The type of this expression is UNDECIDED at compile-time! You must explicitly cast the Expr to the expected type to assert this type at compile-time.";
  private static final String MISSING_TYPE_DECLARATION_FOR_EMPTY_LIST_INITIALIZATION =
      "The type of this empty list is UNDECIDED at compile-time! You must explicitly declare the type of a variable having the empty list `[]` assigned to it to assert this type statically at compile-time.";
  public static final String MISSING_TYPE_DECLARATION_FOR_LAMBDA_INITIALIZATION =
      // WARNING: CHECKING AGAINST THIS IN FunctionCallExpr.java! DO NOT CHANGE THIS STRING WITHOUT CHECKING THERE FIRST.
      "Ambiguous Lambda Expression Type: Type hint required. When a lambda Expr's type is not constrained by its context, the type must be statically declared via either a type annotation, or a cast.";
  private static final String INVALID_CAST_ERROR_MESSAGE_FMT_STR =
      "Invalid cast: Found <%s> which cannot be converted to <%s>.";
  private static final String INVALID_MEMBER_REFERENCE = "Invalid Member Reference: %s has no such member %s.";
  private static final String UNSET_REQUIRED_STRUCT_MEMBER =
      "Builder Missing Required Struct Member: While building %s, required field%s %s need%s to be set before calling build().";
  private static final String WRONG_NUMBER_OF_ARGS_FOR_LAMBDA_DEFINITION =
      "Lambda expression definition contains the incorrect number of args. Expected %s.";
  private static final String DUPLICATE_KEY_BINDING =
      "Found unexpected duplicate binding for key %s:%s. Bound keys should be unique across all used Modules.";
  private static final String DUPLICATE_KEY_BINDINGS =
      "Found unexpected duplicate bindings for keys %s. Bound keys should be unique across all used Modules.";
  private static final String REBINDING_KEYS_FROM_OUTER_USING_BLOCK =
      "Found unexpected duplicate bindings for the following keys already bound in outer using block %s. Bound keys should be unique across all used Modules.";
  private static final String DUPLICATE_MODULE_DEFINITION =
      "Found unexpected duplicate definition of Module %s. Module names must be unique.";
  private static final String USING_DUPLICATE_MODULES =
      "Found the following unexpected duplicate Modules in using block [%s].";
  private static final String USING_UNDEFINED_MODULES =
      "Found the following undefined Modules referenced in using block [%s].";
  private static final String INVALID_PROCEDURE_DEFINITION_WITHIN_USING_BLOCK =
      "Invalid Procedure Definition: Procedure %s %s may not be defined within a using block.";
  private static final String DUPLICATE_INJECTED_LOCAL_NAMES =
      "Duplicate names in using-clause of procedure %s %s for each of the following names %s. Fix by providing an alias to give the dependency a unique local name: `using(<bindingName>:<type> as <aliasName>)`.";
  private static final String PROCEDURE_CALL_MISSING_BINDING_KEYS =
      "Illegal call to procedure %s %s. The following keys must be bound: %s.";
  private static final String GRAPH_FUNCTION_DOES_NOT_RETURN_FUTURE =
      "Graph %s %s must return a result wrapped in a future<>.";
  private static final String GRAPH_FUNCTION_WITH_DUPLICATED_NODE_NAMES =
      "Graph %s has the following unexpected duplicate Node names %s.";
  private static final String GRAPH_FUNCTION_WITH_UNCONNECTED_NODES =
      "Graph Function %s has the following unconnected nodes %s. All nodes must be reachable from root in a Graph Function.";
  private static final String BLOCKING_CALL_INDIRECTLY_REACHABLE_FROM_GRAPH_FUNCTION =
      "Graph Function %s %s has illegal transitive dep on the following blocking procedures %s. Blocking is forbidden within a Graph Function in order to avoid deadlocking.";
  private static final String BLOCKING_PROCEDURE_MISSING_BLOCKING_ANNOTATION =
      "Procedure %s %s is blocking but is missing required explicit blocking annotation.";
  private static final String INVALID_USE_OF_BLOCKING_GENERICS_ON_BLOCKING_PROCEDURE =
      "Illegal use of blocking-generics on procedure `%s %s`. The procedure is guaranteed blocking.";
  private static final String PROCEDURE_DEPENDING_ON_BLOCKING_PROCEDURE_MISSING_BLOCKING_ANNOTATION =
      "Procedure %s %s depends on blocking procedures %s but is missing required explicit blocking annotation.";
  private static final String INVALID_USE_OF_BLOCKING_GENERICS_ON_PROCEDURE_DEPENDING_ON_BLOCKING_PROCEDURE =
      "Illegal use of blocking-generics on procedure `%s %s`. The procedure is guaranteed blocking due to dependencies on blocking procedures %s.";
  private static final String INVALID_BLOCKING_ANNOTATION_ON_NON_BLOCKING_PROCEDURE_DEFINITION =
      "Non-blocking procedure `%s %s` must not be annotated as blocking.";
  private static final String INVALID_USE_OF_BLOCKING_GENERICS_ON_NON_BLOCKING_PROCEDURE_DEFINITION =
      "Illegal use of blocking-generics on procedure `%s %s`. The procedure is guaranteed non-blocking.";
  private static final String INVALID_USE_OF_BLOCKING_GENERICS_OVER_ARG_NOT_MARKED_MAYBE_BLOCKING =
      "Illegal use of blocking-generics on procedure `%s %s`. Marked blocking-generic over arg %s %s which is not marked with the required `blocking?` annotation.";
  private static final String ILLEGAL_NODE_REFERENCE_CYCLE_IN_GRAPH_PROCEDURE =
      "Illegal node reference cycle detected within Graph procedure <%s>. Through transitive node references, node <%s> depends cyclically on itself. Graph nodes must represent a DAG.";
  private static final String GRAPH_CONSUMER_ROOT_NODE_IS_NOT_CONSUMER_FN =
      "Root node <%s> of Graph Consumer <%s> must defer to a consumer<...> as this Graph should not return a value. If you would like to return a value, change the signature to `graph function`.";
  private static final String BACKREFERENCE_OUTSIDE_OF_VALID_PIPE_CHAIN_CONTEXT =
      "Illegal use of backreference (`^`) outside of valid pipe chain context. Backreferences may only be used in a non-source pipe chain stage.";
  private static final String CONTRACT_IMPLEMENTATION_FOR_UNDEFINED_CONTRACT =
      "Invalid Contract Implementation: %s is attempting to implement undefined contract %s.";
  private static final String CONTRACT_IMPLEMENTATION_WITH_WRONG_NUMBER_OF_TYPE_PARAMS =
      "Invalid Contract Implementation: %s does not have the correct number of type params required by %s.";
  private static final String DUPLICATE_CONTRACT_IMPLEMENTATION =
      "Invalid Contract Implementation: %s is a duplicate of existing implementation %s.";
  private static final String CONTRACT_IMPLEMENTATION_MISSION_REQUIRED_PROCEDURE_DEFS =
      "Invalid Contract Implementation: %s is missing definitions for the following required procedures %s.";
  private static final String CONTRACT_IMPLEMENTATION_WITH_EXTRA_PROCEDURE_DEFS =
      "Invalid Contract Implementation: %s defines the following procedures that are not required by the implemented contract %s.";
  private static final String CONTRACT_PROCEDURE_IMPLEMENTATION_DOES_NOT_MATCH_REQUIRED_SIGNATURE =
      "Invalid Contract Implementation: Found the following definition of %s::%s -\n\t\t%s\n\tbut requires:\n\t\t%s";
  private static final String INVALID_REFERENCE_TO_UNDEFINED_CONTRACT =
      "Invalid Contract Reference: Contract %s is not defined.";
  private static final String CONTRACT_REFERENCE_WITH_WRONG_NUMBER_OF_TYPE_PARAMS =
      "Invalid Contract Reference: %s does not have the correct number of type params required by %s.";
  private static final String INVALID_REFERENCE_TO_UNDEFINED_CONTRACT_PROCEDURE =
      "Invalid Contract Procedure Reference: Contract %s<%s> does not define any procedure named `%s`.";
  private static final String CONTRACT_PROCEDURE_REFERENCE_WITHOUT_REQUIRED_ANNOTATION_ON_GENERIC_FUNCTION =
      "Invalid Contract Procedure Reference: Generic procedure calls a Contract procedure %s::%s over a generic type param so should be annotated as `requires(%s<%s>)`";
  private static final String GENERIC_PROCEDURE_REQUIRES_UNDEFINED_CONTRACT =
      "Invalid Required Contract: Generic Procedure `%s` is attempting to require undefined contract `%s`.";
  private static final String GENERIC_PROCEDURE_REQUIRES_CONTRACT_WITH_WRONG_NUMBER_OF_TYPE_PARAMS =
      "Invalid Required Contract: `%s` does not have the correct number of type params required by `%s`.";
  private static final String GENERIC_PROCEDURE_CALL_REQUIRES_OUTPUT_TYPE_TO_BE_CONSTRAINED_BY_CONTEXT =
      "Invalid Generic Procedure Call: For the call to the following generic procedure `%s` with the following signature:\n\t\t`%s`\n\tThe output types cannot be fully inferred by the argument types alone. The output type must be contextually constrained by either a type annotation or a static cast.";
  private static final String GENERIC_PROCEDURE_CALL_FOR_CONCRETE_TYPES_WITH_REQUIRED_CONTRACT_IMPLEMENTATION_MISSING =
      "Invalid Generic Procedure Call: For the call to the following generic procedure `%s` with the following signature:\n\t\t`%s`\n\tNo implementation of the required contract %s<%s>.";
  private static final String
      INVALID_GENERIC_PROCEDURE_REFERENCE_AS_FIRST_CLASS_OBJECT_WITHOUT_CONTEXTUAL_TYPE_ASSERTION =
      "Illegal Generic Procedure Reference: Generic Procedure `%s` with the following signature:\n\t\t`%s`\n" +
      "\tmay not be referenced as a First Class object without a contextual assertion of the concrete procedure" +
      " type requested. \n" +
      "\tTry adding an explicit type annotation for the concrete procedure signature you need as in the below example.\n\n" +
      "\tE.g.:\n" +
      "\t\tFor signature:\n" +
      "\t\t\trequires(...)\n" +
      "\t\t\tfunction genericFooFn<T, V>(t: T) -> V {...}\n" +
      "\t\tTry something like this (ensuring Foo and Bar satisfy any Contract Requirements):\n" +
      "\t\t\tvar fn: function<Foo -> Bar> = genericFooFn;\n" +
      "\t\tBad:\n" +
      "\t\t\tvar fn = genericFooFn;\n";

  public ClaroTypeException(String message) {
    super(message);
  }

  public ClaroTypeException(Type actualType, Type expectedType) {
    super(String.format(INVALID_TYPE_ERROR_MESSAGE_FMT_STR, actualType, expectedType));
  }

  public static ClaroTypeException forInvalidSubscriptForNonCollectionType(Type actualType, ImmutableSet<?> expectedTypeOptions) {
    return new ClaroTypeException(INVALID_SUBSCRIPT_FOR_NON_COLLECTION_TYPE);
  }

  public ClaroTypeException(Type actualType, ImmutableSet<?> expectedTypeOptions) {
    super(
        String.format(
            INVALID_TYPE_ONE_OF_ERROR_MESSAGE_FMT_STR,
            actualType,
            Joiner.on(", ").join(expectedTypeOptions)
        )
    );
  }

  public ClaroTypeException(String operatorStr, ImmutableSet<Type> expectedTypeOptions) {
    super(
        String.format(
            INVALID_OPERATOR_OPERANDS_TYPE_ONE_OF_ERROR_MESSAGE_FMT_STR,
            operatorStr,
            Joiner.on(", ").join(expectedTypeOptions)
        )
    );
  }

  public ClaroTypeException(Type actualExprType, BaseType expectedBaseType) {
    super(
        String.format(
            INVALID_BASE_TYPE_ERROR_MESSAGE_FMT_STR,
            actualExprType,
            expectedBaseType
        )
    );
  }

  public static ClaroTypeException forUnexpectedIdentifierRedeclaration(String identifier) {
    return new ClaroTypeException(String.format(UNEXPECTED_IDENTIFIER_REDECLARATION, identifier));
  }

  public static ClaroTypeException forUndecidedTypeLeak() {
    return new ClaroTypeException(UNDECIDED_TYPE_LEAK_GENERIC_ERROR_MESSAGE_FMT_STR);
  }

  public static <T> ClaroTypeException forUndecidedTypeLeak(T contextuallyExpectedType) {
    return new ClaroTypeException(
        String.format(
            UNDECIDED_TYPE_LEAK_ERROR_MESSAGE_FMT_STR,
            contextuallyExpectedType
        )
    );
  }

  public static ClaroTypeException forUndecidedTypeLeak(ImmutableSet<Type> contextuallySupportedExpectedType) {
    return new ClaroTypeException(
        String.format(
            UNDECIDED_TYPE_LEAK_ERROR_MESSAGE_FMT_STR,
            Joiner.on(", ").join(contextuallySupportedExpectedType)
        )
    );
  }

  public static ClaroTypeException forUndecidedTypeLeakEmptyListInitialization() {
    return new ClaroTypeException(MISSING_TYPE_DECLARATION_FOR_EMPTY_LIST_INITIALIZATION);
  }

  public static ClaroTypeException forAmbiguousLambdaExprMissingTypeDeclarationForLambdaInitialization() {
    return new ClaroTypeException(MISSING_TYPE_DECLARATION_FOR_LAMBDA_INITIALIZATION);
  }

  public static ClaroTypeException forInvalidCast(Object actualType, Type assertedType) {
    return new ClaroTypeException(
        String.format(
            INVALID_CAST_ERROR_MESSAGE_FMT_STR,
            actualType,
            assertedType
        )
    );
  }

  public static ClaroTypeException forInvalidMemberReference(Type structType, String identifier) {
    return new ClaroTypeException(
        String.format(
            INVALID_MEMBER_REFERENCE,
            structType,
            identifier
        )
    );
  }

  public static ClaroTypeException forUnsetRequiredStructMember(
      Type structType, ImmutableSet<?> unsetFields) {
    boolean plural = unsetFields.size() > 1;
    return new ClaroTypeException(
        String.format(
            UNSET_REQUIRED_STRUCT_MEMBER,
            structType,
            // English is weird.
            plural ? "s" : "",
            Joiner.on(", ").join(unsetFields),
            plural ? "" : "s"
        )
    );
  }

  public static ClaroTypeException forWrongNumberOfArgsForLambdaDefinition(Type expectedType) {
    return new ClaroTypeException(String.format(WRONG_NUMBER_OF_ARGS_FOR_LAMBDA_DEFINITION, expectedType));
  }

  public static ClaroTypeException forDuplicateKeyBinding(String name, Type type) {
    return new ClaroTypeException(String.format(DUPLICATE_KEY_BINDING, name, type));
  }

  public static ClaroTypeException forDuplicateModuleDefinition(String moduleName) {
    return new ClaroTypeException(String.format(DUPLICATE_MODULE_DEFINITION, moduleName));
  }

  public static ClaroTypeException forUsingDuplicateModules(ImmutableList<String> duplicatedUsedModules) {
    return new ClaroTypeException(String.format(USING_DUPLICATE_MODULES, Joiner.on(", ").join(duplicatedUsedModules)));
  }

  public static ClaroTypeException forUsingUndefinedModules(ImmutableList<String> undefinedModulesList) {
    return new ClaroTypeException(String.format(USING_UNDEFINED_MODULES, Joiner.on(", ").join(undefinedModulesList)));
  }

  public static ClaroTypeException forDuplicateKeyBindings(Set<Key> duplicatedKeyBindingsSet) {
    return new ClaroTypeException(
        String.format(
            DUPLICATE_KEY_BINDINGS,
            duplicatedKeyBindingsSet.stream()
                .map(key -> String.format("%s:%s", key.name, key.type))
                .collect(Collectors.joining(", ", "[", "]"))
        )
    );
  }

  public static ClaroTypeException forRebindingKeyFromOuterUsingBlock(Set<Key> reboundKeys) {
    return new ClaroTypeException(
        String.format(
            REBINDING_KEYS_FROM_OUTER_USING_BLOCK,
            reboundKeys.stream()
                .map(key -> String.format("%s:%s", key.name, key.type))
                .collect(Collectors.joining(", ", "[", "]"))
        )
    );
  }

  public static ClaroTypeException forInvalidProcedureDefinitionWithinUsingBlock(String procedureName, Type resolvedProcedureType) {
    return new ClaroTypeException(
        String.format(INVALID_PROCEDURE_DEFINITION_WITHIN_USING_BLOCK, procedureName, resolvedProcedureType));
  }

  public static ClaroTypeException forDuplicateInjectedLocalNames(
      String procedureName, Type procedureType, Set<String> duplicateInjectedLocalNames) {
    return new ClaroTypeException(
        String.format(
            DUPLICATE_INJECTED_LOCAL_NAMES,
            procedureName,
            procedureType,
            duplicateInjectedLocalNames.stream()
                .collect(Collectors.joining(", ", "[", "]"))
        )
    );
  }

  public static ClaroTypeException forMissingBindings(
      String procedureName, Type procedureType, Set<Key> missingBindings) {
    return new ClaroTypeException(
        String.format(
            PROCEDURE_CALL_MISSING_BINDING_KEYS,
            procedureName,
            procedureType,
            missingBindings.stream()
                .map(key -> String.format("%s:%s", key.name, key.type))
                .collect(Collectors.joining(", ", "[", "]"))
        )
    );
  }

  public static ClaroTypeException forGraphFunctionNotReturningFuture(String procedureName, Type procedureType) {
    return new ClaroTypeException(
        String.format(
            GRAPH_FUNCTION_DOES_NOT_RETURN_FUTURE,
            procedureName,
            procedureType
        )
    );
  }

  public static ClaroTypeException forGraphFunctionWithDuplicatedNodeNames(
      String procedureName, Set<String> duplicatedNodeNamesSet) {
    return new ClaroTypeException(
        String.format(
            GRAPH_FUNCTION_WITH_DUPLICATED_NODE_NAMES,
            procedureName,
            duplicatedNodeNamesSet.stream()
                .collect(Collectors.joining(", ", "[", "]"))
        )
    );
  }

  public static ClaroTypeException forGraphFunctionWithUnconnectedNodes(String procedureName, Set<String> unusedNodes) {
    return new ClaroTypeException(
        String.format(
            GRAPH_FUNCTION_WITH_UNCONNECTED_NODES,
            procedureName,
            unusedNodes.stream()
                .collect(Collectors.joining(", ", "[", "]"))
        )
    );
  }

  public static ClaroTypeException forBlockingCallIndirectlyReachableFromGraphFunction(
      String graphFunctionName, Type graphFunctionType, Map<String, Type> blockingProcedureDeps) {
    return new ClaroTypeException(
        String.format(
            BLOCKING_CALL_INDIRECTLY_REACHABLE_FROM_GRAPH_FUNCTION,
            graphFunctionName,
            graphFunctionType,
            blockingProcedureDeps.entrySet().stream()
                .map(entry -> String.format("%s %s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", ", "[", "]"))
        )
    );
  }

  public static ClaroTypeException forInvalidBlockingProcedureDefinitionMissingBlockingAnnotation(
      String procedureName,
      Type resolvedProcedureType,
      Map<String, Type> blockingProcedureDeps) {
    if (blockingProcedureDeps.isEmpty()) {
      // This procedure itself is making the blocking call, it's not the result of any sort of transitive procedure dep.
      return new ClaroTypeException(
          String.format(
              BLOCKING_PROCEDURE_MISSING_BLOCKING_ANNOTATION,
              procedureName,
              resolvedProcedureType
          )
      );
    } else {
      // This procedure is blocking because of a dep on a blocking procedure.
      return new ClaroTypeException(
          String.format(
              PROCEDURE_DEPENDING_ON_BLOCKING_PROCEDURE_MISSING_BLOCKING_ANNOTATION,
              procedureName,
              resolvedProcedureType,
              blockingProcedureDeps.entrySet().stream()
                  .map(entry -> String.format("%s %s", entry.getKey(), entry.getValue()))
                  .collect(Collectors.joining(", ", "[", "]"))
          )
      );
    }
  }

  public static ClaroTypeException forInvalidUseOfBlockingGenericsOnBlockingProcedureDefinition(
      String procedureName,
      Type resolvedProcedureType,
      Map<String, Type> blockingProcedureDeps) {
    if (blockingProcedureDeps.isEmpty()) {
      // This procedure itself is making the blocking call, it's not the result of any sort of transitive procedure dep.
      return new ClaroTypeException(
          String.format(
              INVALID_USE_OF_BLOCKING_GENERICS_ON_BLOCKING_PROCEDURE,
              procedureName,
              resolvedProcedureType
          )
      );
    } else {
      // This procedure is blocking because of a dep on a blocking procedure.
      return new ClaroTypeException(
          String.format(
              INVALID_USE_OF_BLOCKING_GENERICS_ON_PROCEDURE_DEPENDING_ON_BLOCKING_PROCEDURE,
              procedureName,
              resolvedProcedureType,
              blockingProcedureDeps.entrySet().stream()
                  .map(entry -> String.format("%s %s", entry.getKey(), entry.getValue()))
                  .collect(Collectors.joining(", ", "[", "]"))
          )
      );
    }
  }

  public static ClaroTypeException forInvalidBlockingAnnotationOnNonBlockingProcedureDefinition(
      String procedureName, Type resolvedProcedureType) {
    return new ClaroTypeException(
        String.format(
            INVALID_BLOCKING_ANNOTATION_ON_NON_BLOCKING_PROCEDURE_DEFINITION,
            procedureName,
            resolvedProcedureType
        )
    );
  }

  public static ClaroTypeException forInvalidUseOfBlockingGenericsOnNonBlockingProcedureDefinition(
      String procedureName, Type resolvedProcedureType) {
    return new ClaroTypeException(
        String.format(
            INVALID_USE_OF_BLOCKING_GENERICS_ON_NON_BLOCKING_PROCEDURE_DEFINITION,
            procedureName,
            resolvedProcedureType
        )
    );
  }

  public static RuntimeException forInvalidUseOfBlockingGenericsOverArgNotMarkedMaybeBlocking(
      String procedureName, Type resolvedProcedureType, String givenArgName, Type actualArgType) {
    return new RuntimeException(
        String.format(
            INVALID_USE_OF_BLOCKING_GENERICS_OVER_ARG_NOT_MARKED_MAYBE_BLOCKING,
            procedureName,
            resolvedProcedureType,
            givenArgName,
            actualArgType
        )
    );
  }

  public static ClaroTypeException forNodeReferenceCycleInGraphProcedure(String procedureName, String nodeName) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_NODE_REFERENCE_CYCLE_IN_GRAPH_PROCEDURE,
            procedureName,
            nodeName
        )
    );
  }

  public static ClaroTypeException forGraphConsumerRootNodeIsNotConsumerFn(String procedureName, String nodeName) {
    return new ClaroTypeException(
        String.format(
            GRAPH_CONSUMER_ROOT_NODE_IS_NOT_CONSUMER_FN,
            nodeName,
            procedureName
        )
    );
  }

  public static ClaroTypeException forBackreferenceOutsideOfValidPipeChainContext() {
    return new ClaroTypeException(
        BACKREFERENCE_OUTSIDE_OF_VALID_PIPE_CHAIN_CONTEXT
    );
  }

  public static ClaroTypeException forImplementationOfUnknownContract(String contractName, String implementationName) {
    return new ClaroTypeException(
        String.format(
            CONTRACT_IMPLEMENTATION_FOR_UNDEFINED_CONTRACT,
            implementationName,
            contractName
        ));
  }

  public static ClaroTypeException forContractImplementationWithWrongNumberOfTypeParams(
      String contractTypeString, String implementationTypeString) {
    return new ClaroTypeException(
        String.format(
            CONTRACT_IMPLEMENTATION_WITH_WRONG_NUMBER_OF_TYPE_PARAMS, contractTypeString, implementationTypeString));
  }

  public static ClaroTypeException forDuplicateContractImplementation(
      String currentImplementationTypeString, String otherImplementationTypeString) {
    return new ClaroTypeException(
        String.format(
            DUPLICATE_CONTRACT_IMPLEMENTATION,
            currentImplementationTypeString,
            otherImplementationTypeString
        ));
  }

  public static ClaroTypeException forContractImplementationMissingRequiredProcedureDefinitions(
      String contractTypeString, Sets.SetView<String> missingContractProcedures) {
    return new ClaroTypeException(
        String.format(
            CONTRACT_IMPLEMENTATION_MISSION_REQUIRED_PROCEDURE_DEFS,
            contractTypeString,
            missingContractProcedures.stream().collect(Collectors.joining(", ", "[", "]"))
        ));
  }

  public static ClaroTypeException forContractImplementationWithExtraProcedureDefinitions(
      String contractTypeString, Sets.SetView<String> extraContractImplProcedures) {
    return new ClaroTypeException(
        String.format(
            CONTRACT_IMPLEMENTATION_WITH_EXTRA_PROCEDURE_DEFS,
            contractTypeString,
            extraContractImplProcedures.stream().collect(Collectors.joining(", ", "[", "]"))
        ));
  }

  public static ClaroTypeException forContractProcedureImplementationSignatureMismatch(
      String contractTypeString,
      String procedureName,
      Type contractExpectedProcedureSignature,
      Type resolvedProcedureType) {
    return new ClaroTypeException(
        String.format(
            CONTRACT_PROCEDURE_IMPLEMENTATION_DOES_NOT_MATCH_REQUIRED_SIGNATURE,
            contractTypeString,
            procedureName,
            resolvedProcedureType,
            contractExpectedProcedureSignature
        ));
  }

  public static ClaroTypeException forReferencingUnknownContract(String contractName) {
    return new ClaroTypeException(
        String.format(
            INVALID_REFERENCE_TO_UNDEFINED_CONTRACT,
            contractName
        ));
  }

  public static ClaroTypeException forContractReferenceWithWrongNumberOfTypeParams(
      String contractTypeString, String implementationTypeString) {
    return new ClaroTypeException(
        String.format(
            CONTRACT_REFERENCE_WITH_WRONG_NUMBER_OF_TYPE_PARAMS, contractTypeString, implementationTypeString));
  }

  public static ClaroTypeException forContractReferenceUndefinedProcedure(
      String contractName, ImmutableList<String> concreteTypeStrings, String name) {
    return new ClaroTypeException(
        String.format(
            INVALID_REFERENCE_TO_UNDEFINED_CONTRACT_PROCEDURE,
            contractName,
            String.join(", ", concreteTypeStrings),
            name
        ));
  }

  public static ClaroTypeException forGenericProcedureRequiresUnknownContract(String requiredContract, String functionName) {
    return new ClaroTypeException(
        String.format(
            GENERIC_PROCEDURE_REQUIRES_UNDEFINED_CONTRACT,
            functionName,
            requiredContract
        ));
  }

  public static ClaroTypeException forGenericProcedureRequiresContractImplementationWithWrongNumberOfTypeParams(
      String referencedContractTypeString, String actualContractTypeString) {
    return new ClaroTypeException(
        String.format(
            GENERIC_PROCEDURE_REQUIRES_CONTRACT_WITH_WRONG_NUMBER_OF_TYPE_PARAMS,
            referencedContractTypeString,
            actualContractTypeString
        ));
  }

  public static ClaroTypeException forGenericProcedureCallWithoutOutputTypeSufficientlyConstrainedByArgsAndContext(String name, Type referencedIdentifierType) {
    return new ClaroTypeException(
        String.format(
            GENERIC_PROCEDURE_CALL_REQUIRES_OUTPUT_TYPE_TO_BE_CONSTRAINED_BY_CONTEXT,
            name,
            referencedIdentifierType
        ));
  }

  public static ClaroTypeException forGenericProcedureCallForConcreteTypesWithRequiredContractImplementationMissing(
      String name, Type referencedIdentifierType, String requiredContract, ImmutableList<String> requiredContractConcreteTypes) {
    return new ClaroTypeException(
        String.format(
            GENERIC_PROCEDURE_CALL_FOR_CONCRETE_TYPES_WITH_REQUIRED_CONTRACT_IMPLEMENTATION_MISSING,
            name,
            referencedIdentifierType,
            requiredContract,
            Joiner.on(", ").join(requiredContractConcreteTypes)
        ));
  }

  public static ClaroTypeException forContractProcedureReferencedWithoutRequiredAnnotationOnGenericFunction(
      String contractName, String procedureName, ImmutableList<Type> resolvedContractConcreteTypes) {
    return new ClaroTypeException(
        String.format(
            CONTRACT_PROCEDURE_REFERENCE_WITHOUT_REQUIRED_ANNOTATION_ON_GENERIC_FUNCTION,
            contractName,
            procedureName,
            contractName,
            Joiner.on(", ").join(resolvedContractConcreteTypes)
        ));
  }

  public static ClaroTypeException forInvalidGenericProcedureReferenceAsFirstClassObjectWithoutContextualTypeAssertion(
      String procedureName, Type procedureType) {
    return new ClaroTypeException(
        String.format(
            INVALID_GENERIC_PROCEDURE_REFERENCE_AS_FIRST_CLASS_OBJECT_WITHOUT_CONTEXTUAL_TYPE_ASSERTION,
            procedureName,
            procedureType
        ));
  }
}
