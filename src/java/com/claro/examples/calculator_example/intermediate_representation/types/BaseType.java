package com.claro.examples.calculator_example.intermediate_representation.types;

public enum BaseType {
  // TODO(steving) Determine whether "UNDECIDED" really belongs.
  // This type exists solely for the sake of the compiler being able to put off some type decisions until it gathers
  // context that allows it to decide the actual type being expressed.
  UNDECIDED,
  INTEGER,
  DOUBLE,
  BOOLEAN,
  STRING,
  ARRAY,
  LIST, // Linked.
  TUPLE, // Immutable Array.
  IMMUTABLE_MAP,
  MAP,
  STRUCT, // Structure of associated values.
  OPTIONAL, // A type wrapping one of the other Types in a boolean indicating presence.
  OBJECT, // Struct with associated procedures.
}
