package com.claro.intermediate_representation.types.impls.builtins_impls.collections;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.ClaroBuiltinTypeImplementation;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;

public class ClaroSet<V> extends HashSet<V> implements ClaroBuiltinTypeImplementation {
  private final Types.SetType claroType;

  public ClaroSet(Types.SetType claroType) {
    super();
    this.claroType = claroType;
  }

  public int length() {
    return super.size();
  }

  @Override
  public Type getClaroType() {
    return claroType;
  }

  public ClaroSet<V> add(Collection<? extends V> c) {
    super.addAll(c);
    return this;
  }

  @Override
  public String toString() {
    return this.stream()
        .map(Object::toString)
        .collect(Collectors.joining(", ", this.claroType.isMutable() ? "mut {" : "{", "}"));
  }
}