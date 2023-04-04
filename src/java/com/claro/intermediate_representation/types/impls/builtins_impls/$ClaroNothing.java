package com.claro.intermediate_representation.types.impls.builtins_impls;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

public class $ClaroNothing implements ClaroBuiltinTypeImplementation {
  public final static $ClaroNothing SINGLETON_NOTHING = new $ClaroNothing();

  @Override
  public Type getClaroType() {
    return Types.NothingType.get();
  }

  @Override
  public String toString() {
    return "nothing";
  }
}
