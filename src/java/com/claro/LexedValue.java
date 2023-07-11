package com.claro;

import com.google.auto.value.AutoValue;

import java.util.function.Supplier;

@AutoValue
public abstract class LexedValue<T> {
  public abstract T getVal();

  public abstract Supplier<String> getCurrentInputLine();

  public abstract int getLen();

  public static <T> LexedValue<T> create(T val, Supplier<String> currentInputLine, int len) {
    return new AutoValue_LexedValue<>(val, currentInputLine, len);
  }
}
