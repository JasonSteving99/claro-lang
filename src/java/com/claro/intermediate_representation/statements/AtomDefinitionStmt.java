package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class AtomDefinitionStmt extends Stmt {
  public final IdentifierReferenceTerm name;
  private final int atomId;
  private static int globalAtomCount = 0;
  private boolean alreadyRegisteredAtom = false;

  public AtomDefinitionStmt(IdentifierReferenceTerm name) {
    super(ImmutableList.of());
    this.name = name;
    this.atomId = AtomDefinitionStmt.getNextGlobalAtomId();
  }

  public static int getNextGlobalAtomId() {
    return globalAtomCount++;
  }

  public void registerType(ScopedHeap scopedHeap) {
    // Simply need to validate that there's no other usage of this name already.
    if (scopedHeap.isIdentifierDeclared(this.name.identifier)) {
      this.name.logTypeError(ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.name.identifier));
    } else {
      // Simply place the type in the symbol table. Any references to this atom as a value, will need to have some
      // simple special handling to allow the reference.
      String thisModuleDisambiguator =
          ScopedHeap.getDefiningModuleDisambiguator(Optional.empty());
      scopedHeap.putIdentifierValueAsTypeDef(
          this.name.identifier,
          Types.AtomType.forNameAndDisambiguator(this.name.identifier, thisModuleDisambiguator),
          null
      );
      scopedHeap.initializeIdentifier(this.name.identifier);
      // Now I need to cache this atom. This codepath will be accessed more than once when compiling a module in order
      // to setup a synthetic symbol table for validating that exported procedures are actually defined in .claro files
      // so avoid adding this to the atom cache more than once here.
      if (!alreadyRegisteredAtom) {
        InternalStaticStateUtil.AtomDefinition_CACHE_INDEX_BY_MODULE_AND_ATOM_NAME.put(
            thisModuleDisambiguator, this.name.identifier, this.atomId);
        alreadyRegisteredAtom = true;
      }
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
    String definingModuleDisambiguator = ScopedHeap.getDefiningModuleDisambiguator(Optional.empty());
    if (InternalStaticStateUtil.AtomDefinition_CACHE_INDEX_BY_MODULE_AND_ATOM_NAME.build()
        .containsRow(definingModuleDisambiguator)) {
      return InternalStaticStateUtil.AtomDefinition_CACHE_INDEX_BY_MODULE_AND_ATOM_NAME.build()
          .rowMap()
          // Only want the atoms defined in this current module.
          .get(definingModuleDisambiguator)
          .entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getValue))
          .map(entry -> String.format(
              "$ClaroAtom.forTypeNameAndDisambiguator(\"%s\", \"%s\")",
              entry.getKey(),
              definingModuleDisambiguator
          ))
          .collect(Collectors.joining(", "));
    }
    return "/* No atoms defined in this compilation unit. Skipping Atom Cache init.*/";
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Nothing to codegen for an atom def.
    return null;
  }
}
