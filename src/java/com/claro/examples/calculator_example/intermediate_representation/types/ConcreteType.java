package com.claro.examples.calculator_example.intermediate_representation.types;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

@AutoValue
abstract class ConcreteType extends Type {
  public static ConcreteType create(BaseType baseType) {
    return new AutoValue_ConcreteType(baseType, ImmutableMap.of());
  }
}
