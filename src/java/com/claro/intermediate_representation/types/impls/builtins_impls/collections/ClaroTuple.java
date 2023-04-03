package com.claro.intermediate_representation.types.impls.builtins_impls.collections;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

// TODO(steving) Make this into an AutoValue class.

/**
 * NOTE TO FUTURE OVER-ZEALOUS-JASON...You can't genericize this class (at least not while backing storage with a Java
 * array), so don't bother going down that route.
 */
public class ClaroTuple implements Collection {
  private final Types.TupleType claroType;
  // We store them in an array of object references.. this is lame because it's not contiguous memory but there's just
  // not a better option in Java.
  private final Object[] values;

  public ClaroTuple(Types.TupleType claroType, Object... values) {
    this.claroType = claroType;
    this.values = values;
  }

  public void set(int index, Object val) {
    // Bounds for Tuple reassignment were already checked at compile time.
    this.values[index] = val;
  }

  @SuppressWarnings("unchecked") // The whole point is that we're already checking this.
  public <T> T getElement(int i) {
    return (T) this.values[i];
  }

  public int length() {
    return this.values.length;
  }

  @Override
  public String toString() {
    StringBuilder res = new StringBuilder(this.claroType.isMutable() ? "mut " : "");
    res.append("(");
    for (int i = 0; i < this.length() - 1; i++) {
      res.append(this.values[i].toString());
      res.append(", ");
    }
    res.append(this.getElement(this.length() - 1).toString());
    res.append(")");
    return res.toString();
  }

  @Override
  public Type getClaroType() {
    return claroType;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ClaroTuple)) {
      return false;
    }
    ClaroTuple otherTuple = (ClaroTuple) other;
    if (!this.claroType.equals(otherTuple.claroType)) {
      return false;
    }
    for (int i = 0; i < this.values.length; ++i) {
      if (!this.values[i].equals(otherTuple.values[i])) {
        return false;
      }
    }
    return true;
  }

  // This is scary, but Java requires that I provide an overridden implementation of hashCode() if I override equals().
  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = 31 * hashCode + this.claroType.hashCode();

    for (Object value : this.values) {
      hashCode = 31 * hashCode + value.hashCode();
    }

    return hashCode;
  }
}
