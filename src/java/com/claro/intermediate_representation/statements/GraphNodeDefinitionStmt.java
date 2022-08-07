package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GraphNodeDefinitionStmt extends Stmt {

  protected final String nodeName;
  private final Expr nodeExpr;
  // If this node is a Root node then we'll know the type that it needs to return by default.
  Optional<Type> optionalExpectedNodeType = Optional.empty();
  // This will be used as the generated function's return type.
  Type actualNodeType;
  private final ImmutableList<String> upstreamGraphNodeReferences;

  // We'll validate that graph functions are acyclic by verifying that each node is only type-checked exactly once.
  private boolean alreadyValidated = false;

  public GraphNodeDefinitionStmt(String nodeName, Expr nodeExpr) {
    super(ImmutableList.of());
    this.nodeName = nodeName;
    this.nodeExpr = nodeExpr;
    this.upstreamGraphNodeReferences =
        InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeReferencesBuilder
            .build();
    InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeReferencesBuilder = new ImmutableList.Builder<>();
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

    if (optionalExpectedNodeType.isPresent()) {
      // Graph functions are all returning futures, but Claro can automatically handle nodes that are themselves
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
    } else {
      this.actualNodeType = this.nodeExpr.getValidatedExprType(scopedHeap);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource nodeBodyGeneratedJavaSource = this.nodeExpr.generateJavaSourceOutput(scopedHeap);

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
        .append(this.actualNodeType.getJavaSourceType())
        .append(" $")
        .append(this.nodeName)
        .append("_nodeImpl(")
        .append(
            this.upstreamGraphNodeReferences.stream()
                .map(dep -> {
                  Type upstreamDepType = scopedHeap.getValidatedIdentifierType(dep);
                  return String.format(
                      "%s %s, ",
                      // Claro Graph Functions automatically unwraps Future types for the user.
                      upstreamDepType.baseType().equals(BaseType.FUTURE)
                      ? upstreamDepType.parameterizedTypeArgs().get("$value").getJavaSourceType()
                      : upstreamDepType.getJavaSourceType(),
                      dep
                  );
                })
                .collect(Collectors.joining("")))
        .append(propagatedGraphFunctionArgsAndInjectedKeys)
        .append(") {\n")
        // This allows things like lambdas to work within nodes as well.
        .append(Stmt.consumeGeneratedJavaSourceStmtsBeforeCurrentStmt())
        .append("\n\t\treturn ")
        .append(nodeBodyGeneratedJavaSource.javaSourceBody())
        .append(";\n")
        .append("\t}\n");
  }


  private StringBuilder generateGraphNodeAsyncMethod(
      ScopedHeap scopedHeap,
      String optionalAlreadyScheduledFutureStr,
      StringBuilder propagatedGraphFunctionArgsAndInjectedKeys,
      StringBuilder propagatedGraphFunctionArgsAndInjectedKeysValues) {
    StringBuilder res = new StringBuilder()
        .append(
            String.format(
                // If the node's type is already a future we don't need to wrap it in the codegen.
                "\tprivate "
                + (this.actualNodeType.baseType().equals(BaseType.FUTURE) ? "%s" : "ClaroFuture<%s>")
                + " $%s_nodeAsync(%s) {\n",
                this.actualNodeType.getJavaSourceType(),
                this.nodeName,
                propagatedGraphFunctionArgsAndInjectedKeys
            ));

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
    if (upstreamGraphNodeReferences.size() > 0) {
      String calledUpstreamFutures =
          this.upstreamGraphNodeReferences.stream()
              .map(
                  node ->
                      String.format(
                          "\t\t\t\t$%s_nodeAsync(\n%s",
                          node,
                          propagatedGraphFunctionArgsAndInjectedKeysValues
                      ))
              .collect(Collectors.joining(",\n"));
      if (upstreamGraphNodeReferences.size() > 1) {
        res.append(
            String.format(
                "\t\tClaroFuture<List<Object>> depsFuture =\n\t\t\tnew ClaroFuture(%s, Futures.allAsList(\n%s));\n",
                // TODO(steving) I need to model the future's type as a [OneOf<upstream dep types>] to make this
                //  strictly typed within Claro's type system. Currently this is an ugly hack although it's fortunately
                //  not observable to any users.
                "Types.UNDECIDED",
                calledUpstreamFutures
            ));
      } else { // upstreamGraphNodeReferences.size() == 1.
        Type upstreamDepType = scopedHeap.getValidatedIdentifierType(upstreamGraphNodeReferences.get(0));
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

    res.append(
        String.format(
            // If the node's type is already a future we don't need to wrap it in the codegen.
            "\t\t"
            + (this.actualNodeType.baseType().equals(BaseType.FUTURE) ? "%s" : "ClaroFuture<%s>")
            + " nodeFuture =\n",
            this.actualNodeType.getJavaSourceType()
        ));

    if (upstreamGraphNodeReferences.size() > 0) {
      res.append(
          String.format(
              "\t\t\tnew ClaroFuture(%s, Futures." +
              (this.actualNodeType.baseType().equals(BaseType.FUTURE) ? "transformAsync" : "transform") +
              "(\n" +
              "\t\t\t\tdepsFuture,\n" +
              "\t\t\t\t" + (upstreamGraphNodeReferences.size() > 1 ? "deps" : "dep") + " ->\n" +
              "\t\t\t\t\t$%s_nodeImpl(\n",
              this.actualNodeType.getJavaSourceClaroType(),
              this.nodeName
          ))
          .append(
              IntStream.range(0, upstreamGraphNodeReferences.size())
                  .mapToObj(
                      index -> {
                        Type upstreamGraphNodeType =
                            scopedHeap.getValidatedIdentifierType(upstreamGraphNodeReferences.get(index));
                        return
                            upstreamGraphNodeReferences.size() > 1
                            ? String.format(
                                "\t\t\t\t\t\t(%s) deps.get(%s),\n ",
                                (upstreamGraphNodeType.baseType().equals(BaseType.FUTURE)
                                 ? upstreamGraphNodeType.parameterizedTypeArgs().get("$value")
                                 : upstreamGraphNodeType)
                                    .getJavaSourceType(),
                                index
                            )
                            : "\t\t\t\t\t\tdep,\n ";
                      }
                  ).collect(Collectors.joining("")))
          .append(propagatedGraphFunctionArgsAndInjectedKeysValues)
          // Always schedule the transformation to take place on the configured ExecutorService otherwise there would be a
          // chance that some heavy work would be done on the thread that called the transform (which could easily be the
          // request thread that we never want to block).
          .append(
              ",\n\t\t\t\tClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE));\n");
    } else {
      // Claro allows nodes to be defined by Exprs that are already of type future<...> so that graph functions can be
      // naturally composed. If this node expr is already a future, then we do not need to schedule any work on an
      // executor since that will have already been handled.
      if (this.actualNodeType.baseType().equals(BaseType.FUTURE)) {
        res.append(
            String.format(
                "\t\t\t$%s_nodeImpl(\n%s;\n",
                this.nodeName,
                propagatedGraphFunctionArgsAndInjectedKeysValues
            )
        );
      } else {
        res.append(
            String.format(
                "\t\t\tnew ClaroFuture(%s, ClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE\n" +
                "\t\t\t\t.submit(() -> $%s_nodeImpl(\n%s));\n",
                this.actualNodeType.getJavaSourceClaroType(),
                this.nodeName,
                propagatedGraphFunctionArgsAndInjectedKeysValues
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

    res.append("\t\treturn nodeFuture;\n")
        .append("\t}\n");

    return res;
  }

  private StringBuilder generatePropagatedFunctionArgsAndInjectedKeys(ScopedHeap scopedHeap) {
    return new StringBuilder()
        .append(
            InternalStaticStateUtil.GraphFunctionDefinitionStmt_graphFunctionArgs.entrySet().stream()
                .map(
                    nameTypeProviderEntry ->
                        String.format(
                            "%s %s",
                            nameTypeProviderEntry.getValue().resolveType(scopedHeap).getJavaSourceType(),
                            nameTypeProviderEntry.getKey()
                        ))
                .collect(Collectors.joining(", ")))
        .append(
            InternalStaticStateUtil.GraphFunctionDefinitionStmt_graphFunctionOptionalInjectedKeys
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
        .append(InternalStaticStateUtil.GraphFunctionDefinitionStmt_graphFunctionArgs.keySet().stream()
                    .map(arg -> String.format("\t\t\t\t\t\t%s", arg))
                    .collect(Collectors.joining(",\n")))
        .append(
            InternalStaticStateUtil.GraphFunctionDefinitionStmt_graphFunctionOptionalInjectedKeys
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
