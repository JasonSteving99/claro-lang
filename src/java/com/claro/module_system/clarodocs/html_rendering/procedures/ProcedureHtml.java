package com.claro.module_system.clarodocs.html_rendering.procedures;

import com.claro.intermediate_representation.types.Types;
import com.claro.module_system.clarodocs.html_rendering.Util;
import com.claro.module_system.clarodocs.html_rendering.typedefs.TypeHtml;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.tofu.SoyTofu;

import java.util.List;

// TODO(steving) Migrate to pre-compiled SoySauce templates instead of slower (hence deprecated) SoyTofu templates.
@SuppressWarnings("deprecation")
public class ProcedureHtml {
  private static final SoyTofu.Renderer PROCEDURES_TEMPLATE =
      Util.SOY.newRenderer("procedures.exportedProcedure");

  public static SanitizedContent generateProcedureHtml(SerializedClaroModule.Procedure procedure) {
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

  public static SanitizedContent renderFunction(String name, TypeProtos.FunctionType function) {
    ImmutableMap.Builder<String, Object> args = ImmutableMap.builder();
    args.put("name", name)
        .put(
            "argTypes",
            function.getArgTypesList().stream()
                .map(Types::parseTypeProto)
                .map(TypeHtml::renderTypeHtml)
                .collect(ImmutableList.toImmutableList())
        )
        .put("outputType", TypeHtml.renderTypeHtml(Types.parseTypeProto(function.getOutputType())));
    setOptionalRequiredContracts(function.getRequiredContractsList(), args);
    setOptionalGenericTypeParams(ImmutableList.copyOf(function.getOptionalGenericTypeParamNamesList()), args);
    return PROCEDURES_TEMPLATE.setData(args.build()).renderHtml();
  }

  public static SanitizedContent renderConsumer(String name, TypeProtos.ConsumerType consumer) {
    ImmutableMap.Builder<String, Object> args = ImmutableMap.builder();
    args.put("name", name)
        .put(
            "argTypes",
            consumer.getArgTypesList().stream()
                .map(Types::parseTypeProto)
                .map(TypeHtml::renderTypeHtml)
                .collect(ImmutableList.toImmutableList())
        );
    setOptionalRequiredContracts(consumer.getRequiredContractsList(), args);
    setOptionalGenericTypeParams(ImmutableList.copyOf(consumer.getOptionalGenericTypeParamNamesList()), args);
    return PROCEDURES_TEMPLATE.setData(args.build()).renderHtml();
  }

  public static SanitizedContent renderProvider(String name, TypeProtos.ProviderType provider) {
    ImmutableMap.Builder<String, Object> args = ImmutableMap.builder();
    args.put("name", name)
        .put("outputType", TypeHtml.renderTypeHtml(Types.parseTypeProto(provider.getOutputType())));
    setOptionalRequiredContracts(provider.getRequiredContractsList(), args);
    setOptionalGenericTypeParams(ImmutableList.copyOf(provider.getOptionalGenericTypeParamNamesList()), args);
    return PROCEDURES_TEMPLATE.setData(args.build()).renderHtml();
  }

  private static void setOptionalGenericTypeParams(
      List<String> genericTypeParamNames, ImmutableMap.Builder<String, Object> args) {
    if (genericTypeParamNames.size() > 0) {
      args.put("genericTypeParams", genericTypeParamNames);
    }
  }

  private static void setOptionalRequiredContracts(
      List<TypeProtos.RequiredContract> requiredContracts, ImmutableMap.Builder<String, Object> args) {
    if (requiredContracts.size() > 0) {
      args.put(
          "requiredContracts",
          requiredContracts.stream()
              .map(
                  r -> ImmutableMap.of(
                      "contractName", r.getName(),
                      "genericTypeParams", ImmutableList.copyOf(r.getGenericTypeParamsList())
                  )).collect(ImmutableList.toImmutableList())
      );
    }
  }
}
