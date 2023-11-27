package com.claro.runtime_utilities.injector;

import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.TypeProvider;
import com.google.auto.value.AutoValue;

import java.util.Optional;

// Internal representation of a potentially aliased key present in a using clause. This is literally identical to the
// existing InjectedKey.java with the only difference being that modeling the key name as an IdentifierReferenceTerm
// allows logging better error messages as applicable. (For now only necessary for Graphs which must assert that
// injected values are deeply-immutable).
@AutoValue
public abstract class InjectedKeyIdentifier {
  public abstract IdentifierReferenceTerm getName();

  public abstract TypeProvider getTypeProvider();

  public abstract Optional<String> getOptionalAlias();

  public static InjectedKeyIdentifier create(IdentifierReferenceTerm name, TypeProvider typeProvider, Optional<String> optionalAlias) {
    return new AutoValue_InjectedKeyIdentifier(name, typeProvider, optionalAlias);
  }

  public InjectedKey toInjectedKey() {
    return InjectedKey.create(getName().getIdentifier(), getTypeProvider(), getOptionalAlias());
  }
}
