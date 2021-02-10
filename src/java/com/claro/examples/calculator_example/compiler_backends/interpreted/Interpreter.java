package com.claro.examples.calculator_example.compiler_backends.interpreted;

import com.claro.examples.calculator_example.CalculatorParser;
import com.claro.examples.calculator_example.compiler_backends.CompilerBackend;
import com.claro.examples.calculator_example.compiler_backends.ParserUtil;
import com.claro.examples.calculator_example.intermediate_representation.ProgramNode;
import com.claro.examples.calculator_example.intermediate_representation.Target;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Interpreter implements CompilerBackend {
  private final boolean SILENT;

  // TODO(steving) Migrate this file to use an actual cli library.
  // TODO(steving) Consider Apache Commons Cli 1.4 https://commons.apache.org/proper/commons-cli/download_cli.cgi
  public Interpreter(String... args) {
    this.SILENT = args.length >= 1 && args[0].equals("--silent");
  }

  @Override
  public void run() throws Exception {
    if (!this.SILENT) {
      System.out.println("Enter your expression:");
    }
    // TODO(steving) DO NOT SUBMIT until you fix this to figure out a way to take in these files within Bazel scope.
    String inputFile = "src/java/com/claro/examples/calculator_example/calculator_programs/second.calculator";
    Scanner scan = null;
    try {
      scan = new Scanner(new File(inputFile));
    } catch (FileNotFoundException e) {
      System.err.println(String.format("File not found: %s\nExiting.", inputFile));
      return;
    }
    StringBuilder inputProgram = new StringBuilder();
    while (scan.hasNextLine()) {
      inputProgram.append(scan.nextLine());
      // Scanner is being stupid and dropping all the newlines... so this may give an extra compared to what's in the
      // source file, but who cares, the grammar will handle it.
      inputProgram.append("\n");
    }

    CalculatorParser parser = ParserUtil.createParser(inputProgram.toString());

    // These are unused for the interpreted case. We're not gonna produce any files.
    parser.generatedClassName = "";
    parser.package_string = "";

    if (!this.SILENT) {
      System.out.print("= ");
    }

    // In INTERPRETED mode don't print out the parser result, it doesn't have a value.
    ((ProgramNode) parser.parse().value).generateTargetOutput(Target.INTERPRETED);
  }
}
