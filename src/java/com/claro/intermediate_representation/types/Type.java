package com.claro.intermediate_representation.types;

import com.claro.ClaroParserException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.concurrent.atomic.AtomicReference;

public abstract class Type {
  public abstract BaseType baseType();

  // name -> Type.
  public abstract ImmutableMap<String, Type> parameterizedTypeArgs();

  // In order for codegen of a narrowed type to correctly produce references to the synthetic variable casted to the
  // appropriate type, we need a way to determine that the identifier is actually referencing the narrowed type and
  // not the original identifier. THIS IS METADATA - DO NOT CONSIDER FOR EQUALITY CHECKS.
  public final AtomicReference<Boolean> autoValueIgnored_IsNarrowedType = new AtomicReference<>(false);

  public String getJavaSourceType() {
    String res;
    if (parameterizedTypeArgs().isEmpty()) {
      res = baseType().getJavaSourceFmtStr();
    } else {
      res = String.format(
          baseType().getJavaSourceFmtStr(),
          parameterizedTypeArgs().values().stream()
              .map(Type::getJavaSourceType)
              .collect(ImmutableList.toImmutableList())
              .toArray()
      );
    }
    return res;
  }

  // This needs to return a JavaSource String that will construct an instance of this Type, i.e. *this* class.
  public String getJavaSourceClaroType() {
    throw new NotImplementedException();
  }

  // Convert to Claro's canonical user-facing builtin type name.
  public String toString() {
    String res;
    if (parameterizedTypeArgs().isEmpty()) {
      res = this.baseType().getClaroCanonicalTypeNameFmtStr();
    } else {
      res = String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          parameterizedTypeArgs().values().stream()
              .map(Type::toString)
              .collect(ImmutableList.toImmutableList())
              .toArray()
      );
    }
    return res;
  }

  // This method truly only exists to allow the interface to the types declared in the Types class
  // to be accessed by the Type interface exclusively, and we'll make sure to call this method only
  // in cases where it makes sense. Probably this shows some categorical design flaw..but I'll find
  // a cleaner solution later.
  public BaseType getPossiblyOverridenBaseType() {
    throw new ClaroParserException("Internal Compiler Error: This method is unsupported.");
  }

}
