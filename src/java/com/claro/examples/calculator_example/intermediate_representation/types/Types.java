package com.claro.examples.calculator_example.intermediate_representation.types;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.Expr;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;

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
    public abstract ImmutableMap<String, Type> getArgTypes();

    public abstract Type getReturnType();

    // TODO(steving) When comparing Types we don't ever want to care about the *name*, this is meaningless to the
    // TODO(steving) compiler and should be treated equivalently to a user comment in terms of the program's semantic
    // TODO(steving) execution. So Make this field *ignored* by AutoValue so that we can compare function type equality.
    // TODO(steving) https://github.com/google/auto/blob/master/value/userguide/howto.md#ignore
    public abstract String getProcedureName();

    public abstract boolean hasArgs();

    public abstract boolean hasReturnValue();

    public abstract String getJavaNewTypeDefinitionStmt(StringBuilder body);

    private static final Function<ImmutableCollection<Type>, String> collectToArgTypesListFormatFn =
        typeCollection -> {
          return typeCollection.size() > 1 ?
                 typeCollection.stream()
                     .map(Type::toString).collect(Collectors.joining(", ", "|", "|")) :
                 typeCollection.stream().findFirst().get().toString();
        };

    // TODO(steving) I should instead just be much smarter and make a top-level Type interface that doesn't have any of
    // TODO(steving) those methods that I don't want to be obligatory in the hierarchy...design better, Jason.
    // TODO(steving) Note, that this approach likely breaks AutoValue's builtin equals() and hashCode() implementations.
    @Override
    public ImmutableMap<String, Type> parameterizedTypeArgs() {
      throw new IllegalStateException(
          "Internal Compiler Error: method parameterizedTypeArgs() would be ambiguous for Procedure Types, defer to " +
          "getReturnType() or getArgTypes() as applicable instead.");
    }

    @Override
    public String getJavaSourceType() {
      return String.format(this.baseType().getJavaSourceFmtStr(), this.getProcedureName());
    }

    @AutoValue
    public abstract static class FunctionType extends ProcedureType {
      // Factory method for a function that takes args and returns a value.
      public static FunctionType forArgsAndReturnTypes(
          String functionName, ImmutableMap<String, Type> argTypes, Type returnType) {
        // Inheritance has gotten out of hand yet again.... FunctionType doesn't fit within the mold and won't have a
        // parameterizedTypeArgs map used
        return new AutoValue_Types_ProcedureType_FunctionType(
            BaseType.FUNCTION,
            argTypes,
            returnType,
            functionName,
            /* hasArgs= */ true,
            /* hasReturnValue= */ true
        );
      }

      @Override
      public String getJavaNewTypeDefinitionStmt(StringBuilder body) {
        return String.format(
            this.baseType().getJavaNewTypeDefinitionStmtFmtStr(),
            this.getProcedureName(),
            getReturnType().getJavaSourceType(),
            this.getArgTypes().entrySet().asList().stream()
                .map(
                    stringTypeEntry ->
                        String.format("%s %s", stringTypeEntry.getValue()
                            .getJavaSourceType(), stringTypeEntry.getKey()))
                .collect(Collectors.joining(", ")),
            body,
            this,
            this.getProcedureName(),
            this.getProcedureName(),
            this.getProcedureName(),
            this.getProcedureName()
        );
      }

      @Override
      public String toString() {
        return String.format(
            this.baseType().getClaroCanonicalTypeNameFmtStr(),
            collectToArgTypesListFormatFn.apply(this.getArgTypes().values()),
            this.getReturnType()
        );
      }
    }

    @AutoValue
    public abstract static class ProviderType extends ProcedureType {

      // TODO(steving) I should instead just be much smarter and make a top-level Type interface that doesn't have any of
      // TODO(steving) those methods that I don't want to be obligatory in the hierarchy...design better, Jason.
      @Override
      public ImmutableMap<String, Type> getArgTypes() {
        throw new IllegalStateException(
            "Internal Compiler Error: Providers do not accept args, calling getArgTypes() is invalid.");
      }

      public static ProviderType forReturnType(String functionName, Type returnType) {
        return new AutoValue_Types_ProcedureType_ProviderType(
            BaseType.PROVIDER_FUNCTION,
            returnType,
            functionName,
            /* hasArgs= */ false,
            /* hasReturnValue= */ true
        );
      }

      @Override
      public String getJavaNewTypeDefinitionStmt(StringBuilder body) {
        String returnTypeJavaSource = getReturnType().getJavaSourceType();
        return String.format(
            this.baseType().getJavaNewTypeDefinitionStmtFmtStr(),
            this.getProcedureName(),
            returnTypeJavaSource,
            body,
            this,
            this.getProcedureName(),
            this.getProcedureName(),
            this.getProcedureName(),
            this.getProcedureName()
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

      // TODO(steving) I should instead just be much smarter and make a top-level Type interface that doesn't have any of
      // TODO(steving) those methods that I don't want to be obligatory in the hierarchy...design better, Jason.
      @Override
      public Type getReturnType() {
        throw new IllegalStateException(
            "Internal Compiler Error: Consumers do not have a return value, calling getReturnType() is invalid.");
      }

      public static ConsumerType forConsumerArgTypes(String functionName, ImmutableMap<String, Type> argTypes) {
        return new AutoValue_Types_ProcedureType_ConsumerType(
            BaseType.CONSUMER_FUNCTION,
            argTypes,
            functionName,
            /* hasArgs= */ true,
            /* hasReturnValue= */ false
        );
      }

      @Override
      public String getJavaNewTypeDefinitionStmt(StringBuilder body) {
        return String.format(
            this.baseType().getJavaNewTypeDefinitionStmtFmtStr(),
            this.getProcedureName(),
            this.getArgTypes().entrySet().asList().stream()
                .map(
                    stringTypeEntry ->
                        String.format("%s %s", stringTypeEntry.getValue()
                            .getJavaSourceType(), stringTypeEntry.getKey()))
                .collect(Collectors.joining(", ")),
            body,
            this,
            this.getProcedureName(),
            this.getProcedureName(),
            this.getProcedureName(),
            this.getProcedureName()
        );
      }

      @Override
      public String toString() {
        return String.format(
            this.baseType().getClaroCanonicalTypeNameFmtStr(),
            collectToArgTypesListFormatFn.apply(this.getArgTypes().values())
        );
      }
    }

    public abstract class ProcedureWrapper {
      // This is a little ridiculous, but the type safety will have to be managed exclusively by the Compiler's type
      // checking system. Stress test... Oh le do it. The given ScopedHeap is likely already the same one as given at
      // the function's definition time, but honestly just in case some weird scoping jiu-jitsu has to happen later this
      // is safer to pass in whatever ScopedHeap is necessary at call-time.
      public abstract Object apply(ImmutableMap<String, Expr> args, ScopedHeap scopedHeap);

      public Object apply(ScopedHeap scopedHeap) {
        return apply(ImmutableMap.of(), scopedHeap);
      }

      @Override
      public String toString() {
        return String.format("%s %s", ProcedureType.this.toString(), ProcedureType.this.getProcedureName());
      }
    }
  }
}
