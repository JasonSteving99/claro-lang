package com.claro.examples.calculator_example.intermediate_representation.types;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

public final class Types {
  public static final Type INTEGER = ConcreteType.create(BaseType.INTEGER);
  public static final Type FLOAT = ConcreteType.create(BaseType.FLOAT);
  public static final Type STRING = ConcreteType.create(BaseType.STRING);
  public static final Type BOOLEAN = ConcreteType.create(BaseType.BOOLEAN);

  // Special type not to actually make it out of the type-checking phase of the compiler.
  public static final Type UNDECIDED = ConcreteType.create(BaseType.UNDECIDED);

  @AutoValue
  public abstract static class ListType extends Type {
    public static ListType forValueType(Type valueType) {
      return new AutoValue_Types_ListType(BaseType.LIST, ImmutableMap.of("values", valueType));
    }
  }
}
