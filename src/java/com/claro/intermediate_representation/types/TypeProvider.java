package com.claro.intermediate_representation.types;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * This functional-interface exists to allow for multi-stage lazy type resolution. This allows user-defined types to be
 * determined late, after parsing the line itself. This will allow user-defined-types definitions to live anywhere w/in
 * a given scope and still be referenced on lines preceding their definition.
 */
public interface TypeProvider {
  Type resolveType(ScopedHeap scopedHeap);

  final class ImmediateTypeProvider implements TypeProvider {

    private final Type immediateType;

    public ImmediateTypeProvider(Type immediateType) {
      this.immediateType = immediateType;
    }

    public static TypeProvider of(Type immediateType) {
      return new ImmediateTypeProvider(immediateType);
    }

    @Override
    public Type resolveType(ScopedHeap scopedHeap) {
      return immediateType;
    }
  }

  final class Util {
    public static <K> ImmutableMap<K, Type> resolveTypeProviderMap(ScopedHeap scopedHeap, ImmutableMap<K, TypeProvider> typeProviderMap) {
      return typeProviderMap.entrySet().stream()
          .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, entry -> entry.getValue().resolveType(scopedHeap)));
    }
  }
}
