package com.claro;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.function.Supplier;

@Data
@AllArgsConstructor
public class LexedValue<T> {
  public final T val;
  public final Supplier<String> currentInputLine;
  public final int len;
}
