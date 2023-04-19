package com.claro.runtime_utilities.injector;

import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.TypeProvider;
import lombok.Value;

import java.util.Optional;

// Internal representation of a potentially aliased key present in a using clause. This is literally identical to the
// existing InjectedKey.java with the only difference being that modeling the key name as an IdentifierReferenceTerm
// allows logging better error messages as applicable. (For now only necessary for Graphs which must assert that
// injected values are deeply-immutable).
@Value
public class InjectedKeyIdentifier {
  public IdentifierReferenceTerm name;
  public TypeProvider typeProvider;
  public Optional<String> optionalAlias;

  public InjectedKey toInjectedKey() {
    return new InjectedKey(name.getIdentifier(), typeProvider, optionalAlias);
  }
}
