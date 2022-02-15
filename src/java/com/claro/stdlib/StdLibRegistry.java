package com.claro.stdlib;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableTable;

public class StdLibRegistry {
  public static final ImmutableTable<String, Type, Types.ProcedureType.ProcedureWrapper> stdLibProcedureTypes =
      ImmutableTable.of(
          Exec.getProcedureName(), Exec.getProcedureType(), Exec.execProcedureWrapper
      );
}
