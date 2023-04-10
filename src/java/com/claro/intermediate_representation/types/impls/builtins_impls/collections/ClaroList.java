package com.claro.intermediate_representation.types.impls.builtins_impls.collections;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

import java.util.ArrayList;
import java.util.Collections;

public class ClaroList<T> extends ArrayList<T> implements Collection {
  private final Types.ListType claroType;

  public ClaroList(Types.ListType claroType) {
    super();
    this.claroType = claroType;
  }

  public ClaroList(Types.ListType claroType, int initialSize) {
    super(initialSize);
    this.claroType = claroType;
  }

  public ClaroList(Types.ListType claroType, java.util.Collection<T> from) {
    super(from);
    this.claroType = claroType;
  }

  @SafeVarargs // https://docs.oracle.com/javase/specs/jls/se7/html/jls-9.html#jls-9.6.3.7
  public static <T> ClaroList<T> initializeList(Types.ListType claroType, T... args) {
    ClaroList<T> arrayList = new ClaroList<>(claroType, args.length);
    Collections.addAll(arrayList, args);
    return arrayList;
  }

  @Override
  public String toString() {
    return (this.claroType.isMutable() ? "mut " : "") + super.toString();
  }

  @SuppressWarnings("unchecked")
  @Override
  public T getElement(int i) {
    return super.get(i);
  }

  // In Claro, this'll end up being a method defined on the Iterable interface.
  public int length() {
    return ClaroList.this.size();
  }

  @Override
  public Type getClaroType() {
    return claroType;
  }
}