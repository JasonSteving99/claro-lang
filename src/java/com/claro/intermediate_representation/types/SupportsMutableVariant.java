package com.claro.intermediate_representation.types;

public interface SupportsMutableVariant<T extends Type> {
  T toMutableVariant();

  boolean isMutable();
}
