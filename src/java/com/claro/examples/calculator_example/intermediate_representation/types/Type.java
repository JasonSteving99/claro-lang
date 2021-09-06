package com.claro.examples.calculator_example.intermediate_representation.types;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public abstract class Type {
  public abstract BaseType baseType();

  // name -> Type.
  public abstract ImmutableMap<String, Type> parameterizedTypeArgs();

  public String getJavaSourceType() {
    String res;
    if (parameterizedTypeArgs().isEmpty()) {
      res = baseType().getJavaSourceFmtStr();
    } else {
      res = String.format(
          baseType().getJavaSourceFmtStr(),
          parameterizedTypeArgs().values().stream()
              .map(Type::getJavaSourceType)
              .collect(ImmutableList.toImmutableList())
              .toArray()
      );
    }
    return res;
  }

  // This needs to return a JavaSource String that will construct an instance of this Type, i.e. *this* class.
  public String getJavaSourceClaroType() {
    throw new NotImplementedException();
  }

  // Convert to Claro's canonical user-facing builtin type name.
  public String toString() {
    String res;
    if (parameterizedTypeArgs().isEmpty()) {
      res = this.baseType().getClaroCanonicalTypeNameFmtStr();
    } else {
      res = String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          parameterizedTypeArgs().values().stream()
              .map(Type::toString)
              .collect(ImmutableList.toImmutableList())
              .toArray()
      );
    }
    return res;
  }
}
