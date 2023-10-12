package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.Optional;

public class StaticValueDefStmt extends Stmt {

  public final IdentifierReferenceTerm identifier;
  private final TypeProvider type;
  public final boolean isLazy;
  public Optional<Type> resolvedType;
  private boolean alreadyAssertedTypes = false;

  public StaticValueDefStmt(IdentifierReferenceTerm identifier, TypeProvider type, boolean isLazy) {
    super(ImmutableList.of());
    this.identifier = identifier;
    this.type = type;
    this.isLazy = isLazy;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!this.alreadyAssertedTypes) {
      this.alreadyAssertedTypes = true;
      // Do type resolution unconditionally.
      this.resolvedType = Optional.of(this.type.resolveType(scopedHeap));

      // Need to validate that the identifier hasn't already been declared, and then validate that the
      if (scopedHeap.isIdentifierDeclared(this.identifier.identifier)) {
        this.identifier.logTypeError(ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.identifier.identifier));
        return; // Failed.
      }

      // Now add this new identifier to the symbol table and mark it as a static value.
      scopedHeap.observeStaticIdentifierValue(this.identifier.identifier, this.resolvedType.get(), this.isLazy);

      // Ensure that the static value is of some deeply immutable type to prevent data races.
      if (!Types.isDeeplyImmutable(this.resolvedType.get())) {
        this.resolvedType = Optional.empty();
        this.identifier.logTypeError(ClaroTypeException.forIllegalMutableStaticValueDeclaration());
        return;
      }

      // NOTE: There's an implicit assumption that the module validation logic will handle additionally validating that
      // there is in fact a `provider<Bar>` named `static_FOO()` for any `static FOO: Bar;` found in a .claro_module_api.
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // NOTE: There's an implicit assumption that the module validation logic will actually handle generating the
    // initialization of this value.
    return GeneratedJavaSource.forStaticDefinitionsAndPreamble(
        new StringBuilder(),
        new StringBuilder("public static ")
            .append(this.resolvedType.get().getJavaSourceType())
            .append(" ")
            .append(this.identifier.identifier)
            .append(";\n")
    );
  }

  public StringBuilder generateStaticInitialization(StringBuilder res) {
    StringBuilder staticInit = new StringBuilder()
        .append(this.identifier.identifier)
        .append(" = static_")
        .append(this.identifier.identifier)
        .append(".apply();\n");

    if (this.isLazy) {
      // If the static value is declared lazy, then instead of eagerly initializing its value on startup, declare an
      // additional boolean to track whether the value has been initialized, and a static init procedure that'll do the
      // init, IFF it hasn't been done yet.
      String stateVar = this.identifier.identifier + "$isInitialized";
      res.append("private static boolean ")
          .append(stateVar)
          .append(" = false;\n")
          .append("public static synchronized ")
          .append(this.resolvedType.get().getJavaSourceType())
          .append(" lazyStaticInitializer$")
          .append(this.identifier.identifier)
          .append("() {\n" +
                  "  if (!")
          .append(stateVar)
          .append(") {\n")
          .append(staticInit)
          .append("\n  ")
          .append(stateVar)
          .append(" = true;\n")
          .append("  }\n" +
                  "  return ")
          .append(this.identifier.identifier)
          .append(";\n}\n");
    } else {
      // If the static value isn't declared lazy, then it should be eagerly initialized on program startup.
      res.append("static {\n\t").append(staticInit).append("}\n");
    }
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support `StaticValueDefStmt` in the interpreted backend just yet!");
  }
}
