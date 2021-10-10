package com.claro.intermediate_representation.types.impls.user_defined_impls.structs;

import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public final class ImmutableClaroStruct extends ClaroStruct<ImmutableClaroStruct> {

  public ImmutableClaroStruct(Types.StructType structType, Map<String, Object> delegateFieldMap) {
    super(structType, ImmutableMap.copyOf(delegateFieldMap));
  }

  @Override
  public void set(String identifier, Object value) {
    throw new UnsupportedOperationException(
        "Internal Compiler Error: Immutable Struct type does not support field reassignment after instantiation." +
        " The interpreter is in an invalid state if this is ever thrown.");
  }
}
