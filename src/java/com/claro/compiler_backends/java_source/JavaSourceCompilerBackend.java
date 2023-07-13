package com.claro.compiler_backends.java_source;

import com.claro.ClaroParser;
import com.claro.ClaroParserException;
import com.claro.ModuleApiParser;
import com.claro.compiler_backends.CompilerBackend;
import com.claro.compiler_backends.ParserUtil;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.ModuleNode;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.ProgramNode;
import com.claro.intermediate_representation.Target;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.module_system.ModuleApiParserUtil;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.stdlib.StdLibUtil;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.devtools.common.options.OptionsParser;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
  private final Optional<String> OPTIONAL_UNIQUE_MODULE_NAME;

  // TODO(steving) Migrate this file to use an actual cli library.
  // TODO(steving) Consider Apache Commons Cli 1.4 https://commons.apache.org/proper/commons-cli/download_cli.cgi
  public JavaSourceCompilerBackend(String... args) {
    JavaSourceCompilerBackendCLIOptions options = parseCLIOptions(args);

    if (options.java_package.isEmpty() || options.srcs.isEmpty()) {
      System.err.println("Error: --java_package and [--src ...]+ are required args.");
    }
    if (options.classname.isEmpty() == options.unique_module_name.isEmpty()) {
      System.err.println("Error: Exactly one of --unique_module_name and --classname should be set.");
    }

    this.SILENT = options.silent;
    this.GENERATED_CLASSNAME = Optional.ofNullable(options.classname.isEmpty() ? null : options.classname);
    this.PACKAGE_STRING = Optional.of(options.java_package);
    this.SRCS =
        options.srcs
            .stream()
            .map(f -> SrcFile.forFilenameAndPath(f.substring(f.lastIndexOf('/') + 1, f.lastIndexOf('.')), f))
            .collect(ImmutableList.toImmutableList());
    this.OPTIONAL_UNIQUE_MODULE_NAME =
        Optional.ofNullable(options.unique_module_name.isEmpty() ? null : options.unique_module_name);
  }

  private static JavaSourceCompilerBackendCLIOptions parseCLIOptions(String... args) {
    OptionsParser parser = OptionsParser.newOptionsParser(JavaSourceCompilerBackendCLIOptions.class);
    parser.parseAndExitUponError(args);
    return parser.getOptions(JavaSourceCompilerBackendCLIOptions.class);
  }

  // Note: This method is assuming that whatever script allowed you to invoke the compiler directly has already done
  // validation that you have exactly 0 or 1 .claro_module_api files and, if 1, then --classname is set to "".
  @Override
  public void run() throws Exception {
    ScopedHeap scopedHeap = new ScopedHeap();
    scopedHeap.enterNewScope();
    if (this.SRCS.size() == 1) {
      checkTypesAndGenJavaSourceForSrcFiles(this.SRCS.get(0), ImmutableList.of(), Optional.empty(), scopedHeap);
    } else {
      // The main file will be required if this is not a module definition, otherwise a module api file is required.
      SrcFile mainSrcFile = null;
      Optional<SrcFile> optionalModuleApiFile = Optional.empty();
      ImmutableList.Builder<SrcFile> nonMainSrcFiles = ImmutableList.builder();
      for (SrcFile srcFile : this.SRCS) {
        if (!srcFile.getUsesClaroInternalFileSuffix()
            && this.GENERATED_CLASSNAME.isPresent()
            && srcFile.getFilename().equals(this.GENERATED_CLASSNAME.get())) {
          mainSrcFile = srcFile;
        } else if (srcFile.getUsesClaroModuleApiFileSuffix()) {
          // In this case we'll just assume that we're being asked to compile a module which should have no "main" as a
          // module in itself is not an executable thing. Create a synthetic main though for the sake of having a logical
          // unit around which to center compilation.
          mainSrcFile = SrcFile.create(
              // TODO(steving) In the future I think that I'll want to add some sort of hash of the input program that
              //   produced this module, so that I can have increased confidence that the produced program is the one
              //   we're expecting when we go to assemble the whole program.
              this.OPTIONAL_UNIQUE_MODULE_NAME.get(),
              CharSource.wrap("_ = 1;").asByteSource(StandardCharsets.UTF_8).openStream()
          );
          optionalModuleApiFile = Optional.of(srcFile);
        } else {
          nonMainSrcFiles.add(srcFile);
        }
      }
      checkTypesAndGenJavaSourceForSrcFiles(mainSrcFile, nonMainSrcFiles.build(), optionalModuleApiFile, scopedHeap);
    }
  }

  private ClaroParser getParserForSrcFile(SrcFile srcFile) {
    return ParserUtil.createParser(
        readFile(srcFile),
        srcFile.getFilename(),
        srcFile.getUsesClaroInternalFileSuffix(),
        /*escapeSpecialChars*/true
    );
  }

  private ModuleApiParser getModuleApiParserForSrcFile(SrcFile srcFile) {
    ModuleApiParser moduleApiParser = ModuleApiParserUtil.createParser(readFile(srcFile), srcFile.getFilename());
    moduleApiParser.generatedClassName = srcFile.getFilename();
    return moduleApiParser;
  }

  private static String readFile(SrcFile srcFile) {
    Scanner scan = new Scanner(srcFile.getFileInputStream());

    StringBuilder inputProgram = new StringBuilder();
    while (scan.hasNextLine()) {
      inputProgram.append(scan.nextLine());
      // Scanner is being stupid and dropping all the newlines... so this may give an extra compared to what's in the
      // source file, but who cares, the grammar will handle it.
      inputProgram.append("\n");
    }
    return inputProgram.toString();
  }

  private Node.GeneratedJavaSource checkTypesAndGenJavaSourceForSrcFiles(
      SrcFile mainSrcFile,
      ImmutableList<SrcFile> nonMainSrcFiles,
      Optional<SrcFile> optionalModuleApiSrcFile,
      ScopedHeap scopedHeap) throws Exception {
    ImmutableList<ClaroParser> nonMainSrcFileParsers =
        nonMainSrcFiles.stream()
            .map(f -> {
              ClaroParser currNonMainSrcFileParser = getParserForSrcFile(f);
              this.PACKAGE_STRING.ifPresent(
                  s -> currNonMainSrcFileParser.package_string = s.equals("") ? "" : "package " + s + ";\n\n");
              return currNonMainSrcFileParser;
            })
            .collect(ImmutableList.toImmutableList());
    Optional<ModuleApiParser> optionalModuleApiParser =
        optionalModuleApiSrcFile.map(this::getModuleApiParserForSrcFile);
    ClaroParser mainSrcFileParser = getParserForSrcFile(mainSrcFile);
    this.PACKAGE_STRING.ifPresent(s -> mainSrcFileParser.package_string = s.equals("") ? "" : "package " + s + ";\n\n");

    try {
      // Parse the non-main src files first.
      ImmutableList.Builder<ProgramNode> parsedNonMainSrcFilePrograms = ImmutableList.builder();
      for (ClaroParser nonMainSrcFileParser : nonMainSrcFileParsers) {
        parsedNonMainSrcFilePrograms.add((ProgramNode) nonMainSrcFileParser.parse().value);
      }
      // Push these parsed non-main src programs to where they'll be found for type checking and codegen.
      ProgramNode.nonMainFiles = parsedNonMainSrcFilePrograms.build();
      // Optionally push the module api file to where it'll be found during type checking to validate that the
      // nonMainSrcFilePrograms actually do export the necessary bindings.
      if (optionalModuleApiParser.isPresent()) {
        ProgramNode.moduleApiDef = Optional.of((ModuleNode) optionalModuleApiParser.get().parse().value);
      }
      // Parse the main src file.
      ProgramNode mainSrcFileProgramNode = ((ProgramNode) mainSrcFileParser.parse().value);
      // Here, type checking and codegen of ALL src files is happening at once.
      StringBuilder generateTargetOutputRes =
          mainSrcFileProgramNode.generateTargetOutput(Target.JAVA_SOURCE, scopedHeap, StdLibUtil::registerIdentifiers);
      int totalParserErrorsFound =
          mainSrcFileParser.errorsFound +
          nonMainSrcFileParsers.stream().map(p -> p.errorsFound).reduce(Integer::sum).orElse(0);
      if (totalParserErrorsFound == 0 && Expr.typeErrorsFound.isEmpty() && ProgramNode.miscErrorsFound.isEmpty()) {
        if (optionalModuleApiParser.isPresent()) {
          // Here, we were asked to compile a non-executable Claro Module, rather than an executable Claro program. So,
          // we need to populate and emit a SerializedClaroModule proto that can be used as a dep for other Claro
          // Modules/programs.
          serializeClaroModule(
              this.PACKAGE_STRING.get(),
              this.OPTIONAL_UNIQUE_MODULE_NAME.get(),
              optionalModuleApiSrcFile.get(),
              generateTargetOutputRes,
              nonMainSrcFiles
          );
        } else {
          // Here, we were simply asked to codegen an executable Claro program. Output the codegen'd Java source to
          // stdout directly where it will be piped by Claro's Bazel rules into the appropriate .java file.
          System.out.println(generateTargetOutputRes);
        }
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

  private static void serializeClaroModule(
      String projectPackage,
      String uniqueModuleName,
      SrcFile moduleApiSrcFile,
      StringBuilder moduleCodegen,
      ImmutableList<SrcFile> moduleImplFiles) throws IOException {
    SerializedClaroModule.Builder serializedClaroModuleBuilder =
        SerializedClaroModule.newBuilder()
            .setModuleDescriptor(
                SerializedClaroModule.UniqueModuleDescriptor.newBuilder()
                    .setProjectPackage(projectPackage)
                    .setUniqueModuleName(uniqueModuleName))
            .setModuleApiFile(
                SerializedClaroModule.ClaroSourceFile.newBuilder()
                    .setOriginalFilename(moduleApiSrcFile.getFilename())
                    .setOriginalFilenameBytes(
                        ByteString.copyFrom(ByteStreams.toByteArray(moduleApiSrcFile.getFileInputStream()))))
            .setStaticJavaCodegen(
                ByteString.copyFrom(moduleCodegen.toString().getBytes(StandardCharsets.UTF_8)));
    for (SrcFile moduleImplFile : moduleImplFiles) {
      serializedClaroModuleBuilder.addModuleImplFiles(
          SerializedClaroModule.ClaroSourceFile.newBuilder()
              .setOriginalFilename(moduleImplFile.getFilename())
              .setOriginalFilenameBytes(
                  ByteString.copyFrom(ByteStreams.toByteArray(moduleImplFile.getFileInputStream()))));
    }

    // Finally write the proto message to stdout where Claro's Bazel rules will pipe this output into the appropriate
    // .claro_module output file.
    serializedClaroModuleBuilder
        .build()
        .writeDelimitedTo(System.out);
  }

  private void warnNumErrorsFound(int totalParserErrorsFound) {
    int totalErrorsFound = totalParserErrorsFound + Expr.typeErrorsFound.size() + ProgramNode.miscErrorsFound.size();
    System.err.println(Math.max(totalErrorsFound, 1) + " Error" + (totalErrorsFound > 1 ? "s" : ""));
  }

  @AutoValue
  static abstract class SrcFile {
    abstract String getFilename();

    abstract InputStream getFileInputStream();

    abstract boolean getUsesClaroInternalFileSuffix();

    abstract boolean getUsesClaroModuleApiFileSuffix();

    static SrcFile forFilenameAndPath(String filename, String path) {
      try {
        return new AutoValue_JavaSourceCompilerBackend_SrcFile(
            filename,
            Files.newInputStream(FileSystems.getDefault().getPath(path), StandardOpenOption.READ),
            path.endsWith(".claro_internal"),
            path.endsWith(".claro_module_api")
        );
      } catch (IOException e) {
        throw new RuntimeException("File not found:", e);
      }
    }

    static SrcFile create(String filename, InputStream inputStream) {
      return new AutoValue_JavaSourceCompilerBackend_SrcFile(filename, inputStream, false, false);
    }
  }
}
