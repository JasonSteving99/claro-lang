package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

// TODO(steving) Support for this Stmt is completely a short term lazy hack to not have to full on introduce the meta-
// TODO(steving) type `Type` into the Claro runtime. This would likely require wrapping all the java source primitive
// TODO(steving) types in a class like ClaroInteger just so it could report a Type via some builtin method.
public class ShowTypeStmt extends Stmt {

  private Type exprType;

  public ShowTypeStmt(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Make sure that the encapsulated Expr does its on type validation on itself even though Print itself has no
    // constraints to impart on it.
    exprType = ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource expr_java_source = ((Expr) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap);
    return GeneratedJavaSource.create(
        new StringBuilder(
            String.format(
                "System.out.println(\"%s\"); // Type of `%s` determined at compile-time,\n",
                // TODO(steving) Once generics are implemented, technically concrete expression types may not be known at
                // TODO(steving) compile-time (e.g. [oneof<Foo|Bar>] could have [Foo(), Bar(), ...]).
                // TODO(steving) in that world you'll need to actually implement the ability to decide whether to determine
                // TODO(steving) the type at compile-time or runtime in order to correctly do `type(l[0])` for example.
                exprType,
                // Dump the OG java source in a comment purely for clarity purposes.
                expr_java_source.javaSourceBody()
            )
        ),
        expr_java_source.optionalStaticDefinitions(),
        expr_java_source.optionalStaticPreambleStmts()
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    System.out.println(exprType);
    return null;
  }
}
