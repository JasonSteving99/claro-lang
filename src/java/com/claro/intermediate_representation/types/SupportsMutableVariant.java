package com.claro.intermediate_representation.types;

import java.util.Optional;

public interface SupportsMutableVariant<T extends Type> {
  T toShallowlyMutableVariant();

  // It's actually not always possible to convert arbitrary types to a deeply-immutable variant type. In particular this
  // is because it's not always possible to convert all UserDefinedTypes to deeply-immutable variants (since they may
  // have explicitly encoded a `mut` annotation within its wrapped type) and these may appear nested within any of these
  // mutable structured types.
  Optional<T> toDeeplyImmutableVariant();

  boolean isMutable();
}
