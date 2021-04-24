package com.claro.examples.calculator_example.intermediate_representation.types;

public enum BaseType {
  // This type exists solely for the sake of the compiler being able to put off some type decisions until it gathers
  // context that allows it to decide the actual type being expressed. E.g. empty list `[]` doesn't know its type until
  // context is imposed upon it for example in an assignment statement.
  UNDECIDED,
  INTEGER("int", "Integer"),
  FLOAT("float", "Double"),
  BOOLEAN("boolean"),
  STRING("string", "String"),
  ARRAY,
  LIST("list<%s>", "ClaroList<%s>"), // Linked. TODO(steving) Make this linked.
  TUPLE, // Immutable Array.
  IMMUTABLE_MAP,
  MAP,
  STRUCT, // Structure of associated values.
  OPTIONAL, // A type wrapping one of the other Types in a boolean indicating presence.

  // Function reference. Remember, in Claro, Function specifically means a standalone procedure that doesn't depend on
  // any manner of instance values.
  FUNCTION(
      "function<%s -> %s>",
      // Allow scoping rules to behave the same as they appear in Claro source since Claro allows function defs anywhere.
      // so just define an inner class directly within the method where the generated java source lives.
      "final class $%s {\n" +
      "  %s apply(%s) {\n" +
      "%s\n" +
      "  }\n" +
      "}\n" +
      // We just want a single instance of this function's wrapper class to exist... it's already obnoxious that it
      // exists at all.
      "final $%s %s = new $%s();\n"
  ),

  OBJECT, // Struct with associated procedures.
  ;

  private final String javaSourceFmtStr;
  private final String claroCanonicalTypeNameFmtStr;


  BaseType(String typeName) {
    this.claroCanonicalTypeNameFmtStr = typeName;
    this.javaSourceFmtStr = typeName;
  }

  BaseType(String claroCanonicalTypeNameFmtStr, String javaSourceFmtStr) {
    this.claroCanonicalTypeNameFmtStr = claroCanonicalTypeNameFmtStr;
    this.javaSourceFmtStr = javaSourceFmtStr;
  }

  BaseType() {
    this.claroCanonicalTypeNameFmtStr = null;
    this.javaSourceFmtStr = null;
  }

  public String getJavaSourceFmtStr() {
    if (this.javaSourceFmtStr == null) {
      throw new UnsupportedOperationException(
          String.format("Internal Compiler Error: The BaseType <%s> is not yet supported in Claro!", this));
    }
    return this.javaSourceFmtStr;
  }

  public String getClaroCanonicalTypeNameFmtStr() {
    if (this.claroCanonicalTypeNameFmtStr == null) {
      throw new UnsupportedOperationException(
          String.format("Internal Compiler Error: The BaseType <%s> is not yet supported in Claro!", this));
    }
    return claroCanonicalTypeNameFmtStr;
  }
}
