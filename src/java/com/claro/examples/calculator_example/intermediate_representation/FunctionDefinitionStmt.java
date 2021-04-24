package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.function.Function;

public class FunctionDefinitionStmt extends Stmt {

  private final Types.FunctionType type;

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, Type> argTypes,
      ImmutableList<Type> outputTypes,
      StmtListNode stmtListNode,
      // TODO(steving) Support multi-return functions.
      Expr returnExpr) {
    super(ImmutableList.of(stmtListNode, returnExpr));
    Preconditions.checkArgument(
        outputTypes.size() == 1,
        "Internal Compiler Error: For now Claro only supports single-return functions."
    );
    this.type = Types.FunctionType.forArgsAndReturnTypes(functionName, argTypes, outputTypes);
  }

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, Type> argTypes,
      ImmutableList<Type> outputTypes,
      // TODO(steving) Support multi-return functions.
      Expr returnExpr) {
    super(ImmutableList.of(returnExpr));
    Preconditions.checkArgument(
        outputTypes.size() == 1,
        "Internal Compiler Error: For now Claro only supports single-return functions."
    );
    this.type = Types.FunctionType.forArgsAndReturnTypes(functionName, argTypes, outputTypes);
  }

  @Override
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(this.type.getFunctionName()),
        String.format("Unexpected redeclaration of %s %s.", this.type, this.type.getFunctionName())
    );

    // First we need to mark the function declared within the original calling scope.
    scopedHeap.observeIdentifier(this.type.getFunctionName(), this.type);

    // Now from here step through the function body. I need to mark the args as observed identifiers within this new scope.
    scopedHeap.observeNewScope(false);
    this.type.getArgTypes().forEach(
        (argName, argType) ->
        {
          scopedHeap.observeIdentifier(argName, argType);
          scopedHeap.initializeIdentifier(argName);
        });

    if (this.getChildren().size() == 2) {
      // We have a stmt list to deal with, in addition to the return Expr.
      ((StmtListNode) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);
      // TODO(steving) Validate all of the Exprs once we implement multi-return functions.
      ((Expr) this.getChildren().get(1)).assertExpectedExprType(scopedHeap, this.type.getReturnTypes().get(0));
    } else {
      // We only have to check the return Expr.
      // TODO(steving) Validate all of the Exprs once we implement multi-return functions.
      ((Expr) this.getChildren().get(0)).assertExpectedExprType(scopedHeap, this.type.getReturnTypes().get(0));
    }
    // Leave the function body.
    scopedHeap.exitCurrObservedScope(false);
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    scopedHeap.putIdentifierValue(this.type.getFunctionName(), this.type);
    scopedHeap.initializeIdentifier(this.type.getFunctionName());

    scopedHeap.enterNewScope();

    // Since we're about to immediately execute some java source code gen, we'll need to init the local arg variables.
    this.type.getArgTypes().entrySet().stream()
        .forEach(stringTypeEntry -> {
          // Since we don't have a value to store in the ScopedHeap we'll manually ack that the identifier is init'd.
          scopedHeap.putIdentifierValue(stringTypeEntry.getKey(), stringTypeEntry.getValue());
          scopedHeap.initializeIdentifier(stringTypeEntry.getKey());
        });

    StringBuilder javaSourceOutput;
    String functionDefinitionJavaSourceFmtStr = this.type.getJavaSourceType();
    if (this.getChildren().size() == 2) {
      // There's a StmtListNode to generate code for before the return stmt.
      javaSourceOutput = new StringBuilder(
          String.format(
              functionDefinitionJavaSourceFmtStr,
              ((StmtListNode) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap)
                  // For more consistency I could've definitely made a new ReturnStmt.java file but....what's so bad about
                  // some good ol' fashioned hardcoding?
                  .append("return ")
                  .append(((Expr) this.getChildren().get(1)).generateJavaSourceOutput(scopedHeap))
                  .append(";")
          )
      );
    } else {
      // There's only a single return stmt in this function definition to gen code for.
      javaSourceOutput = new StringBuilder(
          String.format(
              functionDefinitionJavaSourceFmtStr,
              // For more consistency I could've definitely made a new ReturnStmt.java file but....what's so bad about
              // some good ol' fashioned hardcoding?
              "return " + ((Expr) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap) + ";"
          )
      );
    }
    scopedHeap.exitCurrScope();

    return javaSourceOutput;
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap definitionTimeScopedHeap) {
    // Within this function's new scope we'll need to add nodes to declare+init the arg vars within this scope. Do this
    // in order (which means constructing from the tail, up) for no reason other than sanity if we ever look close.
    //
    // Note that if you look closely and squint this below is actually dynamic code generation in Java. Like...duh cuz
    // this whole thing is code gen...that's what a compiler is...but this feels to be more code gen-y so ~shrug~ lol.
    // I think that's neat ;P.
    final ImmutableList<Map.Entry<String, Type>> backwardsArgTypes =
        FunctionDefinitionStmt.this.type.getArgTypes().entrySet().asList().reverse();
    final Function<ImmutableMap<String, Expr>, StmtListNode> getArgDeclarationStmtsFn =
        args -> {
          StmtListNode argDeclarationStmts =
              new StmtListNode(
                  new DeclarationStmt(
                      backwardsArgTypes.get(0).getKey(),
                      backwardsArgTypes.get(0).getValue(),
                      args.get(backwardsArgTypes.get(0).getKey())
                  )
              );
          for (int i = 1; i < backwardsArgTypes.size(); i++) {
            Map.Entry<String, Type> currTailArg = backwardsArgTypes.get(i);
            String currArgName = currTailArg.getKey();
            argDeclarationStmts =
                new StmtListNode(
                    new DeclarationStmt(currArgName, currTailArg.getValue(), args.get(currArgName)),
                    argDeclarationStmts
                );
          }
          return argDeclarationStmts;
        };

    definitionTimeScopedHeap.putIdentifierValue(
        this.type.getFunctionName(),
        this.type,
        new Types.FunctionType.FunctionWrapper() {
          @Override
          public Object apply(ImmutableMap<String, Expr> args, ScopedHeap callTimeScopedHeap) {
            // First things first, this function needs to operate within a totally new scope. NOTE that when this
            // actually finally EXECUTES, because it depends on the time when the function is finally CALLED rather than
            // this current moment where it's defined, the ScopedHeap very likely has additional identifiers present
            // that would not have been intuitive from the source code's definition order... but this is actually ok,
            // the expected scoping semantics are actually ensured by the type-checking phase's assertions at function
            // definition time rather than at its call site. So this is just a note to anyone who might notice any
            // weirdness if you're the type to open up a debugger and step through data... don't be that person.
            callTimeScopedHeap.enterNewScope();

            // Execute the arg declarations assigning them to their given values.
            getArgDeclarationStmtsFn.apply(args).generateInterpretedOutput(callTimeScopedHeap);

            Object returnValue;
            if (FunctionDefinitionStmt.this.getChildren().size() == 2) {
              // Now we need to execute the function body StmtListNode given.
              ((StmtListNode) FunctionDefinitionStmt.this.getChildren().get(0))
                  .generateInterpretedOutput(callTimeScopedHeap);
              // Now we need to execute the return Expr.
              returnValue = ((Expr) FunctionDefinitionStmt.this.getChildren().get(1))
                  .generateInterpretedOutput(callTimeScopedHeap);
            } else {
              // Now we need to execute the return Expr right away since there's no body StmtListNode given.
              returnValue = ((Expr) FunctionDefinitionStmt.this.getChildren().get(0))
                  .generateInterpretedOutput(callTimeScopedHeap);
            }

            // We're done executing this function body now, so we can exit this function's scope.
            callTimeScopedHeap.exitCurrScope();

            return returnValue;
          }
        }
    );

    // This is just the function definition (Stmt), not the call-site (Expr), return no value.
    return null;
  }
}
