package com.claro.compiler_backends.repl;

import com.claro.ClaroParser;
import com.claro.ClaroParserException;
import com.claro.compiler_backends.CompilerBackend;
import com.claro.compiler_backends.ParserUtil;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.compiler_backends.repl.repl_terminal.ReplTerminal;
import com.claro.intermediate_representation.ProgramNode;
import com.claro.intermediate_representation.Target;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.stdlib.StdLibUtil;

import java.util.function.Consumer;

public class Repl implements CompilerBackend {

  // To maintain REPL state, we're gonna keep a heap to reuse across all REPL statements.
  private final ScopedHeap SCOPED_HEAP = new ScopedHeap();

  private Consumer<ScopedHeap> setupStdLibConsumerFn = StdLibUtil::registerIdentifiers;

  public Repl() {
    // Make sure that the REPL's heap is ready.
    SCOPED_HEAP.enterNewScope();
  }

  @Override
  public void run() {
    ReplTerminal replTerminal = new ReplTerminal(this::interpretInstruction);
    replTerminal.runTerminal();
  }

  private Void interpretInstruction(String instruction) {
    // Need a parser for the next line. Unfortunately doesn't seem like we can reuse existing ones.
    ClaroParser parser = getParser(instruction);

    // This is a fair bit of a hack to just delete the '.claro:1: ' prefix from in front of the error messages.
    parser.generatedClassName = "\b\b\b\b\b\b\b\b\b\b";

    try {
      ((ProgramNode) parser.parse().value).generateTargetOutput(Target.REPL, SCOPED_HEAP, setupStdLibConsumerFn);
      setupStdLibConsumerFn = s -> {
      }; // We'll keep reusing the same ScopedHeap, so we don't need to do this again.
      if (!(parser.errorsFound == 0 && Expr.typeErrorsFound.isEmpty() && ProgramNode.miscErrorsFound.isEmpty())) {
        ClaroParser.errorMessages.forEach(Runnable::run);
        Expr.typeErrorsFound.forEach(e -> e.accept(parser.generatedClassName));
        ProgramNode.miscErrorsFound.forEach(Runnable::run);
        warnErrorsFound(parser);
      }
    } catch (ClaroParserException e) {
      parser.errorMessages.forEach(Runnable::run);
      Expr.typeErrorsFound.forEach(err -> err.accept(parser.generatedClassName));
      ProgramNode.miscErrorsFound.forEach(Runnable::run);
      System.out.println(String.format("Error: %s", e.getMessage()));
      warnErrorsFound(parser);
    } catch (Exception e) {
      parser.errorMessages.forEach(Runnable::run);
      Expr.typeErrorsFound.forEach(err -> err.accept(parser.generatedClassName));
      ProgramNode.miscErrorsFound.forEach(Runnable::run);
      e.printStackTrace();
      warnErrorsFound(parser);
    } finally {
      // At the end of everything, clear out all the errors so we don't relog them.
      parser.errorsFound = 0;
      parser.errorMessages.clear();
      Expr.typeErrorsFound.clear();
      ProgramNode.miscErrorsFound.clear();
    }

    // Java is stupid.
    return null;
  }

  private ClaroParser getParser(String currLine) {
    ClaroParser parser = ParserUtil.createParser(currLine.trim(), false);

    // These are unused for the interpreted case. We're not gonna produce any files.
    parser.generatedClassName = "";
    parser.package_string = "";

    return parser;
  }

  private void warnErrorsFound(ClaroParser claroParser) {
    int totalErrorsFound = claroParser.errorsFound + Expr.typeErrorsFound.size() + ProgramNode.miscErrorsFound.size();
    System.err.println(totalErrorsFound + " Error" + (totalErrorsFound > 1 ? "s" : ""));
  }
}
