package com.claro.intermediate_representation.types.impls.user_defined_impls;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

public class $UserDefinedType<T> extends ClaroUserDefinedTypeImplementation {

  private final String name;
  private final String definingModuleDisambiguator;
  private final ImmutableList<Type> parameterizedTypes;
  private final Type wrappedType;
  public final T wrappedValue;


  public $UserDefinedType(String name, String definingModuleDisambiguator, ImmutableList<Type> parameterizedTypes, Type wrappedType, T wrappedValue) {
    this.name = name;
    this.definingModuleDisambiguator = definingModuleDisambiguator;
    this.parameterizedTypes = parameterizedTypes;
    this.wrappedType = wrappedType;
    this.wrappedValue = wrappedValue;
  }

  @Override
  public Type getClaroType() {
    return Types.UserDefinedType.forTypeNameAndParameterizedTypes(
        this.name, this.definingModuleDisambiguator, this.parameterizedTypes);
  }

  @Override
  public String toString() {
    return String.format("%s(%s)", this.name, this.wrappedValue.toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof $UserDefinedType)) {
      return false;
    }
    $UserDefinedType<?> otherUserDefinedType = ($UserDefinedType<?>) obj;
    if (!(this.name.equals(otherUserDefinedType.name)
          && this.definingModuleDisambiguator.equals(otherUserDefinedType.definingModuleDisambiguator)
          && this.parameterizedTypes.equals(otherUserDefinedType.parameterizedTypes))) {
      return false;
    }

    return this.wrappedType.equals(otherUserDefinedType.wrappedType)
           && this.wrappedValue.equals(otherUserDefinedType.wrappedValue);
  }

  @Override
  public int hashCode() {
    return 31 + this.name.hashCode()
           + (31 * this.definingModuleDisambiguator.hashCode())
           + (31 * this.parameterizedTypes.hashCode())
           + (31 * this.wrappedType.hashCode())
           + (31 * this.wrappedValue.hashCode());
  }
}
