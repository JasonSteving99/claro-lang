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
          // such that they appropriately dereference the actual alias type. And, mark the encounter of this self
          // reference so that AliasStmt may check for invalid self references that would consitute "impossible" types
          // that could not be instantiated in finite steps. This marker will be removed by any outer wrapping type that
          // has an implicit bottom so that by the time the outer AliasStmt TypeProvider is reached it can check easily.
          scopedHeap.putIdentifierValue("$POTENTIAL_IMPOSSIBLE_SELF_REFERENCING_TYPE_FOUND", null, true);
          return AliasSelfReferenceType.aliasing(typeName, scopedHeap);
        } else {
          Optional.ofNullable(scopedHeap.getIdentifierValue(typeName))
              .filter(o -> o instanceof TypeProvider)
              .ifPresent(
                  typeProvider -> {
                    // Replace the TypeProvider found in the symbol table with the actual resolved type.
                    if (isTypeDefinition) {
                      scopedHeap.putIdentifierValueAsTypeDef(
                          typeName, ((TypeProvider) typeProvider).resolveType(scopedHeap), null);
                    } else {
                      scopedHeap.putIdentifierValue(
                          typeName, ((TypeProvider) typeProvider).resolveType(scopedHeap), null);
                    }
                  });
          // If this Type is getting referenced, it was used.
          scopedHeap.markIdentifierUsed(typeName);

          Type res = scopedHeap.getValidatedIdentifierType(typeName);

          // Analogous to the situation with the alias definitions, we may need to make note that we've found a
          // recursive self-reference to a user-defined type so that impossible recursive types can be rejected.
          if (scopedHeap.isIdentifierDeclared("$CURR_TYPE_DEF_NAME")
              && scopedHeap.getIdentifierValue("$CURR_TYPE_DEF_NAME").equals(typeName)) {
            scopedHeap.putIdentifierValue("$POTENTIAL_IMPOSSIBLE_SELF_REFERENCING_TYPE_FOUND", null, true);
          }

          // If this is a type from a dep module, then this is a worthwhile use of the dep to mark it used and prevent
          // warnings that the dep is unnecessary. Don't need to mark used for both the user-defined type and its
          // wrapped type, they're both from the same module.
          if (typeName.contains("$") && !typeName.endsWith("$wrappedType")) {
            ScopedHeap.getModuleNameFromDisambiguator(typeName.substring(typeName.indexOf('$') + 1))
                .ifPresent(ScopedHeap::markDepModuleUsed);
          }

          return res;
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
  // This type serves as a hack to wrap another type simply so that I can do some hacky tricks to make this type's
  // .equals() method, represent recursive equality with arbitrary nestings of recursive application of the type being
  // self referenced. To do this the type also represents a hack in the codegen'd JavaSource to represent itself simply
  // as `Object` with calls to collection subscripts to access this value via an "unsafe" cast that Claro will guarantee
  // is valid.
  @AutoValue
  abstract class AliasSelfReferenceType extends Type {
    // We don't want anything to be included in the .equals() except for the BaseType. Two structurally equivalent
    // aliases should be considered interchangeable.
    public final AtomicReference<String> aliasName = new AtomicReference<>();
    private final AtomicReference<Optional<ScopedHeap>> optionalScopedHeap = new AtomicReference<>();
    private final AtomicReference<Optional<Type>> optionalRecursedType = new AtomicReference<>(Optional.empty());

    public static AliasSelfReferenceType aliasing(String aliasName, ScopedHeap scopedHeap) {
      AliasSelfReferenceType res =
          new AutoValue_TypeProvider_AliasSelfReferenceType(BaseType.ALIAS_SELF_REFERENCE, ImmutableMap.of());
      res.aliasName.set(aliasName);
      res.optionalScopedHeap.set(Optional.of(scopedHeap));
      res.optionalRecursedType.set(Optional.empty());
      return res;
    }

    // Use this for JavaSource codegen since I don't want to maintain the ScopedHeap at runtime.
    public static AliasSelfReferenceType aliasing(String aliasName) {
      AliasSelfReferenceType res =
          new AutoValue_TypeProvider_AliasSelfReferenceType(BaseType.ALIAS_SELF_REFERENCE, ImmutableMap.of());
      res.aliasName.set(aliasName);
      res.optionalScopedHeap.set(Optional.empty());
      res.optionalRecursedType.set(Optional.empty());
      return res;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof AliasSelfReferenceType) {
        return true; // All self references are equivalent, the only thing that matters is the structure to this point.
      }
      // Since we're comparing against some other type, it's actually possible that this is representing the recursed
      // type unwrapped a level deeper, so we'd need to do a symbol table lookup.
      Type recursedType;
      if (this.optionalRecursedType.get().isPresent()) {
        recursedType = this.optionalRecursedType.get().get();
      } else if (this.optionalScopedHeap.get().isPresent()) {
        recursedType = this.optionalScopedHeap.get().get().getValidatedIdentifierType(this.aliasName.get());
        this.optionalRecursedType.set(Optional.of(recursedType));
      } else {
        return false;
      }
      return recursedType.equals(obj);
    }

    @Override
    public int hashCode() {
      return optionalScopedHeap.get().get().getValidatedIdentifierType(this.aliasName.get()).hashCode();
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
