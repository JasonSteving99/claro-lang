package com.claro.intermediate_representation.types.impls.builtins_impls.structs;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.ClaroBuiltinTypeImplementation;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ClaroStruct implements ClaroBuiltinTypeImplementation {

  private final Types.StructType structType;
  public final Object[] values;

  public ClaroStruct(Types.StructType structType, Object... values) {
    this.structType = structType;
    this.values = values;
  }

  @Override
  public Type getClaroType() {
    return this.structType;
  }

  @Override
  public String toString() {
    return IntStream.range(0, this.values.length)
        .boxed()
        .map(
            i ->
                String.format(
                    "%s = %s",
                    this.structType.getFieldNames().get(i),
                    this.values[i]
                ))
        .collect(Collectors.joining(", ", (this.structType.isMutable() ? "mut " : "") + "{", "}"));
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ClaroStruct)) {
      return false;
    }
    ClaroStruct otherStruct = (ClaroStruct) obj;
    if (!this.structType.equals(otherStruct.structType)) {
      return false;
    }
    for (int i = 0; i < this.values.length; ++i) {
      if (!this.values[i].equals(otherStruct.values[i])) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;
    hashCode = 31 * hashCode + this.structType.hashCode();
    hashCode = 31 * hashCode + Arrays.hashCode(this.values);

    return hashCode;
  }
}