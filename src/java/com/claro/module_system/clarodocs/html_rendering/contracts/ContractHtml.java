package com.claro.module_system.clarodocs.html_rendering.contracts;

import com.claro.intermediate_representation.types.Types;
import com.claro.module_system.clarodocs.html_rendering.Util;
import com.claro.module_system.clarodocs.html_rendering.procedures.ProcedureHtml;
import com.claro.module_system.clarodocs.html_rendering.typedefs.TypeHtml;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.common.base.Joiner;

import java.util.stream.Collectors;

import static com.claro.module_system.clarodocs.html_rendering.Util.GrammarPart.*;

public class ContractHtml {

  private static final String CONTRACT_DEF_CLASS_NAME = "contract-def";
  private static final String CONTRACT_DEF_TEMPLATE = CONTRACT + " %s" + LT + "%s" + GT + " {\n%s\n}";
  private static final String CONTRACT_IMPL_CLASS_NAME = "contract-def";
  private static final String CONTRACT_IMPL_TEMPLATE = IMPLEMENT + " %s" + LT + "%s" + GT + SEMICOLON;

  public static void renderContractDefHtml(
      StringBuilder res, SerializedClaroModule.ExportedContractDefinition contractDef) {
    res.append(String.format(
        Util.wrapAsDefaultCodeBlock(CONTRACT_DEF_CLASS_NAME, contractDef.getName(), CONTRACT_DEF_TEMPLATE),
        contractDef.getName().substring(0, contractDef.getName().indexOf('$')),
        Joiner.on(", ").join(contractDef.getTypeParamNamesList()),
        contractDef.getSignaturesList().stream()
            .map(ProcedureHtml::generateProcedureHtml)
            .collect(Collectors.joining("\n"))
    ));
  }

  public static void renderContractImplHtml(
      StringBuilder res, SerializedClaroModule.ExportedContractImplementation contractImpl) {
    String implementedContractName = contractImpl.getImplementedContractName();
    res.append(String.format(
        Util.wrapAsDefaultCodeBlock(CONTRACT_IMPL_CLASS_NAME, implementedContractName, CONTRACT_IMPL_TEMPLATE),
        implementedContractName.substring(0, implementedContractName.indexOf('$')),
        contractImpl.getConcreteTypeParamsList().stream()
            .map(t -> TypeHtml.renderTypeHtml(Types.parseTypeProto(t)).toString())
            .collect(Collectors.joining(", "))
    ));
  }
}
