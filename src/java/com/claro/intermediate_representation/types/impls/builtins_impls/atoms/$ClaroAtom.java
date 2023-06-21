package com.claro.intermediate_representation.types.impls.builtins_impls.atoms;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.user_defined_impls.ClaroUserDefinedTypeImplementation;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class $ClaroAtom extends ClaroUserDefinedTypeImplementation {
  // An atom value is just its type.
  public abstract Type getClaroType();

  // Doing some premature optimization here, I'll explicitly cache all atoms, so they're all singleton. This list's
  // ordering will be dependent on the order that atoms are discovered during parsing, but it will be set only once by
  // Claro on startup.
  private static $ClaroAtom[] ATOM_CACHE;

  private static $ClaroAtom forTypeName(String name) {
    return new AutoValue_$ClaroAtom(Types.AtomType.forName(name));
  }

  // This isn't a perfect solution, but this approach generally communicates the intent that this ATOM_CACHE should only
  // ever be set *once* on startup by the Claro compiler generated init.
  public static void initializeCache(ImmutableList<String> init) {
    if ($ClaroAtom.ATOM_CACHE != null) {
      throw new RuntimeException("ILLEGAL ATTEMPT TO MODIFY ATOM CACHE AFTER INITIALIZATION.");
    }
    $ClaroAtom.ATOM_CACHE =
        init.stream().map($ClaroAtom::forTypeName).collect(ImmutableList.toImmutableList())
            .toArray(new $ClaroAtom[init.size()]);
  }

  public static $ClaroAtom forCacheIndex(int cacheIndex) {
    return $ClaroAtom.ATOM_CACHE[cacheIndex];
  }

  @Override
  public String toString() {
    return this.getClaroType().toString();
  }
}
