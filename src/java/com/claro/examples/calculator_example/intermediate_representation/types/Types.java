package com.claro.examples.calculator_example.intermediate_representation.types;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.Expr;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Types {
  public static final Type INTEGER = ConcreteType.create(BaseType.INTEGER);
  public static final Type FLOAT = ConcreteType.create(BaseType.FLOAT);
  public static final Type STRING = ConcreteType.create(BaseType.STRING);
  public static final Type BOOLEAN = ConcreteType.create(BaseType.BOOLEAN);

  // Special type not to actually make it out of the type-checking phase of the compiler.
  public static final Type UNDECIDED = ConcreteType.create(BaseType.UNDECIDED);

  @AutoValue
  public abstract static class ListType extends Type {
    public static ListType forValueType(Type valueType) {
      return new AutoValue_Types_ListType(BaseType.LIST, ImmutableMap.of("$values", valueType));
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
        typesByNameMap -> {
          return typesByNameMap.size() > 1 ?
                 typesByNameMap.stream()
                     .map(Type::toString)
                     .collect(Collectors.joining(", ", "|", "|")) :
                 typesByNameMap.stream().findFirst().map(Type::toString).get();
        };

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
            getReturnType().getJavaSourceType(),
            body,
            this,
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
            body,
            this.toString(),
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

    // This is gonna be used to convey to AutoValue that certain values are nullable and it will generate null-friendly
    // constructors and .equals() and .hashCode() methods.
    // https://github.com/google/auto/blob/master/value/userguide/howto.md#nullable
    @interface Nullable {
    }
  }
}
