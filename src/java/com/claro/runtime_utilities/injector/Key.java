package com.claro.runtime_utilities.injector;

import com.claro.intermediate_representation.types.Type;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Key {
  public abstract String getName();

  public abstract Type getType();

  public static Key create(String name, Type type) {
    return new AutoValue_Key(name, type);
  }
}
