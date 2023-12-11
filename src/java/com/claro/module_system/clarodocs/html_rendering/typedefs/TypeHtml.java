package com.claro.module_system.clarodocs.html_rendering.typedefs;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.module_system.clarodocs.html_rendering.Util;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.tofu.SoyTofu;

import java.util.stream.Collectors;


public class TypeHtml {
  private static final SoyTofu SOY = Util.SOY.forNamespace("types");

  public static StringBuilder renderTypeDef(
      StringBuilder res, String typeName, SerializedClaroModule.ExportedTypeDefinitions.NewTypeDef newTypeDef) {
    ImmutableMap.Builder<String, Object> args = ImmutableMap.builder();
    args.put("typeName", typeName)
        .put(
            "wrappedType",
            renderTypeHtml(Types.parseTypeProto(newTypeDef.getWrappedType()))
        );
    if (newTypeDef.getTypeParamNamesCount() > 0) {
      args.put("genericTypeParams", newTypeDef.getTypeParamNamesList());
    }
    res.append(Util.renderSoy(SOY, "typedef", args.build()));
    return res;
  }

  public static SanitizedContent renderTypeHtml(Type type) {
    switch (type.baseType()) {
      case ATOM:
        return Util.renderSoy(SOY, "atom", ImmutableMap.of("name", ((Types.AtomType) type).getName()));
      case INTEGER:
        return renderToken("INT");
      case LONG:
        return renderToken("LONG");
      case FLOAT:
        return renderToken("FLOAT");
      case DOUBLE:
        return renderToken("DOUBLE");
      case BOOLEAN:
        return renderToken("BOOLEAN");
      case STRING:
        return renderToken("STRING");
      case CHAR:
        return renderToken("CHAR");
      case LIST:
        Types.ListType listType = (Types.ListType) type;
        return Util.renderSoy(
            SOY,
            "list",
            ImmutableMap.of(
                "elemsType", renderTypeHtml(listType.getElementType()),
                "isMut", listType.isMutable()
            )
        );
      case TUPLE:
        Types.TupleType tupleType = (Types.TupleType) type;
        return Util.renderSoy(
            SOY,
            "tuple",
            ImmutableMap.of(
                "elemsTypes", tupleType.getValueTypes()
                    .stream()
                    .map(TypeHtml::renderTypeHtml)
                    .collect(Collectors.toList()),
                "isMut", tupleType.isMutable()
            )
        );
      case SET:
        return Util.renderSoy(
            SOY,
            "set",
            ImmutableMap.of(
                "elemsType", renderTypeHtml(type.parameterizedTypeArgs().get(Types.SetType.PARAMETERIZED_TYPE)),
                "isMut", ((Types.SetType) type).isMutable()
            )
        );
      case MAP:
        return Util.renderSoy(
            SOY,
            "map",
            ImmutableMap.of(
                "keyType", renderTypeHtml(type.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS)),
                "valueType", renderTypeHtml(type.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES)),
                "isMut", ((Types.MapType) type).isMutable()
            )
        );
      case STRUCT:
        Types.StructType structType = (Types.StructType) type;
        return Util.renderSoy(
            SOY,
            "struct",
            ImmutableMap.<String, Object>builder()
                .put("fieldNames", structType.getFieldNames())
                .put(
                    "fieldTypes",
                    structType.getFieldTypes()
                        .stream()
                        .map(TypeHtml::renderTypeHtml)
                        .collect(Collectors.toList())
                ).put("isMut", ((Types.StructType) type).isMutable()).build()
        );
      case ONEOF:
        return Util.renderSoy(
            SOY,
            "oneof",
            ImmutableMap.of(
                "variantTypes",
                ((Types.OneofType) type).getVariantTypes()
                    .stream()
                    .map(TypeHtml::renderTypeHtml)
                    .collect(Collectors.toList())
            )
        );
      case FUNCTION:
        Types.ProcedureType procedureType = (Types.ProcedureType) type;
        return Util.renderSoy(
            SOY,
            "function",
            ImmutableMap.of(
                "argTypes",
                procedureType.getArgTypes().stream().map(TypeHtml::renderTypeHtml).collect(Collectors.toList()),
                "outputType", renderTypeHtml(procedureType.getReturnType())
            )
        );
      case CONSUMER_FUNCTION:
        procedureType = (Types.ProcedureType) type;
        return Util.renderSoy(
            SOY,
            "consumer",
            ImmutableMap.of(
                "argTypes",
                procedureType.getArgTypes().stream().map(TypeHtml::renderTypeHtml).collect(Collectors.toList())
            )
        );
      case PROVIDER_FUNCTION:
        procedureType = (Types.ProcedureType) type;
        return Util.renderSoy(
            SOY,
            "provider",
            ImmutableMap.of("outputType", renderTypeHtml(procedureType.getReturnType())
            )
        );
      case FUTURE:
        return Util.renderSoy(
            SOY,
            "future",
            ImmutableMap.of(
                "wrappedType",
                renderTypeHtml(type.parameterizedTypeArgs().get(Types.FutureType.PARAMETERIZED_TYPE_KEY))
            )
        );
      case USER_DEFINED_TYPE:
        Types.UserDefinedType userDefinedType = (Types.UserDefinedType) type;
        ImmutableMap.Builder<String, Object> args =
            ImmutableMap.<String, Object>builder()
                .put("typeName", userDefinedType.getTypeName())
                .put("definingModuleDisambig", userDefinedType.getDefiningModuleDisambiguator());
        if (!userDefinedType.parameterizedTypeArgs().isEmpty()) {
          args.put(
              "concreteTypeParams",
              userDefinedType.parameterizedTypeArgs().values().stream()
                  .map(TypeHtml::renderTypeHtml)
                  .collect(Collectors.toList())
          );
        }
        return Util.renderSoy(SOY, "userDefinedType", args.build());
      case HTTP_SERVICE:
        return Util.renderSoy(
            SOY,
            "httpService",
            ImmutableMap.of("serviceName", ((Types.HttpServiceType) type).getServiceName())
        );
      case HTTP_CLIENT:
        return Util.renderSoy(
            SOY,
            "httpClient",
            ImmutableMap.of("serviceName", ((Types.HttpClientType) type).getServiceName())
        );
      case HTTP_RESPONSE:
        return Util.renderSoy(SOY, "httpResponse", ImmutableMap.of());
      case $GENERIC_TYPE_PARAM:
        return Util.renderSoy(
            SOY,
            "genericTypeParam",
            ImmutableMap.of("paramName", ((Types.$GenericTypeParam) type).getTypeParamName())
        );
      case $SYNTHETIC_OPAQUE_TYPE_WRAPPED_VALUE_TYPE:
        return renderToken("SYNTHETIC_OPAQUE_TYPE_WRAPPED_VALUE_TYPE");
      case $JAVA_TYPE:
        return renderToken("JAVA_TYPE");
      default:
        throw new RuntimeException("Internal ClaroDocs Error! Attempt to render unknown type: " + type);
    }
  }

  public static SanitizedContent renderToken(String templateName) {
    return Util.SOY.newRenderer("tokens." + templateName).renderHtml();
  }

}
