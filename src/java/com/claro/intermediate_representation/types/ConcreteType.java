package com.claro.intermediate_representation.types;

import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos.PrimitiveType;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos.TypeProto;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

@AutoValue
public abstract class ConcreteType extends Type {
  public static ConcreteType create(BaseType baseType) {
    return new AutoValue_ConcreteType(baseType, ImmutableMap.of());
  }

  @Override
  public String getJavaSourceClaroType() {
    return String.format("ConcreteType.create(BaseType.%s)", this.baseType().toString());
  }

  @Override
  public TypeProto toProto() {
    PrimitiveType primitiveTypeProto;
    switch (this.baseType()) {
      case INTEGER:
        primitiveTypeProto = PrimitiveType.INTEGER;
        break;
      case FLOAT:
        primitiveTypeProto = PrimitiveType.FLOAT;
        break;
      case STRING:
        primitiveTypeProto = PrimitiveType.STRING;
        break;
      case BOOLEAN:
        primitiveTypeProto = PrimitiveType.BOOLEAN;
        break;
      case HTTP_RESPONSE:
        primitiveTypeProto = PrimitiveType.HTTP_RESPONSE;
        break;
      default:
        throw new RuntimeException("Internal Compiler Error: This type <" + this +
                                   "> should not be serialized as it should be an internal only type.");
    }
    return TypeProto.newBuilder().setPrimitive(primitiveTypeProto).build();
  }
}
