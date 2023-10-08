package com.claro.module_system.clarodocs.html_rendering.aliases;

import com.claro.intermediate_representation.types.Type;
import com.claro.module_system.clarodocs.html_rendering.Util;
import com.claro.module_system.clarodocs.html_rendering.typedefs.TypeHtml;

import static com.claro.module_system.clarodocs.html_rendering.Util.GrammarPart.*;

public class AliasHtml {

  private static final String ALIAS_DEF_CLASS_NAME = "alias-def";
  private static final String ALIAS_DEF_TEMPLATE =
      ALIAS + " %s " + COLON + " %s" + SEMICOLON + "\n";

  public static void renderAliasHtml(
      StringBuilder res, String aliasName, Type wrappedType) {
    res.append(String.format(
        Util.wrapAsDefaultCodeBlock(ALIAS_DEF_CLASS_NAME, aliasName, ALIAS_DEF_TEMPLATE),
        aliasName,
        TypeHtml.renderTypeHtml(wrappedType)
    ));
  }
}
