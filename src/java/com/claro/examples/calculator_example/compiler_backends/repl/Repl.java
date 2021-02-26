package com.claro.examples.calculator_example.compiler_backends.repl;

import com.claro.examples.calculator_example.CalculatorParser;
import com.claro.examples.calculator_example.CalculatorParserException;
import com.claro.examples.calculator_example.compiler_backends.CompilerBackend;
import com.claro.examples.calculator_example.compiler_backends.ParserUtil;
import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.compiler_backends.repl.repl_terminal.ReplTerminal;
import com.claro.examples.calculator_example.intermediate_representation.ProgramNode;
import com.claro.examples.calculator_example.intermediate_representation.Target;

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
    CalculatorParser parser = getParser(instruction);

    try {
      ((ProgramNode) parser.parse().value).generateTargetOutput(Target.REPL, SCOPED_HEAP);
    } catch (CalculatorParserException e) {
      System.out.println(String.format("Error: %s", e.getMessage()));
    } catch (Exception e) {
      System.out.println("Invalid Syntax Error:");
      e.printStackTrace();
    }

    // Java is stupid.
    return null;
  }

  private CalculatorParser getParser(String currLine) {
    CalculatorParser parser = ParserUtil.createParser(currLine.trim());

    // These are unused for the interpreted case. We're not gonna produce any files.
    parser.generatedClassName = "";
    parser.package_string = "";

    return parser;
  }
}
