package com.claro.examples.calculator_example.intermediate_representation.types;

public enum BaseType {
  // TODO(steving) Determine whether "UNDECIDED" really belongs.
  // This type exists solely for the sake of the compiler being able to put off some type decisions until it gathers
  // context that allows it to decide the actual type being expressed.
  UNDECIDED,
  INTEGER("Integer"),
  DOUBLE("Double"),
  BOOLEAN("boolean"),
  STRING("String"),
  ARRAY,
  LIST("ClaroList<%s>"), // Linked. TODO(steving) Make this linked.
  TUPLE, // Immutable Array.
  IMMUTABLE_MAP,
  MAP,
  STRUCT, // Structure of associated values.
  OPTIONAL, // A type wrapping one of the other Types in a boolean indicating presence.
  OBJECT, // Struct with associated procedures.
  ;

  private final String javaSourceFmtStr;

  BaseType(String javaSourceFmtStr) {
    this.javaSourceFmtStr = javaSourceFmtStr;
  }

  BaseType() {
    this.javaSourceFmtStr = null;
  }

  public String getJavaSourceFmtStr() {
    if (this.javaSourceFmtStr == null) {
      throw new UnsupportedOperationException(
          String.format("Internal Compiler Error: The BaseType <%s> is not yet supported in Claro!", this));
    }
    return this.javaSourceFmtStr;
  }
}
