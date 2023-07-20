package com.claro.runtime_utilities;

import com.claro.intermediate_representation.types.*;
import com.claro.intermediate_representation.types.impls.ClaroTypeImplementation;
import com.claro.intermediate_representation.types.impls.builtins_impls.structs.ClaroStruct;
import com.claro.intermediate_representation.types.impls.user_defined_impls.$UserDefinedType;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClaroRuntimeUtilities {
  public static ListeningExecutorService DEFAULT_EXECUTOR_SERVICE =
      MoreExecutors.listeningDecorator(
          Executors.newFixedThreadPool(
              Runtime.getRuntime().availableProcessors(),
              // This directly copies the implementation of {@link Executors#defaultThreadFactory} just to override the
              // name given to threads created by Claro's graph functions since I want users to be able to distinguish
              // Claro's defaults from anything that they override.
              new ThreadFactory() {
                private final ThreadGroup group;
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                {
                  SecurityManager s = System.getSecurityManager();
                  group = (s != null) ? s.getThreadGroup() :
                          Thread.currentThread().getThreadGroup();
                }

                public Thread newThread(Runnable r) {
                  String namePrefix = "claro-default-graph-function-pool-thread-";
                  Thread t = new Thread(group, r,
                                        namePrefix + threadNumber.getAndIncrement(),
                                        0
                  );
                  if (t.isDaemon()) {
                    t.setDaemon(false);
                  }
                  if (t.getPriority() != Thread.NORM_PRIORITY) {
                    t.setPriority(Thread.NORM_PRIORITY);
                  }
                  return t;
                }
              }
          )
      );

  // Implementation of this shutdown hook taken directly from ExecutorService documentation: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html?is-external=true#:~:text=void%20shutdownAndAwaitTermination(ExecutorService,Thread.currentThread().interrupt()%3B%0A%20%20%20%7D%0A%20%7D
  public static void $shutdownAndAwaitTermination(ExecutorService pool) {
    pool.shutdown(); // Disable new tasks from being submitted
    try {
      // Wait a while for existing tasks to terminate
      if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
        pool.shutdownNow(); // Cancel currently executing tasks
        // Wait a while for tasks to respond to being cancelled
        if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
          System.err.println("Pool did not terminate");
        }
      }
    } catch (InterruptedException ie) {
      // (Re-)Cancel if current thread also interrupted
      pool.shutdownNow();
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
  }

  public static <T> T assertedTypeValue(Type assertedType, T evaluatedCastedExprValue) {
    try {
      if (evaluatedCastedExprValue instanceof ClaroTypeImplementation) {
        Type actualClaroType = ((ClaroTypeImplementation) evaluatedCastedExprValue).getClaroType();
        if (!actualClaroType.equals(assertedType)) {
          if (assertedType.baseType().equals(BaseType.ONEOF)) {
            if (actualClaroType.baseType().equals(BaseType.ONEOF)) {
              if (((Types.OneofType) assertedType).getVariantTypes()
                  .containsAll(((Types.OneofType) actualClaroType).getVariantTypes())) {
                return evaluatedCastedExprValue;
              }
            } else if (((Types.OneofType) assertedType).getVariantTypes().contains(actualClaroType)) {
              return evaluatedCastedExprValue;
            }
          }
          throw ClaroTypeException.forInvalidCast(actualClaroType, assertedType);
        }
      } else if (assertedType instanceof ConcreteType) {
        // These are native-Claro builtin primitives that are using underlying native-Java runtime implementations.
        Class<?> assertedTypeClazz = assertedType.baseType().getNativeJavaSourceImplClazz();
        if (!evaluatedCastedExprValue.getClass().equals(assertedTypeClazz)) {
          // TODO(steving) Unfortunately this is a workaround that ends up losing type information within the thrown
          //  Exception.. really we should be able to show the native-Claro type string, but instead we'll end up
          //  showing something like "Integer.class could not be converted to string" where `Intger.class` very much
          //  should actually show `int` since it's just the underlying impl of the native-Claro builtin type.
          throw ClaroTypeException.forInvalidCast(evaluatedCastedExprValue.getClass(), assertedType);
        }
      } else {
        throw new ClaroTypeException("Internal Compiler Error! Claro only supports casts to native-Claro types and doesn't yet support native-Java types.");
      }
    } catch (ClaroTypeException e) {
      // Obnoxiously the interface that I'm using here won't allow me to throw ClaroTypeException without making it a
      // compile-time checked requirement, so I'm just rethrowing as a runtime exception. We explicitly want to fail out
      // of the interpreter phase right now for the current Stmt if there was an invalid cast.
      throw new RuntimeException(e);
    }

    // Now that we're confident that this value has the type that we actually thought it had, feel free to keep working
    // with it as stated.
    return evaluatedCastedExprValue;
  }

  public static boolean $instanceof_ClaroTypeImpl(Object obj, Type checkedType) {
    return (obj instanceof ClaroTypeImplementation)
           && ((ClaroTypeImplementation) obj).getClaroType().equals(checkedType);
  }

  public static boolean isErrorType(Type t) {
    // This seems like a very contrived check, but `Error<T>` is part of the stdlib, so there's no way for users to
    // shadow it or create their own type meeting these conditions, so here we'll be good.
    return t.baseType().equals(BaseType.USER_DEFINED_TYPE)
           && ((Types.UserDefinedType) t).getTypeName().equals("Error")
           && t.parameterizedTypeArgs().size() == 1;
  }

  public static Type getClaroType(Object value) {
    if (value instanceof ClaroTypeImplementation) {
      return ((ClaroTypeImplementation) value).getClaroType();
    } else {
      // These are native-Claro builtin primitives that are using underlying native-Java runtime implementations.
      switch (value.getClass().getSimpleName()) {
        case "Integer":
          return Types.INTEGER;
        case "Double":
          return Types.FLOAT;
        case "String":
          return Types.STRING;
        case "Boolean":
          return Types.BOOLEAN;
        default:
          // Obnoxiously the interface that I'm using here won't allow me to throw ClaroTypeException without making it a
          // compile-time checked requirement, so I'm just rethrowing as a runtime exception. We explicitly want to fail out
          // of the interpreter phase right now for the current Stmt if there was an invalid cast.
          throw new RuntimeException(new ClaroTypeException("Internal Compiler Error! Claro only supports casts to native-Claro types and doesn't yet support native-Java types."));
      }
    }
  }

  public static $UserDefinedType<ClaroStruct> $getErrorParsedJson(Type targetType, String jsonPathError, String jsonString) {
    final Types.StructType parsedJsonStructType =
        Types.StructType.forFieldTypes(
            ImmutableList.of("result", "rawJson"),
            ImmutableList.of(
                Types.OneofType.forVariantTypes(
                    ImmutableList.of(
                        targetType,
                        Types.UserDefinedType.forTypeNameAndParameterizedTypes(
                            "Error",
                            // TODO(steving) This is going to be problematic once I begin building out the stdlib modules.
                            /*definingModuleDisambiguator=*/"", // No module for stdlib types that weren't moved into Modules yet.
                            ImmutableList.of(Types.STRING)
                        )
                    )
                ),
                Types.STRING
            ),
            /*isMutable=*/false
        );

    return new $UserDefinedType<>(
        "ParsedJson",
        // TODO(steving) This is going to be problematic once I begin building out the stdlib modules.
        /*definingModuleDisambiguator=*/"", // No module for stdlib types that weren't moved into Modules yet.
        ImmutableList.of(targetType),
        parsedJsonStructType,
        new ClaroStruct(
            parsedJsonStructType,
            new $UserDefinedType<>(
                "Error",
                // TODO(steving) This is going to be problematic once I begin building out the stdlib modules.
                /*definingModuleDisambiguator=*/"", // No module for stdlib types that weren't moved into Modules yet.
                ImmutableList.of(Types.STRING),
                Types.STRING,
                String.format(
                    "Given JSON string did not match the asserted target type definition.\n" +
                    "\tExpected:\n" +
                    "\t\t%s\n" +
                    "\tAt JsonPath:\n" +
                    "\t\t%s",
                    targetType,
                    jsonPathError
                )
            ),
            jsonString
        )
    );
  }

  public static $UserDefinedType<ClaroStruct> $getSuccessParsedJson(
      Type targetType, Object parsedRes, String jsonString) {
    final Types.StructType parsedJsonStructType =
        Types.StructType.forFieldTypes(
            ImmutableList.of("result", "rawJson"),
            ImmutableList.of(
                Types.OneofType.forVariantTypes(
                    ImmutableList.of(
                        targetType,
                        Types.UserDefinedType.forTypeNameAndParameterizedTypes(
                            "Error",
                            // TODO(steving) This is going to be problematic once I begin building out the stdlib modules.
                            /*definingModuleDisambiguator=*/"", // No module for stdlib types that weren't moved into Modules yet.
                            ImmutableList.of(Types.STRING)
                        )
                    )
                ),
                Types.STRING
            ),
            /*isMutable=*/false
        );

    return new $UserDefinedType<>(
        "ParsedJson",
        /*definingModuleDisambiguator=*/"",
        ImmutableList.of(targetType),
        parsedJsonStructType,
        new ClaroStruct(parsedJsonStructType, parsedRes, jsonString)
    );
  }
}
