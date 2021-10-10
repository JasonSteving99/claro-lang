package com.claro.runtime_utilities;

import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.ConcreteType;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.impls.ClaroTypeImplementation;

public class ClaroRuntimeUtilities {
  public static <T> T assertedTypeValue(Type assertedType, T evaluatedCastedExprValue) {
    try {
      if (evaluatedCastedExprValue instanceof ClaroTypeImplementation) {
        Type actualClaroType = ((ClaroTypeImplementation) evaluatedCastedExprValue).getClaroType();
        if (!actualClaroType.equals(assertedType)) {
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
}
