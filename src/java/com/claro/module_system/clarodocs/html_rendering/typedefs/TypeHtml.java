package com.claro.module_system.clarodocs.html_rendering.typedefs;

import com.claro.intermediate_representation.types.SupportsMutableVariant;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.common.collect.ImmutableList;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.claro.module_system.clarodocs.html_rendering.Util.GrammarPart.*;

public class TypeHtml {
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
            renderType(new StringBuilder(), Types.parseTypeProto(newTypeDef.getWrappedType()))
        ));
    return res;
  }

  public static StringBuilder renderType(StringBuilder res, Type type) {
    StringBuilder placeholder = new StringBuilder();
    switch (type.baseType()) {
      case ATOM:
        res.append(((Types.AtomType) type).getName());
        break;
      case INTEGER:
        res.append(INT);
        break;
      case FLOAT:
        res.append(FLOAT);
        break;
      case BOOLEAN:
        res.append(BOOLEAN);
        break;
      case STRING:
        res.append(STRING);
        break;
      case LIST:
        Types.ListType listType = (Types.ListType) type;
        maybeRenderMut(res, listType).append("[");
        renderType(res, listType.getElementType())
            .append("]");
        break;
      case TUPLE:
        Types.TupleType tupleType = (Types.TupleType) type;
        maybeRenderMut(res, tupleType).append("tuple").append(LT).append(
                tupleType.getValueTypes().stream()
                    .map(t -> {
                      String fmt = renderType(placeholder, t).toString();
                      placeholder.setLength(0);
                      return fmt;
                    }).collect(Collectors.joining(", ")))
            .append(GT);
        break;
      case SET:
        maybeRenderMut(res, (Types.SetType) type).append("{");
        renderType(res, type.parameterizedTypeArgs().get(Types.SetType.PARAMETERIZED_TYPE))
            .append("}");
        break;
      case MAP:
        maybeRenderMut(res, (Types.MapType) type).append("{");
        renderType(res, type.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS))
            .append(COLON).append(" ");
        renderType(res, type.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES))
            .append("}");
        break;
      case STRUCT:
        Types.StructType structType = (Types.StructType) type;
        maybeRenderMut(res, structType).append(STRUCT).append("{");
        res.append(
            IntStream.range(0, structType.getFieldTypes().size()).boxed()
                .map(i -> {
                  String fmt = String.format(
                      "%s: %s",
                      structType.getFieldNames().get(i),
                      renderType(placeholder, structType.getFieldTypes().get(i)).toString()
                  );
                  placeholder.setLength(0);
                  return fmt;
                })
                .collect(Collectors.joining(", ")));
        res.append("}");
        break;
      case ONEOF:
        res.append(ONEOF).append(LT).append(
                ((Types.OneofType) type).getVariantTypes().stream()
                    .map(t -> {
                      String fmt = renderType(placeholder, t).toString();
                      placeholder.setLength(0);
                      return fmt;
                    })
                    .collect(Collectors.joining(", ")))
            .append(GT);
        break;
      case FUNCTION:
        Types.ProcedureType procedureType = (Types.ProcedureType) type;
        res.append(FUNCTION).append(LT);
        renderProcedureTypeArgs(res, placeholder, procedureType.getArgTypes())
            .append(ARROW).append(" ");
        renderType(res, procedureType.getReturnType()).append(GT);
        break;
      case CONSUMER_FUNCTION:
        procedureType = (Types.ProcedureType) type;
        res.append(CONSUMER).append(LT);
        renderProcedureTypeArgs(res, placeholder, procedureType.getArgTypes()).append(GT);
        break;
      case PROVIDER_FUNCTION:
        procedureType = (Types.ProcedureType) type;
        res.append(PROVIDER).append(LT);
        renderType(res, procedureType.getReturnType()).append(GT);
        break;
      case FUTURE:
        res.append(FUTURE).append(LT);
        renderType(res, type.parameterizedTypeArgs().get(Types.FutureType.PARAMETERIZED_TYPE_KEY))
            .append(GT);
        break;
      case USER_DEFINED_TYPE:
        Types.UserDefinedType userDefinedType = (Types.UserDefinedType) type;
        res.append("<span class='type-link' onclick=\"renderModule('")
            .append(userDefinedType.getDefiningModuleDisambiguator()).append("', root)\" ")
            .append("onmouseover=\"onMouseOverTypeLink(event, '")
            .append(((Types.UserDefinedType) type).getDefiningModuleDisambiguator()).append("', '")
            .append(((Types.UserDefinedType) type).getTypeName()).append("')\" ")
            .append("onmouseout=\"onMouseOutTypeLink(event)\" ")
            .append(">")
            .append(userDefinedType.getTypeName())
            .append("</span>");
        if (!type.parameterizedTypeArgs().isEmpty()) {
          res.append(
              type.parameterizedTypeArgs().values().stream()
                  .map(t -> {
                    String fmt = renderType(placeholder, t).toString();
                    placeholder.setLength(0);
                    return fmt;
                  }).collect(Collectors.joining(", ", LT.toString(), GT.toString())));
        }
        break;
      case HTTP_SERVICE:
        res.append("HttpService").append(LT).append(((Types.HttpServiceType) type).getServiceName()).append(GT);
        break;
      case HTTP_SERVER:
        res.append("HttpServer").append(LT);
        renderType(res, type.parameterizedTypeArgs().get(Types.HttpServerType.HTTP_SERVICE_TYPE)).append(GT);
        break;
      case HTTP_CLIENT:
        res.append("HttpClient").append(LT).append(((Types.HttpClientType) type).getServiceName()).append(GT);
        break;
      case HTTP_RESPONSE:
        res.append("HttpResponse");
        break;
      case $GENERIC_TYPE_PARAM:
        res.append(((Types.$GenericTypeParam) type).getTypeParamName());
        break;
      default:
        throw new RuntimeException("Internal ClaroDocs Error! Attempt to render unknown type: " + type);
    }
    return res;
  }

  private static StringBuilder maybeRenderMut(StringBuilder res, SupportsMutableVariant<?> type) {
    if (type.isMutable()) {
      res.append(MUT).append(" ");
    }
    return res;
  }

  private static StringBuilder renderProcedureTypeArgs(
      StringBuilder res, StringBuilder placeholder, ImmutableList<Type> args) {
    if (args.size() == 1) {
      return renderType(res, args.get(0));
    }
    return res.append(BAR).append(
        args.stream()
            .map(t -> {
              String fmt = renderType(placeholder, t).toString();
              placeholder.setLength(0);
              return fmt;
            })
            .collect(Collectors.joining(", "))
    ).append(BAR);
  }
}
