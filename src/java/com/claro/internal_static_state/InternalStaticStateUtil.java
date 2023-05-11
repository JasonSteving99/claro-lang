package com.claro.internal_static_state;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.google.common.collect.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

// TODO(steving) Eventually all static centralized state should be moved here to avoid fighting circular deps ever again.
// It's just become too unwieldy to actually have each class manage its own centralized static state given that the
// cross layer build deps become a huge pain with circular deps. So this file serves as a util with intentionally
// minimal dependencies, specifically none that go into the intermediate_representation/(expressions|statements)
// packages to avoid all circular deps.
public class InternalStaticStateUtil {
  public static ImmutableMap<String, TypeProvider> GraphProcedureDefinitionStmt_graphFunctionArgs;
  public static Optional<ImmutableMap<String, TypeProvider>>
      GraphProcedureDefinitionStmt_graphFunctionOptionalInjectedKeys;
  public static HashSet<String> GraphProcedureDefinitionStmt_usedGraphNodesNamesSet = new HashSet<>();

  // This is to be used during the parsing phase so that whenever a GraphNodeReference is legally identified the
  // referenced node will be added to this list so that this GraphNodeDefinitionStmt knows which upstream deps it needs
  // to gen code for. Since GraphNodeReferences are only valid within the scope of matching a GraphNodeDefinition this
  // is valid and safe.
  public static ImmutableSet.Builder<String> GraphNodeDefinitionStmt_upstreamGraphNodeReferencesBuilder =
      ImmutableSet.builder();
  // This set of upstream deps refers to the same node references as the above list, but membership in this set
  // indicates that the reference is implicitly indicating the user wants control over lazy evaluation of the node
  // via access to the subgraph as a provider to execute on demand instead of depending on the already computed result.
  public static ImmutableSet.Builder<String> GraphNodeDefinitionStmt_upstreamGraphNodeProviderReferencesBuilder =
      ImmutableSet.builder();

  // I need a mechanism for easily communicating to sub-nodes in the AST that they are a part of a
  // ProcedureDefinitionStmt so that during type validation, nested procedure call nodes know to update
  // the active instance's used injected keys set.
  public static Optional<Object> ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt = Optional.empty();
  public static Optional<Type> ProcedureDefinitionStmt_optionalActiveProcedureResolvedType = Optional.empty();

  // This field helps establish that we are in fact within a Pipe Chain context, which will allow the pipe chain
  // backreference sigil to be available.
  public static boolean PipeChainStmt_withinPipeChainContext;
  public static AtomicReference<Type> PipeChainStmt_backreferencedPipeChainStageType;
  public static int PipeChainStmt_backreferenceUsagesCount = 0;
  public static AtomicReference<BiFunction<ScopedHeap, Boolean, Object>>
      PipeChainStmt_backreferencedPipeChainStageCodegenFn =
      new AtomicReference<>();

  public static String ContractDefinitionStmt_currentContractName;
  public static ImmutableList<String> ContractDefinitionStmt_currentContractGenericTypeParamNames;
  public static HashSet<String> ContractDefinitionStmt_genericContractImplProceduresCanonicalNames = new HashSet<>();
  public static HashBasedTable<String, ImmutableMap<Type, Type>, /*Node.GeneratedJavaSource*/Object>
      GenericProcedureDefinitionStmt_alreadyCodegenedContractProcedureMonomorphizations = HashBasedTable.create();
  public static HashBasedTable<String, ImmutableMap<Type, Type>, String>
      GenericProcedureDefinitionStmt_monomorphizationsByGenericProcedureCanonName = HashBasedTable.create();

  public static boolean GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation = false;
  public static boolean GnericProcedureDefinitionStmt_doneWithGenericProcedureTypeValidationPhase = false;

  // We want to enable lambda exprs to still be validated against calls to contracts over generic types to ensure that
  // the enclosing Generic procedure `requires` that implementation of the contract.
  public static Optional<ArrayListMultimap/*<String, ImmutableList<Types.$GenericTypeParam>>*/>
      LambdaExpr_optionalActiveGenericProcedureDefRequiredContractNamesToGenericArgs = Optional.empty();
  public static boolean IfStmt_withinConditionTypeValidation = false;
  public static HashMultimap<String, String> InitializersBlockStmt_initializersByInitializedType
      = HashMultimap.create();
  public static HashMultimap<String, String> UnwrappersBlockStmt_unwrappersByUnwrappedType
      = HashMultimap.create();

  // We'll use these nestedComprehension* variables to track the nesting level, and the names of any identifiers that
  // get referenced from within nested comprehensions (as these will need special handling to avoid non-final var
  // references from w/in generated Java lambdas which Java forbids).
  public static int ComprehensionExpr_nestedComprehensionCollectionsCount = -1;
  public static String ComprehensionExpr_nestedComprehensionMappedItemName;
  public static HashSet<String> ComprehensionExpr_nestedComprehensionIdentifierReferences = new HashSet<>();
  public static boolean LoopingConstructs_withinLoopingConstructBody = false;
  public static final HashBasedTable<String, String, Type> HttpServiceDef_endpointProcedureSignatures =
      HashBasedTable.create();
  public static HashSet<String> HttpServiceDef_servicesWithValidEndpointHandlersDefined = Sets.newHashSet();
  public static HashBasedTable<String, String, String> HttpServiceDef_endpointPaths = HashBasedTable.create();

  // This function allows IdentifierReferenceterm to add any referenced vars so that the codegen for the outermost
  // collection can create a class that collects the referenced variables in order to workaround Java's effectively
  // final requirement within lambdas (as comprehension codegen produces lambdas for streaming map/filter).
  public static void addNestedCollectionIdentifierReference(String identifier) {
    if (!ComprehensionExpr_nestedComprehensionMappedItemName.equals(identifier)) {
      ComprehensionExpr_nestedComprehensionIdentifierReferences.add(identifier);
    }
  }
}
