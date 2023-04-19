package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.procedures.functions.FunctionCallExpr;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class GraphNodeDefinitionStmt extends Stmt {

  protected final String nodeName;
  private final Expr nodeExpr;
  // In case this is the root node of a Graph Consumer then this needs to be used for codegen instead of nodeExpr.
  private Optional<ConsumerFunctionCallStmt> optionalConsumerFunctionCall = Optional.empty();

  // If this node is a Root node then we'll know the type that it needs to return by default.
  Optional<Type> optionalExpectedNodeType = Optional.empty();
  // This will be used as the generated function's return type.
  Type actualNodeType;
  private final ImmutableSet<String> upstreamGraphNodeReferences;
  private ImmutableSet<String> upstreamGraphNodeProviderReferences;

  // We'll validate that graph functions are acyclic by verifying that each node is only type-checked exactly once.
  private boolean alreadyValidated = false;

  public GraphNodeDefinitionStmt(String nodeName, Expr nodeExpr) {
    super(ImmutableList.of());
    this.nodeName = nodeName;
    this.nodeExpr = nodeExpr;
    this.upstreamGraphNodeReferences =
        InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeReferencesBuilder.build();
    InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeReferencesBuilder = ImmutableSet.builder();
  }

  // Register this GraphNode's TypeProvider prior to performing type checking so that nodes may reference each other out
  // of declaration order.
  public void registerGraphNodeTypeProvider(ScopedHeap scopedHeap) {
    // At the current scope level, place a new identifier on the heap containing a TypeProvider as the value so that the
    // TypeProvider.Util.getTypeByName utility function can recursively find this node's type and any other transitive
    // dep node types later on at type checking time.
    scopedHeap.putIdentifierValue(
        String.format("@%s", this.nodeName),
        null, (TypeProvider) (s -> {
          try {
            // Side note: Java is weird as hell. The fact that `this` somehow refers to GraphNodeDefinitionStmt in
            // what is essentially an anonymous class definition stmt just seems... off.
            this.assertExpectedExprTypes(scopedHeap);
            return this.actualNodeType;
          } catch (ClaroTypeException e) {
            throw new RuntimeException(e);
          }
        })
    );
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // First things first, we need to assert that we're not attempting to re-validate this node more than once.
    // Multiple validations of this node indicates that there is an indication of a cycle of graph node references
    // which is illegal within a Claro graph function.
    if (alreadyValidated) {
      throw ClaroTypeException.forNodeReferenceCycleInGraphProcedure(
          ((ProcedureDefinitionStmt)
               ((Types.ProcedureType) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureResolvedType
                   .get()).getProcedureDefStmt())
              .procedureName,
          this.nodeName
      );
    }
    alreadyValidated = true;

    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(this.nodeName),
        String.format("Graph node name conflicts with an existing identifier <%s>.", this.nodeName)
    );

    // Because there's a graph traversal about to happen when validating node references, we need to preserve the
    // prior static state, to be sure not to conflict.
    ImmutableSet.Builder<String> priorUpstreamGraphNodeProviderReferencesBuilder =
        InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeProviderReferencesBuilder;
    // Clear it out to get a fresh state for this node.
    InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeProviderReferencesBuilder = ImmutableSet.builder();

    if (optionalExpectedNodeType.isPresent()) {
      if (optionalExpectedNodeType.get().baseType().equals(BaseType.UNDECIDED)) {
        // In the case of a Graph consumer, we need to represent that there's no return type and we're abusing "UNDECIDED"
        // for that signal. This isn't strictly "accurate" as it is certainly already decided that this graph consumer
        // returns nothing, but since there's not a `nothing` type in the language yet, we'll make do with what we've got.

        // I need to hack in support for actually utilizing a ConsumerFunctionCallStmt instead of what was originally parsed as
        // an Expr. So, I'll convert the parsed Expr to a ConsumerFunctionCallStmt.
        if (this.nodeExpr instanceof FunctionCallExpr) {
          // The only valid possibility is for this to actually be a consumer function call.
          FunctionCallExpr nodeExprAsFunctionCallExpr = (FunctionCallExpr) this.nodeExpr;
          this.optionalConsumerFunctionCall =
              Optional.of(new ConsumerFunctionCallStmt(
                  nodeExprAsFunctionCallExpr.name, nodeExprAsFunctionCallExpr.argExprs));
          this.optionalConsumerFunctionCall.get().assertExpectedExprTypes(scopedHeap);
          this.actualNodeType = this.optionalExpectedNodeType.get();
        } else {
          // This is simply handling the case that the parser was unable to handle. The root node of a Graph Consumer
          // *must* be a call to a consumer function because any actual expression implies a value will be returned.
          throw ClaroTypeException.forGraphConsumerRootNodeIsNotConsumerFn(
              ((ProcedureDefinitionStmt)
                   InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.get())
                  .procedureName,
              this.nodeName
          );
        }
      } else {
        // Graph functions/providers are all returning futures, but Claro can automatically handle nodes that are themselves
        // defined by Exprs of type future, so we need to check that this node either returns the wrapped or unwrapped
        // expected type.
        Type wrappedType =
            ((Types.FutureType) optionalExpectedNodeType.get()).parameterizedTypeArgs().get("$value");

        this.actualNodeType = this.nodeExpr.assertSupportedExprType(
            scopedHeap,
            ImmutableSet.of(
                optionalExpectedNodeType.get(),
                wrappedType
            )
        );
      }
    } else {
      this.actualNodeType = this.nodeExpr.getValidatedExprType(scopedHeap);
    }

    // Just in case this node definition expr contained a request for lazy provider injection, take note of that now.
    this.upstreamGraphNodeProviderReferences =
        InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeProviderReferencesBuilder.build();
    InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeProviderReferencesBuilder =
        priorUpstreamGraphNodeProviderReferencesBuilder;

    // Very last thing that's extremely important is forbidding the use of mutable data as a graph node output type.
    // This is a rigid restriction, but doing so (in concert w/ forbidding mutable graph inputs) gives a guarantee that
    // the inherently multithreaded computation represented by the graph is **DATA RACE FREE BY CONSTRUCTION**.
    if (!StructuralConcreteGenericTypeValidationUtil.isDeeplyImmutable(this.actualNodeType)) {
      this.nodeExpr.logTypeError(
          ClaroTypeException.forIllegalUseOfMutableTypeAsGraphNodeResultType(
              this.actualNodeType, this.nodeName, getDeeplyImmutableVariantType(this.actualNodeType)));
    }
  }

  private static Optional<Type> getDeeplyImmutableVariantType(Type type) {
    Type deeplyImmutableVariantType;
    if (type instanceof SupportsMutableVariant<?>) {
      deeplyImmutableVariantType = ((SupportsMutableVariant<?>) type).toDeeplyImmutableVariant();
    } else if (type instanceof Types.UserDefinedType) {
      deeplyImmutableVariantType = ((Types.UserDefinedType) type).toDeeplyImmutableVariant();
    } else { // Assume then it must be a Future<T>.
      deeplyImmutableVariantType =
          Types.FutureType.wrapping(
              getDeeplyImmutableVariantType(type.parameterizedTypeArgs().get(Types.FutureType.PARAMETERIZED_TYPE_KEY))
                  .get()); // The nested bit can't alone be equal to the whole.
    }
    if (deeplyImmutableVariantType.equals(type)) {
      return Optional.empty();
    }
    return Optional.of(deeplyImmutableVariantType);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource nodeBodyGeneratedJavaSource =
        this.actualNodeType.baseType().equals(BaseType.UNDECIDED)
        ? this.optionalConsumerFunctionCall.get().generateJavaSourceOutput(scopedHeap)
        : this.nodeExpr.generateJavaSourceOutput(scopedHeap);

    String optionalAlreadyScheduledFutureStr = String.format("$%s_optionalAlreadyScheduledFuture", this.nodeName);
    StringBuilder propagatedGraphFunctionArgsAndInjectedKeys =
        generatePropagatedFunctionArgsAndInjectedKeys(scopedHeap);
    StringBuilder propagatedGraphFunctionArgsAndInjectedKeysValues =
        generatePropagatedFunctionArgsAndInjectedKeysValues();
    return GeneratedJavaSource.create(
        new StringBuilder()
            .append(
                // Don't need a cache if this is the root node since definitely nobody depends on it.
                this.optionalExpectedNodeType.isPresent()
                ? ""
                : String.format(
                    // If the node's type is already a future we don't need to wrap it in the codegen.
                    "\tprivate Optional<"
                    + (this.actualNodeType.baseType().equals(BaseType.FUTURE) ? "%s" : "ClaroFuture<%s>")
                    + "> %s = Optional.empty();\n",
                    this.actualNodeType.getJavaSourceType(),
                    optionalAlreadyScheduledFutureStr
                ))
            .append(
                generateGraphNodeAsyncMethod(
                    scopedHeap, optionalAlreadyScheduledFutureStr, propagatedGraphFunctionArgsAndInjectedKeys, propagatedGraphFunctionArgsAndInjectedKeysValues))
            .append(
                generateGraphNodeImplMethod(
                    scopedHeap, nodeBodyGeneratedJavaSource, propagatedGraphFunctionArgsAndInjectedKeys))
            .append("\n"),
        nodeBodyGeneratedJavaSource.optionalStaticDefinitions(),
        nodeBodyGeneratedJavaSource.optionalStaticPreambleStmts()
    );
  }

  private StringBuilder generateGraphNodeImplMethod(
      ScopedHeap scopedHeap,
      GeneratedJavaSource nodeBodyGeneratedJavaSource,
      StringBuilder propagatedGraphFunctionArgsAndInjectedKeys) {
    return new StringBuilder()
        .append("\tprivate ")
        .append(this.actualNodeType.equals(Types.UNDECIDED) ? "Void" : this.actualNodeType.getJavaSourceType())
        .append(" $")
        .append(this.nodeName)
        .append("_nodeImpl(")
        .append(
            this.upstreamGraphNodeReferences.stream()
                .map(dep -> {
                  Type upstreamDepInjectedType = scopedHeap.getValidatedIdentifierType(dep);
                  boolean referencedNodeIsFuture = upstreamDepInjectedType.baseType().equals(BaseType.FUTURE);
                  if (this.upstreamGraphNodeProviderReferences.contains(dep)) {
                    // This particular reference is actually requested with lazy provider injection.
                    if (!referencedNodeIsFuture) {
                      upstreamDepInjectedType = Types.FutureType.wrapping(upstreamDepInjectedType);
                    }
                    upstreamDepInjectedType =
                        Types.ProcedureType.ProviderType.typeLiteralForReturnType(
                            upstreamDepInjectedType, /*=explicitlyAnnotatedBlocking*/false);
                  } else if (referencedNodeIsFuture) {
                    upstreamDepInjectedType = upstreamDepInjectedType.parameterizedTypeArgs().get("$value");
                  }
                  return String.format(
                      "%s %s",
                      // Claro Graph Functions automatically unwraps Future types for the user.
                      upstreamDepInjectedType.getJavaSourceType(),
                      dep
                  );
                })
                .collect(
                    Collectors.joining(
                        ", ", "", propagatedGraphFunctionArgsAndInjectedKeys.length() > 0 &&
                                  upstreamGraphNodeReferences.size() > 0 ? ", " : "")))
        .append(propagatedGraphFunctionArgsAndInjectedKeys)
        .append(") {\n")
        // This allows things like lambdas to work within nodes as well.
        .append(Stmt.consumeGeneratedJavaSourceStmtsBeforeCurrentStmt())
        .append("\n\t\t")
        .append(this.actualNodeType.equals(Types.UNDECIDED) ? "" : "return ")
        .append(nodeBodyGeneratedJavaSource.javaSourceBody())
        .append(this.actualNodeType.equals(Types.UNDECIDED) ? "\t\treturn null;\n" : ";\n")
        .append("\t}\n");
  }


  private StringBuilder generateGraphNodeAsyncMethod(
      ScopedHeap scopedHeap,
      String optionalAlreadyScheduledFutureStr,
      StringBuilder propagatedGraphFunctionArgsAndInjectedKeys,
      StringBuilder propagatedGraphFunctionArgsAndInjectedKeysValues) {
    StringBuilder res = new StringBuilder()
        .append("\tprivate ")
        .append(
            (this.actualNodeType.equals(Types.UNDECIDED)
             // If this node's type is UNDECIDED then this is a Graph Consumer and we have nothing to return.
             ? "void"
             // If the node's type is already a future we don't need to wrap it in the codegen.
             : String.format(
                 (this.actualNodeType.baseType().equals(BaseType.FUTURE)
                  ? "%s"
                  : "ClaroFuture<%s>"),
                 this.actualNodeType.getJavaSourceType()
             )
            ));
    res.append(
        String.format(
            " $%s_nodeAsync(%s) {\n", this.nodeName, propagatedGraphFunctionArgsAndInjectedKeys));

    boolean cacheNode = !this.optionalExpectedNodeType.isPresent();

    if (cacheNode) {
      res.append(String.format(
          "\t\tif (%s.isPresent()) {\n" +
          "\t\t\treturn %s.get();\n" +
          "\t\t}\n",
          optionalAlreadyScheduledFutureStr,
          optionalAlreadyScheduledFutureStr
      ));
    }

    // We have different depsFuture type based on the number of upstream deps.
    if (upstreamGraphNodeReferences.size() - upstreamGraphNodeProviderReferences.size() > 0) {
      String calledUpstreamFutures =
          this.upstreamGraphNodeReferences.stream()
              .filter(node -> !this.upstreamGraphNodeProviderReferences.contains(node))
              .map(
                  node ->
                      String.format(
                          "\t\t\t\t$%s_nodeAsync(\n%s",
                          node,
                          propagatedGraphFunctionArgsAndInjectedKeysValues
                      ))
              .collect(Collectors.joining(",\n"));
      if (upstreamGraphNodeReferences.size() - upstreamGraphNodeProviderReferences.size() > 1) {
        res.append(
            String.format(
                "\t\tClaroFuture<List<Object>> depsFuture =\n\t\t\tnew ClaroFuture(%s, Futures.allAsList(\n%s));\n",
                // TODO(steving) I need to model the future's type as a [OneOf<upstream dep types>] to make this
                //  strictly typed within Claro's type system. Currently this is an ugly hack although it's fortunately
                //  not observable to any users.
                "Types.UNDECIDED",
                calledUpstreamFutures
            ));
      } else { // size == 1.
        Type upstreamDepType =
            scopedHeap.getValidatedIdentifierType(
                upstreamGraphNodeReferences.stream()
                    .filter(node -> !upstreamGraphNodeProviderReferences.contains(node)).findFirst().get());
        res.append(
            String.format(
                "\t\t"
                + (upstreamDepType.baseType().equals(BaseType.FUTURE) ? "%s" : "ClaroFuture<%s>")
                + " depsFuture = \n%s;\n",
                upstreamDepType.getJavaSourceType(),
                calledUpstreamFutures
            ));
      }
    }

    res.append("\t\t");
    res.append(
        (this.actualNodeType.equals(Types.UNDECIDED)
         ? "ClaroFuture<Void>"
         // If the node's type is already a future we don't need to wrap it in the codegen.
         : String.format(
             (this.actualNodeType.baseType().equals(BaseType.FUTURE)
              ? "%s"
              : "ClaroFuture<%s>"),
             this.actualNodeType.getJavaSourceType()
         )
        ));
    res.append(" nodeFuture =\n");

    if (upstreamGraphNodeReferences.size() - upstreamGraphNodeProviderReferences.size() > 0) {
      res.append(
          String.format(
              "\t\t\tnew ClaroFuture(%s, Futures." +
              (this.actualNodeType.baseType().equals(BaseType.FUTURE) ? "transformAsync" : "transform") +
              "(\n" +
              "\t\t\t\tdepsFuture,\n" +
              "\t\t\t\t" +
              (upstreamGraphNodeReferences.size() - this.upstreamGraphNodeProviderReferences.size() > 1
               ? "deps" : "dep") + " ->\n" +
              "\t\t\t\t\t$%s_nodeImpl(\n",
              this.actualNodeType.getJavaSourceClaroType(),
              this.nodeName
          ));
      AtomicReference<Integer> upstreamNodeReferencesIndex = new AtomicReference<>(0);
      res.append(
              upstreamGraphNodeReferences.stream()
                  .map(
                      upstreamGraphNodeReference -> {
                        Type upstreamGraphNodeType =
                            scopedHeap.getValidatedIdentifierType(upstreamGraphNodeReference);
                        if (this.upstreamGraphNodeProviderReferences.contains(upstreamGraphNodeReference)) {
                          // If the node's type is already a future we don't need to wrap it in the codegen.
                          boolean isFuture = upstreamGraphNodeType.baseType().equals(BaseType.FUTURE);
                          String upstreamFuture =
                              String.format(
                                  isFuture ? "%s" : "ClaroFuture<%s>",
                                  upstreamGraphNodeType.getJavaSourceType()
                              );
                          return String.format(
                              "\t\t\t\tnew ClaroProviderFunction<%s>() {\n" +
                              "\t\t\t\t\tpublic %s apply() {\n" +
                              "\t\t\t\t\t\treturn $%s_nodeAsync(\n%s;\n" +
                              "\t\t\t\t\t}\n" +
                              "\t\t\t\t\tpublic Type getClaroType() {\n" +
                              "\t\t\t\t\t\treturn %s;\n" +
                              "\t\t\t\t\t}\n" +
                              "\t\t\t\t}",
                              upstreamFuture,
                              upstreamFuture,
                              upstreamGraphNodeReference,
                              propagatedGraphFunctionArgsAndInjectedKeysValues,
                              (isFuture ? upstreamGraphNodeType : Types.FutureType.wrapping(upstreamGraphNodeType))
                                  .getJavaSourceClaroType()
                          );
                        }
                        return
                            upstreamGraphNodeReferences.size() - upstreamGraphNodeProviderReferences.size() > 1
                            ? String.format(
                                "\t\t\t\t\t\t(%s) deps.get(%s)",
                                (upstreamGraphNodeType.baseType().equals(BaseType.FUTURE)
                                 ? upstreamGraphNodeType.parameterizedTypeArgs().get("$value")
                                 : upstreamGraphNodeType)
                                    .getJavaSourceType(),
                                upstreamNodeReferencesIndex.getAndUpdate(curr -> curr + 1)
                            )
                            : "\t\t\t\t\t\tdep";
                      }
                  )
                  // TODO(steving) Cleanup propagatedGraphFunctionArgsAndInjectedKeysValues which has an unnecessary trailing ')' prebuilt into it.
                  .collect(Collectors.joining(
                      ",\n", "", propagatedGraphFunctionArgsAndInjectedKeysValues.length() > 1 ? ",\n" : "")))
          .append(propagatedGraphFunctionArgsAndInjectedKeysValues)
          // Always schedule the transformation to take place on the configured ExecutorService otherwise there would be a
          // chance that some heavy work would be done on the thread that called the transform (which could easily be the
          // request thread that we never want to block).
          .append(
              ",\n\t\t\t\tClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE));\n");
    } else {
      String upstreamLazyProviderDeps = this.upstreamGraphNodeProviderReferences.stream()
          .map(
              node -> {
                Type upstreamNodeProviderReferenceType = scopedHeap.getValidatedIdentifierType(node);
                // If the node's type is already a future we don't need to wrap it in the codegen.
                boolean isFuture = upstreamNodeProviderReferenceType.baseType().equals(BaseType.FUTURE);
                String upstreamFuture =
                    String.format(
                        isFuture ? "%s" : "ClaroFuture<%s>",
                        upstreamNodeProviderReferenceType.getJavaSourceType()
                    );
                return String.format(
                    "\t\t\t\tnew ClaroProviderFunction<%s>() {\n" +
                    "\t\t\t\t\tpublic %s apply() {\n" +
                    "\t\t\t\t\t\treturn $%s_nodeAsync(\n%s;\n" +
                    "\t\t\t\t\t}\n" +
                    "\t\t\t\t\tpublic Type getClaroType() {\n" +
                    "\t\t\t\t\t\treturn %s;\n" +
                    "\t\t\t\t\t}\n" +
                    "\t\t\t\t}",
                    upstreamFuture,
                    upstreamFuture,
                    node,
                    propagatedGraphFunctionArgsAndInjectedKeysValues,
                    (isFuture
                     ? upstreamNodeProviderReferenceType
                     : Types.FutureType.wrapping(upstreamNodeProviderReferenceType))
                        .getJavaSourceClaroType()
                );
              })
          // TODO(steving) Cleanup propagatedGraphFunctionArgsAndInjectedKeysValues which has an unnecessary trailing ')' prebuilt into it.
          .collect(Collectors.joining(
              ",\n", "",
              upstreamGraphNodeProviderReferences.size() > 0 &&
              propagatedGraphFunctionArgsAndInjectedKeysValues.length() > 1
              ? ",\n"
              : ""
          ));
      // Claro allows nodes to be defined by Exprs that are already of type future<...> so that graph functions can be
      // naturally composed. If this node expr is already a future, then we do not need to schedule any work on an
      // executor since that will have already been handled.
      if (this.actualNodeType.baseType().equals(BaseType.FUTURE)) {
        res.append(
            String.format(
                "\t\t\t$%s_nodeImpl(\n%s;\n",
                this.nodeName,
                upstreamLazyProviderDeps + propagatedGraphFunctionArgsAndInjectedKeysValues
            )
        );
      } else {
        res.append(
            String.format(
                "\t\t\tnew ClaroFuture(%s, ClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE\n" +
                "\t\t\t\t.submit(() -> $%s_nodeImpl(\n%s));\n",
                this.actualNodeType.getJavaSourceClaroType(),
                this.nodeName,
                upstreamLazyProviderDeps + propagatedGraphFunctionArgsAndInjectedKeysValues
            ));
      }
    }

    if (cacheNode) {
      res.append(
          String.format(
              "\t\t%s = Optional.of(nodeFuture);\n",
              optionalAlreadyScheduledFutureStr
          ));
    }

    if (!this.actualNodeType.equals(Types.UNDECIDED)) {
      res.append("\t\treturn nodeFuture;\n");
    }
    res.append("\t}\n");

    return res;
  }

  private StringBuilder generatePropagatedFunctionArgsAndInjectedKeys(ScopedHeap scopedHeap) {
    return new StringBuilder()
        .append(
            InternalStaticStateUtil.GraphProcedureDefinitionStmt_graphFunctionArgs.entrySet().stream()
                .map(
                    nameTypeProviderEntry ->
                        String.format(
                            "%s %s",
                            nameTypeProviderEntry.getValue().resolveType(scopedHeap).getJavaSourceType(),
                            nameTypeProviderEntry.getKey()
                        ))
                .collect(Collectors.joining(", ")))
        .append(
            InternalStaticStateUtil.GraphProcedureDefinitionStmt_graphFunctionOptionalInjectedKeys
                .map(
                    injectedKeys -> injectedKeys.entrySet().stream()
                        .map(
                            nameTypeProviderEntry -> {
                              return String.format(
                                  "%s %s",
                                  nameTypeProviderEntry.getValue().resolveType(scopedHeap).getJavaSourceType(),
                                  nameTypeProviderEntry.getKey()
                              );
                            })
                        .collect(Collectors.joining(", ", ", ", "")))
                .orElse(""));
  }

  private StringBuilder generatePropagatedFunctionArgsAndInjectedKeysValues() {
    return new StringBuilder()
        .append(InternalStaticStateUtil.GraphProcedureDefinitionStmt_graphFunctionArgs.keySet().stream()
                    .map(arg -> String.format("\t\t\t\t\t\t%s", arg))
                    .collect(Collectors.joining(",\n")))
        .append(
            InternalStaticStateUtil.GraphProcedureDefinitionStmt_graphFunctionOptionalInjectedKeys
                .map(
                    injectedKeys ->
                        injectedKeys.keySet().stream()
                            .collect(Collectors.joining(", ", ", ", ")")))
                .orElse(")"));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Need to think through the interpreted implementation of all of this.
    return null;
  }
}
