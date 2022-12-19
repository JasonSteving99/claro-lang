package com.claro.intermediate_representation.types;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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

    public static TypeProvider getTypeByName(String typeName, boolean isTypeDefinition) {
      // This is happening during the type-validation pass which happens strictly after the type-discovery pass.
      // So it's possible that the user-defined type either is or isn't already resolved. Check whether it's already
      // resolved, and if so, move on using that concrete StructType. If it wasn't already resolved, then you
      // need to now resolve this type. In this way, all actually referenced types will end up resolving the entire
      // referenced dependency graph in the depth-first order of reference. This algorithm ensures that we'll only
      // resolve each type definition exactly once. We can also warn/error on unused user-defined types using this
      // approach.
      return (scopedHeap) -> {
        if (scopedHeap.isIdentifierDeclared("$CURR_ALIAS_DEF_NAME")
            && scopedHeap.getIdentifierValue("$CURR_ALIAS_DEF_NAME").equals(typeName)) {
          // Encountered a recursive self-reference to the Alias currently being defined. Intentionally prevent infinite
          // recursion during type validation by artificially terminating here with a sentinel AliasSelfReferenceType.
          // This sentinel will have to be manually handled during later type checking steps that encounter this value
          // such that they appropriately dereference the actual alias type.
          return AliasSelfReferenceType.aliasing(typeName);
        } else {
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
        }
      };
    }

    public static Type maybeDereferenceAliasSelfReference(Type maybeAliasType, ScopedHeap scopedHeap) {
      // Just in case we're doing recursive type validation over a self-referencing alias, potentially apply one level of
      // recursion so that we can assert against the full alias type instead of the synthetic AliasSelfReferenceType.
      if (maybeAliasType.baseType().equals(BaseType.ALIAS_SELF_REFERENCE)) {
        return scopedHeap.getValidatedIdentifierType(
            ((TypeProvider.AliasSelfReferenceType) maybeAliasType).aliasName.get());
      } else {
        return maybeAliasType;
      }
    }
  }

  // TODO(steving) Cleanup Types.java so that it's a package instead of a single Java Class. Then this can go in that
  //  package, and have its own BUILD target and be referenced from here w/o a dependency cycle.
  // This type serves as a hack to wrap another type simply so that I can override the toString() implementation for
  // aliases (so that I can just print the alias name), but I still want .equals() deferring to the aliased type.
  @AutoValue
  public abstract static class AliasSelfReferenceType extends Type {
    // We don't want anything to be included in the .equals() except for the BaseType. Two structurally equivalent
    // aliases should be considered interchangeable.
    public final AtomicReference<String> aliasName = new AtomicReference<>();

    public static AliasSelfReferenceType aliasing(String aliasName) {
      AliasSelfReferenceType res =
          new AutoValue_TypeProvider_AliasSelfReferenceType(BaseType.ALIAS_SELF_REFERENCE, ImmutableMap.of());
      res.aliasName.set(aliasName);
      return res;
    }

    @Override
    public String toString() {
      return this.aliasName.get();
    }

    @Override
    public String getJavaSourceType() {
      return "Object";
    }

    @Override
    public String getJavaSourceClaroType() {
      // For aliases, we're not defining a new type, either in Claro or in generated JavaSource. So in order to recurse
      // into itself, we'll go through this Java backdoor of calling the recursive type Object until we actually *use*
      // the value, at which point we'll generate code to cast to the actual type.
      return String.format(
          "TypeProvider.AliasSelfReferenceType.aliasing(\"%s\")",
          this.aliasName.get()
      );
    }
  }
}
