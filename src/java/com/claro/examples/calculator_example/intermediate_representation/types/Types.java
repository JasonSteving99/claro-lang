package com.claro.examples.calculator_example.intermediate_representation.types;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.Expr;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
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

  @AutoValue
  public abstract static class FunctionType extends Type {
    public abstract ImmutableMap<String, Type> getArgTypes();

    public abstract ImmutableList<Type> getReturnTypes();

    public abstract String getFunctionName();

    public static FunctionType forArgsAndReturnTypes(
        String functionName, ImmutableMap<String, Type> argTypes, ImmutableList<Type> returnTypes) {
      // Inheritance has gotten out of hand yet again.... FunctionType doesn't fit within the mold and won't have a
      // parameterizedTypeArgs map used.
      FunctionType res =
          new AutoValue_Types_FunctionType(BaseType.FUNCTION, ImmutableMap.of(), argTypes, returnTypes, functionName);

      return res;
    }

    @Override
    public final String getJavaSourceType() {
      return String.format(
          baseType().getJavaSourceFmtStr(),
          this.getFunctionName(),
          getReturnTypes().size() > 1 ?
          getReturnTypes().stream()
              .map(Type::getJavaSourceType)
              .collect(
                  Collectors.joining(", ", "ImmutableSet<", ">")) :
          getReturnTypes().stream().findFirst().get().getJavaSourceType(),
          this.getArgTypes().entrySet().asList().stream()
              .map(
                  stringTypeEntry ->
                      String.format("%s %s", stringTypeEntry.getValue().getJavaSourceType(), stringTypeEntry.getKey()))
              .collect(Collectors.joining(", ")),
          "%s", // The caller still needs to substitute the actual generated source for this function definition.
          this.getFunctionName(),
          this.getFunctionName(),
          this.getFunctionName()
      );
    }

    @Override
    public final String toString() {
      Function<ImmutableCollection<Type>, String> collectToInputOutputTypeListFormatFn =
          typeCollection -> {
            return typeCollection.size() > 1 ?
                   typeCollection.stream()
                       .map(Type::toString).collect(Collectors.joining(", ", "|", "|")) :
                   typeCollection.stream().findFirst().get().toString();
          };
      return String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          collectToInputOutputTypeListFormatFn.apply(this.getArgTypes().values()),
          collectToInputOutputTypeListFormatFn.apply(this.getReturnTypes())
      );
    }

    public interface FunctionWrapper {
      // This is a little ridiculous, but the type safety will have to be managed exclusively by the Compiler's type
      // checking system. Stress test... Oh le do it. The given ScopedHeap is likely already the same one as given at
      // the function's definition time, but honestly just in case some weird scoping jiu-jitsu has to happen later this
      // is safer to pass in whatever ScopedHeap is necessary at call-time.
      Object apply(ImmutableMap<String, Expr> args, ScopedHeap scopedHeap);
    }
  }
}
