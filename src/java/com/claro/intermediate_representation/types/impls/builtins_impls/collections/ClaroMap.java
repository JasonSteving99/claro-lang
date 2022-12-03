package com.claro.intermediate_representation.types.impls.builtins_impls.collections;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.impls.builtins_impls.ClaroBuiltinTypeImplementation;

import java.util.HashMap;
import java.util.stream.Collectors;

public class ClaroMap<K, V> extends HashMap<K, V> implements ClaroBuiltinTypeImplementation {
  private final Type claroType;

  public ClaroMap(Type claroType) {
    super();
    this.claroType = claroType;
  }

  public ClaroMap(Type claroType, int initialSize) {
    super(initialSize);
    this.claroType = claroType;
  }

  public V getElement(K k) {
    return super.get(k);
  }

  public ClaroMap<K, V> set(K k, V v) {
    super.put(k, v);
    return this;
  }

  public int length() {
    return super.size();
  }

  @Override
  public Type getClaroType() {
    return claroType;
  }

  @Override
  public String toString() {
    return this.entrySet().stream()
        .map(entry -> String.format("%s: %s", entry.getKey(), entry.getValue()))
        .collect(Collectors.joining(", ", "{", "}"));
  }
}