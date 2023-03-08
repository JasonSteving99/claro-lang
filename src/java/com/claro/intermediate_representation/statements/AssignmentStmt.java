package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class AssignmentStmt extends Stmt {

  // TODO(steving) This should just be a child IdentifierReferenceTerm passed to the superclass.
  private final String IDENTIFIER;
  // This is only set after the compiler's type-checking phase.
  private Type identifierValidatedType;

  public AssignmentStmt(String identifier, Expr e) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    Preconditions.checkState(
        scopedHeap.isIdentifierDeclared(this.IDENTIFIER),
        "Attempting to assign to identifier <%s> without declaring it!"
    );
    this.identifierValidatedType = scopedHeap.getValidatedIdentifierType(this.IDENTIFIER);
    if (this.identifierValidatedType.baseType().equals(BaseType.ONEOF)) {
      // Since this is assignment to a oneof type, by definition we'll allow any of the type variants supported
      // by this particular oneof instance.
      Type actualAssignedExprType =
          ((Expr) this.getChildren().get(0)).assertSupportedExprOneofTypeVariant(
              scopedHeap,
              this.identifierValidatedType,
              ((Types.OneofType) this.identifierValidatedType).getVariantTypes()
          );

      // Additionally, in case this identifier is currently being referenced within a scope where its type has been
      // narrowed, then we need to actually undo the narrowing (a.k.a. "widen" the type) if the assignment is to some
      // type other than what it was originally narrowed to.
      if (this.identifierValidatedType.autoValueIgnored_IsNarrowedType.get()) {
        String syntheticNarrowedTypeIdentifier = String.format("$NARROWED_%s", this.IDENTIFIER);
        if (!actualAssignedExprType.equals(
            scopedHeap.getValidatedIdentifierType(syntheticNarrowedTypeIdentifier))) {
          scopedHeap.deleteIdentifierValue(syntheticNarrowedTypeIdentifier);
          this.identifierValidatedType.autoValueIgnored_IsNarrowedType.set(false);
        }
      }
    } else {
      // If it's not a oneof type then we require an exact match.
      ((Expr) this.getChildren().get(0)).assertExpectedExprType(scopedHeap, this.identifierValidatedType);
    }
    scopedHeap.initializeIdentifier(this.IDENTIFIER);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();
    scopedHeap.initializeIdentifier(this.IDENTIFIER);
    GeneratedJavaSource exprGenJavaSource = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    res.append(
        String.format(
            "%s = %s;\n",
            this.IDENTIFIER,
            exprGenJavaSource.javaSourceBody().toString()
        )
    );
    // We've already consumed javaSourceBody, so it's safe to clear.
    exprGenJavaSource.javaSourceBody().setLength(0);

    return GeneratedJavaSource.forJavaSourceBody(res).createMerged(exprGenJavaSource);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Put the computed value of this identifier directly in the heap.
    scopedHeap.updateIdentifierValue(
        this.IDENTIFIER,
        this.getChildren().get(0).generateInterpretedOutput(scopedHeap)
    );
    scopedHeap.initializeIdentifier(this.IDENTIFIER);
    return null;
  }
}
