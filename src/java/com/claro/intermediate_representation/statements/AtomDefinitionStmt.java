package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;

import java.util.stream.Collectors;

public class AtomDefinitionStmt extends Stmt {
  private final IdentifierReferenceTerm name;
  private final int atomId;
  private static int globalAtomCount = 0;

  public AtomDefinitionStmt(IdentifierReferenceTerm name) {
    super(ImmutableList.of());
    this.name = name;
    this.atomId = AtomDefinitionStmt.globalAtomCount++;
  }

  public void registerType(ScopedHeap scopedHeap) {
    // Simply need to validate that there's no other usage of this name already.
    if (scopedHeap.isIdentifierDeclared(this.name.identifier)) {
      this.name.logTypeError(ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.name.identifier));
    } else {
      // Simply place the type in the symbol table. Any references to this atom as a value, will need to have some
      // simple special handling to allow the reference.
      scopedHeap.putIdentifierValueAsTypeDef(
          this.name.identifier, Types.AtomType.forName(this.name.identifier), null);
      scopedHeap.initializeIdentifier(this.name.identifier);
      // Now I need to cache this atom.
      InternalStaticStateUtil.AtomDefinition_CACHE_INDEX_BY_ATOM_NAME.put(this.name.identifier, this.atomId);
    }
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // We only got here if the above type registration worked. So clearly everything's ok.
    return;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Nothing to codegen for an atom def.
    return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
  }

  public static String codegenAtomCacheInit() {
    return String.format(
        "$ClaroAtom.initializeCache(ImmutableList.of(%s));\n",
        InternalStaticStateUtil.AtomDefinition_CACHE_INDEX_BY_ATOM_NAME.build().keySet().stream()
            .map(n -> String.format("\"%s\"", n))
            .collect(Collectors.joining(", "))
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Nothing to codegen for an atom def.
    return null;
  }
}
