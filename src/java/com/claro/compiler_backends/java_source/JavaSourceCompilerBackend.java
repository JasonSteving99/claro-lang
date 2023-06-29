package com.claro.compiler_backends.java_source;

import com.claro.ClaroParser;
import com.claro.ClaroParserException;
import com.claro.compiler_backends.CompilerBackend;
import com.claro.compiler_backends.ParserUtil;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.ProgramNode;
import com.claro.intermediate_representation.Target;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.stdlib.StdLibUtil;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Scanner;

public class JavaSourceCompilerBackend implements CompilerBackend {
  private final boolean SILENT;
  private final Optional<String> GENERATED_CLASSNAME;
  private final Optional<String> PACKAGE_STRING;
  private final ImmutableList<SrcFile> SRCS;

  // TODO(steving) Migrate this file to use an actual cli library.
  // TODO(steving) Consider Apache Commons Cli 1.4 https://commons.apache.org/proper/commons-cli/download_cli.cgi
  public JavaSourceCompilerBackend(String... args) {
    this.SILENT = args.length >= 1 && args[0].equals("--silent=true");
    // For now if you're gonna pass 2 args you gotta pass them all...
    if (args.length >= 2) {
      // args[1] holds the generated classname.
      this.GENERATED_CLASSNAME = Optional.of(args[1].substring("--classname=" .length()));
      // args[2] holds the flag for package...
      String packageArg = args[2].substring("--package=" .length());
      this.PACKAGE_STRING = Optional.of(packageArg.equals("") ? "" : "package " + packageArg + ";\n\n");
      // args[3] is an *OPTIONAL* flag holding the list of files in this Claro module that should be read in instead of
      // reading a single Claro file's contents from stdin.
      if (args.length >= 4) {
        this.SRCS =
            ImmutableList.copyOf(args[3].substring("--srcs=" .length()).split(","))
                .stream()
                .map(f -> SrcFile.forFilenameAndPath(f.substring(f.lastIndexOf('/') + 1, f.length() - 6), f))
                .collect(ImmutableList.toImmutableList());
      } else {
        // TODO(steving) This is getting overly complicated just b/c I don't want to fix Riju's config. Go update Riju
        //    so that this can all be simplified. Only need to continue supporting the single file case via stdin just
        //    in order to avoid breaking Riju config which I'm not going to touch now.
        StdLibUtil.setupBuiltinTypes = true;
        // Turns out there's going to just be a single file that'll be consumed on STDIN.
        this.SRCS = ImmutableList.of(SrcFile.create(this.GENERATED_CLASSNAME.get(), System.in));
      }
    } else {
      this.GENERATED_CLASSNAME = Optional.empty();
      this.PACKAGE_STRING = Optional.empty();
      this.SRCS = ImmutableList.of(SrcFile.create("", System.in));
    }
  }

  @Override
  public void run() throws Exception {
    ScopedHeap scopedHeap = new ScopedHeap();
    scopedHeap.enterNewScope();
    if (this.SRCS.size() == 1) {
      checkTypesAndGenJavaSourceForSrcFiles(this.SRCS.get(0), ImmutableList.of(), scopedHeap);
    } else {
      SrcFile mainSrcFile = null;
      ImmutableList.Builder<SrcFile> nonMainSrcFiles = ImmutableList.builder();
      for (SrcFile srcFile : this.SRCS) {
        if (srcFile.getFilename().equals(this.GENERATED_CLASSNAME.orElse(""))) {
          mainSrcFile = srcFile;
        } else {
          nonMainSrcFiles.add(srcFile);
        }
      }
      checkTypesAndGenJavaSourceForSrcFiles(mainSrcFile, nonMainSrcFiles.build(), scopedHeap);
    }
  }

  private ClaroParser getParserForSrcFile(SrcFile srcFile) {
    Scanner scan = new Scanner(srcFile.getFileInputStream());

    StringBuilder inputProgram = new StringBuilder();
    while (scan.hasNextLine()) {
      inputProgram.append(scan.nextLine());
      // Scanner is being stupid and dropping all the newlines... so this may give an extra compared to what's in the
      // source file, but who cares, the grammar will handle it.
      inputProgram.append("\n");
    }

    return ParserUtil.createParser(inputProgram.toString());
  }

  private Node.GeneratedJavaSource checkTypesAndGenJavaSourceForSrcFiles(
      SrcFile mainSrcFile, ImmutableList<SrcFile> nonMainSrcFiles, ScopedHeap scopedHeap) throws Exception {
    ImmutableList<ClaroParser> nonMainSrcFileParsers =
        nonMainSrcFiles.stream()
            .map(f -> {
              ClaroParser currNonMainSrcFileParser = getParserForSrcFile(f);
              currNonMainSrcFileParser.generatedClassName = f.getFilename();
              this.PACKAGE_STRING.ifPresent(s -> currNonMainSrcFileParser.package_string = s);
              return currNonMainSrcFileParser;
            })
            .collect(ImmutableList.toImmutableList());
    ClaroParser mainSrcFileParser = getParserForSrcFile(mainSrcFile);
    this.GENERATED_CLASSNAME.ifPresent(s -> mainSrcFileParser.generatedClassName = s);
    this.PACKAGE_STRING.ifPresent(s -> mainSrcFileParser.package_string = s);

    try {
      // Parse the non-main src files first.
      ImmutableList.Builder<ProgramNode> parsedNonMainSrcFilePrograms = ImmutableList.builder();
      for (ClaroParser nonMainSrcFileParser : nonMainSrcFileParsers) {
        parsedNonMainSrcFilePrograms.add((ProgramNode) nonMainSrcFileParser.parse().value);
      }
      // Push these parsed non-main src programs to where they'll be found for type checking and codegen.
      ProgramNode.nonMainFiles = parsedNonMainSrcFilePrograms.build();
      // Parse the main src file.
      ProgramNode mainSrcFileProgramNode = ((ProgramNode) mainSrcFileParser.parse().value);
      // Here, type checking and codegen of ALL src files is happening at once.
      StringBuilder generateTargetOutputRes =
          mainSrcFileProgramNode.generateTargetOutput(Target.JAVA_SOURCE, scopedHeap, StdLibUtil::registerIdentifiers);
      int totalParserErrorsFound =
          mainSrcFileParser.errorsFound +
          nonMainSrcFileParsers.stream().map(p -> p.errorsFound).reduce(Integer::sum).orElse(0);
      if (totalParserErrorsFound == 0 && Expr.typeErrorsFound.isEmpty() && ProgramNode.miscErrorsFound.isEmpty()) {
        System.out.println(generateTargetOutputRes);
        System.exit(0);
      } else {
        ClaroParser.errorMessages.forEach(Runnable::run);
        Expr.typeErrorsFound.forEach(e -> e.accept(mainSrcFileParser.generatedClassName));
        ProgramNode.miscErrorsFound.forEach(Runnable::run);
        warnNumErrorsFound(totalParserErrorsFound);
        System.exit(1);
      }
    } catch (ClaroParserException e) {
      ClaroParser.errorMessages.forEach(Runnable::run);
      Expr.typeErrorsFound.forEach(err -> err.accept(mainSrcFileParser.generatedClassName));
      ProgramNode.miscErrorsFound.forEach(Runnable::run);
      System.err.println(e.getMessage());
      warnNumErrorsFound(mainSrcFileParser.errorsFound
                         + nonMainSrcFileParsers.stream().map(p -> p.errorsFound).reduce(Integer::sum).orElse(0));
      if (this.SILENT) {
        // We found errors, there's no point to emit the generated code.
        System.exit(1);
      } else {
        throw e;
      }
    } catch (Exception e) {
      ClaroParser.errorMessages.forEach(Runnable::run);
      Expr.typeErrorsFound.forEach(err -> err.accept(mainSrcFileParser.generatedClassName));
      ProgramNode.miscErrorsFound.forEach(Runnable::run);
      System.err.println(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      warnNumErrorsFound(mainSrcFileParser.errorsFound
                         + nonMainSrcFileParsers.stream().map(p -> p.errorsFound).reduce(Integer::sum).orElse(0));
      if (this.SILENT) {
        // We found errors, there's no point to emit the generated code.
        System.exit(1);
      } else {
        throw e;
      }
    }
    // Should actually be unreachable.
    throw new RuntimeException(
        "Internal Compiler Error! Should be unreachable. JavaSourceCompilerBackend failed to exit with explicit error code.");
  }

  private void warnNumErrorsFound(int totalParserErrorsFound) {
    int totalErrorsFound = totalParserErrorsFound + Expr.typeErrorsFound.size() + ProgramNode.miscErrorsFound.size();
    System.err.println(Math.max(totalErrorsFound, 1) + " Error" + (totalErrorsFound > 1 ? "s" : ""));
  }

  @AutoValue
  static abstract class SrcFile {
    abstract String getFilename();

    abstract InputStream getFileInputStream();

    static SrcFile forFilenameAndPath(String filename, String path) {
      try {
        return new AutoValue_JavaSourceCompilerBackend_SrcFile(
            filename,
            Files.newInputStream(FileSystems.getDefault().getPath(path), StandardOpenOption.READ)
        );
      } catch (IOException e) {
        throw new RuntimeException("File not found:", e);
      }
    }

    static SrcFile create(String filename, InputStream inputStream) {
      return new AutoValue_JavaSourceCompilerBackend_SrcFile(filename, inputStream);
    }
  }
}
