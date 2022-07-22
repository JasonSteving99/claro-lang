package com.claro.intermediate_representation.types;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

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

    public static TypeProvider getTypeByName(String typeName) {
      return getTypeByName(typeName, /*isTypeDefinition=*/false);
    }

    public static TypeProvider getTypeByName(String typeName, boolean isTypeDefinition) {
      // This is happening during the type-validation pass which happens strictly after the type-discovery pass.
      // So it's possible that the user-defined type either is or isn't already resolved. Check whether it's already
      // resolved, and if so, move on using that concrete StructType. If it wasn't already resolved, then you
      // need to now resolve this type. In this way, all actually referenced types will end up resolving the entire
      // referenced dependency graph in the depth-first order of reference. This algorithm ensures that we'll only
      // resolve each type definition exactly once. We can also warn/error on unused user-defined types using this
      // approach.
      return (scopedHeap) -> {
        Optional.ofNullable(scopedHeap.getIdentifierValue(typeName))
            .filter(o -> o instanceof TypeProvider)
            .ifPresent(
                typeProvider -> {
                  // Replace the TypeProvider found in the symbol table with the actual resolved type.
                  scopedHeap.putIdentifierValue(
                      typeName, ((TypeProvider) typeProvider).resolveType(scopedHeap), null);
                  if (isTypeDefinition) {
                    scopedHeap.markIdentifierAsTypeDefinition(typeName);
                  }
                });
        // If this Type is getting referenced, it was used.
        scopedHeap.markIdentifierUsed(typeName);

        return scopedHeap.getValidatedIdentifierType(typeName);
      };
    }
  }
}
