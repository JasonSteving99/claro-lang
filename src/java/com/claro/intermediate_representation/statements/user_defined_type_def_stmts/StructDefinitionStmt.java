package com.claro.intermediate_representation.statements.user_defined_type_def_stmts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.function.Function;
import java.util.stream.Collectors;

public class StructDefinitionStmt extends Stmt implements UserDefinedTypeDefinitionStmt {
  private final String structName;
  private final Function<ScopedHeap, Types.StructType> structTypeProvider;
  private Types.StructType structType;
  private static int anonymousStructInternalCount;

  private static final String PUBLIC_FIELD_FMT_STR = "  public %s %s;";

  public StructDefinitionStmt(String structName, ImmutableMap<String, TypeProvider> fieldTypeProvidersMap, boolean immutable) {
    super(ImmutableList.of());
    this.structName = structName;
    this.structTypeProvider =
        (scopedHeap) -> {
          ImmutableMap<String, Type> fieldTypesMap =
              TypeProvider.Util.resolveTypeProviderMap(scopedHeap, fieldTypeProvidersMap);
          return immutable ?
                 Types.StructType.ImmutableStructType.forFieldTypes(this.structName, fieldTypesMap) :
                 Types.StructType.MutableStructType.forFieldTypes(this.structName, fieldTypesMap);
        };
  }

  public StructDefinitionStmt(ImmutableMap<String, TypeProvider> fieldTypeProvidersMap, boolean immutable) {
    this(
        // We don't actually want any anonymous structs in practice. We'll just give it a name to avoid ambiguity.
        (immutable ? "$Immutable_Struct" : "$Struct_") + anonymousStructInternalCount++,
        fieldTypeProvidersMap,
        immutable
    );
  }

  @Override
  public void registerTypeProvider(ScopedHeap scopedHeap) {
    // Register a null type since it's not yet resolved, and then abuse its Object value field temporarily to hold the
    // TypeProvider that will be used for type-resolution in the later phase.
    scopedHeap.putIdentifierValue(this.structName, null, this.structTypeProvider);
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Resolve the actual type of this Struct.
    this.structType = this.structTypeProvider.apply(scopedHeap);

    // We just need to mark the struct type declared and initialized within the original calling scope.
    // Note that we're putting a *Type* in the symbol table because we want users to have easy native
    // access to type data without doing some fancy backflips for reflection.
    scopedHeap.observeIdentifier(this.structName, this.structType);
    scopedHeap.initializeIdentifier(this.structName);

    // TODO(steving) If this struct is marked immutable, need to assert that every field type in this struct is
    // TODO(steving) also immutable.
  }

  @Override
  public Node.GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    String javaSourceClaroType = this.structType.getJavaSourceClaroType();
    return Node.GeneratedJavaSource.forStaticDefinitions(
        new StringBuilder(
            String.format(
                this.structType.baseType().getJavaNewTypeDefinitionStmtFmtStr(),
                this.structName,
                javaSourceClaroType,
                this.structType.getFieldTypes().entrySet().stream()
                    .map(
                        stringTypeEntry ->
                            String.format(
                                PUBLIC_FIELD_FMT_STR,
                                stringTypeEntry.getValue().getJavaSourceType(),
                                stringTypeEntry.getKey()
                            ))
                    .collect(Collectors.joining("\n")),
                this.structName,
                this.structName
            )
        )
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    scopedHeap.putIdentifierValue(this.structName, this.structType, this.structType);
    return null;
  }
}
