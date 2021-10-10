package com.claro.intermediate_representation.types.impls.user_defined_impls;

import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.impls.ClaroTypeImplementation;

public abstract class ClaroUserDefinedTypeImplementation implements ClaroTypeImplementation {
  public interface ClaroUserDefinedTypeImplementationBuilder<T extends ClaroUserDefinedTypeImplementation>
      extends ClaroTypeImplementation {
    T build() throws ClaroTypeException;
  }
}
