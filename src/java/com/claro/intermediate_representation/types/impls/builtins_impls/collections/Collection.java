package com.claro.intermediate_representation.types.impls.builtins_impls.collections;

import com.claro.intermediate_representation.types.impls.builtins_impls.ClaroBuiltinTypeImplementation;

public interface Collection extends ClaroBuiltinTypeImplementation {
  <T> T getElement(int i);

  int length();
}
