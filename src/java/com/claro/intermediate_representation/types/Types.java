package com.claro.intermediate_representation.types;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

// TODO(steving) This class needs refactoring into a standalone package.
public final class Types {
  public static final Type INTEGER = ConcreteType.create(BaseType.INTEGER);
  public static final Type FLOAT = ConcreteType.create(BaseType.FLOAT);
  public static final Type STRING = ConcreteType.create(BaseType.STRING);
  public static final Type BOOLEAN = ConcreteType.create(BaseType.BOOLEAN);

  // Special type that indicates that the compiler won't be able to determine this type answer until runtime at which
  // point it will potentially fail other runtime type checking. Anywhere where an "UNDECIDED" type is emitted by the
  // compiler we'll require a cast on the expr causing the indecision for the programmer to assert they know what's up.
  public static final Type UNDECIDED = ConcreteType.create(BaseType.UNDECIDED);

  public interface Collection {
    Type getElementType();
  }

  @AutoValue
  public abstract static class ListType extends Type implements Collection {
    private static final String PARAMETERIZED_TYPE_KEY = "$values";

    public static ListType forValueType(Type valueType) {
      return new AutoValue_Types_ListType(BaseType.LIST, ImmutableMap.of(PARAMETERIZED_TYPE_KEY, valueType));
    }

    @Override
    public Type getElementType() {
      return this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_KEY);
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.ListType.forValueType(%s)",
          this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_KEY).getJavaSourceClaroType()
      );
    }
  }

  @AutoValue
  public abstract static class TupleType extends Type implements Collection {

    public abstract ImmutableList<Type> getValueTypes();

    public static TupleType forValueTypes(ImmutableList<Type> valueTypes) {
      return new AutoValue_Types_TupleType(BaseType.TUPLE, ImmutableMap.of(), valueTypes);
    }

    @Override
    public Type getElementType() {
      // We literally have no way of determining this type at compile time without knowing which index is being
      // referenced so instead we'll mark this as UNDECIDED.
      return UNDECIDED;
    }

    @Override
    public String toString() {
      return String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          getValueTypes().stream().map(Type::toString).collect(Collectors.joining(", "))
      );
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.TupleType.forValueTypes(ImmutableList.of(%s))",
          Joiner.on(", ")
              .join(this.getValueTypes()
                        .stream()
                        .map(Type::getJavaSourceClaroType)
                        .collect(ImmutableList.toImmutableList()))
      );
    }
  }

  public abstract static class StructType extends Type {

    public abstract String getName();

    public abstract ImmutableMap<String, Type> getFieldTypes();

    private static TypeProvider forFieldTypeProvidersMap(
        ImmutableMap<String, TypeProvider> fieldTypeProvidersMap, String structName, boolean immutable) {
      return (scopedHeap) -> {
        ImmutableMap<String, Type> fieldTypesMap =
            TypeProvider.Util.resolveTypeProviderMap(scopedHeap, fieldTypeProvidersMap);
        Types.StructType resultStructType =
            immutable ?
            Types.StructType.ImmutableStructType.forFieldTypes(structName, fieldTypesMap) :
            Types.StructType.MutableStructType.forFieldTypes(structName, fieldTypesMap);
        scopedHeap.putIdentifierValue(structName, resultStructType, null);
        return resultStructType;
      };
    }

    @Override
    public String toString() {
      return String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          getName(),
          getFieldTypes().entrySet().stream()
              .map(stringTypeEntry -> String.format("%s: %s", stringTypeEntry.getKey(), stringTypeEntry.getValue()))
              .collect(Collectors.joining(", "))
      );
    }

    @Override
    public String getJavaSourceType() {
      return String.format(this.baseType().getJavaSourceFmtStr(), this.getName());
    }

    @AutoValue
    public abstract static class ImmutableStructType extends StructType {
      public static ImmutableStructType forFieldTypes(String name, ImmutableMap<String, Type> fieldTypes) {
        return new AutoValue_Types_StructType_ImmutableStructType(
            BaseType.IMMUTABLE_STRUCT, ImmutableMap.of(), name, fieldTypes);
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.StructType.ImmutableStructType.forFieldTypes(\"%s\", ImmutableMap.<String, Type>builder()%s.build())",
            this.getName(),
            this.getFieldTypes()
                .entrySet()
                .stream()
                .map(entry -> String.format(".put(\"%s\", %s)", entry.getKey(), entry.getValue()
                    .getJavaSourceClaroType()))
                .collect(Collectors.joining())
        );
      }

      public static TypeProvider forFieldTypeProvidersMap(String structName, ImmutableMap<String, TypeProvider> fieldTypeProvidersMap) {
        return StructType.forFieldTypeProvidersMap(fieldTypeProvidersMap, structName, /*immutable=*/true);
      }
      // TODO(steving) Put some manner of constructor code directly inside this type definition.
    }

    @AutoValue
    public abstract static class MutableStructType extends StructType {
      public static StructType forFieldTypes(String name, ImmutableMap<String, Type> fieldTypes) {
        return new AutoValue_Types_StructType_MutableStructType(BaseType.STRUCT, ImmutableMap.of(), name, fieldTypes);
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.StructType.MutableStructType.forFieldTypes(\"%s\", ImmutableMap.<String, Type>builder()%s.build())",
            this.getName(),
            this.getFieldTypes()
                .entrySet()
                .stream()
                .map(entry -> String.format(".put(\"%s\", %s)", entry.getKey(), entry.getValue()
                    .getJavaSourceClaroType()))
                .collect(Collectors.joining())
        );
      }

      public static TypeProvider forFieldTypeProvidersMap(String structName, ImmutableMap<String, TypeProvider> fieldTypeProvidersMap) {
        return StructType.forFieldTypeProvidersMap(fieldTypeProvidersMap, structName, /*immutable=*/false);
      }
      // TODO(steving) Put some manner of constructor code directly inside this type definition.
    }

  }

  @AutoValue
  public abstract static class BuilderType extends Type {
    private static final ImmutableSet<BaseType> SUPPORTED_BUILT_TYPES =
        ImmutableSet.of(BaseType.STRUCT, BaseType.IMMUTABLE_STRUCT);

    public abstract StructType getBuiltType();

    public static BuilderType forStructType(StructType structType) {
      return new AutoValue_Types_BuilderType(BaseType.BUILDER, ImmutableMap.of(), structType);
    }

    /**
     * This function exists for late binding of user-defined types in the symbol table. This is necessary since we don't
     * have all type information until we parse the entire file and create symbol table entries for all
     * user-defined types.
     *
     * @param structTypeName The name of the (potentially user-defined) type to look for in the symbol table.
     * @return a function that provides the actual resolved BuilderType once the symbol table has all types.
     */
    public static TypeProvider forStructTypeName(String structTypeName) {
      return (scopedHeap) -> {
        Type resolvedTypeFromName = TypeProvider.Util.getTypeByName(structTypeName).resolveType(scopedHeap);
        if (!SUPPORTED_BUILT_TYPES.contains(resolvedTypeFromName.baseType())) {
          throw new RuntimeException(new ClaroTypeException(resolvedTypeFromName, SUPPORTED_BUILT_TYPES));
        }
        return BuilderType.forStructType((StructType) resolvedTypeFromName);
      };
    }

    @Override
    public String toString() {
      return String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          this.getBuiltType().getName()
      );
    }

    @Override
    public String getJavaSourceType() {
      return String.format(this.baseType().getJavaSourceFmtStr(), this.getBuiltType().getJavaSourceType());
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.BuilderType.forStructType(%s)",
          this.getBuiltType().getJavaSourceClaroType()
      );
    }
  }

  public abstract static class ProcedureType extends Type {

    @Nullable
    public abstract ImmutableList<Type> getArgTypes();

    @Nullable
    public abstract Type getReturnType();

    // When comparing Types we don't ever want to care about *names* (or other metadata), these are meaningless to the
    // compiler and should be treated equivalently to a user comment in terms of the program's semantic execution. So
    // Make these fields *ignored* by AutoValue so that we can compare function type equality.
    // https://github.com/google/auto/blob/master/value/userguide/howto.md#ignore
    final AtomicReference<Boolean> autoValueIgnoredHasArgs = new AtomicReference<>();

    public boolean hasArgs() {
      return autoValueIgnoredHasArgs.get();
    }

    final AtomicReference<Boolean> autoValueIgnoredHasReturnValue = new AtomicReference<>();

    public boolean hasReturnValue() {
      return autoValueIgnoredHasReturnValue.get();
    }

    public abstract String getJavaNewTypeDefinitionStmt(String procedureName, StringBuilder body);

    private static final Function<ImmutableList<Type>, String> collectToArgTypesListFormatFn =
        typesByNameMap ->
            typesByNameMap.size() > 1 ?
            typesByNameMap.stream()
                .map(Type::toString)
                .collect(Collectors.joining(", ", "|", "|")) :
            typesByNameMap.stream().findFirst().map(Type::toString).get();

    @Override
    @Nullable
    public ImmutableMap<String, Type> parameterizedTypeArgs() {
      // Internal Compiler Error: method parameterizedTypeArgs() would be ambiguous for Procedure Types, defer to
      // getReturnType() or getArgTypes() as applicable instead.
      return null;
    }

    @AutoValue
    public abstract static class FunctionType extends ProcedureType {
      // Factory method for a function that takes args and returns a value.
      public static FunctionType forArgsAndReturnTypes(ImmutableList<Type> argTypes, Type returnType) {
        // Inheritance has gotten out of hand yet again.... FunctionType doesn't fit within the mold and won't have a
        // parameterizedTypeArgs map used
        FunctionType functionType = new AutoValue_Types_ProcedureType_FunctionType(
            BaseType.FUNCTION,
            argTypes,
            returnType
        );

        functionType.autoValueIgnoredHasArgs.set(true);
        functionType.autoValueIgnoredHasReturnValue.set(true);

        return functionType;
      }

      @Override
      public String getJavaSourceType() {
        return String.format(this.baseType().getJavaSourceFmtStr(), this.getReturnType().getJavaSourceType());
      }

      @Override
      public String getJavaNewTypeDefinitionStmt(String functionName, StringBuilder body) {
        return String.format(
            this.baseType().getJavaNewTypeDefinitionStmtFmtStr(),
            functionName,
            getReturnType().getJavaSourceType(),
            functionName,
            functionName,
            getReturnType().getJavaSourceType(),
            body,
            this,
            functionName,
            functionName,
            functionName,
            functionName
        );
      }

      @Override
      public String toString() {
        return String.format(
            this.baseType().getClaroCanonicalTypeNameFmtStr(),
            collectToArgTypesListFormatFn.apply(this.getArgTypes()),
            this.getReturnType()
        );
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.ProcedureType.FunctionType.forArgsAndReturnTypes(ImmutableList.<Type>of(%s), %s)",
            this.parameterizedTypeArgs()
                .entrySet()
                .stream()
                .map(entry -> String.format(".put(%s, %s)", entry.getKey(), entry.getValue().getJavaSourceClaroType()))
                .collect(Collectors.joining()),
            this.getReturnType().getJavaSourceClaroType()
        );
      }
    }

    @AutoValue
    public abstract static class ProviderType extends ProcedureType {
      public static ProviderType forReturnType(Type returnType) {
        ProviderType providerType = new AutoValue_Types_ProcedureType_ProviderType(
            BaseType.PROVIDER_FUNCTION,
            ImmutableList.of(),
            returnType
        );

        providerType.autoValueIgnoredHasArgs.set(false);
        providerType.autoValueIgnoredHasReturnValue.set(true);

        return providerType;
      }

      @Override
      public String getJavaSourceType() {
        return String.format(this.baseType().getJavaSourceFmtStr(), this.getReturnType().getJavaSourceType());
      }

      @Override
      public String getJavaNewTypeDefinitionStmt(String providerName, StringBuilder body) {
        String returnTypeJavaSource = getReturnType().getJavaSourceType();
        return String.format(
            this.baseType().getJavaNewTypeDefinitionStmtFmtStr(),
            providerName,
            returnTypeJavaSource,
            providerName,
            providerName,
            returnTypeJavaSource,
            body,
            this,
            providerName,
            providerName,
            providerName,
            providerName
        );
      }

      @Override
      public String toString() {
        return String.format(
            this.baseType().getClaroCanonicalTypeNameFmtStr(),
            this.getReturnType()
        );
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.ProcedureType.ProviderType.forReturnType(%s)",
            this.getReturnType().getJavaSourceClaroType()
        );
      }
    }

    @AutoValue
    public abstract static class ConsumerType extends ProcedureType {

      @Override
      @Nullable
      public Type getReturnType() {
        // Internal Compiler Error: Consumers do not have a return value, calling getReturnType() is invalid.
        return null;
      }

      public static ConsumerType forConsumerArgTypes(ImmutableList<Type> argTypes) {
        ConsumerType consumerType = new AutoValue_Types_ProcedureType_ConsumerType(
            BaseType.CONSUMER_FUNCTION,
            argTypes
        );

        consumerType.autoValueIgnoredHasArgs.set(true);
        consumerType.autoValueIgnoredHasReturnValue.set(false);

        return consumerType;
      }

      @Override
      public String getJavaSourceType() {
        return this.baseType().getJavaSourceFmtStr();
      }

      @Override
      public String getJavaNewTypeDefinitionStmt(String consumerName, StringBuilder body) {
        return String.format(
            this.baseType().getJavaNewTypeDefinitionStmtFmtStr(),
            consumerName,
            consumerName,
            consumerName,
            body,
            this.toString(),
            consumerName,
            consumerName,
            consumerName,
            consumerName
        );
      }

      @Override
      public String toString() {
        return String.format(
            this.baseType().getClaroCanonicalTypeNameFmtStr(),
            collectToArgTypesListFormatFn.apply(this.getArgTypes())
        );
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.ProcedureType.ConsumerType.forConsumerArgTypes(ImmutableList.<Type>of(%s))",
            this.parameterizedTypeArgs()
                .entrySet()
                .stream()
                .map(entry -> String.format(".put(%s, %s)", entry.getKey(), entry.getValue().getJavaSourceClaroType()))
                .collect(Collectors.joining())
        );
      }
    }

    public abstract class ProcedureWrapper {
      // This is a little ridiculous, but the type safety will have to be managed exclusively by the Compiler's type
      // checking system. Stress test... Oh le do it. The given ScopedHeap is likely already the same one as given at
      // the function's definition time, but honestly just in case some weird scoping jiu-jitsu has to happen later this
      // is safer to pass in whatever ScopedHeap is necessary at call-time.
      public abstract Object apply(ImmutableList<Expr> args, ScopedHeap scopedHeap);

      public Object apply(ScopedHeap scopedHeap) {
        return apply(ImmutableList.of(), scopedHeap);
      }

      @Override
      public String toString() {
        return ProcedureType.this.toString();
      }
    }

  }

  // This is gonna be used to convey to AutoValue that certain values are nullable and it will generate null-friendly
  // constructors and .equals() and .hashCode() methods.
  // https://github.com/google/auto/blob/master/value/userguide/howto.md#nullable
  @interface Nullable {
  }
}
