package com.claro.module_system.clarodocs.html_rendering.initializers;

import com.claro.module_system.clarodocs.html_rendering.Util;
import com.claro.module_system.clarodocs.html_rendering.procedures.ProcedureHtml;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;

import java.util.stream.Collectors;

import static com.claro.module_system.clarodocs.html_rendering.Util.GrammarPart.INITIALIZERS;

public class InitializersHtml {
  public static final String INITIALIZERS_CLASS = "initializers";
  public static final String INITIALIZERS_BLOCK_TEMPLATE =
      INITIALIZERS + " %s {\n%s\n}";

  public static void renderInitializersBlock(
      StringBuilder res,
      String initializedTypeName,
      SerializedClaroModule.ExportedTypeDefinitions.ProcedureList procedures) {
    res.append(
        Util.wrapAsDefaultCodeBlock(
            INITIALIZERS_CLASS,
            initializedTypeName,
            String.format(
                INITIALIZERS_BLOCK_TEMPLATE,
                initializedTypeName,
                procedures.getProceduresList().stream()
                    .map(procedure -> ProcedureHtml.generateProcedureHtmlWithIndentationLevel(procedure, 1))
                    .collect(Collectors.joining("\n"))
            )
        ));
  }
}
