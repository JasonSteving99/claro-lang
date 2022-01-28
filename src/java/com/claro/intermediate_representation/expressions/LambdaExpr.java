package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.statements.*;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class LambdaExpr extends Expr {
  // In order to give all of the objects that represent Claro lambdas a valid disambiguated name, count lambdas.
  private static long lambdaExprCount = 0;

  private final String lambdaName;
  private final StmtListNode stmtListNode;
  private final ImmutableList<String> argNameList;
  // We'll base this implementation around the existing logic common to all procedures.
  private ProcedureDefinitionStmt delegateProcedureDefinitionStmt;
  // We'll also need to defer to an IdentifierReferenceTerm since we'll pass a reference to the lambda.
  private IdentifierReferenceTerm lambdaReferenceTerm;

  // Support the following syntax:
  //   x -> {
  //     # arbitrarily many lambda body statements can go here.
  //     var y = 2 * x;
  //     return y;
  //   }
  public LambdaExpr(ImmutableList<String> argNameList, StmtListNode stmtListNode) {
    super(ImmutableList.of());
    this.stmtListNode = stmtListNode;
    this.argNameList = argNameList;

    // Keep track of all the lambdas that are created so we can generate unambiguous aliases for the objects
    // generated for the lambdas.
    lambdaExprCount++;
    // Lambdas are unnamed. Let's name them all "$lambda" just to have some unambiguous alias.
    this.lambdaName = "$lambda" + LambdaExpr.lambdaExprCount;
  }

  // Support the following syntax:
  //   () -> {
  //     # arbitrarily many lambda body statements can go here.
  //     var y = 2 * x;
  //     return y;
  //   }
  public LambdaExpr(StmtListNode stmtListNode) {
    this(ImmutableList.of(), stmtListNode);
  }

  // Support the following syntax:
  //   x -> 2 * x;
  // The lambda body being a single Expr is enough information for us to know that the programmer wants
  // to return whatever value is produced by that single Expr.
  public LambdaExpr(ImmutableList<String> argNameList, Expr returnExpr) {
    this(
        argNameList,
        new StmtListNode(
            new ReturnStmt(
                returnExpr,
                // We'll already need to search all Lambda body StmtListNodes for ReturnStmts during the
                // Type validation phase to assert the return Type, so for now we can explicitly decide
                // that we'll figure out later, not during the parsing phase.
                new AtomicReference<TypeProvider>()
            )
        )
    );
  }

  // Support the following syntax:
  //   () -> input("Hey user, gimme some input: ");
  // The lambda body being a single Expr is enough information for us to know that the programmer wants
  // to return whatever value is produced by that single Expr.
  public LambdaExpr(Expr returnExpr) {
    this(ImmutableList.of(), returnExpr);
  }

  // TODO(steving) I THINK THE ANSWER TO THIS IS RETURNING BASE_TYPE.UNDECIDED IF THE TYPE ISN'T ASSERTED.
  // TODO(steving) I'm going to need to find some way to mandate that this Expr always has its Type asserted
  //  onto it instead of having to produce its own Type, since it only knows how to validate Types within it
  //  based on context in the assignment statement.
  // Lambda Expressions MUST have their types asserted on them by their surrounding context, in order to allow
  // syntax that doesn't require lambda expressions to define their types inline since this would typically be
  // redundant just requiring that the programmer typed in the same thing that the enclosing scope has declared,
  // for example when passing a lambda as an arg to a function since the arg type is already declared at the
  // function declaration site.
  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    // Before we even do any other assertions, we need to finish setting up the ReturnStmts in this lambda
    // to configure the Type that they're expected to return.
    if (((Types.ProcedureType) expectedExprType).hasReturnValue()) {
      StmtListNode currHead = this.stmtListNode;
      do {
        Stmt currStmt = (Stmt) currHead.getChildren().get(0);
        if (currStmt instanceof ReturnStmt) {
          ((ReturnStmt) currStmt)
              .setExpectedTypeProvider(
                  TypeProvider.ImmediateTypeProvider.of(((Types.ProcedureType) expectedExprType).getReturnType()));
          // We only need to set the expected Type on the first ReturnStmt that we come across because during
          // the grammar parsing phase, all ReturnStmts are passed the same AtomicReference instance.
          break;
        }
      } while ((currHead = currHead.tail) != null);
    }

    if (this.argNameList.isEmpty()) {
      // We have no args, so no need to pass anything to the ProcedureDefinitionStmt.
      this.delegateProcedureDefinitionStmt =
          new ProviderFunctionDefinitionStmt(
              this.lambdaName,
              BaseType.LAMBDA_PROVIDER_FUNCTION,
              TypeProvider.ImmediateTypeProvider.of(((Types.ProcedureType) expectedExprType).getReturnType()),
              LambdaExpr.this.stmtListNode
          );
    } else {
      ImmutableList<Type> expectedArgTypeList = ((Types.ProcedureType) expectedExprType).getArgTypes();
      // Make sure that we at least have the correct number of args given.
      if (this.argNameList.size() != expectedArgTypeList.size()) {
        throw ClaroTypeException.forWrongNumberOfArgsForLambdaDefinition(expectedExprType);
      }

      // Align the asserted Arg Types with the declared lambda arg names in order, so that we can check they're correct.
      ImmutableMap.Builder<String, TypeProvider> argTypesMapBuilder =
          ImmutableMap.builderWithExpectedSize(this.argNameList.size());
      for (int i = 0; i < this.argNameList.size(); i++) {
        argTypesMapBuilder.put(
            this.argNameList.get(i),
            TypeProvider.ImmediateTypeProvider.of(expectedArgTypeList.get(i))
        );
      }

      // We need to pass those args to the ProcedureDefinitionStmt for validation.
      if (((Types.ProcedureType) expectedExprType).hasReturnValue()) {
        this.delegateProcedureDefinitionStmt =
            new FunctionDefinitionStmt(
                this.lambdaName,
                BaseType.LAMBDA_FUNCTION,
                argTypesMapBuilder.build(),
                TypeProvider.ImmediateTypeProvider.of(((Types.ProcedureType) expectedExprType).getReturnType()),
                LambdaExpr.this.stmtListNode
            );
      } else {
        this.delegateProcedureDefinitionStmt =
            new ConsumerFunctionDefinitionStmt(
                this.lambdaName,
                BaseType.LAMBDA_CONSUMER_FUNCTION,
                argTypesMapBuilder.build(),
                LambdaExpr.this.stmtListNode
            );
      }
    }

    // Before we delegate to our ProcedureDefinitionStmt, we need to make sure that we're able to preserve
    // the status of whether or not ReturnStmts are allowed in the outer scope that this lambda is defined in.
    Optional<String> outerScopeWithinProcedureScope = ReturnStmt.withinProcedureScope;
    boolean outerScopeSupportsReturnStmt = ReturnStmt.supportReturnStmt;

    // Now delegate to our internal ProcedureDefinitionStmt instance to see if it approves the types.
    delegateProcedureDefinitionStmt.registerProcedureTypeProvider(scopedHeap);
    delegateProcedureDefinitionStmt.assertExpectedExprTypes(scopedHeap);

    // Recover the existing state info about whether or not ReturnStmts are supported in the scope surrounding
    // this LambdaExpr.
    ReturnStmt.withinProcedureScope = outerScopeWithinProcedureScope;
    ReturnStmt.supportReturnStmt = outerScopeSupportsReturnStmt;

    // Now to model the last thing we'll do during codegen, setup our lambda reference.
    this.lambdaReferenceTerm = new IdentifierReferenceTerm(this.lambdaName);
    lambdaReferenceTerm.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  // It's invalid to initialize a variable to a lambda expression without declaring the type of that variable
  // since the lambda has no syntactical way of knowing its Type otherwise.
  // E.g. this is INVALID:
  //   var f = (x) -> { return x; };
  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    throw ClaroTypeException.forUndecidedTypeLeakMissingTypeDeclarationForLambdaInitialization();
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    // We're intentionally going to drop any static definitions because while lambdas certainly can grab scope
    // to access things defined before them, their definitions should certainly not be PROVIDING anything other
    // than a reference to itself that can be accessed by anyone else.
    StringBuilder delegateProcedureDefinitionStmtGeneratedJavaSource =
        delegateProcedureDefinitionStmt.generateJavaSourceOutput(scopedHeap).javaSourceBody();
    // Tell the current Stmt that's being parsed to generate an extra Stmt to define this procedure's class
    // before it generates code for itself.
    Stmt.addGeneratedJavaSourceStmtBeforeCurrentStmt(
        delegateProcedureDefinitionStmtGeneratedJavaSource.toString());

    // Wherever this Expr gets codegen'd anyone just needs to get a reference to the generated procedure
    // class instance and that's it.
    return lambdaReferenceTerm.generateJavaSourceBodyOutput(scopedHeap);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Delegate to the ProcedureDefinitionStmt to generate and add the ProcedureWrapper instance to the ScopedHeap.
    delegateProcedureDefinitionStmt.generateInterpretedOutput(scopedHeap);

    // Now I can just use IdentifierReferenceTerm's interpreted output implementation to get this reference to work.
    return lambdaReferenceTerm.generateInterpretedOutput(scopedHeap);
  }
}
