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

public abstract class ProcedureDefinitionStmt extends Stmt {

  private final Types.ProcedureType procedureType;

  public ProcedureDefinitionStmt(
      ImmutableList<Node> children,
      Types.ProcedureType procedureType) {
    super(children);
    this.procedureType = procedureType;
  }

  @Override
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(this.procedureType.getProcedureName()),
        String.format("Unexpected redeclaration of %s %s.", this.procedureType, this.procedureType.getProcedureName())
    );

    // First we need to mark the function declared and initialized within the original calling scope.
    scopedHeap.observeIdentifier(this.procedureType.getProcedureName(), this.procedureType);
    scopedHeap.initializeIdentifier(this.procedureType.getProcedureName());

    // Enter the new scope for this function.
    scopedHeap.observeNewScope(false);

    // I may need to mark the args as observed identifiers within this new scope.
    if (this.procedureType.hasArgs()) {
      this.procedureType.getArgTypes().forEach(
          (argName, argType) ->
          {
            scopedHeap.observeIdentifier(argName, argType);
            scopedHeap.initializeIdentifier(argName);
          });
    }

    // Now from here step through the function body.
    if (this.getChildren().size() == 2) {
      // We have a stmt list to deal with, in addition to the return Expr.
      ((StmtListNode) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);
      ((Expr) this.getChildren().get(1)).assertExpectedExprType(scopedHeap, this.procedureType.getReturnType());
    } else {
      if (this.procedureType.hasReturnValue()) {
        // We only have to check the return Expr.
        ((Expr) this.getChildren().get(0)).assertExpectedExprType(scopedHeap, this.procedureType.getReturnType());
      } else {
        // We only have to check the StmtListNode.
        ((StmtListNode) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);
      }
    }
    // Leave the function body.
    scopedHeap.exitCurrObservedScope(false);
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    scopedHeap.putIdentifierValue(this.procedureType.getProcedureName(), this.procedureType);
    scopedHeap.initializeIdentifier(this.procedureType.getProcedureName());

    scopedHeap.enterNewScope();

    // Since we're about to immediately execute some java source code gen, we might need to init the local arg variables.
    if (this.procedureType.hasArgs()) {
      this.procedureType.getArgTypes().entrySet().stream()
          .forEach(stringTypeEntry -> {
            // Since we don't have a value to store in the ScopedHeap we'll manually ack that the identifier is init'd.
            scopedHeap.putIdentifierValue(stringTypeEntry.getKey(), stringTypeEntry.getValue());
            scopedHeap.initializeIdentifier(stringTypeEntry.getKey());
          });
    }

    StringBuilder javaSourceOutput;
    if (this.getChildren().size() == 2) {
      // There's a StmtListNode to generate code for before the return stmt.
      javaSourceOutput = new StringBuilder(
          this.procedureType.getJavaNewTypeDefinitionStmt(
              ((StmtListNode) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap)
                  // For more consistency I could've definitely made a new ReturnStmt.java file but....what's so bad about
                  // some good ol' fashioned hardcoding?
                  .append("return ")
                  .append(((Expr) this.getChildren().get(1)).generateJavaSourceOutput(scopedHeap))
                  .append(";")
          )
      );
    } else {
      String javaBodyTemplate =
          this.procedureType.hasReturnValue() ?
          // There's only a single return stmt in this function definition to gen code for.
          "return %s;" :
          // There's only a single StmtListNode to gen code for.
          "%s";
      javaSourceOutput = new StringBuilder(
          this.procedureType.getJavaNewTypeDefinitionStmt(
              // For more consistency and I guess avoiding the condition checking above, I could've definitely made a
              // new ReturnStmt.java file but....what's so bad about some good ol' fashioned hardcoding?
              new StringBuilder(
                  String.format(
                      javaBodyTemplate,
                      this.getChildren().get(0).generateJavaSourceOutput(scopedHeap)
                  )
              )
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
    ImmutableList<Map.Entry<String, Type>> backwardsArgTypes =
        this.procedureType.hasArgs() ?
        ProcedureDefinitionStmt.this.procedureType.getArgTypes().entrySet().asList().reverse() :
        null;
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
        this.procedureType.getProcedureName(),
        this.procedureType,
        this.procedureType.new ProcedureWrapper() {
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

            if (ProcedureDefinitionStmt.this.procedureType.hasArgs()) {
              // Execute the arg declarations assigning them to their given values.
              getArgDeclarationStmtsFn.apply(args).generateInterpretedOutput(callTimeScopedHeap);
            }

            Object returnValue = null; // null, since we may or may not have a value to give.
            if (ProcedureDefinitionStmt.this.getChildren().size() == 2) {
              // Now we need to execute the function body StmtListNode given.
              ((StmtListNode) ProcedureDefinitionStmt.this.getChildren().get(0))
                  .generateInterpretedOutput(callTimeScopedHeap);
              // Now we need to execute the return Expr.
              returnValue = ((Expr) ProcedureDefinitionStmt.this.getChildren().get(1))
                  .generateInterpretedOutput(callTimeScopedHeap);
            } else {
              if (ProcedureDefinitionStmt.this.procedureType.hasReturnValue()) {
                // Now we need to execute the return Expr right away since there's no body StmtListNode given.
                returnValue = ((Expr) ProcedureDefinitionStmt.this.getChildren().get(0))
                    .generateInterpretedOutput(callTimeScopedHeap);
              } else {
                // We just need to execute the single StmtListNode.
                ((StmtListNode) ProcedureDefinitionStmt.this.getChildren().get(0))
                    .generateInterpretedOutput(callTimeScopedHeap);
              }
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
