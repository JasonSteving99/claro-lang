package com.claro.module_system.clarodocs.html_rendering;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.claro.module_system.clarodocs.html_rendering.Util.CssClass.*;

public class Util {

  public enum CssClass {
    TOKEN_GROUP_1("tokenGroup1"),
    TOKEN_GROUP_2("tokenGroup2"),
    TOKEN_GROUP_3("tokenGroup3"),
    TOKEN_GROUP_4("tokenGroup4");

    private final String cssClassStr;

    CssClass(String cssClassStr) {
      this.cssClassStr = cssClassStr;
    }

    @Override
    public String toString() {
      return cssClassStr;
    }
  }

  public enum GrammarPart {
    ALIAS(TOKEN_GROUP_1, "alias"),
    ATOM(TOKEN_GROUP_1, "atom"),
    BLOCKING(TOKEN_GROUP_1, "blocking"),
    CONSUMER(TOKEN_GROUP_1, "consumer"),
    FUNCTION(TOKEN_GROUP_1, "function"),
    GRAPH(TOKEN_GROUP_1, "graph"),
    NEWTYPE(TOKEN_GROUP_1, "newtype"),
    PROVIDER(TOKEN_GROUP_1, "provider"),


    COMMA(TOKEN_GROUP_2, ","),
    ARROW(TOKEN_GROUP_2, "->"),
    COLON(TOKEN_GROUP_2, ":"),
    SEMICOLON(TOKEN_GROUP_2, ";"),
    LT(TOKEN_GROUP_2, "&lt;"),
    GT(TOKEN_GROUP_2, "&gt;"),
    CONTRACT(TOKEN_GROUP_2, "contract"),
    ENDPOINT_HANDLERS(TOKEN_GROUP_2, "endpoint_handlers"),
    IMPLEMENT(TOKEN_GROUP_2, "implement"),
    INITIALIZERS(TOKEN_GROUP_2, "initializers"),
    MUT(TOKEN_GROUP_2, "mut"),
    REQUIRES(TOKEN_GROUP_2, "requires"),
    UNWRAPPERS(TOKEN_GROUP_2, "unwrappers"),


    UNDERSCORE(TOKEN_GROUP_3, "underscore"),
    BOOLEAN(TOKEN_GROUP_3, "boolean"),
    FLOAT(TOKEN_GROUP_3, "float"),
    FUTURE(TOKEN_GROUP_3, "future"),
    INT(TOKEN_GROUP_3, "int"),
    ONEOF(TOKEN_GROUP_3, "oneof"),
    STRING(TOKEN_GROUP_3, "string"),
    STRUCT(TOKEN_GROUP_3, "struct"),
    TUPLE(TOKEN_GROUP_3, "tuple"),


    QUESTION_MARK(TOKEN_GROUP_4, "?"),
    BAR(TOKEN_GROUP_4, "|");

    private final String highlightedHtml;

    GrammarPart(CssClass cssClass, String text) {
      this.highlightedHtml = highlight(cssClass, text);
    }

    @Override
    public String toString() {
      return highlightedHtml;
    }
  }

  private static final String DEFAULT_CODE_BLOCK_TEMPLATE =
      "<pre>\n" +
      "  <code class='%s' id='%s'>\n" +
      "%s" +
      "  </code>\n" +
      "</pre>\n";

  public static String highlight(CssClass cssClass, String content) {
    return String.format("<span class='%s'>%s</span>", cssClass, content);
  }

  public static String wrapAsDefaultCodeBlock(String className, String id, String code) {
    return wrapAsDefaultCodeBlock(className, id, code, 0);
  }

  public static String wrapAsDefaultCodeBlockWithIndentationLevel(
      String className, String id, String code, int indentationLevel) {
    return wrapAsDefaultCodeBlock(className, id, code, indentationLevel);
  }

  private static String wrapAsDefaultCodeBlock(String className, String id, String code, int indentationLevel) {
    final String indentationStr = "    ";
    StringBuilder indentedPrefix =
        new StringBuilder(indentationStr.length() * (indentationLevel + 1)).append(indentationStr);
    while (indentationLevel-- > 0) {
      indentedPrefix.append(indentationStr);
    }
    return String.format(
        DEFAULT_CODE_BLOCK_TEMPLATE,
        className,
        id,
        Arrays.asList(code.split("\n")).stream()
            .map(line -> indentedPrefix + line)
            .collect(Collectors.joining("\n"))
    );
  }
}
