package com.claro.module_system.clarodocs.html_rendering.unwrappers;

import com.claro.module_system.clarodocs.html_rendering.Util;
import com.claro.module_system.clarodocs.html_rendering.procedures.ProcedureHtml;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;

import java.util.stream.Collectors;

import static com.claro.module_system.clarodocs.html_rendering.Util.GrammarPart.UNWRAPPERS;

public class UnwrappersHtml {
  public static final String UNWRAPPERS_CLASS = "unwrappers";
  public static final String UNWRAPPERS_BLOCK_TEMPLATE =
      UNWRAPPERS + " %s {\n%s\n}";

  public static void renderUnwrappersBlock(
      StringBuilder res,
      String unwrappedTypeName,
      SerializedClaroModule.ExportedTypeDefinitions.ProcedureList procedures) {
    res.append(
        Util.wrapAsDefaultCodeBlock(
            UNWRAPPERS_CLASS,
            unwrappedTypeName,
            String.format(
                UNWRAPPERS_BLOCK_TEMPLATE,
                unwrappedTypeName,
                procedures.getProceduresList().stream()
                    .map(ProcedureHtml::generateProcedureHtml)
                    // TODO(steving) Temporary until migrate to using SanitizedContent here.
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"))
            )
        ));
  }
}
