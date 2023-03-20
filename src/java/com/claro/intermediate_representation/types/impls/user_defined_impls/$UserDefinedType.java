package com.claro.intermediate_representation.types.impls.user_defined_impls;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

public class $UserDefinedType<T> extends ClaroUserDefinedTypeImplementation {

  private final String name;
  private final Type wrappedType;
  public final T wrappedValue;


  public $UserDefinedType(String name, Type wrappedType, T wrappedValue) {
    this.name = name;
    this.wrappedType = wrappedType;
    this.wrappedValue = wrappedValue;
  }

  @Override
  public Type getClaroType() {
    return Types.UserDefinedType.forTypeNameAndWrappedType(this.name, this.wrappedType);
  }

  @Override
  public String toString() {
    return String.format("%s(%s)", this.name, this.wrappedValue.toString());
  }
}
