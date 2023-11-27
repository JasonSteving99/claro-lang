package com.claro.intermediate_representation.types.impls.builtins_impls.atoms;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.user_defined_impls.ClaroUserDefinedTypeImplementation;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class $ClaroAtom extends ClaroUserDefinedTypeImplementation {
  // An atom value is just its type.
  public abstract Type getClaroType();

  public static $ClaroAtom forTypeNameAndDisambiguator(String name, String definingModuleDisambiguator) {
    return new AutoValue_$ClaroAtom(Types.AtomType.forNameAndDisambiguator(name, definingModuleDisambiguator));
  }

  @Override
  public String toString() {
    return this.getClaroType().toString();
  }
}
