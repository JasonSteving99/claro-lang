package com.claro.module_system.clarodocs.html_rendering.initializers;

import com.claro.module_system.clarodocs.html_rendering.Util;
import com.claro.module_system.clarodocs.html_rendering.procedures.ProcedureHtml;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.template.soy.tofu.SoyTofu;

import java.util.stream.Collectors;

import static com.claro.module_system.clarodocs.html_rendering.Util.GrammarPart.INITIALIZERS;

// TODO(steving) Migrate to pre-compiled SoySauce templates instead of slower (hence deprecated) SoyTofu templates.
@SuppressWarnings("deprecation")
public class InitializersHtml {
  private static final SoyTofu.Renderer INITIALIZERS_TEMPLATE =
      Util.SOY.newRenderer("initializers.initializers");
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
                    .map(ProcedureHtml::generateProcedureHtml)
                    // TODO(steving) Temporary until migrate to using SanitizedContent here.
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"))
            )
        ));
  }
}
