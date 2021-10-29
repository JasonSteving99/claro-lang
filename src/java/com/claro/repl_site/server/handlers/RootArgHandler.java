package com.claro.repl_site.server.handlers;

import com.claro.ClaroParser;
import com.claro.ClaroParserException;
import com.claro.compiler_backends.ParserUtil;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.ProgramNode;
import com.claro.intermediate_representation.Target;
import io.javalin.http.Context;
import io.javalin.http.Handler;

import java.util.Optional;

public class RootArgHandler implements Handler {

  private final ScopedHeap scopedHeap;
  private final StringBuilder stmtHistoryStringBuilder;
  private final StringBuilder printedStringsStringBuilder = new StringBuilder();
  private static final String HISTORICAL_STMT_FMT_STR = "<p>>>> %s</p>";

  public RootArgHandler() {
    this.scopedHeap = new ScopedHeap();
    this.scopedHeap.enterNewScope();
    // This is disabled for the REPL case, we're not going to know if everything is used at the end of each line.
    this.scopedHeap.disableCheckUnused();

    this.stmtHistoryStringBuilder = new StringBuilder();
  }

  @Override
  public void handle(io.javalin.http.Context context) throws Exception {
    String stmt = context.pathParam("arg");
    stmtHistoryStringBuilder.append(String.format(HISTORICAL_STMT_FMT_STR, stmt));
    Optional.ofNullable(evaluateStatement(stmt)).ifPresent(stmtHistoryStringBuilder::append);

    context.html(
        String.format(
            "<p>Claro REPL</p>%s",
            stmtHistoryStringBuilder
        )
    );
  }

  private String evaluateStatement(String stmt) {
    try {
      ((ProgramNode) getClaroParser(stmt).parse().value).generateTargetOutput(Target.REPL, scopedHeap).toString();

      // Get the output that we coerced to being appended to this StringBuilder, and then make sure to consume that
      // output to reset the StringBuilder for the next REPL statement.
      String printedOutput = printedStringsStringBuilder.toString();
      printedStringsStringBuilder.delete(0, printedStringsStringBuilder.length());
      return printedOutput;
    } catch (ClaroParserException e) {
      return String.format("Error: %s", e.getMessage());
    } catch (Exception e) {
      return e.toString();
    }
  }

  private ClaroParser getClaroParser(String stmt) {
    ClaroParser claroParser = ParserUtil.createParser(stmt);
    // These are unused for the interpreted case. We're not gonna produce any files.
    claroParser.generatedClassName = "";
    claroParser.package_string = "";

    // We need to configure the PrintStmt node to defer printing output and instead collect printed strings so
    // that we can ship the printed output to the user on the REPL-site.
    claroParser.printerDelegate = str -> printedStringsStringBuilder.append(String.format("<p>%s</p>", str));

    return claroParser;
  }
}
