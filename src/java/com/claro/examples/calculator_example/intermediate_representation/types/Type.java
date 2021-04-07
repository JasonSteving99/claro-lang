package com.claro.examples.calculator_example.intermediate_representation.types;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public abstract class Type {
  public abstract BaseType baseType();

  // name -> Type.
  public abstract ImmutableMap<String, Type> parameterizedTypeArgs();

  public final String getJavaSourceType() {
    String res;
    if (parameterizedTypeArgs().isEmpty()) {
      res = baseType().getJavaSourceFmtStr();
    } else {
      res = String.format(
          baseType().getJavaSourceFmtStr(),
          parameterizedTypeArgs().values().stream()
              .map(Type::getJavaSourceType)
              .collect(ImmutableList.toImmutableList())
      );
    }
    return res;
  }
}
