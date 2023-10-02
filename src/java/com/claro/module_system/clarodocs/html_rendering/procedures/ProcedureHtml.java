package com.claro.module_system.clarodocs.html_rendering.procedures;

import com.claro.intermediate_representation.types.Types;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ProcedureHtml {
  public static String generateProcedureHtml(SerializedClaroModule.Procedure procedure) {
    switch (procedure.getProcedureTypeCase()) {
      case FUNCTION:
        return renderFunction(procedure.getName(), procedure.getFunction());
      case CONSUMER:
        return renderConsumer(procedure.getName(), procedure.getConsumer());
      case PROVIDER:
        return renderProvider(procedure.getName(), procedure.getProvider());
      default:
        throw new RuntimeException("Internal ClaroDocs Error! Unexpected procedure type case:\n" + procedure);
    }
  }

  private static enum Color {
    BLUE("#2A73A9"),
    ORANGE("orange");
    private final String color;

    Color(String color) {
      this.color = color;
    }
  }

  private static final String FUNCTION = coloredSpan("function", Color.ORANGE);
  private static final String CONSUMER = coloredSpan("consumer", Color.ORANGE);
  private static final String PROVIDER = coloredSpan("provider", Color.ORANGE);
  private static final String ARROW = coloredSpan("->", Color.BLUE);
  private static final String LT = coloredSpan("&lt;", Color.BLUE);
  private static final String GT = coloredSpan("&gt;", Color.BLUE);
  private static final String SEMICOLON = coloredSpan(";", Color.BLUE);

  private static final String FUNCTION_TEMPLATE =
      "<pre>\n" +
      "  <code>\n" +
      "    %s" +
      "    " + FUNCTION + " %s%s(%s) " + ARROW + " %s" + SEMICOLON + "\n" +
      "  </code>\n" +
      "</pre>";

  private static final String CONSUMER_TEMPLATE =
      "<pre>\n" +
      "  <code>\n" +
      "    %s" +
      "    " + CONSUMER + " %s%s(%s)" + SEMICOLON + "\n" +
      "  </code>\n" +
      "</pre>";

  private static final String PROVIDER_TEMPLATE =
      "<pre>\n" +
      "  <code>\n" +
      "    %s" +
      "    " + PROVIDER + " %s%s() " + ARROW + " %s" + SEMICOLON + "\n" +
      "  </code>\n" +
      "</pre>";

  public static String renderFunction(String name, TypeProtos.FunctionType function) {
    return String.format(
        FUNCTION_TEMPLATE,
        renderRequiresClause(function.getRequiredContractsList()),
        name,
        renderGenericTypeParams(function.getOptionalGenericTypeParamNamesList()),
        renderArgs(function.getArgTypesList()),
        Types.parseTypeProto(function.getOutputType()).toString()
    );
  }

  public static String renderConsumer(String name, TypeProtos.ConsumerType consumer) {
    return String.format(
        CONSUMER_TEMPLATE,
        renderRequiresClause(consumer.getRequiredContractsList()),
        name,
        renderGenericTypeParams(consumer.getOptionalGenericTypeParamNamesList()),
        renderArgs(consumer.getArgTypesList())
    );
  }

  public static String renderProvider(String name, TypeProtos.ProviderType provider) {
    return String.format(
        PROVIDER_TEMPLATE,
        renderRequiresClause(provider.getRequiredContractsList()),
        name,
        renderGenericTypeParams(provider.getOptionalGenericTypeParamNamesList()),
        Types.parseTypeProto(provider.getOutputType()).toString()
    );
  }

  private static String renderRequiresClause(List<TypeProtos.RequiredContract> requiredContracts) {
    if (requiredContracts.size() == 0) {
      return "";
    }
    return "    requires(" +
           requiredContracts.stream()
               .map(req -> String.format(
                   "%s" + LT + "%s" + GT, req.getName(), String.join(", ", req.getGenericTypeParamsList())))
               .collect(Collectors.joining(", ")) +
           ")\n";
  }

  private static String renderArgs(List<TypeProtos.TypeProto> argTypeProtos) {
    return IntStream.range(0, argTypeProtos.size()).boxed()
        .map(i -> String.format("arg%s: %s", i, Types.parseTypeProto(argTypeProtos.get(i)).toString()))
        .collect(Collectors.joining(", "));
  }

  private static Object renderGenericTypeParams(List<String> genericTypeParams) {
    return genericTypeParams.isEmpty()
           ? ""
           : genericTypeParams.stream()
               .collect(Collectors.joining(", ", LT, GT));
  }

  private static String coloredSpan(String toWrap, Color color) {
    return String.format("<span style=\"color:%s;\">%s</span>", color.color, toWrap);
  }
}
