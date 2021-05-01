package com.claro.examples.calculator_example.intermediate_representation.types;

public enum BaseType {
  // This type exists solely for the sake of the compiler being able to put off some type decisions until it gathers
  // context that allows it to decide the actual type being expressed. E.g. empty list `[]` doesn't know its type until
  // context is imposed upon it for example in an assignment statement.
  UNDECIDED,
  INTEGER("int", "Integer"),
  FLOAT("float", "Double"),
  BOOLEAN("boolean", "Boolean"),
  STRING("string", "String"),
  ARRAY,
  LIST("list<%s>", "ClaroList<%s>"), // Linked. TODO(steving) Make this linked.
  TUPLE, // Immutable Array.
  IMMUTABLE_MAP,
  MAP,
  STRUCT, // Structure of associated values.
  OPTIONAL, // A type wrapping one of the other Types in a boolean indicating presence.

  /********************************************************************************************************************/
  // Function references. Remember, in Claro, Function specifically means a standalone procedure that doesn't depend on
  // any manner of instance values.

  // A `function` w/o any other modifier (e.g. consumer/provider) is one that takes args
  // and returns some value.
  FUNCTION(
      "function<%s -> %s>",
      "ClaroFunction<%s>",
      // Allow scoping rules to behave the same as they appear in Claro source since Claro allows function defs anywhere.
      // so just define an inner class directly within the method where the generated java source lives.
      "final class $%s extends ClaroFunction<%s> {\n" +
      "  public %s apply(Object... args) {\n" +
      "%s\n" +
      "  }\n" +
      "  @Override\n" +
      "  public String toString() {\n" +
      "    return \"%s\";\n" +
      "  }\n" +
      "}\n" +
      // We just want a single instance of this function's wrapper class to exist... it's already obnoxious that it
      // exists at all.
      "final $%s %s = new $%s();\n"
  ),
  // A `consumer function` is one that takes args but doesn't have any return'd value. Consumer functions should have an
  // observable side-effect or they're literally just wasting electricity and global warming is entirely your fault.
  CONSUMER_FUNCTION(
      "consumer<%s>",
      "ClaroConsumerFunction",
      "final class $%s extends ClaroConsumerFunction {\n" +
      "  public void apply(Object... args) {\n" +
      "%s\n" +
      "  }\n" +
      "  @Override\n" +
      "  public String toString() {\n" +
      "    return \"%s\";\n" +
      "  }\n" +
      "}\n" +
      // We just want a single instance of this function's wrapper class to exist... it's already obnoxious that it
      // exists at all.
      "final $%s %s = new $%s();\n"
  ),
  // A `consumer function` is one that takes args but doesn't have any return'd value. Consumer functions should have an
  // observable side-effect or they're literally just wasting electricity and global warming is entirely your fault.
  PROVIDER_FUNCTION(
      "provider<%s>",
      "ClaroProviderFunction<%s>",
      "final class $%s extends ClaroProviderFunction<%s> {\n" +
      "  public %s apply() {\n" +
      "%s\n" +
      "  }\n" +
      "  @Override\n" +
      "  public String toString() {\n" +
      "    return \"%s\";\n" +
      "  }\n" +
      "}\n" +
      // We just want a single instance of this function's wrapper class to exist... it's already obnoxious that it
      // exists at all.
      "final $%s %s = new $%s();\n"
  ),
  /********************************************************************************************************************/

  OBJECT, // Struct with associated procedures.
  ;

  private final String javaSourceFmtStr;
  private final String claroCanonicalTypeNameFmtStr;
  private final String javaNewTypeDefinitionStmtFmtStr;

  BaseType(String typeName) {
    this.claroCanonicalTypeNameFmtStr = typeName;
    this.javaSourceFmtStr = typeName;
    this.javaNewTypeDefinitionStmtFmtStr = null;
  }

  BaseType(String claroCanonicalTypeNameFmtStr, String javaSourceFmtStr, String javaNewTypeDefinitionStmtFmtStr) {
    this.claroCanonicalTypeNameFmtStr = claroCanonicalTypeNameFmtStr;
    this.javaSourceFmtStr = javaSourceFmtStr;
    this.javaNewTypeDefinitionStmtFmtStr = javaNewTypeDefinitionStmtFmtStr;
  }

  BaseType(String claroCanonicalTypeNameFmtStr, String javaSourceFmtStr) {
    this.claroCanonicalTypeNameFmtStr = claroCanonicalTypeNameFmtStr;
    this.javaSourceFmtStr = javaSourceFmtStr;
    this.javaNewTypeDefinitionStmtFmtStr = null;
  }

  BaseType() {
    this.claroCanonicalTypeNameFmtStr = null;
    this.javaSourceFmtStr = null;
    this.javaNewTypeDefinitionStmtFmtStr = null;
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

  public String getJavaNewTypeDefinitionStmtFmtStr() {
    if (this.javaNewTypeDefinitionStmtFmtStr == null) {
      throw new UnsupportedOperationException(
          String.format("Internal Compiler Error: The BaseType <%s> is not yet supported in Claro!", this));
    }
    return javaNewTypeDefinitionStmtFmtStr;
  }
}
