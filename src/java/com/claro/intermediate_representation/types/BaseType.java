package com.claro.intermediate_representation.types;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Optional;

public enum BaseType {
  // This type exists solely for the sake of the compiler being able to put off some type decisions until it gathers
  // context that allows it to decide the actual type being expressed. E.g. empty list `[]` doesn't know its type until
  // context is imposed upon it for example in an assignment statement. E.g.2. (1, "one")[random(0, 1)] can't determine
  // the Expr type until runtime.
  UNDECIDED("UNDECIDED"),
  INTEGER("int", "Integer", Integer.class),
  FLOAT("float", "Double", Double.class),
  BOOLEAN("boolean", "Boolean", Boolean.class),
  STRING("string", "String", String.class),
  ARRAY,
  LIST("list<%s>", "ClaroList<%s>"), // Linked. TODO(steving) Make this linked.
  // Immutable heterogeneous Array.
  TUPLE("tuple<%s>", "ClaroTuple"),
  IMMUTABLE_MAP,
  MAP,
  BUILDER(
      "builder<%s>", // E.g. builder<Foo>
      "%s.Builder" // E.g. Foo.Builder
  ),
  // Structure of associated values.
  STRUCT(
      "%s{%s}", // E.g. struct{i: int, s: string}.
      "%s", // E.g. Foo.
      // We're defining structs in java source using Lombok's @Data to inherit hashcode and equality checking for free.
      "" +
      // TODO(steving) Standardize the toString representation with the interpreted output..
      "@ToString(includeFieldNames=true)\n" +
      "@Data\n" +
      "@EqualsAndHashCode(callSuper=false)\n" +
      "@Builder(builderClassName = \"Builder\", builderMethodName = \"\")\n" +
      "static class %s extends ClaroUserDefinedTypeImplementation {\n" +
      "  public static final Types.StructType claroType = %s;\n" +
      "%s\n" +
      "  public static Builder builder() {\n" +
      "    return new Builder();\n" +
      "  }\n" +
      "  public Type getClaroType() {\n" +
      "    return claroType;\n" +
      "  }\n" +
      "  public static class Builder implements ClaroUserDefinedTypeImplementationBuilder<%s> {\n" +
      "    public Type getClaroType() {\n" +
      "      return Types.BuilderType.forStructType(claroType);\n" +
      "    }\n" +
      "  }\n" +
      "}\n\n"
  ),
  // Immutable structure of associated values.
  IMMUTABLE_STRUCT(
      "immutable %s{%s}", // E.g. immutable struct{i: int, s: string}.
      "%s", // E.g. ClaroImmutableStruct.
      // We're defining structs in java source using Lombok's @Value to inherit immutability and equality checking free.
      "" +
      // TODO(steving) Standardize the toString representation with the interpreted output..
      "@ToString(includeFieldNames=true)\n" +
      "@Value\n" +
      "@EqualsAndHashCode(callSuper=false)\n" +
      "@Builder(builderClassName = \"Builder\", builderMethodName = \"\")\n" +
      "static class %s extends ClaroUserDefinedTypeImplementation {\n" +
      "  public static final Types.StructType claroType = %s;\n" +
      "%s\n" +
      "  public static Builder builder() {\n" +
      "    return new Builder();\n" +
      "  }\n" +
      "  public Type getClaroType() {\n" +
      "    return claroType;\n" +
      "  }\n" +
      "  public static class Builder implements ClaroUserDefinedTypeImplementationBuilder<%s> {\n" +
      "    public Type getClaroType() {\n" +
      "      return Types.BuilderType.forStructType(claroType);\n" +
      "    }\n" +
      "  }\n" +
      "}\n\n"
  ),
  // TODO(steving) Decide whether Optional<T> should really just be a oneof Type defined something like oneof<T, None> where None is the empty struct None {}.
  OPTIONAL, // A type wrapping one of the other Types in a boolean indicating presence.
  ONEOF, // A quasi union type that selects for one of a finite set of types.

  /********************************************************************************************************************/
  // Function references. Remember, in Claro, Function specifically means a standalone procedure that doesn't depend on
  // any manner of instance values.

  // A `function` w/o any other modifier (e.g. consumer/provider) is one that takes args
  // and returns some value.
  FUNCTION(
      "function<%s -> %s>",
      "ClaroFunction<%s>",
      "private static final class $%s extends ClaroFunction<%s> {\n" +
      "  private final Types.ProcedureType.FunctionType claroType = %s;\n" +
      "  private final $%s %s = this;\n" +
      "  public %s apply(Object... args) {\n" +
      "%s\n" +
      "  }\n" +
      "\n%s\n" +
      "  @Override\n" +
      "  public Type getClaroType() {\n" +
      "    return claroType;\n" +
      "  }\n" +
      "  @Override\n" +
      "  public String toString() {\n" +
      "    return \"%s\";\n" +
      "  }\n" +
      "}\n",
      // We just want a single instance of this function's wrapper class to exist... it's already obnoxious that it
      // exists at all.
      "private static final $%s %s = new $%s();\n"
  ),
  // Declare a special internal type for lambda forms of functions, but don't expose lambdas as a ClaroType,
  // callers don't need to know.
  LAMBDA_FUNCTION(
      "function<%s -> %s>",
      "ClaroFunction<%s>",
      "final class $%s extends ClaroFunction<%s> {\n" +
      "  private final Types.ProcedureType.FunctionType claroType = %s;\n" +
      "  private final $%s %s = this;\n" +
      "%s\n" + // Add final instance variables for any/all captured variables.
      "  $%s(%s) { \n" +
      "%s" + // Instantiate instance variables for any/all captured variables.
      "  }\n" +
      "  public %s apply(Object... args) {\n" +
      "%s\n" +
      "  }\n" +
      "  @Override\n" +
      "  public Type getClaroType() {\n" +
      "    return claroType;\n" +
      "  }\n" +
      "  @Override\n" +
      "  public String toString() {\n" +
      "    return \"%s\";\n" +
      "  }\n" +
      "}\n" +
      // We just want a single instance of this lambda function's wrapper class to exist... it's already obnoxious that
      // it exists at all.
      "final $%s %s = new $%s(%s);\n"
  ),
  // A `consumer function` is one that takes args but doesn't have any return'd value. Consumer functions should have an
  // observable side-effect or they're literally just wasting electricity and global warming is entirely your fault.
  CONSUMER_FUNCTION(
      "consumer<%s>",
      "ClaroConsumerFunction",
      "private static final class $%s extends ClaroConsumerFunction {\n" +
      "  private final Types.ProcedureType.ConsumerType claroType = %s;\n" +
      "  final $%s %s = this;\n" +
      "  public void apply(Object... args) {\n" +
      "%s\n" +
      "  }\n" +
      "  @Override\n" +
      "  public Type getClaroType() {\n" +
      "    return claroType;\n" +
      "  }\n" +
      "  @Override\n" +
      "  public String toString() {\n" +
      "    return \"%s\";\n" +
      "  }\n" +
      "}\n",
      // We just want a single instance of this function's wrapper class to exist... it's already obnoxious that it
      // exists at all.
      "private static final $%s %s = new $%s();\n"
  ),
  // Declare a special internal type for lambda forms of functions, but don't expose lambdas as a ClaroType,
  // callers don't need to know.
  LAMBDA_CONSUMER_FUNCTION(
      "consumer<%s>",
      "ClaroConsumerFunction",
      "final class $%s extends ClaroConsumerFunction {\n" +
      "  private final Types.ProcedureType.ConsumerType claroType = %s;\n" +
      "  final $%s %s = this;\n" +
      "%s\n" + // Add final instance variables for any/all captured variables.
      "  $%s(%s) { \n" +
      "%s" + // Instantiate instance variables for any/all captured variables.
      "  }\n" +
      "  public void apply(Object... args) {\n" +
      "%s\n" +
      "  }\n" +
      "  @Override\n" +
      "  public Type getClaroType() {\n" +
      "    return claroType;\n" +
      "  }\n" +
      "  @Override\n" +
      "  public String toString() {\n" +
      "    return \"%s\";\n" +
      "  }\n" +
      "}\n" +
      // We just want a single instance of this lambda function's wrapper class to exist... it's already obnoxious that
      // it exists at all.
      "final $%s %s = new $%s(%s);\n"
  ),
  // A `provider function` is one that takes no args but return's a value. A provider function is truly only useful if
  // it observes some state in the outside world, for example by taking input from a user in some way. If you don't
  // observe state in the outside world you should've just created a constant variable...be better.
  PROVIDER_FUNCTION(
      "provider<%s>",
      "ClaroProviderFunction<%s>",
      "private static final class $%s extends ClaroProviderFunction<%s> {\n" +
      "  private final Types.ProcedureType.ProviderType claroType = %s;\n" +
      "  final $%s %s = this;\n" +
      "  public %s apply() {\n" +
      "%s\n" +
      "  }\n" +
      "  @Override\n" +
      "  public Type getClaroType() {\n" +
      "    return claroType;\n" +
      "  }\n" +
      "  @Override\n" +
      "  public String toString() {\n" +
      "    return \"%s\";\n" +
      "  }\n" +
      "}\n",
      // We just want a single instance of this function's wrapper class to exist... it's already obnoxious that it
      // exists at all.
      "private static final $%s %s = new $%s();\n"
  ),
  // Declare a special internal type for lambda forms of functions, but don't expose lambdas as a ClaroType,
  // callers don't need to know.
  LAMBDA_PROVIDER_FUNCTION(
      "provider<%s>",
      "ClaroProviderFunction<%s>",
      "final class $%s extends ClaroProviderFunction<%s> {\n" +
      "  private final Types.ProcedureType.ProviderType claroType = %s;\n" +
      "  final $%s %s = this;\n" +
      "%s\n" + // Add final instance variables for any/all captured variables.
      "  $%s(%s) { \n" +
      "%s" + // Instantiate instance variables for any/all captured variables.
      "  }\n" +
      "  public %s apply() {\n" +
      "%s\n" +
      "  }\n" +
      "  @Override\n" +
      "  public Type getClaroType() {\n" +
      "    return claroType;\n" +
      "  }\n" +
      "  @Override\n" +
      "  public String toString() {\n" +
      "    return \"%s\";\n" +
      "  }\n" +
      "}\n" +
      // We just want a single instance of this lambda function's wrapper class to exist... it's already obnoxious that
      // it exists at all.
      "final $%s %s = new $%s(%s);\n"
  ),
  /********************************************************************************************************************/

  // Futures are a natively represented type in Claro so that we can make certain levels of thread safety on builtin
  // concurrency facilities.
  FUTURE("future<%s>", "ClaroFuture<%s>", ListenableFuture.class),

  // Module is a Type that's only modeled internally and shouldn't appear in generated output or in any other user
  // observable way since I don't want Modules to be things that can be passed around like arbitrary data.
  MODULE("module"),
  OBJECT, // Struct with associated procedures.
  TYPE, // This is a meta-type that represents another Type.
  ;

  private final String javaSourceFmtStr;
  private final String claroCanonicalTypeNameFmtStr;
  // Fmt string for the definition of the new type.
  private final String javaNewTypeDefinitionStmtFmtStr;
  // Fmt string setting up the use of the new type which must be placed at the *very beginning* of the generated output.
  private final Optional<String> javaNewTypeStaticPreambleFormatStr;
  private final Optional<Class<?>> nativeJavaSourceImplClazz;

  BaseType(String typeName) {
    this.claroCanonicalTypeNameFmtStr = typeName;
    this.javaSourceFmtStr = typeName;
    this.javaNewTypeDefinitionStmtFmtStr = null;
    this.javaNewTypeStaticPreambleFormatStr = Optional.empty();
    this.nativeJavaSourceImplClazz = Optional.empty();
  }

  BaseType(
      String claroCanonicalTypeNameFmtStr,
      String javaSourceFmtStr,
      String javaNewTypeDefinitionStmtFmtStr) {
    this.claroCanonicalTypeNameFmtStr = claroCanonicalTypeNameFmtStr;
    this.javaSourceFmtStr = javaSourceFmtStr;
    this.javaNewTypeDefinitionStmtFmtStr = javaNewTypeDefinitionStmtFmtStr;
    this.javaNewTypeStaticPreambleFormatStr = Optional.empty();
    this.nativeJavaSourceImplClazz = Optional.empty();
  }

  BaseType(
      String claroCanonicalTypeNameFmtStr,
      String javaSourceFmtStr,
      String javaNewTypeDefinitionStmtFmtStr,
      String javaNewTypeStaticPreambleFormatStr) {
    this.claroCanonicalTypeNameFmtStr = claroCanonicalTypeNameFmtStr;
    this.javaSourceFmtStr = javaSourceFmtStr;
    this.javaNewTypeDefinitionStmtFmtStr = javaNewTypeDefinitionStmtFmtStr;
    this.javaNewTypeStaticPreambleFormatStr = Optional.of(javaNewTypeStaticPreambleFormatStr);
    this.nativeJavaSourceImplClazz = Optional.empty();
  }

  BaseType(String claroCanonicalTypeNameFmtStr, String javaSourceFmtStr, Class<?> nativeJavaSourceImplClazz) {
    this.claroCanonicalTypeNameFmtStr = claroCanonicalTypeNameFmtStr;
    this.javaSourceFmtStr = javaSourceFmtStr;
    this.javaNewTypeDefinitionStmtFmtStr = null;
    this.javaNewTypeStaticPreambleFormatStr = Optional.empty();
    this.nativeJavaSourceImplClazz = Optional.of(nativeJavaSourceImplClazz);
  }

  BaseType(String claroCanonicalTypeNameFmtStr, String javaSourceFmtStr) {
    this.claroCanonicalTypeNameFmtStr = claroCanonicalTypeNameFmtStr;
    this.javaSourceFmtStr = javaSourceFmtStr;
    this.javaNewTypeDefinitionStmtFmtStr = null;
    this.javaNewTypeStaticPreambleFormatStr = Optional.empty();
    this.nativeJavaSourceImplClazz = Optional.empty();
  }

  BaseType() {
    this.claroCanonicalTypeNameFmtStr = null;
    this.javaSourceFmtStr = null;
    this.javaNewTypeDefinitionStmtFmtStr = null;
    this.javaNewTypeStaticPreambleFormatStr = Optional.empty();
    this.nativeJavaSourceImplClazz = Optional.empty();
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

  public String getJavaNewTypeStaticPreambleFormatStr() {
    if (!hasJavaNewTypeStaticPreambleFormatStr()) {
      throw new UnsupportedOperationException(
          String.format("Internal Compiler Error: The BaseType <%s> is not yet supported in Claro!", this));
    }
    return javaNewTypeStaticPreambleFormatStr.get();
  }

  // For this field, allow some procedures (lambdas) to not actually support this when others (explicit functions)
  // don't, but allow them a cleaner way than an exception to determine that.
  public boolean hasJavaNewTypeStaticPreambleFormatStr() {
    return this.javaNewTypeStaticPreambleFormatStr.isPresent();
  }

  public Class<?> getNativeJavaSourceImplClazz() {
    return this.nativeJavaSourceImplClazz.get();
  }
}
