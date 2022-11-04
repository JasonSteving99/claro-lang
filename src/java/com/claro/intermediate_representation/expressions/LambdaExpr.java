package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.statements.*;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class LambdaExpr extends Expr {
  // In order to give all of the objects that represent Claro lambdas a valid disambiguated name, count lambdas.
  private static long lambdaExprCount = 0;

  private final String lambdaName;
  private final StmtListNode stmtListNode;
  private final ImmutableList<String> argNameList;
  private final AtomicReference<TypeProvider> returnTypeReference;
  // We'll base this implementation around the existing logic common to all procedures.
  private ProcedureDefinitionStmt delegateProcedureDefinitionStmt;
  // We'll also need to defer to an IdentifierReferenceTerm since we'll pass a reference to the lambda.
  private IdentifierReferenceTerm lambdaReferenceTerm;
  private boolean alreadyAssertedTypes = false;

  // Support the following syntax:
  //   x -> {
  //     # arbitrarily many lambda body statements can go here.
  //     var y = 2 * x;
  //     return y;
  //   }
  public LambdaExpr(
      ImmutableList<String> argNameList, StmtListNode stmtListNode, AtomicReference<TypeProvider> returnTypeReference, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.stmtListNode = stmtListNode;
    this.argNameList = argNameList;
    this.returnTypeReference = returnTypeReference;

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
  public LambdaExpr(StmtListNode stmtListNode, AtomicReference<TypeProvider> returnTypeReference, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    this(ImmutableList.of(), stmtListNode, returnTypeReference, currentLine, currentLineNumber, startCol, endCol);
  }

  // Support the following syntax:
  //   x -> 2 * x;
  // The lambda body being a single Expr is enough information for us to know that the programmer wants
  // to return whatever value is produced by that single Expr.
  public LambdaExpr(ImmutableList<String> argNameList, Expr returnExpr, AtomicReference<TypeProvider> returnTypeReference, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    this(
        argNameList,
        new StmtListNode(
            new ReturnStmt(
                returnExpr,
                returnTypeReference
            )
        ),
        returnTypeReference,
        currentLine, currentLineNumber, startCol, endCol
    );
  }

  // Support the following syntax:
  //   () -> input("Hey user, gimme some input: ");
  // The lambda body being a single Expr is enough information for us to know that the programmer wants
  // to return whatever value is produced by that single Expr.
  public LambdaExpr(Expr returnExpr, AtomicReference<TypeProvider> returnTypeReference, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    this(ImmutableList.of(), returnExpr, returnTypeReference, currentLine, currentLineNumber, startCol, endCol);
  }

  // Lambda Expressions MUST have their types asserted on them by their surrounding context, in order to allow
  // syntax that doesn't require lambda expressions to define their types inline since this would typically be
  // redundant just requiring that the programmer typed in the same thing that the enclosing scope has declared,
  // for example when passing a lambda as an arg to a function since the arg type is already declared at the
  // function declaration site.
  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    if (!this.alreadyAssertedTypes) {
      // Because lambda expressions involve instantiating a new procedure definition and adding it to the scopedheap
      // it's actually important that this only runs once.
      this.alreadyAssertedTypes = true;

      // Before we delegate to our ProcedureDefinitionStmt, we need to make sure that we're able to preserve
      // the status of whether or not ReturnStmts are allowed in the outer scope that this lambda is defined in.
      Optional<String> outerScopeWithinProcedureScope = ReturnStmt.withinProcedureScope;
      boolean outerScopeSupportsReturnStmt = ReturnStmt.supportReturnStmt;
      TypeProvider outerScopeExpectedReturnTypeProvider = null;
      if (((Types.ProcedureType) expectedExprType).hasReturnValue()) {
        outerScopeExpectedReturnTypeProvider = returnTypeReference.get();
        returnTypeReference.set(
            TypeProvider.ImmediateTypeProvider.of(((Types.ProcedureType) expectedExprType).getReturnType()));
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

      // Before we defer to the ProcedureDefinitionStmt for type validation, we may need to preserve required contracts
      // if any are present.
      boolean cleanupInternalStaticState = false;
      if (InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()) {
        Optional<ImmutableListMultimap<String, ImmutableList<Types.$GenericTypeParam>>>
            optionalRequiredContractNamesToGenericArgs =
            ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
                .get())
                .resolvedProcedureType.getOptionalRequiredContractNamesToGenericArgs();
        if (optionalRequiredContractNamesToGenericArgs.isPresent()) {
          InternalStaticStateUtil.LambdaExpr_optionalActiveGenericProcedureDefRequiredContractNamesToGenericArgs
              = Optional.of(optionalRequiredContractNamesToGenericArgs.get());
          cleanupInternalStaticState = true;
        }
      }

      // Now delegate to our internal ProcedureDefinitionStmt instance to see if it approves the types.
      delegateProcedureDefinitionStmt.registerProcedureTypeProvider(scopedHeap);
      delegateProcedureDefinitionStmt.assertExpectedExprTypes(scopedHeap);

      if (cleanupInternalStaticState) {
        InternalStaticStateUtil.LambdaExpr_optionalActiveGenericProcedureDefRequiredContractNamesToGenericArgs
            = Optional.empty();
      }

      // Recover the existing state info about whether or not ReturnStmts are supported in the scope surrounding
      // this LambdaExpr.
      ReturnStmt.withinProcedureScope = outerScopeWithinProcedureScope;
      ReturnStmt.supportReturnStmt = outerScopeSupportsReturnStmt;
      if (((Types.ProcedureType) expectedExprType).hasReturnValue()) {
        returnTypeReference.set(outerScopeExpectedReturnTypeProvider);
      }

      // Now to model the last thing we'll do during codegen, setup our lambda reference.
      this.lambdaReferenceTerm =
          new IdentifierReferenceTerm(this.lambdaName, currentLine, currentLineNumber, startCol, endCol);
      lambdaReferenceTerm.assertExpectedExprType(scopedHeap, expectedExprType);
    }

    // Because lambdas actually will need to be typechecked multiple times in the case of typechecking within multiple
    // different monomorphizations, we need to reset this to allow it to redo this work over different types for the
    // next monomorphization. This is safe, because the guard above still protects against recursive calls back into the
    // same lambda expr initialization during the same outer function definition statement.
    if (InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation ||
        (InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()
         &&
         ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
             .get()).procedureName.contains("$MONOMORPHIZATION"))) {
      this.alreadyAssertedTypes = false;
    }
  }

  // It's invalid to initialize a variable to a lambda expression without declaring the type of that variable
  // since the lambda has no syntactical way of knowing its Type otherwise.
  // E.g. this is INVALID:
  //   var f = (x) -> { return x; };
  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!this.acceptUndecided) {
      throw ClaroTypeException.forAmbiguousLambdaExprMissingTypeDeclarationForLambdaInitialization();
    }
    return Types.UNDECIDED;
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
