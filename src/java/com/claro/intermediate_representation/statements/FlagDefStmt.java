package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class FlagDefStmt extends Stmt {

  public final IdentifierReferenceTerm identifier;
  private final TypeProvider type;
  public Type resolvedType;
  private static final ImmutableSet<Type> SUPPORTED_FLAG_TYPES =
      ImmutableSet.of(
          Types.BOOLEAN, Types.STRING, Types.INTEGER,
          Types.ListType.forValueType(Types.STRING, /*isMutable=*/false)
      );

  public FlagDefStmt(IdentifierReferenceTerm identifier, TypeProvider type) {
    super(ImmutableList.of());
    this.identifier = identifier;
    this.type = type;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Do type resolution unconditionally.
    this.resolvedType = this.type.resolveType(scopedHeap);

    // Need to validate that the identifier hasn't already been declared.
    if (scopedHeap.isIdentifierDeclared(this.identifier.identifier)) {
      this.identifier.logTypeError(ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.identifier.identifier));
      return; // Failed.
    }

    // Now add this new identifier to the symbol table and mark it as a static value.
    scopedHeap.observeStaticIdentifierValue(this.identifier.identifier, this.resolvedType, /*isLazy=*/true);

    // Ensure that the static value is of some supported type to ensure that it can be automatically parsed.
    if (!SUPPORTED_FLAG_TYPES.contains(resolvedType)) {
      this.identifier.logTypeError(ClaroTypeException.forIllegalFlagTypeDeclaration(SUPPORTED_FLAG_TYPES));
      return;
    }

    // Immediately mark the flag initialized so that it can be referenced. As there are no static providers associated
    // with an exported flag def (unlike with static value defs), there's no chance that flags depend on each other and
    // so there's no concern on initialization ordering.
    scopedHeap.initializeIdentifier(this.identifier.identifier);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // NOTE: There's an implicit assumption that the module validation logic will actually handle generating the
    // initialization of this value.
    return GeneratedJavaSource.forStaticDefinitionsAndPreamble(
        new StringBuilder(),
        new StringBuilder("public static ")
            .append(this.resolvedType.getJavaSourceType())
            .append(" ")
            .append(this.identifier.identifier)
            .append(";\n")
    );
  }

  public StringBuilder generateStaticInitialization(StringBuilder res) {
    // Flags are always lazily initialized, so declare an additional boolean to track whether the value has been
    // initialized, and a static init procedure that'll do the init, IFF it hasn't been done yet.
    String stateVar = this.identifier.identifier + "$isInitialized";
    res.append("private static boolean ")
        .append(stateVar)
        .append(" = false;\n")
        .append("public static synchronized ")
        .append(this.resolvedType.getJavaSourceType())
        .append(" lazyStaticInitializer$")
        .append(this.identifier.identifier)
        .append("() {\n" +
                "  if (!")
        .append(stateVar)
        .append(") {\n")
        .append(this.identifier.identifier)
        .append(" = ");

    generateFlagClaroTypeInit(res);

    res.append("\n  ")
        .append(stateVar)
        .append(" = true;\n")
        .append("  }\n" +
                "  return ")
        .append(this.identifier.identifier)
        .append(";\n}\n");
    return res;
  }

  private void generateFlagClaroTypeInit(StringBuilder res) {
    String javaParsedOptionTypeCast =
        String.format("(%s)", getJavaSourceParsedOptionType(this.resolvedType, /*primitiveValueTypes=*/false));
    if (this.resolvedType.baseType().equals(BaseType.LIST)) {
      res.append("new ClaroList(")
          .append(this.resolvedType.getJavaSourceClaroType())
          .append(", ")
          .append(javaParsedOptionTypeCast)
          .append("com.claro.runtime_utilities.flags.$Flags.lazyStaticInitializer$parsedOptions().get(\"")
          .append(this.identifier.identifier)
          .append("\"));");
    } else {
      res.append(javaParsedOptionTypeCast)
          .append("com.claro.runtime_utilities.flags.$Flags.lazyStaticInitializer$parsedOptions().get(\"")
          .append(this.identifier.identifier)
          .append("\");");
    }
  }

  public static String generateAnnotatedOptionField(String flagName, Type type) {
    StringBuilder res =
        new StringBuilder("@Option(\n    name = \"")
            .append(flagName)
            .append("\",\n    defaultValue = \"");

    switch (type.baseType()) {
      case BOOLEAN:
        res.append("false");
        break;
      case INTEGER:
        res.append("0");
        break;
      default:
        // Intentionally empty. Use empty string for strings or lists.
    }

    res.append("\"");

    if (type.baseType().equals(BaseType.LIST)) {
      res.append(",\n    allowMultiple = true");
    }

    res.append("\n)\npublic ")
        .append(getJavaSourceParsedOptionType(type, /*primitiveValueTypes=*/true))
        .append(" flag$")
        .append(flagName)
        .append(";");

    return res.toString();
  }

  private static String getJavaSourceParsedOptionType(Type type, boolean primitiveValueTypes) {
    if (type.baseType().equals(BaseType.LIST)) {
      return String.format("List<%s>", ((Types.ListType) type).getElementType().getJavaSourceType());
    }
    if (primitiveValueTypes) {
      switch (type.baseType()) {
        case BOOLEAN:
          return "boolean";
        case INTEGER:
          return "int";
        case STRING:
          return "String"; // Would've done this anyway, but explicitly for consistency...
        default:
          throw new RuntimeException("Internal Compiler Error! Unsupported Flag Type: " + type);
      }
    }
    return type.getJavaSourceType();
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support `FlagDefStmt` in the interpreted backend just yet!");
  }
}
