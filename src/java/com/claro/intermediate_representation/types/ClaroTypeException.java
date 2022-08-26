package com.claro.intermediate_representation.types;

import com.claro.runtime_utilities.injector.Key;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ClaroTypeException extends Exception {

  private static final String INVALID_TYPE_ERROR_MESSAGE_FMT_STR =
      "Invalid type:\n\tFound:\n\t\t%s\n\tExpected:\n\t\t%s";
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
  private static final String MISSING_TYPE_DECLARATION_FOR_LAMBDA_INITIALIZATION =
      "The type of this lambda is UNDECIDED at compile-time! You must explicitly declare the type of a variable with a lambda expression assigned to it to assert this type statically at compile-time.";
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
      "Illegal use of blocking-generics on procedure %s %s. The procedure is guaranteed blocking.";
  private static final String PROCEDURE_DEPENDING_ON_BLOCKING_PROCEDURE_MISSING_BLOCKING_ANNOTATION =
      "Procedure %s %s depends on blocking procedures %s but is missing required explicit blocking annotation.";
  private static final String INVALID_USE_OF_BLOCKING_GENERICS_ON_PROCEDURE_DEPENDING_ON_BLOCKING_PROCEDURE =
      "Illegal use of blocking-generics on procedure %s %s. The procedure is guaranteed blocking due to dependencies on blocking procedures %s.";
  private static final String INVALID_BLOCKING_ANNOTATION_ON_NON_BLOCKING_PROCEDURE_DEFINITION =
      "Non-blocking procedure %s %s must not be annotated as blocking.";
  private static final String INVALID_USE_OF_BLOCKING_GENERICS_ON_NON_BLOCKING_PROCEDURE_DEFINITION =
      "Illegal use of blocking-generics on procedure %s %s. The procedure is guaranteed non-blocking.";
  private static final String ILLEGAL_NODE_REFERENCE_CYCLE_IN_GRAPH_PROCEDURE =
      "Illegal node reference cycle detected within Graph procedure <%s>. Through transitive node references, node <%s> depends cyclically on itself. Graph nodes must represent a DAG.";
  private static final String GRAPH_CONSUMER_ROOT_NODE_IS_NOT_CONSUMER_FN =
      "Root node <%s> of Graph Consumer <%s> must defer to a consumer<...> as this Graph should not return a value. If you would like to return a value, change the signature to `graph function`.";


  public ClaroTypeException(String message) {
    super(message);
  }

  public ClaroTypeException(Type actualType, Type expectedType) {
    super(String.format(INVALID_TYPE_ERROR_MESSAGE_FMT_STR, actualType, expectedType));
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

  public static <T> ClaroTypeException forUndecidedTypeLeak() {
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

  public static ClaroTypeException forUndecidedTypeLeakMissingTypeDeclarationForLambdaInitialization() {
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
}
