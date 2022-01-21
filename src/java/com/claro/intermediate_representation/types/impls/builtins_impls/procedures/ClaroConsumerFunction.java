package com.claro.intermediate_representation.types.impls.builtins_impls.procedures;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.impls.builtins_impls.ClaroBuiltinTypeImplementation;

public abstract class ClaroConsumerFunction<T> implements ClaroBuiltinTypeImplementation {

  public abstract void apply(Object... args);

  @Override
  public abstract Type getClaroType();
}
