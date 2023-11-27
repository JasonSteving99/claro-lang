package com.claro.runtime_utilities.injector;

import com.claro.intermediate_representation.types.TypeProvider;
import com.google.auto.value.AutoValue;

import java.util.Optional;

// Internal representation of a potentially aliased key present in a using clause.
@AutoValue
public abstract class InjectedKey {
  public abstract String getName();

  public abstract TypeProvider getTypeProvider();

  public abstract Optional<String> getOptionalAlias();

  public static InjectedKey create(String name, TypeProvider typeProvider, Optional<String> optionalAlias) {
    return new AutoValue_InjectedKey(name, typeProvider, optionalAlias);
  }
}
