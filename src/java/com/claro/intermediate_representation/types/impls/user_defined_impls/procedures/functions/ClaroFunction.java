package com.claro.intermediate_representation.types.impls.user_defined_impls.procedures.functions;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.impls.builtins_impls.ClaroBuiltinTypeImplementation;

public abstract class ClaroFunction<T> implements ClaroBuiltinTypeImplementation {

  public abstract T apply(Object... args);

  @Override
  public Type getClaroType() {
    throw new RuntimeException("TODO");
  }
}