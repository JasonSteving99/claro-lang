package com.claro.examples.calculator_example.intermediate_representation.types;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

@AutoValue
public abstract class ConcreteType extends Type {
  public static ConcreteType create(BaseType baseType) {
    return new AutoValue_ConcreteType(baseType, ImmutableMap.of());
  }

  @Override
  public String getJavaSourceClaroType() {
    return String.format("ConcreteType.create(BaseType.%s)", this.baseType().toString());
  }
}
