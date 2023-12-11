package com.claro.intermediate_representation.types;

public class ConcreteTypes {
  public static final Type INTEGER = ConcreteType.create(BaseType.INTEGER);
  public static final Type LONG = ConcreteType.create(BaseType.LONG);
  public static final Type FLOAT = ConcreteType.create(BaseType.FLOAT);
  public static final Type DOUBLE = ConcreteType.create(BaseType.DOUBLE);
  public static final Type STRING = ConcreteType.create(BaseType.STRING);
  public static final Type CHAR = ConcreteType.create(BaseType.CHAR);
  public static final Type BOOLEAN = ConcreteType.create(BaseType.BOOLEAN);
  public static final Type HTTP_RESPONSE = ConcreteType.create(BaseType.HTTP_RESPONSE);

  public static final Type MODULE = ConcreteType.create(BaseType.MODULE);
  // Special type that indicates that the compiler won't be able to determine this type answer until runtime at which
  // point it will potentially fail other runtime type checking. Anywhere where an "UNDECIDED" type is emitted by the
  // compiler we'll require a cast on the expr causing the indecision for the programmer to assert they know what's up.
  public static final Type UNDECIDED = ConcreteType.create(BaseType.UNDECIDED);
  // Special type that indicates that the compiler will *NEVER* be possibly able to determine this type at compile-time
  // specifically because *THERE WAS SOME SORT OF COMPILATION ERROR* caused by the user writing a bug. This will allow
  // compilation to continue after reaching some illegal user code by returning this "UNKNOWABLE" type.
  public static final Type UNKNOWABLE = ConcreteType.create(BaseType.UNKNOWABLE);
}
