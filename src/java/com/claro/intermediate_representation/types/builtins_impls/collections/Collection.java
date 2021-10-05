package com.claro.intermediate_representation.types.builtins_impls.collections;

import com.claro.intermediate_representation.types.builtins_impls.ClaroBuiltinTypeImplementation;

public interface Collection extends ClaroBuiltinTypeImplementation {
  public <T> T getElement(int i);

  public int length();
}
