package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;
import org.apache.commons.text.StringEscapeUtils;

import java.util.HashMap;

public class PrintStmt extends Stmt {

  public PrintStmt(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    String expr_java_source = this.getChildren().get(0).generateJavaSourceOutput().toString();
    return new StringBuilder(
      String.format(
        "System.out.println(String.format(\"%s == %%s\", %s));\n",
        StringEscapeUtils.escapeJava(expr_java_source),
        expr_java_source
      )
    );
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    System.out.println(this.getChildren().get(0).generateInterpretedOutput(heap));
    return null;
  }
}
