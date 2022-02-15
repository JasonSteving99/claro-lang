package com.claro.stdlib;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;

public class StdLibUtil {

  public static void registerIdentifiers(ScopedHeap scopedHeap) {
    for (Table.Cell<String, Type, Types.ProcedureType.ProcedureWrapper> stdLibProcedure
        : StdLibRegistry.stdLibProcedureTypes.cellSet()) {
      registerStdLibProcedure(
          scopedHeap, stdLibProcedure.getRowKey(), stdLibProcedure.getColumnKey(), stdLibProcedure.getValue());
    }
  }

  private static void registerStdLibProcedure(
      ScopedHeap scopedHeap,
      String procedureName,
      Type procedureType,
      Types.ProcedureType.ProcedureWrapper procedureWrapper) {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(procedureName),
        String.format(
            "Internal Compiler Error: Unexpected redeclaration of std-lib procedure %s %s.",
            procedureType,
            procedureName
        )
    );

    // Finally mark the function declared and initialized within the original calling scope.
    scopedHeap.putIdentifierValue(procedureName, procedureType, procedureWrapper);
  }
}
