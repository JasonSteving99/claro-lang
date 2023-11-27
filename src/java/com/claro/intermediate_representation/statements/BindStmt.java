package com.claro.intermediate_representation.statements;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.runtime_utilities.injector.Injector;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;

public class BindStmt extends Stmt {
  public final String name;
  public final TypeProvider typeProvider;
  public Type type;
  private final Expr expr;

  public BindStmt(String name, TypeProvider typeProvider, Expr expr) {
    super(ImmutableList.of());
    this.name = name;
    this.typeProvider = typeProvider;
    this.expr = expr;
  }

  public void registerBindingType(ScopedHeap scopedHeap) {
    this.type = typeProvider.resolveType(scopedHeap);
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    expr.assertExpectedExprType(scopedHeap, type);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGenJavaSource = expr.generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                // We're expecting this binding to be executed within a scope that has access to a bindings Map.
                "Injector.bindings.put(Key.create(\"%s\", %s), %s);\n",
                this.name,
                this.type.getJavaSourceClaroType(),
                exprGenJavaSource.javaSourceBody().toString()
            )
        )
    );
    exprGenJavaSource.javaSourceBody().setLength(0);

    return res.createMerged(exprGenJavaSource);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Since this is not a lazy binding, we'll just directly execute the bound expression now and cache it.
    Object boundValue = expr.generateInterpretedOutput(scopedHeap);

    return Injector.bindings.put(
        Key.create(name, type),
        // The only reason for using an Expr instead of just the value itself, is so that the typical DeclarationStmt
        // can be depended on when the ProcedureDefinitionStmt impl needs to init an implicit local variable for this.
        new Expr(ImmutableList.of(), null, -1, -1, -1) {
          @Override
          public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
            throw new ClaroParserException("Internal Compiler Error: This should be unreachable. Bug in BindStmt impl.");
          }

          @Override
          public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
            throw new ClaroParserException("Internal Compiler Error: This should be unreachable. Bug in BindStmt impl.");
          }

          @Override
          public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
            return boundValue;
          }
        }
    );
  }
}
