package com.claro.examples.calculator_example.intermediate_representation.types.builtins_impls.collections;

import com.claro.examples.calculator_example.intermediate_representation.types.Type;

import java.util.ArrayList;

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

  public static <T> ClaroList<T> initializeList(Type claroType, T... args) {
    ClaroList<T> arrayList = new ClaroList<>(claroType, args.length);
    for (T arg : args) {
      arrayList.add(arg);
    }
    return arrayList;
  }

  @Override
  public <T> T getElement(int i) {
    return (T) super.get(i);
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