package com.claro.examples.calculator_example.intermediate_representation.types;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

@AutoValue
abstract class ParameterizedType extends Type {
  public static ParameterizedType create(BaseType baseType, ImmutableMap<String, Type> parameterizedTypeArgs) {
    return new AutoValue_ParameterizedType(baseType, parameterizedTypeArgs);
  }
}
