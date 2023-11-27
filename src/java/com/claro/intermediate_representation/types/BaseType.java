package com.claro.intermediate_representation.types;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Optional;

public enum BaseType {
  // This type exists solely for the sake of the compiler being able to put off some type decisions until it gathers
  // context that allows it to decide the actual type being expressed. E.g. empty list `[]` doesn't know its type until
  // context is imposed upon it for example in an assignment statement. E.g.2. (1, "one")[random(0, 1)] can't determine
  // the Expr type until runtime.
  UNDECIDED("UNDECIDED"),
  UNKNOWABLE("<UNKNOWABLE DUE TO PRIOR TYPE VALIDATION ERROR>"),
  ATOM("%s", "$ClaroAtom"),
  INTEGER("int", "Integer", Integer.class),
  FLOAT("float", "Double", Double.class),
  BOOLEAN("boolean", "Boolean", Boolean.class),
  STRING("string", "String", String.class),
  ARRAY,
  LIST("[%s]", "ClaroList<%s>"), // ArrayList.
  // Immutable heterogeneous Array.
  TUPLE("tuple<%s>", "ClaroTuple"),
  SET("{%s}", "ClaroSet<%s>"),
  MAP("{%s: %s}", "ClaroMap<%s, %s>"),
  BUILDER(
      "builder<%s>", // E.g. builder<Foo>
      "%s.Builder" // E.g. Foo.Builder
  ),
  // Structure of associated values.
  STRUCT(
      "struct{%s}", // E.g. struct{i: int, s: string}.
      "ClaroStruct"
  ),
  // TODO(steving) Decide whether Optional<T> should really just be a oneof Type defined something like oneof<T, None> where None is the empty struct None {}.
  OPTIONAL, // A type wrapping one of the other Types in a boolean indicating presence.
  // A union type that selects for one of a finite set of types.
  ONEOF("oneof<%s>", "Object", Object.class),

  /********************************************************************************************************************/
  // Function references. Remember, in Claro, Function specifically means a standalone procedure that doesn't depend on
  // any manner of instance values.

  // A `function` w/o any other modifier (e.g. consumer/provider) is one that takes args
  // and returns some value.
  FUNCTION(
      "function<%s -> %s>%s",
      "ClaroFunction<%s>",
      "public static final class $%s extends ClaroFunction<%s> {\n" +
      "  private final Types.ProcedureType.FunctionType claroType = %s;\n" +
      "  private final $%s %s = this;\n" +
      "  public %s apply(Object... $args) {\n" +
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
      "public static final $%s %s = new $%s();\n"
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
      "  public %s apply(Object... $args) {\n" +
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
      "public static final class $%s extends ClaroConsumerFunction {\n" +
      "  private final Types.ProcedureType.ConsumerType claroType = %s;\n" +
      "  final $%s %s = this;\n" +
      "  public void apply(Object... $args) {\n" +
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
      "public static final $%s %s = new $%s();\n"
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
      "  public void apply(Object... $args) {\n" +
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
      "public static final class $%s extends ClaroProviderFunction<%s> {\n" +
      "  private final Types.ProcedureType.ProviderType claroType = %s;\n" +
      "  final $%s %s = this;\n" +
      "  public %s apply() {\n" +
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
      "public static final $%s %s = new $%s();\n"
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

  USER_DEFINED_TYPE("%s", "$UserDefinedType<%s>"),

  // This type is interesting in the sense that there's actually no way to manually initialize an instance of this type
  // yourself. The language models this internally, and it is used solely in conjunction with generating an HttpClient.
  HTTP_SERVICE("%s", null),
  // In some ways this type is blessed abilities that no other type in the language has. In particular, the compiler
  // will validate that its parameterized type is in fact an HttpService. Other types can only simulate this behavior
  // via initializers.
  HTTP_CLIENT("HttpClient<%s>", "%s"),
  HTTP_SERVER("HttpServer<%s>", "com.claro.runtime_utilities.http.$ClaroHttpServer"),
  HTTP_RESPONSE("HttpResponse", "com.claro.intermediate_representation.types.impls.builtins_impls.http.$ClaroHttpResponse"),

  // Generic Type Param and Contract are Types that are only modeled internally and shouldn't appear in generated output
  // or in any other user observable way.
  $GENERIC_TYPE_PARAM("%s", (String) null, (String) null),
  $CONTRACT("contract %s<%s>", (String) null, (String) null),
  $CONTRACT_IMPLEMENTATION("$CONTRACT_IMPLEMENTATION", (String) null, (String) null),

  // These are synthetic types that exist solely for the sake of artificially creating a terminating sentinal type to
  // prevent unbounded recursion within a recursively defined type alias or user-defined type.
  ALIAS_SELF_REFERENCE,

  // This is a synthetic type that exists solely to represent the wrapped type for an opaque type imported from some dep
  // module that doesn't allow consuming modules to access its definition. This synthetic type is a placeholder whose
  // only intended usage is to signal whether this opaque type is deeply-immutable.
  $SYNTHETIC_OPAQUE_TYPE_WRAPPED_VALUE_TYPE,

  // This is an internal-only type that's used to hold the java type defined by a .claro_internal src file as the
  // implementation type of an exported opaque type. This is used to enable the creation of officially supported
  // modules providing access to any manner of pre-existing functionality from the Java ecosystem to the Claro ecosystem
  // in a curated manner. This obvious workaround allows a standardized approach to exposing Java types to Claro
  // programs in a way that avoids every single type requiring compiler intrinsics support (e.g. new lexing tokens,
  // grammar rules, AST nodes etc.). Use of this type is *extremely* unsafe, easily leading to an "unsound" type system
  // if used incorrectly, so there's no concrete plan to expose this to users in any way.
  $JAVA_TYPE;

  private final String javaSourceFmtStr;
  private final String claroCanonicalTypeNameFmtStr;
  // Fmt string for the definition of the new type.
  private final String javaNewTypeDefinitionStmtFmtStr;
  // Fmt string setting up the use of the new type which must be placed at the *very beginning* of the generated output.
  private final Optional<String> javaNewTypeStaticPreambleFormatStr;
  public final Optional<Class<?>> nativeJavaSourceImplClazz;

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
