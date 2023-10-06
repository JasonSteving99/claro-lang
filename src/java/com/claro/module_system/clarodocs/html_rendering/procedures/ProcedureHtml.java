package com.claro.module_system.clarodocs.html_rendering.procedures;

import com.claro.intermediate_representation.types.Types;
import com.claro.module_system.clarodocs.html_rendering.Util;
import com.claro.module_system.clarodocs.html_rendering.typedefs.TypeHtml;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.claro.module_system.clarodocs.html_rendering.Util.GrammarPart.*;


public class ProcedureHtml {
  public static String generateProcedureHtml(SerializedClaroModule.Procedure procedure) {
    return generateProcedureHtml(procedure, 0);
  }

  public static String generateProcedureHtmlWithIndentationLevel(
      SerializedClaroModule.Procedure procedure, int indentationLevel) {
    return generateProcedureHtml(procedure, indentationLevel);
  }

  private static String generateProcedureHtml(SerializedClaroModule.Procedure procedure, int indentationLevel) {
    switch (procedure.getProcedureTypeCase()) {
      case FUNCTION:
        return renderFunction(procedure.getName(), procedure.getFunction(), indentationLevel);
      case CONSUMER:
        return renderConsumer(procedure.getName(), procedure.getConsumer(), indentationLevel);
      case PROVIDER:
        return renderProvider(procedure.getName(), procedure.getProvider(), indentationLevel);
      default:
        throw new RuntimeException("Internal ClaroDocs Error! Unexpected procedure type case:\n" + procedure);
    }
  }

  private static final String PROCEDURE_DEF_CLASS = "procedure-def";
  private static final String FUNCTION_TEMPLATE = "%s\n" + FUNCTION + " %s%s(%s) " + ARROW + " %s" + SEMICOLON;
  private static final String CONSUMER_TEMPLATE = "%s\n" + CONSUMER + " %s%s(%s)" + SEMICOLON;
  private static final String PROVIDER_TEMPLATE = "%s\n" + PROVIDER + " %s%s() " + ARROW + " %s" + SEMICOLON;

  public static String renderFunction(String name, TypeProtos.FunctionType function, int indentationLevel) {
    return String.format(
        Util.wrapAsDefaultCodeBlockWithIndentationLevel(PROCEDURE_DEF_CLASS, name, FUNCTION_TEMPLATE, indentationLevel),
        renderRequiresClause(function.getRequiredContractsList()),
        name,
        renderGenericTypeParams(function.getOptionalGenericTypeParamNamesList()),
        renderArgs(function.getArgTypesList()),
        TypeHtml.renderType(new StringBuilder(), Types.parseTypeProto(function.getOutputType()))
    );
  }

  public static String renderConsumer(String name, TypeProtos.ConsumerType consumer, int indentationLevel) {
    return String.format(
        Util.wrapAsDefaultCodeBlockWithIndentationLevel(PROCEDURE_DEF_CLASS, name, CONSUMER_TEMPLATE, indentationLevel),
        renderRequiresClause(consumer.getRequiredContractsList()),
        name,
        renderGenericTypeParams(consumer.getOptionalGenericTypeParamNamesList()),
        renderArgs(consumer.getArgTypesList())
    );
  }

  public static String renderProvider(String name, TypeProtos.ProviderType provider, int indentationLevel) {
    return String.format(
        Util.wrapAsDefaultCodeBlockWithIndentationLevel(PROCEDURE_DEF_CLASS, name, PROVIDER_TEMPLATE, indentationLevel),
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
