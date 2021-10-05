package com.claro.compiler_backends.repl;

import com.claro.ClaroParser;
import com.claro.ClaroParserException;
import com.claro.compiler_backends.CompilerBackend;
import com.claro.compiler_backends.ParserUtil;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.compiler_backends.repl.repl_terminal.ReplTerminal;
import com.claro.intermediate_representation.ProgramNode;
import com.claro.intermediate_representation.Target;

import java.util.HashSet;

public class Repl implements CompilerBackend {

  // To maintain REPL state, we're gonna keep a heap to reuse across all REPL statements.
  private final ScopedHeap SCOPED_HEAP = new ScopedHeap();
  private final HashSet<String> SYMBOL_TABLE = new HashSet<>();
  private final HashSet<String> USED_SYMBOL_TABLE = new HashSet<>();

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

    try {
      ((ProgramNode) parser.parse().value).generateTargetOutput(Target.REPL, SCOPED_HEAP);
    } catch (ClaroParserException e) {
      System.out.println(String.format("Error: %s", e.getMessage()));
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Java is stupid.
    return null;
  }

  private ClaroParser getParser(String currLine) {
    ClaroParser parser = ParserUtil.createParser(currLine.trim());

    // These are unused for the interpreted case. We're not gonna produce any files.
    parser.generatedClassName = "";
    parser.package_string = "";

    return parser;
  }
}
