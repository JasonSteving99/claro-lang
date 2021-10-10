package com.claro.intermediate_representation.types.impls.builtins_impls.collections;

import com.claro.intermediate_representation.types.Type;

import java.util.ArrayList;
import java.util.Collections;

public class ClaroList<T> extends ArrayList<T> implements Collection {
  private final Type claroType;

  public ClaroList(Type claroType) {
    super();
    this.claroType = claroType;
  }

  public ClaroList(Type claroType, int initialSize) {
    super(initialSize);
    this.claroType = claroType;
  }

  @SafeVarargs // https://docs.oracle.com/javase/specs/jls/se7/html/jls-9.html#jls-9.6.3.7
  public static <T> ClaroList<T> initializeList(Type claroType, T... args) {
    ClaroList<T> arrayList = new ClaroList<>(claroType, args.length);
    Collections.addAll(arrayList, args);
    return arrayList;
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