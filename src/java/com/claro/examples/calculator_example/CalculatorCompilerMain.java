package com.claro.examples.calculator_example;
  
import com.claro.examples.calculator_example.intermediate_representation.ProgramNode;
import com.claro.examples.calculator_example.intermediate_representation.Target;

import java.io.StringReader;
import java.util.Scanner;

public class CalculatorCompilerMain {
  public static void main(String[] args) throws Exception {
    if (args.length == 0 || !args[0].equals("--silent")) {
      System.out.println("Enter your expression:");
    }
    Scanner scan = new Scanner(System.in);
    StringBuilder inputProgram = new StringBuilder();
    while(scan.hasNextLine()) {
      inputProgram.append(scan.nextLine());
      // Scanner is being stupid and dropping all the newlines... so this may give an extra compared to what's in the
      // source file, but who cares, the grammar will handle it.
      inputProgram.append("\n");
    }

    CalculatorParser parser = createParser(inputProgram.toString());

    // TODO(steving) Migrate this file to use an actual cli library.
    // For now if you're gonna pass 2 args you gotta pass them all...
    if (args.length >= 2) {
      // args[1] holds the generated classname.
      parser.generatedClassName = args[1].substring("--classname=".length());
      // args[2] holds the flag for package...
      String packageArg = args[2].substring("--package=".length());
      parser.package_string = packageArg.equals("") ? "" : "package " + packageArg + ";\n\n";
    }
    if (args.length == 0 || !args[0].equals("--silent")) {
      System.out.print("= ");
    }
    // TODO(steving) Update to take the compiler target as an arg.
    System.out.println(((ProgramNode) parser.parse().value).generateTargetOutput(Target.JAVA_SOURCE));
  }

  private static CalculatorLexer createLexer(String input) {
    return new CalculatorLexer(new StringReader(input));
  }
 
  private static CalculatorParser createParser(String input) {
    return new CalculatorParser(createLexer(input));
  }
}
