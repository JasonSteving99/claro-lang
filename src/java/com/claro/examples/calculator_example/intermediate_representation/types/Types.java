package com.claro.examples.calculator_example.intermediate_representation.types;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

public final class Types {
  public static final Type INTEGER = ConcreteType.create(BaseType.INTEGER);
  public static final Type DOUBLE = ConcreteType.create(BaseType.DOUBLE);
  public static final Type STRING = ConcreteType.create(BaseType.STRING);
  public static final Type BOOLEAN = ConcreteType.create(BaseType.BOOLEAN);

  @AutoValue
  public abstract static class ListType extends Type {
    public static ListType forValueType(Type valueType) {
      return new AutoValue_Types_ListType(BaseType.LIST, ImmutableMap.of("values", valueType));
    }
  }
}
