package com.claro.intermediate_representation.statements.user_defined_type_def_stmts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;

public class UnwrappersBlockStmt extends Stmt {
  private final String unwrappedTypeName;
  private final ImmutableList<ProcedureDefinitionStmt> unwrapperProcedureDefs;

  public UnwrappersBlockStmt(String unwrappedTypeName, ImmutableList<ProcedureDefinitionStmt> unwrapperProcedureDefs) {
    super(ImmutableList.of());
    this.unwrappedTypeName = unwrappedTypeName;
    this.unwrapperProcedureDefs = unwrapperProcedureDefs;
  }

  public void registerProcedureTypeProviders(ScopedHeap scopedHeap) {
    for (ProcedureDefinitionStmt proc : this.unwrapperProcedureDefs) {
      proc.registerProcedureTypeProvider(scopedHeap);
      InternalStaticStateUtil.InitializersBlockStmt_unwrappersByUnwrappedType
          .put(this.unwrappedTypeName, proc.procedureName);
    }
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!scopedHeap.isIdentifierDeclared(this.unwrappedTypeName)) {
      throw ClaroTypeException.forIllegalInitializersBlockReferencingUndeclaredInitializedType(this.unwrappedTypeName);
    }
    Type validatedInitializedType =
        TypeProvider.Util.getTypeByName(this.unwrappedTypeName, true).resolveType(scopedHeap);
    if (!validatedInitializedType.baseType().equals(BaseType.USER_DEFINED_TYPE)) {
      throw ClaroTypeException.forIllegalInitializersBlockReferencingNonUserDefinedType(
          this.unwrappedTypeName, validatedInitializedType);
    }

    // Do type validation of all the initializer procedures. Note, that these procedures will be uniquely allowed to
    // make use of the auto-generated default constructor for the initialized type. This is by design, as this allows
    // the initializer procedures to essentially impose semantics on the type.
    for (ProcedureDefinitionStmt proc : unwrapperProcedureDefs) {
      // TODO(steving) Come back in the future and make it possible to progress past this if it throws an exception so
      //  that all the procedures can be validated still.
      proc.assertExpectedExprTypes(scopedHeap);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
    for (ProcedureDefinitionStmt proc : unwrapperProcedureDefs) {
      res = res.createMerged(proc.generateJavaSourceOutput(scopedHeap));
    }
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    for (ProcedureDefinitionStmt proc : unwrapperProcedureDefs) {
      proc.generateInterpretedOutput(scopedHeap);
    }

    // These are just the proc definitions (Stmts), not the call-sites (Exprs), return no value.
    return null;
  }
}
