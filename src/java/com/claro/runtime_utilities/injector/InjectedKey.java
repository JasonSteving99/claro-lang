package com.claro.runtime_utilities.injector;

import com.claro.intermediate_representation.types.TypeProvider;
import lombok.Value;

import java.util.Optional;

// Internal representation of a potentially aliased key present in a using clause.
@Value
public class InjectedKey {
  public String name;
  public TypeProvider typeProvider;
  public Optional<String> optionalAlias;
}
