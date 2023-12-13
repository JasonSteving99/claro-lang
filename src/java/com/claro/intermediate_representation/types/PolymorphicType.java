package com.claro.intermediate_representation.types;

// This interface represents any builtin Claro Type that is semantically "polymorphic" by nature, but will need to be
// converted to a monomorphic runtime representation for both efficiency and (TODO) simple runtime type introspection.
public interface PolymorphicType {
  String getConcreteJavaClassRepresentation();
}
