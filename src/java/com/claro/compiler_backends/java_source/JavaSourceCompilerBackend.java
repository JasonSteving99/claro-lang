package com.claro.compiler_backends.java_source;

import com.claro.ClaroParser;
import com.claro.ClaroParserException;
import com.claro.compiler_backends.CompilerBackend;
import com.claro.compiler_backends.ParserUtil;
import com.claro.intermediate_representation.ProgramNode;
import com.claro.intermediate_representation.Target;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.stdlib.StdLibUtil;

import java.util.Optional;
import java.util.Scanner;

public class JavaSourceCompilerBackend implements CompilerBackend {
  private final boolean SILENT;
  private final Optional<String> GENERATED_CLASSNAME;
  private final Optional<String> PACKAGE_STRING;

  // TODO(steving) Migrate this file to use an actual cli library.
  // TODO(steving) Consider Apache Commons Cli 1.4 https://commons.apache.org/proper/commons-cli/download_cli.cgi
  public JavaSourceCompilerBackend(String... args) {
    this.SILENT = args.length >= 1 && args[0].equals("--silent=true");
    // For now if you're gonna pass 2 args you gotta pass them all...
    if (args.length >= 2) {
      // args[1] holds the generated classname.
      this.GENERATED_CLASSNAME = Optional.of(args[1].substring("--classname=".length()));
      // args[2] holds the flag for package...
      String packageArg = args[2].substring("--package=".length());
      this.PACKAGE_STRING = Optional.of(packageArg.equals("") ? "" : "package " + packageArg + ";\n\n");
    } else {
      this.GENERATED_CLASSNAME = Optional.empty();
      this.PACKAGE_STRING = Optional.empty();
    }
  }

  @Override
  public void run() throws Exception {
    // TODO(steving) Figure out a way to take file contents by reading files within Bazel scope.
    Scanner scan = new Scanner(System.in);

    StringBuilder inputProgram = new StringBuilder();
    while (scan.hasNextLine()) {
      inputProgram.append(scan.nextLine());
      // Scanner is being stupid and dropping all the newlines... so this may give an extra compared to what's in the
      // source file, but who cares, the grammar will handle it.
      inputProgram.append("\n");
    }

    ClaroParser parser = ParserUtil.createParser(inputProgram.toString());

    this.GENERATED_CLASSNAME.ifPresent(s -> parser.generatedClassName = s);
    this.PACKAGE_STRING.ifPresent(s -> parser.package_string = s);

    try {
      ProgramNode programNode = ((ProgramNode) parser.parse().value);
      StringBuilder generatedCode =
          programNode.generateTargetOutput(Target.JAVA_SOURCE, StdLibUtil::registerIdentifiers);
      if (parser.errorsFound == 0 && Expr.typeErrorsFound.isEmpty() && ProgramNode.miscErrorsFound.isEmpty()) {
        System.out.println(generatedCode);
        System.exit(0);
      } else {
        parser.errorMessages.forEach(Runnable::run);
        Expr.typeErrorsFound.forEach(e -> e.accept(parser.generatedClassName));
        ProgramNode.miscErrorsFound.forEach(Runnable::run);
        warnErrorsFound(parser);
        System.exit(1);
      }
    } catch (ClaroParserException e) {
      parser.errorMessages.forEach(Runnable::run);
      Expr.typeErrorsFound.forEach(err -> err.accept(parser.generatedClassName));
      ProgramNode.miscErrorsFound.forEach(Runnable::run);
      System.err.println(e.getMessage());
      warnErrorsFound(parser);
      if (this.SILENT) {
        // We found errors, there's no point to emit the generated code.
        System.exit(1);
      } else {
        throw e;
      }
    } catch (Exception e) {
      parser.errorMessages.forEach(Runnable::run);
      Expr.typeErrorsFound.forEach(err -> err.accept(parser.generatedClassName));
      ProgramNode.miscErrorsFound.forEach(Runnable::run);
      System.err.println(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      warnErrorsFound(parser);
      if (this.SILENT) {
        // We found errors, there's no point to emit the generated code.
        System.exit(1);
      } else {
        throw e;
      }
    }
  }

  private void warnErrorsFound(ClaroParser claroParser) {
    int totalErrorsFound = claroParser.errorsFound + Expr.typeErrorsFound.size() + ProgramNode.miscErrorsFound.size();
    System.err.println(Math.max(totalErrorsFound, 1) + " Error" + (totalErrorsFound > 1 ? "s" : ""));
  }
}
