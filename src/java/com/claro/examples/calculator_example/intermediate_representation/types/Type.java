package com.claro.examples.calculator_example.intermediate_representation.types;

import com.google.common.collect.ImmutableMap;

public abstract class Type {
  public abstract BaseType baseType();

  // name -> Type.
  public abstract ImmutableMap<String, Type> parameterizedTypeArgs();
}
