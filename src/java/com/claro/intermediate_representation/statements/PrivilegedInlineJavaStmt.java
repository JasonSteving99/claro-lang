package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

public class PrivilegedInlineJavaStmt extends Stmt {
  private final ImmutableMap<String, TypeProvider> capturedTypeProviders;
  private ImmutableMap<String, Type> capturedTypes;
  private final String java;

  public PrivilegedInlineJavaStmt(String capturedTypeProviders, String java) {
    super(ImmutableList.of());
    this.capturedTypeProviders =
        ImmutableList.copyOf(
                Optional.ofNullable(capturedTypeProviders)
                    .orElse("")
                    .split(","))
            .stream()
            .map(String::trim)
            .filter(n -> !n.isEmpty())
            .collect(ImmutableMap.toImmutableMap(
                n -> n,
                n -> TypeProvider.Util.getTypeByName(n, /*isTypeDefinition=*/ false)
            ));
    this.java = java;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Somehow this must've been allowed by the Parser, so the only thing that needs to happen here is just
    // synthetically mark *every* variable in the current scope as initialized/used.
    scopedHeap.scopeStack.peek().scopedSymbolTable.keySet().stream()
        .filter(identifier ->
                    // Workaround the return tracking.
                    !InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()
                    || !(identifier.startsWith("$") && identifier.endsWith("RETURNS")))
        .forEach(
            identifier -> {
              scopedHeap.markIdentifierUsed(identifier);
              scopedHeap.initializeIdentifier(identifier);
            }
        );
    // Resolve the captured types.
    ImmutableMap.Builder<String, Type> capturedTypesBuilder = ImmutableMap.builder();
    for (Map.Entry<String, TypeProvider> captured : this.capturedTypeProviders.entrySet()) {
      capturedTypesBuilder.put(captured.getKey(), captured.getValue().resolveType(scopedHeap));
    }
    this.capturedTypes = capturedTypesBuilder.build();
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Somehow this must've been allowed by the Parser, so the only thing that needs to happen here is just
    // synthetically mark *every* variable in the current scope as initialized/used.
    scopedHeap.scopeStack.peek().scopedSymbolTable.keySet().stream()
        .filter(identifier ->
                    // Workaround the return tracking.
                    !InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()
                    || !(identifier.startsWith("$") && identifier.endsWith("RETURNS")))
        .forEach(
            identifier -> {
              scopedHeap.markIdentifierUsed(identifier);
              scopedHeap.initializeIdentifier(identifier);
            }
        );
    String javaWithTypeCapturesFormatted = this.java;
    for (Map.Entry<String, Type> capturedType : this.capturedTypes.entrySet()) {
      // First format any usages of the Java type of the captured Claro type.
      javaWithTypeCapturesFormatted =
          javaWithTypeCapturesFormatted.replaceAll(
              String.format("\\$\\$JAVA_TYPE\\(%s\\)", capturedType.getKey()),
              Matcher.quoteReplacement(capturedType.getValue().getJavaSourceType())
          );
      // Then format any usages of the Claro type of the captured type.
      javaWithTypeCapturesFormatted =
          javaWithTypeCapturesFormatted.replaceAll(
              String.format("\\$\\$CLARO_TYPE\\(%s\\)", capturedType.getKey()),
              Matcher.quoteReplacement(capturedType.getValue().getJavaSourceClaroType())
          );
    }
    return GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder("// BEGIN PRIVILEGED INLINE JAVA\n")
            .append(javaWithTypeCapturesFormatted)
            .append("// END PRIVILEGED INLINE JAVA\n"));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    throw new RuntimeException("Internal Compiler Error! Privileged Inline Java is not supported in the Interpreted backend!");
  }
}
