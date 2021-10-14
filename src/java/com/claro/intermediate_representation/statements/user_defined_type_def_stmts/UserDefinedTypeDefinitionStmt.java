package com.claro.intermediate_representation.statements.user_defined_type_def_stmts;

import com.claro.compiler_backends.interpreted.ScopedHeap;

public interface UserDefinedTypeDefinitionStmt {
  void registerTypeProvider(ScopedHeap scopedHeap);
}
