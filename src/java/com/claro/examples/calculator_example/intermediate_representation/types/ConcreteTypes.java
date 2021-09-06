package com.claro.examples.calculator_example.intermediate_representation.types;

public class ConcreteTypes {
  public static final Type INTEGER = ConcreteType.create(BaseType.INTEGER);
  public static final Type FLOAT = ConcreteType.create(BaseType.FLOAT);
  public static final Type STRING = ConcreteType.create(BaseType.STRING);
  public static final Type BOOLEAN = ConcreteType.create(BaseType.BOOLEAN);

  // Special type that indicates that the compiler won't be able to determine this type answer until runtime at which
  // point it will potentially fail other runtime type checking. Anywhere where an "UNDECIDED" type is emitted by the
  // compiler we'll require a cast on the expr causing the indecision for the programmer to assert they know what's up.
  public static final Type UNDECIDED = ConcreteType.create(BaseType.UNDECIDED);
}
