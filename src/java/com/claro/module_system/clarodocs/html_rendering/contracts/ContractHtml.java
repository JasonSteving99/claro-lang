package com.claro.module_system.clarodocs.html_rendering.contracts;

import com.claro.intermediate_representation.types.Types;
import com.claro.module_system.clarodocs.html_rendering.Util;
import com.claro.module_system.clarodocs.html_rendering.procedures.ProcedureHtml;
import com.claro.module_system.clarodocs.html_rendering.typedefs.TypeHtml;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.tofu.SoyTofu;

public class ContractHtml {
  private static final SoyTofu SOY = Util.SOY.forNamespace("contracts");

  public static void renderContractDefHtml(
      StringBuilder res, SerializedClaroModule.ExportedContractDefinition contractDef) {
    ImmutableMap.Builder<String, Object> args = ImmutableMap.builder();
    args.put("name", contractDef.getName().substring(0, contractDef.getName().indexOf('$')))
        .put("genericTypeParams", ImmutableList.copyOf(contractDef.getTypeParamNamesList()))
        .put(
            "signatures",
            contractDef.getSignaturesList().stream()
                .map(ProcedureHtml::generateProcedureHtml)
                .collect(ImmutableList.toImmutableList())
        );
    res.append(Util.renderSoy(SOY, "contractDef", args.build()));
  }

  public static void renderContractImplHtml(
      StringBuilder res, SerializedClaroModule.ExportedContractImplementation contractImpl) {
    String implementedContractName = contractImpl.getImplementedContractName();
    ImmutableMap.Builder<String, Object> args = ImmutableMap.builder();
    args.put("name", implementedContractName.substring(0, implementedContractName.indexOf('$')))
        .put(
            "genericTypeParams",
            contractImpl.getConcreteTypeParamsList().stream()
                .map(t -> TypeHtml.renderTypeHtml(Types.parseTypeProto(t)))
                .collect(ImmutableList.toImmutableList())
        );
    res.append(Util.renderSoy(SOY, "contractImpl", args.build()));
  }
}
