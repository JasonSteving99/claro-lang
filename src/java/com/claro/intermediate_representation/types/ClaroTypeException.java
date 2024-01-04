package com.claro.intermediate_representation.types;

import com.claro.runtime_utilities.injector.Key;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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


  ////////////////////////////////////////////////////////////////////////////////
  // BEGIN WARNING: "IF_CHANGE(UNDECIDED)"
  //   FunctionCallExpr's type validation has a dependency on the word "UNDECIDED"
  //   to check whether it should propagate an error or not. Bad design, sure, but
  //   it is the current state of the world. If you're going to change these error
  //   messages, first validate FunctionCallExpr type validation is updated too.
  ////////////////////////////////////////////////////////////////////////////////
  private static final String UNDECIDED_TYPE_LEAK_ERROR_MESSAGE_FMT_STR =
      "The type of this expression is UNDECIDED at compile-time! You must explicitly cast the Expr to the contextually expected type <%s> to assert this type at compile-time or fix a bug if the contextually expected type isn't applicable.";
  private static final String UNDECIDED_TYPE_LEAK_GENERIC_ERROR_MESSAGE_FMT_STR =
      "The type of this expression is UNDECIDED at compile-time! You must explicitly cast the Expr to the expected type to assert this type at compile-time.";
  private static final String MISSING_TYPE_DECLARATION_FOR_EMPTY_LIST_INITIALIZATION =
      "The type of this empty list is UNDECIDED at compile-time! You must explicitly declare the type of a variable having the empty list `[]` assigned to it to assert this type statically at compile-time.";
  private static final String MISSING_TYPE_DECLARATION_FOR_EMPTY_MAP_INITIALIZATION =
      "The type of this empty map is UNDECIDED at compile-time! You must explicitly declare the type of a variable having the empty map `{}` assigned to it to assert this type statically at compile-time.";
  ////////////////////////////////////////////////////////////////////////////////
  // END WARNING: "IF_CHANGE(UNDECIDED)"
  ////////////////////////////////////////////////////////////////////////////////


  public static final String MISSING_TYPE_DECLARATION_FOR_LAMBDA_INITIALIZATION =
      // WARNING: CHECKING AGAINST THIS IN FunctionCallExpr.java! DO NOT CHANGE THIS STRING WITHOUT CHECKING THERE FIRST.
      "Ambiguous Lambda Expression Type: Type hint required. When a lambda Expr's type is not constrained by its context, the type must be statically declared via either a type annotation, or a cast.";
  private static final String INVALID_CAST_ERROR_MESSAGE_FMT_STR =
      "Invalid Cast: Found <%s> which cannot be converted to <%s>.";
  private static final String IMPOSSIBLE_CAST_ERROR_MESSAGE_FMT_STR =
      "Invalid Cast: Attempting to cast the Tuple subscript to a type that isn't present in the tuple. This cast would definitely fail at runtime.\n" +
      "\t\tFound:\n" +
      "\t\t\t%s\n" +
      "\t\tExpected one of the following:\n" +
      "%s";
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
      "Invalid Contract Implementation: %s is a duplicate of existing implementation.";
  private static final String CONTRACT_IMPLEMENTATION_MISSION_REQUIRED_PROCEDURE_DEFS =
      "Invalid Contract Implementation: %s is missing definitions for the following required procedures %s.";
  private static final String CONTRACT_IMPLEMENTATION_WITH_EXTRA_PROCEDURE_DEFS =
      "Invalid Contract Implementation: %s defines the following procedures that are not required by the implemented contract %s.";
  private static final String CONTRACT_IMPLEMENTATION_VIOLATING_IMPLIED_TYPES_CONSTRAINT =
      "Invalid Contract Implementation: The Contract you're attempting to implement is defined as %s<%s => %s> which means that there can only be exactly one implementation of %s for the unconstrained type params %s.\n" +
      "\t\tHowever, the following conflicting implementations were found:\n" +
      "\t\t\t%s\n" +
      "\t\tAND\n" +
      "\t\t\t%s";
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
  private static final String CONTRACT_PROCEDURE_CALL_OVER_UNIMPLEMENTED_CONCRETE_TYPES =
      "Invalid Contract Procedure Reference: No implementation of %s found.";
  private static final String CONTRACT_PROCEDURE_DYNAMIC_DISPATCH_CALL_OVER_UNSUPPORTED_CONTRACT_TYPE_PARAMS =
      "%s\n\tIf you were attempting to make a dynamic dispatch call over this Contract Procedure, then (for the given argument/return types) the following contract implementations must be present:\n\t\t- %s";
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
  private static final String TUPLE_SUBSCRIPT_INDEX_OUT_OF_BOUNDS =
      "Tuple Subscript Literal Out of Bounds:\n\tFor subscript on tuple of type: %s\n\tFound:\n\t\t%s\n\tExpected:\n\t\tindex in range [0, %s)";
  private static final String TUPLE_SUBSCRIPT_ASSIGNMENT_FOR_NON_COMPILE_TIME_CONSTANT_INDEX =
      "Invalid Tuple Subscript: Subscript must be an integer literal for reassignment of elements within a tuple.";
  private static final String TUPLE_HAS_UNEXPECTED_SIZE =
      "Tuple Has Wrong Number of Elements:\n\tFound:\n\t\t%s\n\tExpected:\n\t\t%s - %s";
  private static final String IMPOSSIBLE_RECURSIVE_ALIAS_TYPE_DEFINITION =
      "Impossible Recursive Alias Type Definition: Alias `%s` represents a type that is impossible to initialize in a " +
      "finite number of steps. To define a recursive type you must ensure that there is an implicit \"bottom\" type " +
      "to terminate the recursion. Try wrapping the Alias self-reference in some builtin empty-able collection:\n" +
      "\tE.g.\n\t\tInstead of:\n\t\t\talias BadType : tuple<int, BadType>\n" +
      "\t\tTry something like:\n\t\t\talias GoodType : tuple<int, [GoodType]>";
  private static final String AMBIGUOUS_LAMBDA_FIRST_CLASS_GENERIC_ARG =
      "Ambiguous Lambda Expr as Generic Arg: The type signature of the lambda passed as arg %s is ambiguous.\n" +
      "\tClaro infers generic types from left-to-right, starting from the\n" +
      "\tcontextually expected return type (if constrained) then continuing with\n" +
      "\targ type inference. Upon reaching the lambda passed as arg %s, Claro does\n" +
      "\tnot have enough information to infer the lambda's full type signature.\n" +
      "\t\tClaro partially inferred the following arg type:\n" +
      "\t\t\t%s\n" +
      "\tYou must provide an explicit type annotation to constrain the generic\n" +
      "\ttype(s) in arg %s!";
  private static final String INVALID_LAMBDA_CAST =
      "Invalid Lambda Expr Cast: Cannot cast lambda to non-procedure type!\n\tExpected: %s";
  private static final String AMBIGUOUS_CONTRACT_PROCEDURE_CALL_MISSING_REQUIRED_CONTEXTUAL_OUTPUT_TYPE_ASSERTION =
      "Ambiguous Contract Procedure Call: Calls to the procedure `%s<%s>::%s` is ambiguous without an explicit type annotation to constrain the expected generic return type `%s`.";
  private static final String AMBIGUOUS_GENERIC_PROVIDER_CALL_MISSING_REQUIRED_CONTEXTUAL_OUTPUT_TYPE_ASSERTION =
      "Ambiguous Generic Provider Call: Calls to the generic `%s` `%s` is ambiguous without an explicit type annotation to constrain the expected generic return type `%s`.";
  private static final String ILLEGAL_ONEOF_TYPE_DECL_W_DUPLICATED_TYPES =
      "Illegal Oneof Type Declaration: The given type declaration `oneof<%s>` has duplicated types!\n\tInstead, rewrite it as the following:\n\t\t%s";
  private static final String ILLEGAL_INSTANCEOF_CHECK_AGAINST_ONEOF_TYPE =
      "Illegal instanceof Check: %s is not a concrete type! Use instanceof to check which concrete type variant the given oneof is currently holding.";
  private static final String ILLEGAL_INSTANCEOF_CHECK_OVER_NON_ONEOF_EXPR =
      "Illegal instanceof Check: %s is a statically known concrete type! Using instanceof over a statically known concrete type is never necessary.";
  private static final String INVALID_BOOLEAN_EXPR_IMPLYING_A_SINGLE_VALUE_IS_MORE_THAN_ONE_TYPE =
      "Invalid Boolean Expression: This expression implies that the given value `%s` is of *both* of the following types:\n\t\t%s\n\tAND\n\t\t%s\n\t By definition, all Claro values are of exactly *one* type, so this boolean expression is never valid.";
  private static final String ILLEGAL_MUTABLE_TYPE_ANNOTATION_ON_INHERENTLY_IMMUTABLE_TYPE =
      "Illegal Type Declaration: Cannot mark %s with the `mut` type modifier, it is inherently immutable.";
  private static final String ILLEGAL_MUTATION_ATTEMPT_ON_IMMUTABLE_VALUE =
      "Illegal Mutation of Immutable Value: Mutation of immutable values is forbidden!\n" +
      "\t\tFound the immutable type:\n" +
      "\t\t\t%s\n" +
      "\t\tIn order to mutate this value, the value's type would need to be updated to:\n" +
      "\t\t\t%s";
  private static final String INVALID_UNWRAP_OF_BUILTIN_TYPE =
      "Invalid Unwrap of Builtin Type: `unwrap()` is only supported over concrete user-defined custom types wrapping another type. The value you attempted to unwrap is a builtin type, meaning you have already reached the \"bottom\", there is nothing to unwrap.\n" +
      "\t\tFound:\n" +
      "\t\t\t%s";
  private static final String INVALID_UNWRAP_OF_GENERIC_TYPE =
      "Illegal Unwrap of Generic Type: `unwrap()` is only supported over concrete user-defined custom types wrapping another type. The value you attempted to unwrap is a Generic type which cannot be unwrapped.\n" +
      "\t\tFound:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_INITIALIZERS_BLOCK_REFERENCING_UNDECLARED_INITIALIZED_TYPE =
      "Illegal %s Block for Undeclared Type: No custom type named `%s` declared within the current scope!";
  private static final String ILLEGAL_INITIALIZERS_BLOCK_REFERENCING_NON_USER_DEFINED_TYPE =
      "Illegal %s Block for Non-User-Defined Type: `%s` does not reference a user-defined type!\n" +
      "\t\tFound:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_EXPORTED_INITIALIZERS_BLOCK_REFERENCING_TYPE_FROM_DEP_MODULE =
      "Illegally Exporting an %s Block for a Type Defined in a Dep Module: In order for Claro's incremental compilation scheme to function properly, a Module may only export %s blocks for their own explicitly exported type definitions.";
  private static final String ILLEGAL_USE_OF_USER_DEFINED_TYPE_DEFAULT_CONSTRUCTOR_OUTSIDE_OF_INITIALIZER_PROCEDURES =
      "Illegal Use of User-Defined Type Constructor Outside of Initializers Block: An initializers block has been defined for the custom type `%s`, so, in order to maintain any semantic constraints that the initializers are intended to impose on the type, you aren't allowed to use the type's default constructor directly.\n" +
      "\t\tInstead, to get an instance of this type, consider calling one of the defined initializers:\n" +
      "%s";
  private static final String ILLEGAL_USE_OF_USER_DEFINED_TYPE_DEFAULT_UNWRAPPER_OUTSIDE_OF_UNWRAPPER_PROCEDURES =
      "Illegal Use of User-Defined Type Unwrapper Outside of Unwrappers Block: An unwrappers block has been defined for the custom type `%s`, so, in order to maintain any semantic constraints that the unwrappers are intended to impose on the type, you aren't allowed to use the type's default `unwrap()` function directly.\n" +
      "\t\tInstead, to unwrap an instance of this type, consider calling one of the defined unwrappers:\n" +
      "%s";
  private static final String ILLEGAL_USE_OF_OPAQUE_TYPE_UNWRAPPER =
      "Illegal Attempted Unwrap of Opaque Type: Direct use of `unwrap()` over opaque types is forbidden. Only the " +
      "defining module is able to access the internal value of an opaque type. Reference the defining module's api " +
      "to determine which exported procedures (if any) can be deferred to to operate over this opaque type instead of " +
      "attempting to unwrap the value directly.";
  private static final String ILLEGAL_IMPLICIT_UNWRAP_OF_OPAQUE_USER_DEFINED_TYPE_IN_MATCH_PATTERN =
      "Illegal Implicit Unwrap of Opaque User-Defined Type in Match Pattern: An unwrappers block has been defined for the custom type `%s`, so, in order to maintain any semantic constraints that the unwrappers are intended to impose on the type, you aren't allowed to implicitly `unwrap()` values of this type by attempting to pattern match the wrapped value.\n" +
      "\t\tFor example, the following case pattern:\n" +
      "\t\t\tcase %s(X) -> ...use X...;\n" +
      "\t\tis equivalent to the following use of unwrap:\n" +
      "\t\t\tcase Matched%s ->\n" +
      "\t\t\t\tvar X = unwrap(Matched%s);\n" +
      "\t\t\t\t...use X...;\n\n" +
      "\t\tInstead, to match on an instance of this type you must use a wildcard as in one of the below options:\n" +
      "\t\t\t- case %s(_) -> ...;\n" +
      "\t\t\t- case _ -> ...;";
  private static final String ILLEGAL_AUTOMATIC_ERROR_PROPAGATION =
      "Illegal Use of Automatic Error Propagation: Automatic Error Propagation only applies to oneofs containing at least one `Error<T>` variant and one non-`Error<T>` variant.\n" +
      "\t\tFound:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_AUTOMATIC_ERROR_PROPAGATION_OUTSIDE_OF_PROCEDURE_BODY =
      "Illegal Use of Automatic Error Propagation: Automatic Error Propagation is not allowed outside of the body procedure of a procedure that supports returning the possible error type(s) (i.e. in the top-level scope, or in a Consumer function) as this operation is just syntactic sugar for conditionally doing an early return from a procedure to propagate an error.";
  private static final String ILLEGAL_AUTOMATIC_ERROR_PROPAGATION_FOR_UNSUPPORTED_RETURN_TYPE =
      "Illegal Use of Automatic Error Propagation: Automatic Error Propagation not supported here due to the error type variant(s) not being valid return types.\n" +
      "\t\tFound:\n" +
      "\t\t\t%s\n" +
      "\t\tExpected return type:\n" +
      "\t\t\t%s";
  private static final String INVALID_STRUCT_FIELD_ACCESS_FOR_NON_EXISTENT_FIELD =
      "Illegal Struct Field Access: The requested field `%s` doesn't exist!\n" +
      "\t\tFound Struct:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_PROCEDURE_ARG =
      "Illegal Mutable Graph Procedure Arg: As Graph Procedures are multi-threaded by nature, all args must be deeply-immutable in order to guarantee that Graph Procedures are data-race free by construction.\n" +
      "\t\tFound the mutable type:\n" +
      "\t\t\t%s\n" +
      "\t\tTo correct this, consider converting the arg's type to its deeply-immutable variant:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_PROCEDURE_ARG_W_NO_SUGGESTED_DEEPLY_IMMUTABLE_TYPE =
      "Illegal Mutable Graph Procedure Arg: As Graph Procedures are multi-threaded by nature, all args must be deeply-immutable in order to guarantee that Graph Procedures are data-race free by construction.\n" +
      "\t\tFound the mutable type:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_PROCEDURE_INJECTED_VALUE =
      "Illegal Mutable Graph Procedure Injected Value: As Graph Procedures are multi-threaded by nature, all injected values must be deeply-immutable in order to guarantee that Graph Procedures are data-race free by construction.\n" +
      "\t\tFound the mutable type:\n" +
      "\t\t\t%s\n" +
      "\t\tTo correct this, consider converting the injected value's type to its deeply-immutable variant:\n" +
      "\t\t\t%s";
  private static final String
      ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_PROCEDURE_INJECTED_VALUE_W_NO_SUGGESTED_DEEPLY_IMMUTABLE_TYPE =
      "Illegal Mutable Graph Procedure Injected Value: As Graph Procedures are multi-threaded by nature, all injected values must be deeply-immutable in order to guarantee that Graph Procedures are data-race free by construction.\n" +
      "\t\tFound the mutable type:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_NODE_RESULT_TYPE =
      "Illegal Mutable Graph Node Result: As Graph Procedures are multi-threaded by nature, all node expression types must be deeply-immutable in order to guarantee that Graph Procedures are data-race free by construction.\n" +
      "\t\tFound the result of node `%s` to have the mutable type:\n" +
      "\t\t\t%s\n" +
      "\t\tTo correct this, consider converting to its deeply-immutable variant:\n" +
      "\t\t\t%s";
  private static final String
      ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_NODE_RESULT_TYPE_W_NO_SUGGESTED_DEEPLY_IMMUTABLE_TYPE =
      "Illegal Mutable Graph Node Result: As Graph Procedures are multi-threaded by nature, all node expression types must be deeply-immutable in order to guarantee that Graph Procedures are data-race free by construction.\n" +
      "\t\tFound the result of node `%s` to have the mutable type:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_TRANSITIVE_USE_OF_MUTABLE_TYPE_AS_GRAPH_PROCEDURE_INJECTED_VALUE =
      "Illegal Mutable Graph Procedure Transitive Injected Value: As Graph Procedures are multi-threaded by nature, all injected values must be deeply-immutable in order to guarantee that Graph Procedures are data-race free by construction.\n" +
      "\t\tFound the following mutable types being transitively injected into procedures called by Graph Procedure `%s`:" +
      "%s";
  private static final String ILLEGAL_LAMBDA_CAPTURE_OF_TYPE_THAT_CANNOT_BE_AUTO_CONVERTED_TO_DEEPLY_IMMUTABLE_VARIANT =
      "Illegal Lambda Capture of Mutable Value: All variables captured within a lambda context must be either deeply-immutable or auto-convertible to a deeply-immutable variant." +
      " This restriction ensures that lambdas do not lead to so-called \"spooky action at a distance\" and is essential to guaranteeing that Graph Procedures are data-race free" +
      " by construction.\n" +
      "\t\tFound the following mutable type:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_MUTATION_OF_LAMBDA_CAPTURED_VARIABLE =
      "Illegal Mutation of Lambda Captured Variable: The value of all variables captured within a lambda context are final and may not be changed." +
      " This restriction ensures that lambdas do not lead to so-called \"spooky action at a distance\" and is essential to guaranteeing that Graph Procedures are data-race free" +
      " by construction.";
  private static final String ILLEGAL_LAMBDA_CAPTURE_OF_MUTABLE_TYPE =
      "Illegal Lambda Capture of Mutable Value: All variables captured within a lambda context must be deeply-immutable." +
      " This restriction ensures that lambdas do not lead to so-called \"spooky action at a distance\" and is essential to guaranteeing that Graph Procedures are data-race free" +
      " by construction.\n" +
      "\t\tFound the following mutable type:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_USE_OF_BREAK_STMT_OUTSIDE_LOOPING_CONSTRUCT =
      "Illegal Use of `break` Outside Loop Body: The use of the `break` keyword is to exit a looping construct early, so it's invalid to use anywhere else.";
  private static final String INVALID_HTTP_ENDPOINT_PATH_VARIABLE =
      "Invalid Http Endpoint Path Variable: All Http Endpoint definitions must be of the form `<endpointName>: \"/(<page>/|{<pathVariable>}/)*\"` where `<pathVariable>` must be a valid identifier.";
  private static final String ILLEGAL_HTTP_CLIENT_TYPE_WITH_NON_HTTP_SERVICE_PARAM_TYPE =
      "Illegal HttpClient Type: HttpClient<T>'s parameterized type, T, must be some HttpService type.\n" +
      "\t\tFound the following type:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_HTTP_SERVER_TYPE_WITH_NON_HTTP_SERVICE_PARAM_TYPE =
      "Illegal HttpClient Type: HttpServer<T>'s parameterized type, T, must be some HttpService type.\n" +
      "\t\tFound the following type:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_PARSE_FROM_JSON_FOR_UNSUPPORTED_TARGET_TYPE =
      "Illegal Attempt To Parse JSON String to Unsupported Target Type: Claro can only generate Automatic JSON parsing code for types structurally matching the following (pseudocode) type definition:\n" +
      "\t\tnewtype JSON : oneof<Nothing, boolean, int, float, string, [JSON], struct{field1: JSON, ..., fieldN: JSON}, {string: JSON}>\n" +
      "\tFound the following type:\n" +
      "\t\t%s";
  private static final String ILLEGAL_PARSE_FROM_JSON_FOR_UNSUPPORTED_TARGET_ONEOF_TYPE =
      "Illegal Attempt To Parse JSON String to Target Type Containing Unsupported Oneof: Claro's builtin JSON Parser currently supports only a limited single-token lookahead and does no recursive backtracking" +
      " so it can only generate Automatic JSON parsing code for oneof types with <= 1 list and <= 1 struct each in its variants set, and as long as both int and float do not appear together:\n" +
      "\tFor the give target type:\n" +
      "\t\t%s" +
      "\tFound the following unsupported oneof type definition:\n" +
      "\t\t%s";
  private static final String ILLEGAL_PARSE_FROM_JSON_WITH_NO_TARGET_TYPE_ASSERTION =
      "Illegal Attempt to Parse JSON String w/ No Target Type Contextually Asserted: Claro's Automatic JSON parsing codegen requires a contextually asserted target type in order to determine what JSON " +
      "to expect and to determine what Claro types to parse the JSON into.\n" +
      "\tInstead of something like:\n" +
      "\t\tvar parsed = fromJson(\"...\");\n" +
      "\tProvide some target type instead:\n" +
      "\t\tvar parsed: TargetType = fromJson(\"...\");";
  private static final String INVALID_ENDPOINT_HANDLERS_BLOCK_FOR_HTTP_SERVICE_UNDEFINED =
      "Invalid Endpoint Handlers Block for Undefined HttpService: No HttpService named `%s` declared within the current scope!";
  private static final String INVALID_ENDPOINT_HANDLERS_BLOCK_FOR_NON_HTTP_SERVICE =
      "Illegal Endpoint Handlers Block for Non-HttpService Type: `%s` does not reference an HttpService!\n" +
      "\t\tFound:\n" +
      "\t\t\t%s";
  private static final String INVALID_ENDPOINT_HANDLERS_NOT_SATISFYING_REQUIRED_SIGNATURE_FOR_HANDLED_HTTP_SERVICE =
      "Invalid Endpoint Handlers: For the given HttpService, `%s`, each endpoint must have a corresponding graph procedure defined " +
      "to handle requests. The following endpoint handler definitions were invalid:\n" +
      "%s";
  private static final String INVALID_HTTP_SERVER_GENERATION_REQUESTED_WITH_NO_HTTP_SERVICE_ENDPOINT_HANDLERS_DEFINED =
      "Invalid HttpServer Generation Requested for HttpService Missing Endpoint Handlers Definition: In order to " +
      "automatically generate an HttpServer for the given HttpService an `endpoint_handlers` block such as the " +
      "following must be defined:\n" +
      "\t\tendpoint_handlers %s {\n" +
      "%s\n" +
      "\t\t}";
  private static final String UNEXPECTED_ARGS_PASSED_TO_PROVIDER_CALL =
      "Unexpected Args Passed to Provider Call: Found %s args passed to no-arg Provider call.\n" +
      "\t\tFound:\n" +
      "\t\t\t%s";
  private static final String ILLEGAL_CONSUMER_CALL_USED_AS_EXPRESSION =
      "Illegal Consumer Call Used as Expression: Calling a Consumer does not return a value, it cannot be used as an expression.\n" +
      "\t\tFound:\n" +
      "\t\t\t%s";
  private static final String FUNCTION_CALLED_WITH_WRONG_NUMBER_OF_ARGS =
      "Illegal Function Call With Wrong Number of Args: The called %s was passed the wrong number of args.\n" +
      "\t\tFound:\n" +
      "\t\t\t%s\n" +
      "\t\tExpected:\n" +
      "\t\t\t%s";
  private static final String FUNCTION_CALL_RESULT_IGNORED =
      "Illegally Ignoring Value Returned From Function Call: The result of call to %s must not be silently ignored.\n" +
      "\t\tIf you really don't need to use the result, you may explicitly discard it using the \"trashcan operator\" (note this only makes sense if your function is side-effecting):\n" +
      "\t\t\t_ = fnWhoseResultIWantToThrowAway(foo);";
  private static final String MATCH_OVER_UNSUPPORTED_TYPE =
      "Illegal Match Over Unsupported Type: Claro *CURRENTLY* only supports matching over primitive types.\n" +
      "\t\tFound:\n" +
      "\t\t\t%s";
  private static final String MATCH_OVER_UNSUPPORTED_WRAPPED_TYPE =
      "Illegal Match Over User-Defined Type Wrapping Unsupported Type: Claro *CURRENTLY* only supports matching over primitive types.\n" +
      "\t\tFound User-Defined type:\n" +
      "\t\t\t%s\n" +
      "\t\tWrapping unsupported type:\n" +
      "\t\t\t%s";
  private static final String MATCH_OVER_UNSUPPORTED_BASE_VALUE_TYPES =
      "Illegal Match Over Type Entirely Composed of Base Types For Which Exact Matches are Impossible to Evaluate.\n" +
      "\tMatched Expression Type:\n" +
      "\t\t%s\n" +
      "\tContains the following unsupported base types:\n" +
      "\t\t- %s";
  private static final String MATCH_CONTAINS_DUPLICATE_DEFAULT_CASES =
      "Illegal Match Containing Multiple Default Cases: Each match block should contain at most one case matching the `_` wildcard.";
  private static final String NON_EXHAUSTIVE_MATCH =
      "Non-exhaustive Match: The given cases do not match every possible value of the matched type `%s`.\n" +
      "\tFor example the following case is unhandled:\n" +
      "\t\t%s\n" +
      "\tYou can also ensure that all possible cases are being handled by adding a final fallback case as below:\n" +
      "\t\tcase _ -> ...;";
  private static final String DUPLICATE_MATCH_CASE =
      "Illegal Duplicate Match Case: All case patterns should be unique within a match block.";
  private static final String USELESS_MATCH_OVER_SINGLE_DEFAULT_CASE =
      "Uselessly Matching Against Single Wildcard Case: Use of match is just unwanted noise when matching over a single wildcard pattern. Remove the match and just write the logic directly:\n" +
      "\tE.g. Instead of:\n" +
      "\t\tvar toMatch = getVal(...);\n" +
      "\t\tmatch (toMatch) {\n\t\t\tcase _ -> handleVal(toMatch);\n\t\t\t}\n" +
      "\tRewrite as the following:\n" +
      "\t\thandleVal(getVal(...));";
  private static final String USELESS_MATCH_CASE_TYPE_LITERAL_PATTERN_FOR_NON_ONEOF_MATCHED_VAL =
      "Uselessly Matching Non-Oneof Typed Value Against Type Literal: The type of the matched Expr is statically known to be %s, it's useless to match against the type.";
  private static final String USELESS_DEFAULT_CASE_IN_ALREADY_EXHAUSTIVE_MATCH =
      "Useless Default Case: The given patterns exhaustively match every possible value of the matched type. Remove the branch matching against the wildcard pattern, as it is dead code.";
  private static final String ILLEGAL_PROCEDURE_CALL_IN_MATCH_CASE_PATTERN =
      "Illegal Match Case Pattern: Unexpected attempt to call a procedure as a Match case pattern. Match case patterns must be compile-time constant values, or wildcards.";
  private static final String ILLEGAL_SHADOWING_OF_DECLARED_VARIABLE_FOR_WILDCARD_BINDING =
      "Wildcard Binding Shadows Declared Variable: Names of wildcard bindings in Match case patterns must not shadow any already-declared variable in scope.";
  private static final String INVALID_PATTERN_MATCHING_WRONG_TYPE =
      "Invalid Pattern Matching Wrong Type: This pattern is unable to match against values of the matched expression type.\n" +
      "\tMatched Expression Type:\n" +
      "\t\t%s\n" +
      "\tPattern Type:\n" +
      "\t\t%s";
  private static final String MODULE_EXPORTED_PROCEDURE_NOT_DEFINED_IN_MODULE_IMPL_FILES =
      "Module Exported Procedure Not Defined In Given Module Implementation Files: The given Module API definition " +
      "expected the following exported procedure to be defined:\n" +
      "\tName:\n" +
      "\t\t%s\n" +
      "\tSignature:\n" +
      "\t\t%s";
  private static final String MODULE_EXPORTED_PROCEDURE_NAME_BOUND_TO_INCORRECT_IMPLEMENTATION_TYPE =
      "Module Exported Procedure Name Bound To Incorrect Implementation Type: Exported procedure `%s` is bound to " +
      "incorrect type in the given module implementation files.\n" +
      "\tFound:\n" +
      "\t\t%s\n" +
      "\tExpected:\n" +
      "\t\t%s";
  private static final String MODULE_EXPORTED_OPAQUE_TYPE_NOT_DEFINED_IN_MODULE_IMPL_FILES =
      "Module Exported Opaque Type Not Defined In Given Module Implementation Files: The given Module API definition " +
      "expected the following exported opaque type to be defined:\n" +
      "\tExpected:\n" +
      "\t\tnewtype %s%s : <some %stype>";
  private static final String MODULE_EXPORTED_OPAQUE_TYPE_NAME_BOUND_TO_INCORRECT_IMPLEMENTATION_TYPE =
      "Module Exported Opaque Type Name Bound To Incorrect Implementation Type: Exported opaque type `%s` is bound to " +
      "incorrect type in the given module implementation files.\n" +
      "\tThe exported opaque type definition:\n" +
      "\t\topaque newtype %s%s%s\n" +
      "\tExpected:\n" +
      "\t\tnewtype %s%s : <some %stype>";
  private static final String MODULE_EXPORTED_OPAQUE_TYPE_INTERNAL_DEFINITION_HAS_WRONG_TYPE_PARAMS =
      "Module Exported Opaque Type's Internal Definition Uses Incorrect Type Params: Exported opaque type's internal " +
      "definition should use the type params declared in the .claro_module_api file.\n" +
      "\tThe exported opaque type definition:\n" +
      "\t\topaque newtype %s%s%s\n" +
      "\tFound:\n" +
      "\t\tnewtype %s%s : %s";
  private static final String MODULE_EXPORTED_OPAQUE_TYPE_INTERNAL_DEFINITION_DOES_NOT_MATCH_DECLARED_MUTABILITY =
      "Module Exported Opaque Type's Internal Definition Does Not Match Declared Mutability: Exported opaque type's " +
      "internal definition should use the type params declared in the .claro_module_api file.\n" +
      "\tThe declared mutability of the exported opaque type definition:\n" +
      "\t\topaque newtype %s%s%s\n" +
      "\tDoes not match the mutability of the internal definition:\n" +
      "\t\tnewtype %s%s : %s";
  private static final String MODULE_API_REFERENCES_TYPE_FROM_TRANSITIVE_DEP_MODULE_NOT_EXPLICITLY_EXPORTED =
      "Module API References Type Declared in non-Exported Dep Module: In order for a .claro_module_api to reference a" +
      " type defined in a dep Module, that dep Module must be explicitly included in the " +
      "`claro_module(..., exports = [...])` so that consumers of this Module are able to access the type's definition.\n" +
      "\tEither drop all references to types defined in these dep Modules, or update your module build target as follows:\n" +
      "\t\tclaro_module(\n" +
      "\t\t\t...,\n" +
      "\t\t\texports = [\n" +
      "\t\t\t\t%s\n" +
      "\t\t\t],\n" +
      "\t\t)";
  private static final String MODULE_UNNECESSARILY_EXPORTS_DEP_MODULES_THAT_ARE_NOT_REFERENCED_IN_MODULE_API =
      "Module Exports Dep Modules Unnecessarily: This Module should only list a transitive dep Module in " +
      "`claro_module(..., exports = [...])` if types from that dep Module are explicitly referenced in the " +
      ".claro_module_api file declaring this Modules public interface.\n" +
      "\tDrop the following Modules from the Module's exports list:\n" +
      "\t\t%s";
  private static final String USING_OPTIONAL_STDLIB_DEP_MODULE_WITHOUT_EXPLICIT_BUILD_DEP =
      "Missing Dependency on Referenced Optional Stdlib Module: In order for Claro to minimize executable size of " +
      "programs that do not actually use the entire stdlib, explicit deps on optional stdlib modules are required " +
      "to be placed on modules that do actually utilize them.\n" +
      "\tEither drop all references to this optional stdlib module, or update your module build target as follows:\n" +
      "\t\tclaro_module(\n" +
      "\t\t\t...\n" +
      "\t\t\toptional_stdlib_deps = [\n" +
      "\t\t\t\t\"%s\"\n" +
      "\t\t\t],\n" +
      "\t\t)";
  private static final String ILLEGAL_CONTRACT_IMPL_OVER_TYPES_NOT_DEFINED_IN_CURRENT_COMPILATION_UNIT =
      "Illegal Contract Implementation Over Types Not Defined In Current Module: Contracts defined in a " +
      "claro_module() may only be implemented over types defined in the current module. This ensures that Claro's " +
      "incremental compilation scheme functions properly (see Rust's \"Orphan Rules\": https://smallcultfollowing.com/babysteps/blog/2015/01/14/little-orphan-impls/).\n" +
      "\tThe following Contract Implementation:\n" +
      "\t\t%s\n" +
      "\tAttempts to implement the contract over the following external types:\n" +
      "\t- %s";
  private static final String MODULE_EXPORTED_CONTRACT_IMPL_NOT_DEFINED_IN_MODULE_IMPL_FILES =
      "Module Exported Contract Implementation Not Defined In Given Module Implementation Files: The given Module API definition " +
      "expected the following exported contract implementation to be defined:\n" +
      "\t%s";
  private static final String MODULE_EXPORTED_STATIC_VALUE_PROVIDER_NOT_DEFINED_IN_MODULE_IMPL_FILES =
      "Module Exported Static Value Provider Not Defined In Given Module Implementation Files: The given Module API definition " +
      "exports:\n" +
      "\t\tstatic %s: %s;\n" +
      "\tExpected the following provider implementation to be defined in the module's implementation:\n" +
      "\t\tprovider static_%s() -> %s {\n" +
      "\t\t\t...\n" +
      "\t\t}";
  private static final String MODULE_EXPORTED_STATIC_VALUE_PROVIDER_NAME_BOUND_TO_INCORRECT_IMPLEMENTATION_TYPE =
      "Module Exported Static Value Provider Name Bound To Incorrect Implementation Type: The given Module API definition " +
      "exports:\n" +
      "\t\tstatic %s: %s;\n" +
      "\tExpected the following provider implementation to be defined in the module's implementation:\n" +
      "\t\tprovider static_%s() -> %s {\n" +
      "\t\t\t...\n" +
      "\t\t}\n" +
      "\tFound:\n" +
      "\t\t%sprovider static_%s() -> %s {\n" +
      "\t\t\t...\n" +
      "\t\t}";
  private static final String ILLEGAL_ASSIGNMENT_ATTEMPT_ON_STATIC_VALUE =
      "Illegal Assignment to Static Value: Static values are deeply-immutable and may not be reassigned. These " +
      "restrictions ensure that Claro is able to statically eliminate the possibility of data races over static " +
      "values in concurrent contexts.";
  private static final String ILLEGAL_MUTABLE_STATIC_VALUE_DECLARATION =
      "Illegal Mutable Static Value: Static values are deeply-immutable and may not be reassigned. These " +
      "restrictions ensure that Claro is able to statically eliminate the possibility of data races over static " +
      "values in concurrent contexts.";
  private static final String ILLEGAL_FLAG_TYPE_DECLARATION =
      "Illegal Flag Type Declaration: Flags must be of one of the following supported types:\n" +
      "\t- %s";
  private static final String ILLEGAL_DUPLICATE_FLAG_DEFS_FOUND =
      "Illegal Duplicate Flag Definitions: Flags names must be globally unique so that they can be specified on the " +
      "command line.\n" +
      "\tFound the following flags duplicated in the following modules:\n" +
      "%s";
  private static final String ILLEGAL_USE_OF_OPAQUE_USER_DEFINED_TYPE_DEFAULT_CONSTRUCTOR =
      "Illegal Attempt to Initialize Opaque Type: An opaque type may only be instantiated by its defining module. In " +
      "order to get an instance of `%s`, reference the defining module to find an exported procedure or static value " +
      "of the desired type.";

  public ClaroTypeException(String message) {
    super(message);
  }

  public ClaroTypeException(Type actualType, Type expectedType) {
    super(String.format(INVALID_TYPE_ERROR_MESSAGE_FMT_STR, actualType, expectedType));
  }

  public ClaroTypeException(BaseType actualBaseType, Type expectedType) {
    super(String.format(INVALID_TYPE_ERROR_MESSAGE_FMT_STR, actualBaseType, expectedType));
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

  public static ClaroTypeException forUndecidedTypeLeakEmptyMapInitialization() {
    return new ClaroTypeException(MISSING_TYPE_DECLARATION_FOR_EMPTY_MAP_INITIALIZATION);
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

  public static ClaroTypeException forInvalidCast(Type assertedType, ImmutableList<Type> possibleTypes) {
    return new ClaroTypeException(
        String.format(
            IMPOSSIBLE_CAST_ERROR_MESSAGE_FMT_STR,
            assertedType,
            ImmutableSet.copyOf(possibleTypes)
                .stream()
                .map(Type::toString)
                .collect(Collectors.joining("\n\t\t\t- ", "\t\t\t- ", ""))
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
                .map(key -> String.format("%s:%s", key.getName(), key.getType()))
                .collect(Collectors.joining(", ", "[", "]"))
        )
    );
  }

  public static ClaroTypeException forRebindingKeyFromOuterUsingBlock(Set<Key> reboundKeys) {
    return new ClaroTypeException(
        String.format(
            REBINDING_KEYS_FROM_OUTER_USING_BLOCK,
            reboundKeys.stream()
                .map(key -> String.format("%s:%s", key.getName(), key.getType()))
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
                .map(key -> String.format("%s:%s", key.getName(), key.getType()))
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

  public static ClaroTypeException forDuplicateContractImplementation(String currentImplementationTypeString) {
    return new ClaroTypeException(String.format(DUPLICATE_CONTRACT_IMPLEMENTATION, currentImplementationTypeString));
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

  public static ClaroTypeException forContractProcedureCallOverUnimplementedConcreteTypes(
      String contractImplTypeString) {
    return new ClaroTypeException(
        String.format(
            CONTRACT_PROCEDURE_CALL_OVER_UNIMPLEMENTED_CONCRETE_TYPES,
            contractImplTypeString
        )
    );
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

  public static ClaroTypeException forTupleIndexOutOfBounds(Type collectionExprType, int tupleActualSize, int literalIndex) {
    return new ClaroTypeException(
        String.format(
            TUPLE_SUBSCRIPT_INDEX_OUT_OF_BOUNDS,
            collectionExprType,
            literalIndex,
            tupleActualSize
        ));
  }

  public static Exception forTupleIndexNonLiteralForAssignment() {
    return new ClaroTypeException(TUPLE_SUBSCRIPT_ASSIGNMENT_FOR_NON_COMPILE_TIME_CONSTANT_INDEX);
  }

  public static Exception forTupleHasUnexpectedSize(int expectedSize, int actualSize, Type expectedType) {
    return new ClaroTypeException(
        String.format(
            TUPLE_HAS_UNEXPECTED_SIZE,
            actualSize,
            expectedSize,
            expectedType
        )
    );
  }

  public static Exception forImpossibleRecursiveAliasTypeDefinition(String alias) {
    return new ClaroTypeException(String.format(IMPOSSIBLE_RECURSIVE_ALIAS_TYPE_DEFINITION, alias));
  }

  public static Exception forAmbiguousLambdaFirstClassGenericArg(
      int argNumber, String inferredPartialType) {
    return new ClaroTypeException(
        String.format(
            AMBIGUOUS_LAMBDA_FIRST_CLASS_GENERIC_ARG,
            argNumber,
            argNumber,
            inferredPartialType,
            argNumber
        )
    );
  }

  public static ClaroTypeException forInvalidLambdaExprCast(Type expectedExprType) {
    return new ClaroTypeException(
        String.format(
            INVALID_LAMBDA_CAST,
            expectedExprType
        )
    );
  }

  public static ClaroTypeException forContractProcedureCallWithoutRequiredContextualOutputTypeAssertion(
      String contractName, ImmutableList<String> typeParamNames, String procedureName, Type outputType) {
    return new ClaroTypeException(
        String.format(
            AMBIGUOUS_CONTRACT_PROCEDURE_CALL_MISSING_REQUIRED_CONTEXTUAL_OUTPUT_TYPE_ASSERTION,
            contractName,
            Joiner.on(", ").join(typeParamNames),
            procedureName,
            outputType
        )
    );
  }

  public static ClaroTypeException forGenericProviderCallWithoutRequiredContextualOutputTypeAssertion(
      Type providerType, String name, Type outputType) {
    return new ClaroTypeException(
        String.format(
            AMBIGUOUS_GENERIC_PROVIDER_CALL_MISSING_REQUIRED_CONTEXTUAL_OUTPUT_TYPE_ASSERTION,
            providerType,
            name,
            outputType
        )
    );
  }

  public static ClaroTypeException forIllegalOneofTypeDeclarationWithDuplicatedTypes(
      ImmutableList<Type> variants, ImmutableSet<Type> variantTypesSet) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_ONEOF_TYPE_DECL_W_DUPLICATED_TYPES,
            variants.stream().map(Type::toString).collect(Collectors.joining(", ")),
            String.format(
                variantTypesSet.size() > 1 ? "oneof<%s>" : "%s",
                variantTypesSet.stream().map(Type::toString).collect(Collectors.joining(", "))
            )
        )
    );
  }

  public static ClaroTypeException forIllegalInstanceofCheckAgainstOneofType(Type checkedType) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_INSTANCEOF_CHECK_AGAINST_ONEOF_TYPE,
            checkedType
        )
    );
  }

  public static ClaroTypeException forIllegalInstanceofCheckOverNonOneofExpr(Type validatedOneofExprType) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_INSTANCEOF_CHECK_OVER_NON_ONEOF_EXPR,
            validatedOneofExprType
        )
    );
  }

  public static ClaroTypeException forInvalidBooleanExprImplyingASingleValueIsMoreThanOneType(
      String identifier, Type type1, Type type2) {
    return new ClaroTypeException(
        String.format(
            INVALID_BOOLEAN_EXPR_IMPLYING_A_SINGLE_VALUE_IS_MORE_THAN_ONE_TYPE,
            identifier,
            type1,
            type2
        )
    );
  }

  public static ClaroTypeException forContractProcedureDynamicDispatchCallOverUnsupportedContractTypeParams(
      String contractImplTypeString,
      List<String> requiredContractImplsForDynamicDispatchSupport,
      Set<String> actualImpls) {
    return new ClaroTypeException(
        String.format(
            CONTRACT_PROCEDURE_DYNAMIC_DISPATCH_CALL_OVER_UNSUPPORTED_CONTRACT_TYPE_PARAMS,
            String.format(
                CONTRACT_PROCEDURE_CALL_OVER_UNIMPLEMENTED_CONCRETE_TYPES,
                contractImplTypeString
            ),
            requiredContractImplsForDynamicDispatchSupport.stream()
                .map(requiredImpl -> {
                  if (actualImpls.contains(requiredImpl)) {
                    return requiredImpl;
                  }
                  return String.format("%s\t\t(NOT IMPLEMENTED!)", requiredImpl);
                })
                .collect(Collectors.joining("\n\t\t- "))
        )
    );
  }

  public static ClaroTypeException forContractImplementationViolatingImpliedTypesConstraint(
      String contractName,
      ImmutableList<String> unconstrainedContractTypeParamNames,
      ImmutableSet<String> impliedTypeParamNames,
      String contractTypeString,
      String existingContractTypeString) {
    String unconstrainedTypeParams = String.join(", ", unconstrainedContractTypeParamNames);
    return new ClaroTypeException(
        String.format(
            CONTRACT_IMPLEMENTATION_VIOLATING_IMPLIED_TYPES_CONSTRAINT,
            contractName,
            unconstrainedTypeParams,
            String.join(", ", impliedTypeParamNames.asList()),
            contractName,
            unconstrainedTypeParams,
            contractTypeString,
            existingContractTypeString
        )
    );
  }

  public static ClaroTypeException forIllegalMutableTypeAnnotationOnInherentlyImmutableType(
      Type inherentlyImmutableType) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_MUTABLE_TYPE_ANNOTATION_ON_INHERENTLY_IMMUTABLE_TYPE,
            inherentlyImmutableType
        )
    );
  }

  public static ClaroTypeException forIllegalMutationAttemptOnImmutableValue(Type listExprType, Type toMutableVariant) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_MUTATION_ATTEMPT_ON_IMMUTABLE_VALUE,
            listExprType,
            toMutableVariant
        )
    );
  }

  public static ClaroTypeException forInvalidUnwrapOfBuiltinType(Type validatedExprType) {
    return new ClaroTypeException(
        String.format(
            INVALID_UNWRAP_OF_BUILTIN_TYPE,
            validatedExprType
        )
    );
  }

  public static ClaroTypeException forInvalidUnwrapOfGenericType(Type validatedExprType) {
    return new ClaroTypeException(
        String.format(
            INVALID_UNWRAP_OF_GENERIC_TYPE,
            validatedExprType
        )
    );
  }

  public static ClaroTypeException forIllegalInitializersBlockReferencingUndeclaredInitializedType(
      String initializedTypeName, boolean initializers) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_INITIALIZERS_BLOCK_REFERENCING_UNDECLARED_INITIALIZED_TYPE,
            initializers ? "Initializers" : "Unwrappers",
            initializedTypeName
        )
    );
  }

  public static ClaroTypeException forIllegalInitializersBlockReferencingNonUserDefinedType(
      String identifier, Type type, boolean initializers) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_INITIALIZERS_BLOCK_REFERENCING_NON_USER_DEFINED_TYPE,
            initializers ? "Initializers" : "Unwrappers",
            identifier,
            type
        )
    );
  }

  public static ClaroTypeException forIllegalUseOfUserDefinedTypeDefaultConstructorOutsideOfInitializerProcedures(
      Type userDefinedType, Collection<String> initializerProcedureTypes) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_USE_OF_USER_DEFINED_TYPE_DEFAULT_CONSTRUCTOR_OUTSIDE_OF_INITIALIZER_PROCEDURES,
            userDefinedType,
            initializerProcedureTypes.stream()
                .collect(Collectors.joining("\n\t\t\t- ", "\t\t\t- ", ""))
        )
    );
  }

  public static ClaroTypeException forIllegalUseOfUserDefinedTypeDefaultUnwrapperOutsideOfUnwrapperProcedures(
      Type userDefinedType, Collection<String> unwrapperProcedureTypes) {
    if (unwrapperProcedureTypes == null) {
      return new ClaroTypeException(ILLEGAL_USE_OF_OPAQUE_TYPE_UNWRAPPER);
    }
    return new ClaroTypeException(
        String.format(
            ILLEGAL_USE_OF_USER_DEFINED_TYPE_DEFAULT_UNWRAPPER_OUTSIDE_OF_UNWRAPPER_PROCEDURES,
            userDefinedType,
            unwrapperProcedureTypes.stream()
                .collect(Collectors.joining("\n\t\t\t- ", "\t\t\t- ", ""))
        )
    );
  }

  public static ClaroTypeException forIllegalImplicitUnwrapOfOpaqueUserDefinedTypeInMatchPattern(
      Type userDefinedType, String userDefinedTypeName) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_IMPLICIT_UNWRAP_OF_OPAQUE_USER_DEFINED_TYPE_IN_MATCH_PATTERN,
            userDefinedType,
            userDefinedTypeName,
            userDefinedTypeName,
            userDefinedTypeName,
            userDefinedTypeName
        )
    );
  }

  public static ClaroTypeException forIllegalAutomaticErrorPropagation(Type validatedExprType) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_AUTOMATIC_ERROR_PROPAGATION,
            validatedExprType
        )
    );
  }

  public static ClaroTypeException forIllegalAutomaticErrorPropagationOutsideOfProcedureBody() {
    return new ClaroTypeException(ILLEGAL_AUTOMATIC_ERROR_PROPAGATION_OUTSIDE_OF_PROCEDURE_BODY);
  }

  public static ClaroTypeException forIllegalAutomaticErrorPropagationForUnsupportedReturnType(Type validatedOneofType, Type activeProcedureReturnType) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_AUTOMATIC_ERROR_PROPAGATION_FOR_UNSUPPORTED_RETURN_TYPE,
            validatedOneofType,
            activeProcedureReturnType
        )
    );
  }

  public static ClaroTypeException forInvalidStructFieldAccessForNonExistentField(Type validatedStructType, String fieldName) {
    return new ClaroTypeException(
        String.format(
            INVALID_STRUCT_FIELD_ACCESS_FOR_NON_EXISTENT_FIELD,
            fieldName,
            validatedStructType
        )
    );
  }

  public static ClaroTypeException forIllegalUseOfMutableTypeAsGraphProcedureArg(Type argType, Optional<? extends Type> immutableVariantArgType) {
    if (immutableVariantArgType.isPresent()) {
      return new ClaroTypeException(
          String.format(
              ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_PROCEDURE_ARG,
              argType,
              immutableVariantArgType.get()
          )
      );
    } else {
      return new ClaroTypeException(
          String.format(
              ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_PROCEDURE_ARG_W_NO_SUGGESTED_DEEPLY_IMMUTABLE_TYPE,
              argType
          )
      );
    }
  }

  public static ClaroTypeException forIllegalUseOfMutableTypeAsGraphProcedureInjectedValue(Type argType, Optional<? extends Type> immutableVariantArgType) {
    if (immutableVariantArgType.isPresent()) {
      return new ClaroTypeException(
          String.format(
              ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_PROCEDURE_INJECTED_VALUE,
              argType,
              immutableVariantArgType.get()
          )
      );
    } else {
      return new ClaroTypeException(
          String.format(
              ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_PROCEDURE_INJECTED_VALUE_W_NO_SUGGESTED_DEEPLY_IMMUTABLE_TYPE,
              argType
          )
      );
    }
  }

  public static ClaroTypeException forIllegalUseOfMutableTypeAsGraphNodeResultType(Type actualNodeType, String nodeName, Optional<? extends Type> immutableVariantType) {
    if (immutableVariantType.isPresent()) {
      return new ClaroTypeException(
          String.format(
              ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_NODE_RESULT_TYPE,
              nodeName,
              actualNodeType,
              immutableVariantType.get()
          )
      );
    } else {
      return new ClaroTypeException(
          String.format(
              ILLEGAL_USE_OF_MUTABLE_TYPE_AS_GRAPH_NODE_RESULT_TYPE_W_NO_SUGGESTED_DEEPLY_IMMUTABLE_TYPE,
              nodeName,
              actualNodeType
          )
      );
    }
  }

  // TODO(steving) This error definitely needs to be improved on to actually point to the specific call that's
  //  triggering this issue.
  public static ClaroTypeException forIllegalTransitiveUseOfMutableTypeAsGraphProcedureInjectedValue(
      String procedureName, ImmutableMap<String, Type> mutableTransitiveInjectedKeys) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_TRANSITIVE_USE_OF_MUTABLE_TYPE_AS_GRAPH_PROCEDURE_INJECTED_VALUE,
            procedureName,
            mutableTransitiveInjectedKeys.entrySet().stream()
                .map(e -> String.format("\n\t\t\t- %s : %s", e.getKey(), e.getValue()))
                .collect(Collectors.joining())
        )
    );
  }


  public static ClaroTypeException forIllegalMutationOfLambdaCapturedVariable() {
    return new ClaroTypeException(ILLEGAL_MUTATION_OF_LAMBDA_CAPTURED_VARIABLE);
  }

  public static ClaroTypeException forIllegalLambdaCaptureOfMutableType(Type referencedIdentifierType) {
    return new ClaroTypeException(
        String.format(ILLEGAL_LAMBDA_CAPTURE_OF_MUTABLE_TYPE, referencedIdentifierType));
  }

  public static ClaroTypeException forIllegalUseOfBreakStmtOutsideLoopingConstruct() {
    return new ClaroTypeException(ILLEGAL_USE_OF_BREAK_STMT_OUTSIDE_LOOPING_CONSTRUCT);
  }

  public static ClaroTypeException forInvalidHttpEndpointPathVariable() {
    return new ClaroTypeException(INVALID_HTTP_ENDPOINT_PATH_VARIABLE);
  }

  public static ClaroTypeException forIllegalHttpClientTypeWithNonHttpServiceParameterizedType(Type type) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_HTTP_CLIENT_TYPE_WITH_NON_HTTP_SERVICE_PARAM_TYPE,
            type
        )
    );
  }

  public static ClaroTypeException forIllegalHttpServerTypeWithNonHttpServiceParameterizedType(Type type) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_HTTP_SERVER_TYPE_WITH_NON_HTTP_SERVICE_PARAM_TYPE,
            type
        )
    );
  }

  public static ClaroTypeException forIllegalParseFromJSONForUnsupportedType(Type assertedParsedResultType) {
    return new ClaroTypeException(
        String.format(ILLEGAL_PARSE_FROM_JSON_FOR_UNSUPPORTED_TARGET_TYPE, assertedParsedResultType));
  }

  public static ClaroTypeException forIllegalParseFromJSONForUnsupportedOneofType(Type type, Type assertedParsedResultType) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_PARSE_FROM_JSON_FOR_UNSUPPORTED_TARGET_ONEOF_TYPE,
            assertedParsedResultType,
            type
        )
    );
  }

  public static ClaroTypeException forIllegalParseFromJSONWithNoTargetTypeAssertion() {
    return new ClaroTypeException(ILLEGAL_PARSE_FROM_JSON_WITH_NO_TARGET_TYPE_ASSERTION);
  }

  public static ClaroTypeException forInvalidEndpointHandlersBlockForHttpServiceUndefined() {
    return new ClaroTypeException(INVALID_ENDPOINT_HANDLERS_BLOCK_FOR_HTTP_SERVICE_UNDEFINED);
  }

  public static ClaroTypeException forInvalidEndpointHandlersBlockForNonHttpService(String serviceName, Type referencedServiceType) {
    return new ClaroTypeException(
        String.format(
            INVALID_ENDPOINT_HANDLERS_BLOCK_FOR_NON_HTTP_SERVICE,
            serviceName,
            referencedServiceType
        )
    );
  }

  public static ClaroTypeException forInvalidEndpointHandlersNotSatisfyingRequiredSignatureForHandledHttpService(
      String identifier, HashMap<String, ImmutableList<Type>> invalidEndpointSignatures) {
    return new ClaroTypeException(
        String.format(
            INVALID_ENDPOINT_HANDLERS_NOT_SATISFYING_REQUIRED_SIGNATURE_FOR_HANDLED_HTTP_SERVICE,
            identifier,
            invalidEndpointSignatures.entrySet().stream()
                .map(e -> {
                  if (e.getValue() == null) {
                    return String.format(
                        "%s: <NO SUCH ENDPOINT>",
                        e.getKey()
                    );
                  } else {
                    return String.format(
                        "%s:\n\t\t\tFound:\n\t\t\t\t%s\n\t\t\tExpected:\n\t\t\t\t%s",
                        e.getKey(),
                        e.getValue().get(0),
                        e.getValue().get(1)
                    );
                  }
                })
                .collect(Collectors.joining("\n\t- ", "\t- ", ""))
        )
    );
  }

  public static ClaroTypeException forInvalidHttpServerGenerationRequestedWithNoHttpServiceEndpointHandlersDefined(
      String httpServiceName, Map<String, Integer> endpointHandlerArgCounts) {
    return new ClaroTypeException(
        String.format(
            INVALID_HTTP_SERVER_GENERATION_REQUESTED_WITH_NO_HTTP_SERVICE_ENDPOINT_HANDLERS_DEFINED,
            httpServiceName,
            Joiner.on("\n")
                .join(
                    endpointHandlerArgCounts.entrySet().stream()
                        .map(e -> String.format(
                            "\t\t\tgraph %s %s(%s) -> future<HttpResponse> {\n" +
                            "\t\t\t\t...\n" +
                            "\t\t\t}",
                            e.getValue() == 0 ? "provider" : "function",
                            e.getKey(),
                            IntStream.range(0, e.getValue())
                                .mapToObj(i -> String.format("pathArg%s: string", i))
                                .collect(Collectors.joining(", "))
                        ))
                        .collect(ImmutableList.toImmutableList())
                        .reverse()
                )
        )
    );
  }

  public static ClaroTypeException forUnexpectedArgsPassedToProviderCall(Type providerFnType, int numArgs) {
    return new ClaroTypeException(
        String.format(
            UNEXPECTED_ARGS_PASSED_TO_PROVIDER_CALL,
            numArgs,
            providerFnType
        )
    );
  }

  public static ClaroTypeException forIllegalConsumerCallUsedAsExpression(Type consumerFnType) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_CONSUMER_CALL_USED_AS_EXPRESSION,
            consumerFnType
        )
    );
  }

  public static ClaroTypeException forFunctionCallWithWrongNumberOfArgs(int argsCount, Type functionType, int actualArgsGiven) {
    return new ClaroTypeException(
        String.format(
            FUNCTION_CALLED_WITH_WRONG_NUMBER_OF_ARGS,
            functionType,
            actualArgsGiven,
            argsCount
        )
    );
  }

  public static ClaroTypeException forFunctionCallUsedAsStmt(Type functionType) {
    return new ClaroTypeException(
        String.format(
            FUNCTION_CALL_RESULT_IGNORED,
            functionType
        )
    );
  }

  public static ClaroTypeException forMatchOverUnsupportedBaseValueTypes(
      Type matchedExprType, ImmutableSet<Type> unsupportedBaseValueTypes) {
    return new ClaroTypeException(
        String.format(
            MATCH_OVER_UNSUPPORTED_BASE_VALUE_TYPES,
            matchedExprType,
            unsupportedBaseValueTypes.stream().map(Type::toString).collect(Collectors.joining("\n\t\t\t- "))
        )
    );
  }

  public static ClaroTypeException forMatchContainsDuplicateDefaultCases() {
    return new ClaroTypeException(MATCH_CONTAINS_DUPLICATE_DEFAULT_CASES);
  }

  public static ClaroTypeException forMatchIsNotExhaustiveOverAllPossibleValues(Type matchedExprType, String counterExample) {
    return new ClaroTypeException(
        String.format(
            NON_EXHAUSTIVE_MATCH,
            matchedExprType,
            counterExample
        )
    );
  }

  public static ClaroTypeException forDuplicateMatchCase() {
    return new ClaroTypeException(DUPLICATE_MATCH_CASE);
  }

  public static ClaroTypeException forUselessMatchStatementOverSingleDefaultCase() {
    return new ClaroTypeException(USELESS_MATCH_OVER_SINGLE_DEFAULT_CASE);
  }

  public static ClaroTypeException forUselessMatchCaseTypeLiteralPatternForNonOneofMatchedVal(Type matchedExprType) {
    return new ClaroTypeException(
        String.format(
            USELESS_MATCH_CASE_TYPE_LITERAL_PATTERN_FOR_NON_ONEOF_MATCHED_VAL,
            matchedExprType
        )
    );
  }

  public static ClaroTypeException forUselessDefaultCaseInAlreadyExhaustiveMatch() {
    return new ClaroTypeException(USELESS_DEFAULT_CASE_IN_ALREADY_EXHAUSTIVE_MATCH);
  }

  public static ClaroTypeException forIllegalProcedureCallInMatchCasePattern() {
    return new ClaroTypeException(ILLEGAL_PROCEDURE_CALL_IN_MATCH_CASE_PATTERN);
  }

  public static ClaroTypeException forIllegalShadowingOfDeclaredVariableForWildcardBinding() {
    return new ClaroTypeException(ILLEGAL_SHADOWING_OF_DECLARED_VARIABLE_FOR_WILDCARD_BINDING);
  }

  public static ClaroTypeException forInvalidPatternMatchingWrongType(Type expectedExprType, Type actualType) {
    return new ClaroTypeException(
        String.format(
            INVALID_PATTERN_MATCHING_WRONG_TYPE,
            expectedExprType,
            actualType
        )
    );
  }

  public static ClaroTypeException forModuleExportedProcedureNotDefinedInModuleImplFiles(
      String procedureName, Type procedureSignatureType) {
    return new ClaroTypeException(
        String.format(
            MODULE_EXPORTED_PROCEDURE_NOT_DEFINED_IN_MODULE_IMPL_FILES,
            procedureName,
            procedureSignatureType
        )
    );
  }

  public static ClaroTypeException forModuleExportedProcedureNameBoundToIncorrectImplementationType(
      String exportedProcedureName, Type expectedExportedProcedureType, Type actualIdentifierType) {
    return new ClaroTypeException(
        String.format(
            MODULE_EXPORTED_PROCEDURE_NAME_BOUND_TO_INCORRECT_IMPLEMENTATION_TYPE,
            exportedProcedureName,
            actualIdentifierType,
            expectedExportedProcedureType
        )
    );
  }

  public static ClaroTypeException forIllegalExportedInitializersBlockReferencingTypeFromDepModule(boolean initializers) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_EXPORTED_INITIALIZERS_BLOCK_REFERENCING_TYPE_FROM_DEP_MODULE,
            initializers ? "Initializers" : "Unwrappers",
            initializers ? "initializers" : "unwrappers"
        ));
  }

  public static ClaroTypeException forModuleAPIReferencesTypeFromTransitiveDepModuleNotExplicitlyExplicitlyExported(
      Set<String> nonExportedDeps) {
    return new ClaroTypeException(
        String.format(
            MODULE_API_REFERENCES_TYPE_FROM_TRANSITIVE_DEP_MODULE_NOT_EXPLICITLY_EXPORTED,
            nonExportedDeps.stream().map(d -> String.format("\"%s\"", d)).collect(Collectors.joining(",\n\t\t\t\t"))
        )
    );
  }

  public static ClaroTypeException forUnnecessaryExportedDepModule(Set<String> unnecessarilyExportedDeps) {
    return new ClaroTypeException(
        String.format(
            MODULE_UNNECESSARILY_EXPORTS_DEP_MODULES_THAT_ARE_NOT_REFERENCED_IN_MODULE_API,
            Joiner.on("\n\t\t- ").join(unnecessarilyExportedDeps)
        )
    );
  }

  public static ClaroTypeException forUsingOptionalStdlibDepModuleWithoutExplicitBuildDep(String optionalStdlibDepModuleName) {
    return new ClaroTypeException(
        String.format(
            USING_OPTIONAL_STDLIB_DEP_MODULE_WITHOUT_EXPLICIT_BUILD_DEP,
            optionalStdlibDepModuleName
        )
    );
  }

  public static ClaroTypeException forIllegalContractImplOverTypesNotDefinedInCurrentCompilationUnit(
      String contractTypeString, ImmutableList<Type> implTypeParamsDefinedOutsideCurrentCompilationUnit) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_CONTRACT_IMPL_OVER_TYPES_NOT_DEFINED_IN_CURRENT_COMPILATION_UNIT,
            contractTypeString,
            Joiner.on("\n\t- ").join(implTypeParamsDefinedOutsideCurrentCompilationUnit)
        )
    );
  }

  public static ClaroTypeException forModuleExportedContractImplementationNotDefinedInModuleImplFiles(
      String contractImplCanonicalName) {
    return new ClaroTypeException(
        String.format(MODULE_EXPORTED_CONTRACT_IMPL_NOT_DEFINED_IN_MODULE_IMPL_FILES, contractImplCanonicalName));
  }

  public static ClaroTypeException forModuleExportedStaticValueProviderNotDefinedInModuleImplFiles(
      String staticValueIdentifier, Type staticValueType) {
    return new ClaroTypeException(
        String.format(
            MODULE_EXPORTED_STATIC_VALUE_PROVIDER_NOT_DEFINED_IN_MODULE_IMPL_FILES,
            staticValueIdentifier,
            staticValueType,
            staticValueIdentifier,
            staticValueType
        ));
  }

  public static ClaroTypeException forModuleExportedStaticValueProviderNameBoundToIncorrectImplementationType(
      String staticValueIdentifier, Type staticValueType, Type actualType, boolean isBlocking) {
    return new ClaroTypeException(
        String.format(
            MODULE_EXPORTED_STATIC_VALUE_PROVIDER_NAME_BOUND_TO_INCORRECT_IMPLEMENTATION_TYPE,
            staticValueIdentifier,
            staticValueType,
            staticValueIdentifier,
            staticValueType,
            isBlocking ? "blocking " : "",
            staticValueIdentifier,
            actualType
        ));
  }

  public static ClaroTypeException forIllegalAssignmentAttemptOnStaticValue() {
    return new ClaroTypeException(ILLEGAL_ASSIGNMENT_ATTEMPT_ON_STATIC_VALUE);
  }

  public static ClaroTypeException forIllegalMutableStaticValueDeclaration() {
    return new ClaroTypeException(ILLEGAL_MUTABLE_STATIC_VALUE_DECLARATION);
  }

  public static ClaroTypeException forIllegalFlagTypeDeclaration(ImmutableSet<Type> supportedFlagTypes) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_FLAG_TYPE_DECLARATION,
            Joiner.on("\n\t- ").join(supportedFlagTypes)
        )
    );
  }

  public static ClaroTypeException forIllegalDuplicateFlagDefsFound(
      Map<String, List<String>> duplicatedFlagsToUniqueModuleNames) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_DUPLICATE_FLAG_DEFS_FOUND,
            duplicatedFlagsToUniqueModuleNames.entrySet().stream()
                .map(e -> {
                  StringBuilder res = new StringBuilder("\t\t").append(e.getKey()).append(":\n");
                  for (String definingModule : e.getValue()) {
                    res.append("\t\t\t- ").append(definingModule).append("\n");
                  }
                  return res.toString();
                })
                .collect(Collectors.joining("\n"))
        )
    );
  }

  public static ClaroTypeException forModuleExportedOpaqueTypeNotDefinedInModuleImplFiles(
      String typeName, boolean isMutable, ImmutableList<String> parameterizedTypeNames) {
    return new ClaroTypeException(
        String.format(
            MODULE_EXPORTED_OPAQUE_TYPE_NOT_DEFINED_IN_MODULE_IMPL_FILES,
            typeName,
            parameterizedTypeNames.isEmpty()
            ? ""
            : String.format("<%s>", Joiner.on(", ").join(parameterizedTypeNames)),
            isMutable ? "mut " : ""
        )
    );
  }

  public static ClaroTypeException forModuleExportedOpaqueTypeNameBoundToIncorrectImplementationType(
      String typeName, boolean isMutable, ImmutableList<String> parameterizedTypeNames) {
    String typeParams =
        parameterizedTypeNames.isEmpty()
        ? ""
        : String.format("<%s>", Joiner.on(", ").join(parameterizedTypeNames));
    return new ClaroTypeException(
        String.format(
            MODULE_EXPORTED_OPAQUE_TYPE_NAME_BOUND_TO_INCORRECT_IMPLEMENTATION_TYPE,
            typeName,
            isMutable ? "mut " : "",
            typeName,
            typeParams,
            typeName,
            typeParams,
            isMutable ? "mut " : ""
        )
    );
  }

  public static ClaroTypeException forModuleExportedOpaqueTypeInternalDefinitionHasWrongTypeParams(
      String identifier, boolean isMutable, ImmutableList<String> expectedParameterizedTypeNames, ImmutableList<String> actualParameterizedTypes, Type actualWrappedType) {
    return new ClaroTypeException(
        String.format(
            MODULE_EXPORTED_OPAQUE_TYPE_INTERNAL_DEFINITION_HAS_WRONG_TYPE_PARAMS,
            isMutable ? "mut " : "",
            identifier,
            expectedParameterizedTypeNames.isEmpty()
            ? ""
            : expectedParameterizedTypeNames.stream().collect(Collectors.joining(", ", "<", ">")),
            identifier,
            actualParameterizedTypes.isEmpty()
            ? ""
            : actualParameterizedTypes.stream().collect(Collectors.joining(", ", "<", ">")),
            actualWrappedType
        )
    );
  }

  public static ClaroTypeException forModuleExportedOpaqueTypeInternalDefinitionDoesNotMatchDeclaredMutability(
      String identifier, boolean isMutable, ImmutableList<String> parameterizedTypeNames, Type type) {
    String typeParams =
        parameterizedTypeNames.isEmpty()
        ? ""
        : parameterizedTypeNames.stream().collect(Collectors.joining(", ", "<", ">"));
    return new ClaroTypeException(
        String.format(
            MODULE_EXPORTED_OPAQUE_TYPE_INTERNAL_DEFINITION_DOES_NOT_MATCH_DECLARED_MUTABILITY,
            isMutable ? "mut " : "",
            identifier,
            typeParams,
            identifier,
            typeParams,
            type
        )
    );
  }

  public static ClaroTypeException forIllegalUseOfOpaqueUserDefinedTypeDefaultConstructor(
      Type referencedUserDefinedType) {
    return new ClaroTypeException(
        String.format(
            ILLEGAL_USE_OF_OPAQUE_USER_DEFINED_TYPE_DEFAULT_CONSTRUCTOR,
            referencedUserDefinedType
        )
    );
  }
}
