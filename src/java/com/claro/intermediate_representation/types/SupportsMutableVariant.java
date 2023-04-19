package com.claro.intermediate_representation.types;

public interface SupportsMutableVariant<T extends Type> {
  T toShallowlyMutableVariant();

  T toDeeplyImmutableVariant();

  boolean isMutable();
}
