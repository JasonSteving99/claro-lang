package com.claro.module_system.clarodocs.html_rendering.typedefs;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.module_system.clarodocs.html_rendering.Util;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.tofu.SoyTofu;

import java.util.stream.Collectors;

import static com.claro.module_system.clarodocs.html_rendering.Util.GrammarPart.*;

public class TypeHtml {
  private static final SoyTofu SOY = Util.SOY.forNamespace("types");
  private static final String TYPEDEF_TEMPLATE =
      "<pre>\n" +
      "  <code class='typedef' id='%s'>\n" +
      "    " + NEWTYPE + " " + "%s%s " + COLON + " %s\n" +
      "  </code>\n" +
      "</pre>";

  public static StringBuilder renderTypeDef(
      StringBuilder res, String typeName, SerializedClaroModule.ExportedTypeDefinitions.NewTypeDef newTypeDef) {
    res.append(
        String.format(
            TYPEDEF_TEMPLATE,
            typeName,
            typeName,
            newTypeDef.getTypeParamNamesCount() == 0
            ? ""
            : String.format("%s%s%s", LT, String.join(", ", newTypeDef.getTypeParamNamesList()), GT),
            renderTypeHtml(Types.parseTypeProto(newTypeDef.getWrappedType()))
        ));
    return res;
  }

  public static SanitizedContent renderTypeHtml(Type type) {
    switch (type.baseType()) {
      case ATOM:
        return renderSoy("atom", ImmutableMap.of("name", ((Types.AtomType) type).getName()));
      case INTEGER:
        return renderToken("INT");
      case FLOAT:
        return renderToken("FLOAT");
      case BOOLEAN:
        return renderToken("BOOLEAN");
      case STRING:
        return renderToken("STRING");
      case LIST:
        Types.ListType listType = (Types.ListType) type;
        return renderSoy(
            "list",
            ImmutableMap.of(
                "elemsType", renderTypeHtml(listType.getElementType()),
                "isMut", listType.isMutable()
            )
        );
      case TUPLE:
        Types.TupleType tupleType = (Types.TupleType) type;
        return renderSoy(
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
        return renderSoy(
            "set",
            ImmutableMap.of(
                "elemsType", renderTypeHtml(type.parameterizedTypeArgs().get(Types.SetType.PARAMETERIZED_TYPE)),
                "isMut", ((Types.SetType) type).isMutable()
            )
        );
      case MAP:
        return renderSoy(
            "map",
            ImmutableMap.of(
                "keyType", renderTypeHtml(type.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS)),
                "valueType", renderTypeHtml(type.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES)),
                "isMut", ((Types.MapType) type).isMutable()
            )
        );
      case STRUCT:
        Types.StructType structType = (Types.StructType) type;
        return renderSoy(
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
        return renderSoy(
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
        return renderSoy(
            "function",
            ImmutableMap.of(
                "argTypes",
                procedureType.getArgTypes().stream().map(TypeHtml::renderTypeHtml).collect(Collectors.toList()),
                "outputType", renderTypeHtml(procedureType.getReturnType())
            )
        );
      case CONSUMER_FUNCTION:
        procedureType = (Types.ProcedureType) type;
        return renderSoy(
            "consumer",
            ImmutableMap.of(
                "argTypes",
                procedureType.getArgTypes().stream().map(TypeHtml::renderTypeHtml).collect(Collectors.toList())
            )
        );
      case PROVIDER_FUNCTION:
        procedureType = (Types.ProcedureType) type;
        return renderSoy(
            "provider",
            ImmutableMap.of("outputType", renderTypeHtml(procedureType.getReturnType())
            )
        );
      case FUTURE:
        return renderSoy(
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
        return renderSoy("userDefinedType", args.build());
      case HTTP_SERVICE:
        return renderSoy(
            "httpService",
            ImmutableMap.of("serviceName", ((Types.HttpServiceType) type).getServiceName())
        );
      case HTTP_CLIENT:
        return renderSoy(
            "httpClient",
            ImmutableMap.of("serviceName", ((Types.HttpClientType) type).getServiceName())
        );
      case HTTP_RESPONSE:
        return renderSoy("httpResponse", ImmutableMap.of());
      case $GENERIC_TYPE_PARAM:
        return renderSoy(
            "genericTypeParam",
            ImmutableMap.of("paramName", ((Types.$GenericTypeParam) type).getTypeParamName())
        );
      default:
        throw new RuntimeException("Internal ClaroDocs Error! Attempt to render unknown type: " + type);
    }
  }

  public static SanitizedContent renderToken(String templateName) {
    return Util.SOY.newRenderer("tokens." + templateName).renderHtml();
  }

  public static SanitizedContent renderSoy(String templateName, ImmutableMap<String, Object> args) {
    return SOY.newRenderer("." + templateName).setData(args).renderHtml();
  }
}
