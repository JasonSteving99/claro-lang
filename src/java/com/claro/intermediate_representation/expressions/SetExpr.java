package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.*;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.ClaroSet;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SetExpr extends Expr {

  private final Optional<ImmutableList<Expr>> initializerValues;
  private final boolean isMutable;
  public Optional<Type> optionalAssertedType = Optional.empty();

  public SetExpr(ImmutableList<Expr> initializerValues, boolean isMutable, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.initializerValues = Optional.of(initializerValues);
    this.isMutable = isMutable;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    expectedExprType = TypeProvider.Util.maybeDereferenceAliasSelfReference(expectedExprType, scopedHeap);
    // Definitely have a set here, the user can't lie and call it something else. Early check here before type inference
    // only in the case of an empty initializer since we'll give a better error message in the non-empty case if waiting
    // until after inference.
    if (!expectedExprType.baseType().equals(BaseType.SET)) {
      logTypeError(new ClaroTypeException(BaseType.SET, expectedExprType));
      return;
    }
    this.optionalAssertedType = Optional.of(expectedExprType);
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // All of the initializer values must be of the same type.
    // TODO(steving) In the future when Claro's type system is up to it, sets should be restricted to immutable values.
    if (this.optionalAssertedType.isPresent()) {
      initializerValues.get().get(0)
          .assertExpectedExprType(
              scopedHeap,
              this.optionalAssertedType.get().parameterizedTypeArgs().get(Types.SetType.PARAMETERIZED_TYPE)
          );
    } else {
      this.optionalAssertedType =
          Optional.of(Types.SetType.forValueType(initializerValues.get().get(0).getValidatedExprType(scopedHeap)));
    }
    for (Expr expr : initializerValues.get().subList(1, initializerValues.get().size())) {
      expr.assertExpectedExprType(
          scopedHeap,
          this.optionalAssertedType.get().parameterizedTypeArgs().get(Types.SetType.PARAMETERIZED_TYPE)
      );
    }
    return Types.SetType.forValueType(
        this.optionalAssertedType.get().parameterizedTypeArgs().get(Types.SetType.PARAMETERIZED_TYPE),
        this.isMutable
    );
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return getGeneratedJavaSource(scopedHeap, this.optionalAssertedType.get(), this.initializerValues.get());
  }

  static GeneratedJavaSource getGeneratedJavaSource(ScopedHeap scopedHeap, Type validatedType, ImmutableList<Expr> initializerValues) {
    StringBuilder resJavaSourceBody =
        new StringBuilder(
            String.format(
                "new %s(%s)",
                validatedType.getJavaSourceType(),
                validatedType.getJavaSourceClaroType()
            ));
    if (initializerValues.isEmpty()) {
      return GeneratedJavaSource.forJavaSourceBody(resJavaSourceBody);
    }

    AtomicReference<GeneratedJavaSource> setValsGenJavaSource =
        new AtomicReference<>(GeneratedJavaSource.forJavaSourceBody(new StringBuilder()));
    resJavaSourceBody.append(".add(ImmutableList.of(");
    resJavaSourceBody.append(
        initializerValues.stream()
            .map(expr -> {
              GeneratedJavaSource setValGenJavaSource = expr.generateJavaSourceOutput(scopedHeap);
              String setValJavaSourceString = setValGenJavaSource.javaSourceBody().toString();
              // We've consumed the javaSourceBody, it's safe to clear.
              setValGenJavaSource.javaSourceBody().setLength(0);
              // Now merge with the overall gen java source to track all of the static and preamble stmts.
              setValsGenJavaSource.set(setValsGenJavaSource.get().createMerged(setValGenJavaSource));

              return setValJavaSourceString;
            })
            .collect(Collectors.joining(", ")));
    resJavaSourceBody.append("))");

    return GeneratedJavaSource.forJavaSourceBody(resJavaSourceBody)
        .createMerged(setValsGenJavaSource.get());
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    if (!initializerValues.isPresent()) {
      return new ClaroSet<Object>(this.optionalAssertedType.get());
    }
    return new ClaroSet<Object>(this.optionalAssertedType.get())
        .add(
            this.initializerValues.get().stream()
                .map(expr -> expr.generateInterpretedOutput(scopedHeap))
                .collect(ImmutableList.toImmutableList())
                .asList());
  }
}
