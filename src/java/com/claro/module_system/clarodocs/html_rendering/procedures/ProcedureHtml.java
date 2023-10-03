package com.claro.module_system.clarodocs.html_rendering.procedures;

import com.claro.intermediate_representation.types.Types;
import com.claro.module_system.clarodocs.html_rendering.typedefs.TypeHtml;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.claro.module_system.clarodocs.html_rendering.Util.GrammarPart.*;


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

  private static final String FUNCTION_TEMPLATE =
      "<pre>\n" +
      "  <code class='procedure-def'>\n" +
      "    %s" +
      "    " + FUNCTION + " %s%s(%s) " + ARROW + " %s" + SEMICOLON + "\n" +
      "  </code>\n" +
      "</pre>";

  private static final String CONSUMER_TEMPLATE =
      "<pre>\n" +
      "  <code class='procedure-def'>\n" +
      "    %s" +
      "    " + CONSUMER + " %s%s(%s)" + SEMICOLON + "\n" +
      "  </code>\n" +
      "</pre>";

  private static final String PROVIDER_TEMPLATE =
      "<pre>\n" +
      "  <code class='procedure-def'>\n" +
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
        TypeHtml.renderType(new StringBuilder(), Types.parseTypeProto(function.getOutputType()))
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
        TypeHtml.renderType(new StringBuilder(), Types.parseTypeProto(provider.getOutputType()))
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
    StringBuilder placeholder = new StringBuilder();
    return IntStream.range(0, argTypeProtos.size()).boxed()
        .map(i -> {
          String fmt = String.format(
              "arg%s: %s", i, TypeHtml.renderType(placeholder, Types.parseTypeProto(argTypeProtos.get(i))));
          placeholder.setLength(0);
          return fmt;
        })
        .collect(Collectors.joining(", "));
  }

  private static Object renderGenericTypeParams(List<String> genericTypeParams) {
    return genericTypeParams.isEmpty()
           ? ""
           : genericTypeParams.stream()
               .collect(Collectors.joining(", ", LT.toString(), GT.toString()));
  }
}
