package com.claro.runtime_utilities.injector;

import com.claro.intermediate_representation.types.Type;
import lombok.Value;

@Value
public class Key {
  public String name;
  public Type type;
}
