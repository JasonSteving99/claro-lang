package com.claro.compiler_backends.java_source;

import com.claro.ClaroParser;
import com.claro.ClaroParserException;
import com.claro.ModuleApiParser;
import com.claro.compiler_backends.CompilerBackend;
import com.claro.compiler_backends.ParserUtil;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.compiler_backends.java_source.monomorphization.MonomorphizationCoordinator;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages;
import com.claro.intermediate_representation.ModuleNode;
import com.claro.intermediate_representation.ProgramNode;
import com.claro.intermediate_representation.Target;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.statements.StmtListNode;
import com.claro.intermediate_representation.statements.contracts.ContractDefinitionStmt;
import com.claro.intermediate_representation.statements.contracts.ContractImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.module_system.ModuleApiParserUtil;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;
import com.claro.stdlib.StdLibUtil;
import com.google.auto.value.AutoValue;
import com.google.common.collect.*;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.devtools.common.options.OptionsParser;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JavaSourceCompilerBackend implements CompilerBackend {

  // ***** BEGIN DEP MODULE MONOMORPHIZATION RELATED FIELDS *****
  // This is effectively a CLI arg but in order to avoid this being able to be set accidentally or purposefully by users
  // this is a static field that will be directly configured by {@link DepModuleMonomorphization.java} when the compiler
  // is re-invoked as a dep module monomorphization subprocess.
  public static boolean DEP_MODULE_MONOMORPHIZATION_ENABLED = false;
  public static ScopedHeap scopedHeap;
  public static ProgramNode mainSrcFileProgramNode;
  public static JavaSourceCompilerBackend javaSourceCompilerBackend;
  private static ImmutableMap<String, ImmutableMap<String, ImmutableSet<ImmutableList<Type>>>>
      depsClosureCodegendMonomorphizationsByModuleAndProc = ImmutableMap.of();
  // ***** END DEP MODULE MONOMORPHIZATION RELATED FIELDS *****

  private final String[] COMMAND_LINE_ARGS;
  private final ImmutableMap<String, SrcFile> MODULE_DEPS;
  private final ImmutableSet<SrcFile> TRANSITIVE_MODULE_DEPS;
  private final ImmutableMap<String, String> RESOURCES;
  private final ImmutableSet<String> EXPORTS;
  private final boolean SILENT;
  private final Optional<String> GENERATED_CLASSNAME;
  private final Optional<String> MAIN_FILE_NAME;
  private final Optional<String> PACKAGE_STRING;
  private final ImmutableList<SrcFile> SRCS;
  private final Optional<String> OPTIONAL_UNIQUE_MODULE_NAME;
  private final Optional<String> OPTIONAL_OUTPUT_FILE_PATH;

  public JavaSourceCompilerBackend(String... args) {
    JavaSourceCompilerBackend.javaSourceCompilerBackend = this;
    this.COMMAND_LINE_ARGS = args;
    JavaSourceCompilerBackendCLIOptions options = parseCLIOptions(this.COMMAND_LINE_ARGS);

    if (options.java_package.isEmpty() || options.srcs.isEmpty()) {
      System.err.println("Error: --java_package and [--src ...]+ are required args.");
      System.exit(1);
    }
    if (options.classname.isEmpty() == options.unique_module_name.isEmpty()) {
      System.err.println("Error: Exactly one of --unique_module_name and --classname should be set.");
      System.exit(1);
    }
    if (!options.classname.isEmpty() // this is a claro_binary() with a main method.
        && options.optional_stdlib_modules_used_in_transitive_closure.contains("http")
        && !options.optional_stdlib_deps.contains("http")) {
      System.err.println("Error: The transitive closure of Modules depended on by this Claro Binary includes a dep on" +
                         "the optional stdlib Module `http` which requires Claro to generate teardown logic in the " +
                         "generated main method. In order for Claro to be able to do this, this compilation unit must " +
                         "explicitly opt in to a dependency on `http` in order for the generated code's dependencies " +
                         "to be present in the generated executable Jar.\n" +
                         "\tUpdate the build target as follows:\n" +
                         "\t\tclaro_binary(\n" +
                         "\t\t\t...\n" +
                         "\t\t\toptional_stdlib_deps = [\"http\"],\n" +
                         "\t\t)");
      System.exit(1);
    }

    this.SILENT = options.silent;
    this.GENERATED_CLASSNAME = Optional.ofNullable(options.classname.isEmpty() ? null : options.classname);
    this.MAIN_FILE_NAME = Optional.ofNullable(options.main_file_name.isEmpty() ? null : options.main_file_name);
    this.PACKAGE_STRING = Optional.of(options.java_package);
    this.SRCS =
        options.srcs
            .stream()
            .map(f -> SrcFile.forFilenameAndPath(f.substring(f.lastIndexOf('/') + 1, f.lastIndexOf('.')), f))
            .collect(ImmutableList.toImmutableList());
    this.OPTIONAL_UNIQUE_MODULE_NAME =
        Optional.ofNullable(options.unique_module_name.isEmpty() ? null : options.unique_module_name);
    if (!this.OPTIONAL_UNIQUE_MODULE_NAME.isPresent()) {
      // We're in a top level claro_binary(). The current class name (which follows a different convention that module
      // class generated names - for no good reason) may be needed for codegen.
      InternalStaticStateUtil.optionalGeneratedClassName = this.GENERATED_CLASSNAME;
    }
    HashSet<String> directDepPaths = Sets.newHashSet();
    this.MODULE_DEPS =
        options.deps.stream().collect(ImmutableMap.toImmutableMap(
            s -> s.substring(0, s.indexOf(':')),
            s -> {
              String modulePath = s.substring(s.indexOf(':') + 1);
              directDepPaths.add(modulePath);
              return SrcFile.forFilenameAndPath(
                  modulePath.substring(modulePath.lastIndexOf('/') + 1, modulePath.lastIndexOf('.')),
                  modulePath
              );
            }
        ));
    // Make sure to mark the stdlib dep modules so they're "privileged" to avoid some errors relating to usage and non-export.
    ScopedHeap.stdlibDepModules = ImmutableSet.copyOf(options.stdlib_modules);
    this.TRANSITIVE_MODULE_DEPS =
        options.transitive_deps.stream()
            // If I already have a direct dep on some dep that's also listed as a transitive dep, then I *don't* need to
            // process the dep a second time.
            .filter(modulePath -> !directDepPaths.contains(modulePath))
            .map(
                modulePath -> SrcFile.forFilenameAndPath(
                    modulePath.substring(modulePath.lastIndexOf('/') + 1, modulePath.lastIndexOf('.')),
                    modulePath
                ))
            .collect(ImmutableSet.toImmutableSet());
    this.RESOURCES = options.resources.stream()
        .map(s -> s.split(":"))
        .collect(ImmutableMap.toImmutableMap(
            parts -> parts[0],
            parts -> parts[1]
        ));
    this.EXPORTS = options.exports.stream().collect(ImmutableSet.toImmutableSet());
    this.OPTIONAL_OUTPUT_FILE_PATH =
        Optional.ofNullable(options.output_file_path.isEmpty() ? null : options.output_file_path);

    // Make sure that the MonomorphizationCoordinator knows paths to all .claro_module files that may be used for
    // monomorphization of generic procedures from direct and transitive dep modules.
    if (!options.dep_graph_claro_module_by_unique_name.isEmpty()) {
      MonomorphizationCoordinator.DEP_GRAPH_CLARO_MODULE_PATHS_BY_UNIQUE_MODULE_NAME =
          options.dep_graph_claro_module_by_unique_name.stream()
              .map(s -> s.split(":"))
              .collect(ImmutableMap.toImmutableMap(
                  split -> split[0],
                  split -> split[1]
              ));
    }
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
    scopedHeap = new ScopedHeap();
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
            && this.MAIN_FILE_NAME.isPresent()
            && srcFile.getFilename().equals(this.MAIN_FILE_NAME.get())) {
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
        this.GENERATED_CLASSNAME.orElse(srcFile.getFilename()),
        srcFile.getUsesClaroInternalFileSuffix(),
        /*escapeSpecialChars*/true
    );
  }

  private ModuleApiParser getModuleApiParserForSrcFile(SrcFile srcFile, String uniqueModuleName) {
    return getModuleApiParserForFileContents("$THIS_MODULE$", uniqueModuleName, readFile(srcFile));
  }

  private ModuleApiParser getModuleApiParserForFileContents(String moduleName, String uniqueModuleName, String moduleApiFileContents) {
    ModuleApiParser moduleApiParser = ModuleApiParserUtil.createParser(moduleApiFileContents, moduleName);
    moduleApiParser.moduleName = moduleName;
    moduleApiParser.uniqueModuleName = uniqueModuleName;
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

  private void checkTypesAndGenJavaSourceForSrcFiles(
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
              currNonMainSrcFileParser.optionalModuleName = optionalModuleApiSrcFile.map(unused -> "$THIS_MODULE$");
              currNonMainSrcFileParser.optionalUniqueModuleName = this.OPTIONAL_UNIQUE_MODULE_NAME;
              return currNonMainSrcFileParser;
            })
            .collect(ImmutableList.toImmutableList());
    Optional<ModuleApiParser> optionalModuleApiParser =
        optionalModuleApiSrcFile
            .map(moduleApiSrcFile -> {
              ModuleApiParser res =
                  getModuleApiParserForSrcFile(moduleApiSrcFile, this.OPTIONAL_UNIQUE_MODULE_NAME.get());
              res.isModuleApiForCurrentCompilationUnit = true;
              return res;
            });
    ClaroParser mainSrcFileParser = getParserForSrcFile(mainSrcFile);
    this.PACKAGE_STRING.ifPresent(s -> mainSrcFileParser.package_string = s.equals("") ? "" : "package " + s + ";\n\n");

    try {
      ImmutableList.Builder<ContractDefinitionStmt> importedContractDefinitionStmts = ImmutableList.builder();
      // In addition to the direct module deps, I'll also need to setup the ScopedHeap with all
      // types+initializers+unwrappers that were exported by any transitive dep modules exported by any of this module's
      // direct dep modules. This is in order for compilation to comprehend the transitive exported types that direct
      // dep modules are referencing in its exported types and procedure signatures.
      {
        ImmutableList.Builder<SerializedClaroModule> transitiveModules = ImmutableList.builder();
        for (SrcFile transitiveDepModuleSrcFile : this.TRANSITIVE_MODULE_DEPS) {
          SerializedClaroModule parsedModule =
              SerializedClaroModule.parseDelimitedFrom(transitiveDepModuleSrcFile.getFileInputStream());
          transitiveModules.add(parsedModule);
          importedContractDefinitionStmts.addAll(
              registerDepModuleExportedTypes(scopedHeap, Optional.empty(), parsedModule));
          registerDepModuleExportedTypeInitializersAndUnwrappers(scopedHeap, Optional.empty(), parsedModule);
        }
        // Ensure the contract impls are all registered *after* modules are defined.
        transitiveModules.build().forEach(p -> registerDepModuleContractImpls(scopedHeap, p));
      }
      // Before even parsing the given .claro files, handle the given dependencies by accounting for all dep modules.
      // I need to set up the ScopedHeap with all symbols exported by the direct module deps. Additionally, this is
      // where the parsers will get configured with the necessary state to enable parsing references to bindings
      // exported by the dep modules (e.g. `MyDep::foo(...)`) as module references rather than contract references.
      importedContractDefinitionStmts.addAll(
          setupModuleDepBindings(scopedHeap, this.MODULE_DEPS));

      // If this is compiled as a module, then to be safe to disambiguate types defined in other modules from this one
      // I'll need to save the unique module name of this module under the special name $THIS_MODULE$.
      if (optionalModuleApiParser.isPresent()) {
        ScopedHeap.currProgramDepModules.put(
            "$THIS_MODULE$",
            /*isUsed=*/true,
            SerializedClaroModule.UniqueModuleDescriptor.newBuilder()
                .setProjectPackage(this.PACKAGE_STRING.get())
                .setUniqueModuleName(this.OPTIONAL_UNIQUE_MODULE_NAME.get())
                .build()
        );
      }
      // Register any Resources with the ProgramNode so that it can set them up when necessary.
      ProgramNode.resourcesByName = this.RESOURCES;
      if (!this.RESOURCES.isEmpty()) {
        ScopedHeap.currProgramDepModules.put(
            "resources",
            /*isUsed=*/false,
            SerializedClaroModule.UniqueModuleDescriptor.newBuilder()
                .setProjectPackage("claro.lang")
                .setUniqueModuleName("stdlib$resources$resources")
                .build()
        );
      }

      // Parse the non-main src files first.
      ImmutableList.Builder<ProgramNode> parsedNonMainSrcFilePrograms = ImmutableList.builder();
      for (ClaroParser nonMainSrcFileParser : nonMainSrcFileParsers) {
        parsedNonMainSrcFilePrograms.add((ProgramNode) nonMainSrcFileParser.parse().value);
      }
      // Push these parsed non-main src programs to where they'll be found for type checking and codegen.
      ProgramNode.nonMainFiles = parsedNonMainSrcFilePrograms.build();
      ProgramNode.importedContractDefinitionStmts = importedContractDefinitionStmts.build();
      // Optionally push the module api file to where it'll be found during type checking to validate that the
      // nonMainSrcFilePrograms actually do export the necessary bindings.
      if (optionalModuleApiParser.isPresent()) {
        ProgramNode.moduleApiDef = Optional.of((ModuleNode) optionalModuleApiParser.get().parse().value);
        ScopedHeap.transitiveExportedDepModules = this.EXPORTS;
      }
      // Parse the main src file.
      mainSrcFileProgramNode = ((ProgramNode) mainSrcFileParser.parse().value);

      int totalParserErrorsFound =
          mainSrcFileParser.errorsFound +
          nonMainSrcFileParsers.stream().map(p -> p.errorsFound).reduce(Integer::sum).orElse(0);
      // Don't even bother attempting type validation if there was a parsing error.
      if (totalParserErrorsFound == 0) {
        // Here, type checking and codegen of ALL src files is happening at once.
        StringBuilder generateTargetOutputRes = null;
        if (DEP_MODULE_MONOMORPHIZATION_ENABLED) {
          // In this case, since this module has already been validated, a full recompilation is not necessary. Instead,
          // just do the "discovery" phases that will bring the necessary types/identifiers into memory so that generic
          // procedures requested by the monomorphization coordinator can be selectively type checked and monomorphized.
          // This should account for a significant time savings, going a good ways towards minimizing wasted work.
          mainSrcFileProgramNode.runDiscoveryCompilationPhases(scopedHeap);
          // Finally just blindly mark all of the flags and static values initialized so that any procedures that end up
          // being a part of a monomorphization can reference the static values.
          ProgramNode.moduleApiDef.get().exportedFlagDefs.forEach(
              s -> scopedHeap.initializeIdentifier(s.identifier.identifier));
          ProgramNode.moduleApiDef.get().exportedStaticValueDefs.forEach(
              s -> scopedHeap.initializeIdentifier(s.identifier.identifier));
        } else {
          generateTargetOutputRes =
              mainSrcFileProgramNode.generateTargetOutput(Target.JAVA_SOURCE, scopedHeap, StdLibUtil::registerIdentifiers);
        }
        if (Expr.typeErrorsFound.isEmpty() && ProgramNode.miscErrorsFound.isEmpty()) {
          if (optionalModuleApiParser.isPresent()) {
            if (DEP_MODULE_MONOMORPHIZATION_ENABLED) {
              // In this case we're intentionally avoiding doing any file writing for the compilation results, as for
              // the sake of dep module monomorphization we don't actually want to re-emit this module's codegen, we
              // simply wanted to ready the AST and symbol table for monomorphizations that will be requested afterwards
              // by the monomorphization coordinator.
              return;
            } else {
              // Here, we were asked to compile a non-executable Claro Module, rather than an executable Claro program. So,
              // we need to populate and emit a SerializedClaroModule proto that can be used as a dep for other Claro
              // Modules/programs.
              serializeClaroModule(
                  this.PACKAGE_STRING.get(),
                  this.OPTIONAL_UNIQUE_MODULE_NAME.get(),
                  generateTargetOutputRes,
                  nonMainSrcFiles,
                  scopedHeap
              );
            }
          } else {
            if (this.OPTIONAL_OUTPUT_FILE_PATH.isPresent()) {
              // Here we've been asked to write the output to a particular file.
              try (FileWriter outputFileWriter = new FileWriter(createOutputFile())) {
                outputFileWriter.write(generateTargetOutputRes.toString());
              }
            } else {
              // Here, we were simply asked to codegen an executable Claro program. Output the codegen'd Java source to
              // stdout directly where it will be piped by Claro's Bazel rules into the appropriate .java file.
              System.out.println(generateTargetOutputRes);
            }
          }
          return;
        }
      }
      // Fall into this error reporting if we encountered any parsing or type validation errors.
      ClaroParser.errorMessages.forEach(Runnable::run);
      Expr.typeErrorsFound.forEach(e -> e.accept(mainSrcFileParser.generatedClassName));
      ProgramNode.miscErrorsFound.forEach(Runnable::run);
      warnNumErrorsFound(totalParserErrorsFound);
      System.exit(1);
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

  private ImmutableList<ContractDefinitionStmt> setupModuleDepBindings(
      ScopedHeap scopedHeap, ImmutableMap<String, SrcFile> moduleDeps) throws Exception {
    ImmutableMap.Builder<String, SerializedClaroModule> parsedClaroModuleProtosBuilder = ImmutableMap.builder();
    ImmutableList.Builder<ContractDefinitionStmt> importedContractDefinitionStmts = ImmutableList.builder();
    // Setup dep module exported types.
    {
      ImmutableList.Builder<SerializedClaroModule> parsedModulesBuilder = ImmutableList.builder();
      for (Map.Entry<String, SrcFile> moduleDep : moduleDeps.entrySet()) {
        SerializedClaroModule parsedModule =
            SerializedClaroModule.parseDelimitedFrom(moduleDep.getValue().getFileInputStream());
        parsedModulesBuilder.add(parsedModule);
        parsedClaroModuleProtosBuilder.put(moduleDep.getKey(), parsedModule);

        // First thing, register this dep module somewhere central that can be referenced by both codegen and the parsers.
        ScopedHeap.currProgramDepModules.put(moduleDep.getKey(), /*isUsed=*/false, parsedModule.getModuleDescriptor());

        importedContractDefinitionStmts.addAll(
            registerDepModuleExportedTypes(scopedHeap, Optional.of(moduleDep.getKey()), parsedModule));
      }
      // Ensure the contract impls are all registered *after* modules are defined.
      ImmutableList<SerializedClaroModule> parsedModules = parsedModulesBuilder.build();
      parsedModules.forEach(p -> registerDepModuleContractImpls(scopedHeap, p));
      ProgramNode.transitiveExportedFlags =
          parsedModules.stream()
              .flatMap(
                  m -> {
                    SerializedClaroModule.ExportedFlagDefinitions f = m.getExportedFlagDefinitions();
                    return Streams.concat(
                        f.getDirectExportedFlagsList().stream()
                            .map(exportedFlag ->
                                     Maps.immutableEntry(
                                         m.getModuleDescriptor().getUniqueModuleName(), exportedFlag)),
                        f.getTransitiveExportedFlagsByDefiningModuleMap().entrySet().stream()
                            .flatMap(e ->
                                         e.getValue().getFlagsList().stream()
                                             .map(flag -> Maps.immutableEntry(e.getKey(), flag)))
                    );
                  })
              .collect(ImmutableSetMultimap.toImmutableSetMultimap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // After having successfully parsed all dep module files and registered all of their exported types, it's time to
    // finally register their remaining exports.
    ImmutableMap<String, SerializedClaroModule> parsedClaroModuleProtos = parsedClaroModuleProtosBuilder.build();

    // Register the exported static values.
    BiFunction<Map.Entry<String, SerializedClaroModule>, String, BiConsumer<TypeProtos.TypeProto, Boolean>>
        registerStaticValue =
        // God I hate Java lambdas. Just want a lambda with 4 args *facepalm*.
        (moduleDep, name) -> (type, isLazy) -> {
          String disambiguatedStaticValueIdentifier =
              String.format(
                  "%s$%s",
                  name,
                  moduleDep.getValue().getModuleDescriptor().getUniqueModuleName()
              );
          JavaSourceCompilerBackend.scopedHeap.observeStaticIdentifierValue(
              disambiguatedStaticValueIdentifier,
              Types.parseTypeProto(type),
              isLazy
          );
          JavaSourceCompilerBackend.scopedHeap.initializeIdentifier(disambiguatedStaticValueIdentifier);
        };
    for (Map.Entry<String, SerializedClaroModule> moduleDep : parsedClaroModuleProtos.entrySet()) {
      for (SerializedClaroModule.ExportedFlagDefinitions.ExportedFlag depExportedFlag :
          moduleDep.getValue().getExportedFlagDefinitions().getDirectExportedFlagsList()) {
        registerStaticValue
            .apply(moduleDep, depExportedFlag.getName())
            .accept(depExportedFlag.getType(), /*isLazy=*/true); // All flags are lazy.
      }
      for (SerializedClaroModule.ExportedStaticValue depExportedStaticValue :
          moduleDep.getValue().getExportedStaticValuesList()) {
        registerStaticValue
            .apply(moduleDep, depExportedStaticValue.getName())
            .accept(depExportedStaticValue.getType(), depExportedStaticValue.getIsLazy());
      }
    }

    // Register the exported procedures.
    for (Map.Entry<String, SerializedClaroModule> moduleDep : parsedClaroModuleProtos.entrySet()) {
      // Setup the regular exported procedures.
      for (SerializedClaroModule.Procedure depExportedProc :
          moduleDep.getValue().getExportedProcedureDefinitionsList()) {
        Types.ProcedureType procedureType = getProcedureTypeFromProto(depExportedProc);
        scopedHeap.putIdentifierValue(
            String.format("$DEP_MODULE$%s$%s", moduleDep.getKey(), depExportedProc.getName()),
            procedureType,
            // If this is a generic procedure, then the symbol table will hold a function that's used to register a
            // concrete call, and get back the monomorphization's canonical name, otherwise, null.
            maybeSetupGenericDepModuleProcedure(moduleDep.getKey(), depExportedProc, procedureType)
        );
      }
      registerDepModuleExportedTypeInitializersAndUnwrappers(scopedHeap, Optional.of(moduleDep.getKey()), moduleDep.getValue());
      // Make note of any synthetic procedures generated by any HttpServiceDefStmts.
      moduleDep.getValue().getExportedHttpServiceDefinitionsList().forEach(
          syntheticHttpServiceDef ->
              syntheticHttpServiceDef.getEndpointsList().forEach(
                  endpoint ->
                      scopedHeap.putIdentifierValue(
                          String.format("$DEP_MODULE$%s$%s", moduleDep.getKey(), endpoint.getEndpointName()),
                          getProcedureTypeFromProto(endpoint.getProcedure())
                      )));

    }
    // Make note of all types defined in dep modules so that they can be referenced again during validation of exported
    // procedure signatures.
    ScopedHeap.currProgramDepModuleExportedTypes =
        parsedClaroModuleProtos.entrySet().stream().collect(ImmutableMap.toImmutableMap(
            Map.Entry::getKey,
            e -> e.getValue().getExportedTypeDefinitions()
        ));

    // Make note of any monomorphizations that the dep modules have already codegend so that work can be skipped here.
    HashMap<String, ImmutableListMultimap.Builder<String, ImmutableList<Type>>>
        depsClosureCodegendMonmorphizationsBuilder = Maps.newHashMap();
    for (Map.Entry<String, SerializedClaroModule> moduleDep : parsedClaroModuleProtos.entrySet()) {
      for (Map.Entry<String, SerializedClaroModule.CodegendMonomorphizationsList> monomorphizations
          : moduleDep.getValue().getCodegendMonomorphizationsTransitiveClosureByUniqueModuleNameMap().entrySet()) {
        depsClosureCodegendMonmorphizationsBuilder.putIfAbsent(
            monomorphizations.getKey(), ImmutableListMultimap.builder());
        monomorphizations.getValue().getMonomorphizationsList().forEach(m -> {
          String procName = m.getProcedureName();
          depsClosureCodegendMonmorphizationsBuilder.get(monomorphizations.getKey()).put(
              procName,
              m.getConcreteTypeParamsList().stream()
                  .map(Types::parseTypeProto)
                  .collect(ImmutableList.toImmutableList())
          );
        });
      }
    }
    JavaSourceCompilerBackend.depsClosureCodegendMonomorphizationsByModuleAndProc =
        depsClosureCodegendMonmorphizationsBuilder.entrySet().stream().collect(
            ImmutableMap.toImmutableMap(
                Map.Entry::getKey,
                e -> e.getValue().build().asMap().entrySet().stream().collect(ImmutableMap.toImmutableMap(
                    Map.Entry::getKey,
                    e2 -> ImmutableSet.copyOf(e2.getValue())
                ))
            ));
    return importedContractDefinitionStmts.build();
  }

  private static ProcedureDefinitionStmt syntheticProcedureDefStmt = null;

  private static BiFunction<ScopedHeap, ImmutableMap<Type, Type>, String> maybeSetupGenericDepModuleProcedure(
      String depModuleName, SerializedClaroModule.Procedure depExportedProc, Types.ProcedureType procedureType) {
    BiFunction<ScopedHeap, ImmutableMap<Type, Type>, String> symbolTableValue = null;
    if (procedureType.getGenericProcedureArgNames().map(l -> !l.isEmpty()).orElse(false)) {
      // If we're dealing with a generic procedure from a dep module, then we'll need to also setup an additional
      // value in its symbol table entry that will be used by FunctionCallExpr to derive the monomorphization name
      // based on the concrete arg types at the callsite. We'll also be able to utilize this as a "hook" of sorts to
      // configure any other work that'll be necessary to register the fact that a monomomorphization of one of this
      // compilation unit's dep modules actually needs to be generated before we can go to Java.
      symbolTableValue =
          (internalScopedHeap, concreteTypeParams) -> {
            ImmutableList.Builder<Type> orderedConcreteTypeParamsBuilder = ImmutableList.builder();
            ImmutableList<String> genericProcedureArgNames = procedureType.getGenericProcedureArgNames().get();
            for (int i = 0; i < genericProcedureArgNames.size(); i++) {
              orderedConcreteTypeParamsBuilder.add(
                  concreteTypeParams.get(Types.$GenericTypeParam.forTypeParamName(genericProcedureArgNames.get(i))));
            }
            ImmutableList<Type> orderedConcreteTypeParams = orderedConcreteTypeParamsBuilder.build();
            String monomorphizationName = ContractProcedureImplementationStmt.getCanonicalProcedureName(
                /*contractName=*/"$MONOMORPHIZATION", orderedConcreteTypeParams, depExportedProc.getName());
            // Enable FunctionCallExpr to mark this monomorphization used.
            internalScopedHeap.putIdentifierValueAtLevel(
                monomorphizationName,
                procedureType,
                null,
                /*scopeLevel=*/0
            );

            // Don't need to duplicate codegen of some monomorphization that was already generated by a downstream
            // module. This is actually a significant compilation performance optimization.
            if (Optional.ofNullable(
                    depsClosureCodegendMonomorphizationsByModuleAndProc.get(
                        ScopedHeap.getDefiningModuleDisambiguator(Optional.of(depModuleName))))
                .flatMap(m -> Optional.ofNullable(m.get(depExportedProc.getName())))
                .map(typeParams -> !typeParams.contains(orderedConcreteTypeParams))
                .orElse(true)) {
              // Make note of this needed dep module monomorphization somewhere so that just before finalizing codegen
              // we can trigger dep module monomorphization.
              InternalStaticStateUtil.JavaSourceCompilerBackend_depModuleGenericMonomoprhizationsNeeded.put(
                  depModuleName,
                  getMonomorphizationRequest(
                      depExportedProc,
                      orderedConcreteTypeParams,
                      procedureType.getAllTransitivelyRequiredContractNamesToGenericArgs().entries().stream()
                          .map(e -> Maps.immutableEntry(
                              e.getKey(),
                              e.getValue().stream()
                                  .map(concreteTypeParams::get)
                                  .collect(ImmutableList.toImmutableList())
                          ))
                          .collect(ImmutableMap.toImmutableMap(
                              Map.Entry::getKey,
                              Map.Entry::getValue
                          ))
                  )
              );
            }
            return monomorphizationName;
          };
      // Each parsed procedure type will be given a synthetic procedure definition stmt literally just for the sake of
      // giving ProcedureDefinitionStmt's transitive procedure type checking something to interact with.
      if (syntheticProcedureDefStmt == null) {
        syntheticProcedureDefStmt = new ProcedureDefinitionStmt(
            "$$SYNTHETIC_DEP_MODULE_PROCEDURE_DEF$$",
            (unused) -> (unused2) -> procedureType,
            new StmtListNode(new Stmt(ImmutableList.of()) {
              @Override
              public void assertExpectedExprTypes(ScopedHeap scopedHeap) {
              }

              @Override
              public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
                return null;
              }

              @Override
              public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
                return null;
              }
            })
        ) {
          @Override
          public void registerProcedureTypeProvider(ScopedHeap scopedHeap) {
          }

          @Override
          public void assertExpectedExprTypes(ScopedHeap scopedHeap) {
          }

          @Override
          public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
            throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
          }
        };
      }
      procedureType.autoValueIgnoredProcedureDefStmt.set(syntheticProcedureDefStmt);
    }
    return symbolTableValue;
  }

  private static IPCMessages.MonomorphizationRequest getMonomorphizationRequest(
      SerializedClaroModule.Procedure depExportedProc,
      ImmutableList<Type> orderedConcreteTypeParams,
      ImmutableMap<String, ImmutableList<Type>> requiredContracts) {
    return IPCMessages.MonomorphizationRequest.newBuilder()
        .setProcedureName(depExportedProc.getName())
        .addAllConcreteTypeParams(
            orderedConcreteTypeParams.stream().map(Type::toProto).collect(Collectors.toList()))
        .addAllUserDefinedTypeConcreteTypeParamsMetadata(
            orderedConcreteTypeParams.stream().filter(t -> t instanceof Types.UserDefinedType)
                .map(t -> {
                  Types.UserDefinedType userDefinedType = (Types.UserDefinedType) t;
                  String disambiguatedIdentifier =
                      String.format("%s$%s", userDefinedType.getTypeName(), userDefinedType.getDefiningModuleDisambiguator());
                  return IPCMessages.MonomorphizationRequest.UserDefinedTypeMetadata.newBuilder()
                      .setType(t.toProto().getUserDefinedType())
                      .addAllTypeParamNames(
                          Optional.ofNullable(Types.UserDefinedType.$typeParamNames.get(disambiguatedIdentifier))
                              .orElse(ImmutableList.of()))
                      .setWrappedType(
                          Types.UserDefinedType.$resolvedWrappedTypes.get(disambiguatedIdentifier).toProto())
                      .build();
                }).collect(Collectors.toList()))
        .addAllRequiredContractImplementations(
            requiredContracts.entrySet().stream()
                .map(e -> {
                  IPCMessages.ExportedContractImplementation.Builder builder =
                      IPCMessages.ExportedContractImplementation.newBuilder()
                          .setImplementedContractName(e.getKey())
                          .addAllConcreteTypeParams(
                              e.getValue().stream()
                                  .map(Type::toProto)
                                  .collect(Collectors.toList()))
                          .addAllConcreteSignatures(
                              ((Types.$Contract) scopedHeap.getValidatedIdentifierType(e.getKey()))
                                  .getProcedureNames().stream()
                                  .map(n -> getIPCMessagesProcedureProtoFromProcedureType(
                                      n,
                                      (Types.ProcedureType) scopedHeap.getValidatedIdentifierType(
                                          ContractProcedureImplementationStmt.getCanonicalProcedureName(
                                              e.getKey(), e.getValue(), n))
                                  ))
                                  .collect(Collectors.toList()));
                  Optional<SerializedClaroModule.UniqueModuleDescriptor> optionalContractImplDefiningModuleDescriptor =
                      ((Types.$ContractImplementation) scopedHeap.getValidatedIdentifierType(
                          ContractImplementationStmt.getContractTypeString(
                              e.getKey(), e.getValue().stream().map(Type::toString).collect(Collectors.toList()))))
                          .getOptionalDefiningModuleDisambiguator()
                          .map(disambiguator ->
                                   // This .get() is an assumption that the contract impl is coming from a direct
                                   // dep with a registered name.
                                   ScopedHeap.getModuleNameFromDisambiguator(disambiguator).get())
                          .map(contractImplModuleName ->
                                   Optional.ofNullable(ScopedHeap.currProgramDepModules.rowMap()
                                                           .get(contractImplModuleName))
                                       .map(m -> m.values().stream().findFirst().get()).get());
                  if (optionalContractImplDefiningModuleDescriptor.isPresent()) {
                    builder.setContractImplDefiningModuleDescriptor(
                        IPCMessages.ExportedContractImplementation.UniqueModuleDescriptor.newBuilder()
                            .setProjectPackage(
                                optionalContractImplDefiningModuleDescriptor.get().getProjectPackage())
                            .setUniqueModuleName(
                                optionalContractImplDefiningModuleDescriptor.get().getUniqueModuleName()));
                  } else {
                    // In this case, the contract was defined in the current compilation unit which happens to be the
                    // top-level claro_binary(). Typically namespacing isn't needed for these procedures as they can't
                    // be called from other dep modules. However, in the case of dep module monomorphization over a
                    // required contract impl defined in the claro_binary(), the scoping is in fact necessary just as
                    // a quirk of the Java codegen representation.
                    builder.setContractImplDefiningModuleDescriptor(
                        IPCMessages.ExportedContractImplementation.UniqueModuleDescriptor.newBuilder()
                            .setProjectPackage(
                                JavaSourceCompilerBackend.javaSourceCompilerBackend.PACKAGE_STRING.get())
                            .setUniqueModuleName(
                                JavaSourceCompilerBackend.javaSourceCompilerBackend.GENERATED_CLASSNAME.get())
                    );

                  }
                  return builder.build();
                }).collect(Collectors.toList()))
        .build();
  }

  // Register an optionally named module dep's exported type initializers and unwrappers. All direct dep modules will be
  // inherently named, and transitive exported deps of those direct deps will be unnamed. This is in keeping with Claro
  // enabling module consumers to actually utilize transitive exported types from dep modules w/o actually placing a
  // direct dep on those modules so long as they never try to explicitly *name* any types from those transitive exported
  // dep modules.
  private static void registerDepModuleExportedTypeInitializersAndUnwrappers(
      ScopedHeap scopedHeap, Optional<String> optionalModuleName, SerializedClaroModule parsedModule) {
    // Make note of any initializers exported by this dep module.
    for (Map.Entry<String, SerializedClaroModule.ExportedTypeDefinitions.ProcedureList> initializerEntry :
        parsedModule.getExportedTypeDefinitions().getInitializersByTypeNameMap().entrySet()) {
      optionalModuleName.ifPresent(
          moduleName ->
              initializerEntry.getValue().getProceduresList().forEach(
                  p -> {
                    Types.ProcedureType procedureType = getProcedureTypeFromProto(p);
                    scopedHeap.putIdentifierValue(
                        String.format("$DEP_MODULE$%s$%s", moduleName, p.getName()),
                        procedureType,
                        // If this is a generic procedure, then the symbol table will hold a function that's used to register a
                        // concrete call, and get back the monomorphization's canonical name, otherwise, null.
                        maybeSetupGenericDepModuleProcedure(moduleName, p, procedureType)
                    );
                  }));
      InternalStaticStateUtil.InitializersBlockStmt_initializersByInitializedTypeNameAndModuleDisambiguator
          .put(
              initializerEntry.getKey(),
              parsedModule.getModuleDescriptor().getUniqueModuleName(),
              initializerEntry.getValue().getProceduresList().stream()
                  .map(
                      // These are actually only used for error-reporting, so I'll use a user-friendly string here.
                      initializer -> String.format(
                          "%s::%s",
                          optionalModuleName.orElse(parsedModule.getModuleDescriptor().getUniqueModuleName()),
                          initializer.getName()
                      ))
                  .collect(ImmutableSet.toImmutableSet())
          );
    }
    // Make note of any unwrappers exported by this dep module.
    for (Map.Entry<String, SerializedClaroModule.ExportedTypeDefinitions.ProcedureList> unwrapperEntry :
        parsedModule.getExportedTypeDefinitions().getUnwrappersByTypeNameMap().entrySet()) {
      optionalModuleName.ifPresent(
          moduleName ->
              unwrapperEntry.getValue().getProceduresList().forEach(
                  p -> {
                    Types.ProcedureType procedureType = getProcedureTypeFromProto(p);
                    scopedHeap.putIdentifierValue(
                        String.format("$DEP_MODULE$%s$%s", moduleName, p.getName()),
                        procedureType,
                        // If this is a generic procedure, then the symbol table will hold a function that's used to register a
                        // concrete call, and get back the monomorphization's canonical name, otherwise, null.
                        maybeSetupGenericDepModuleProcedure(moduleName, p, procedureType)
                    );
                  }));
      InternalStaticStateUtil.UnwrappersBlockStmt_unwrappersByUnwrappedTypeNameAndModuleDisambiguator
          .put(
              unwrapperEntry.getKey(),
              parsedModule.getModuleDescriptor().getUniqueModuleName(),
              unwrapperEntry.getValue().getProceduresList().stream()
                  .map(
                      // These are actually only used for error-reporting, so I'll use a user-friendly string here.
                      unwrapper -> String.format(
                          "%s::%s",
                          optionalModuleName.orElse(parsedModule.getModuleDescriptor().getUniqueModuleName()),
                          unwrapper.getName()
                      ))
                  .collect(ImmutableSet.toImmutableSet())
          );
    }
  }

  // Register an optionally named module dep's exported types. All direct dep modules will be inherently named, and
  // transitive exported deps of those direct deps will be unnamed. This is in keeping with Claro enabling module
  // consumers to actually utilize transitive exported types from dep modules w/o actually placing a direct dep on those
  // modules so long as they never try to explicitly *name* any types from those transitive exported dep modules.
  private static ImmutableList<ContractDefinitionStmt> registerDepModuleExportedTypes(
      ScopedHeap scopedHeap, Optional<String> optionalModuleName, SerializedClaroModule parsedModule) {
    // Register any alias defs found in the module.
    for (Map.Entry<String, TypeProtos.TypeProto> exportedAliasDef :
        parsedModule.getExportedTypeDefinitions().getExportedAliasDefsByNameMap().entrySet()) {
      String disambiguatedIdentifier =
          String.format("%s$%s", exportedAliasDef.getKey(), parsedModule.getModuleDescriptor().getUniqueModuleName());
      scopedHeap.putIdentifierValueAsTypeDef(disambiguatedIdentifier, Types.parseTypeProto(exportedAliasDef.getValue()), null);
    }

    // Register any newtype defs found in the module.
    for (Map.Entry<String, SerializedClaroModule.ExportedTypeDefinitions.NewTypeDef> exportedNewTypedef :
        parsedModule.getExportedTypeDefinitions().getExportedNewtypeDefsByNameMap().entrySet()) {
      String disambiguatedIdentifier =
          String.format("%s$%s", exportedNewTypedef.getKey(), parsedModule.getModuleDescriptor()
              .getUniqueModuleName());
      // Register the user-defined-type itself.
      Type newType =
          Types.parseTypeProto(
              TypeProtos.TypeProto.newBuilder()
                  .setUserDefinedType(exportedNewTypedef.getValue().getUserDefinedType())
                  .build());
      // Declare the dep type twice just for the sake of the constructor being easily found by the FunctionCallExpr
      // where the naming convention is different.
      scopedHeap.putIdentifierValueAsTypeDef(disambiguatedIdentifier, newType, null);
      optionalModuleName.ifPresent(
          moduleName ->
              scopedHeap.putIdentifierValueAsTypeDef(
                  String.format("$DEP_MODULE$%s$%s", moduleName, exportedNewTypedef.getKey()),
                  newType,
                  null
              ));
      // Register any type param names.
      if (!newType.parameterizedTypeArgs().isEmpty()) {
        Types.UserDefinedType.$typeParamNames.put(
            disambiguatedIdentifier, ImmutableList.copyOf(exportedNewTypedef.getValue().getTypeParamNamesList()));
      }
      // Register the wrapped type.
      Type wrappedType = Types.parseTypeProto(exportedNewTypedef.getValue().getWrappedType());
      scopedHeap.putIdentifierValueAsTypeDef(
          String.format("%s$wrappedType", disambiguatedIdentifier), wrappedType, null);
      Types.UserDefinedType.$resolvedWrappedTypes.put(disambiguatedIdentifier, wrappedType);
      // Register its constructor. However, there may be no constructor in the case of an opaque type that's not allowed
      // to be constructed by consumers anyways.
      if (optionalModuleName.isPresent() && exportedNewTypedef.getValue().hasConstructor()) {
        Type cons = getProcedureTypeFromProto(exportedNewTypedef.getValue().getConstructor());
        scopedHeap.putIdentifierValue(
            String.format("$DEP_MODULE$%s$%s$constructor", optionalModuleName.get(), exportedNewTypedef.getKey()),
            cons
        );
      }
    }

    // Register any AtomDefinitionStmts found in the module.
    for (int i = 0; i < parsedModule.getExportedAtomDefinitionsList().size(); i++) {
      String atomName = parsedModule.getExportedAtomDefinitions(i);
      String disambiguatedAtomIdentifier =
          String.format("%s$%s", atomName, parsedModule.getModuleDescriptor().getUniqueModuleName());
      scopedHeap.putIdentifierValueAsTypeDef(
          disambiguatedAtomIdentifier,
          Types.AtomType.forNameAndDisambiguator(atomName, parsedModule.getModuleDescriptor().getUniqueModuleName()),
          null
      );
      scopedHeap.initializeIdentifier(disambiguatedAtomIdentifier);
      // Now I need to cache this atom.
      InternalStaticStateUtil.AtomDefinition_CACHE_INDEX_BY_MODULE_AND_ATOM_NAME.put(
          parsedModule.getModuleDescriptor().getUniqueModuleName(), disambiguatedAtomIdentifier, i);
    }

    ImmutableList.Builder<ContractDefinitionStmt> importedContractDefinitionStmts = ImmutableList.builder();
    // Register any ContractDefs found in the module.
    parsedModule.getExportedContractDefinitionsList().forEach(
        contractDef -> {
          // For the sake of all contracts always using a consistent naming across all relative deps, the names of
          // contracts are disambiguated at all times.
          String disambiguatedContractName = contractDef.getName();
          // Setup an empty set to collect all implementations in.
          ContractDefinitionStmt.contractImplementationsByContractName.put(disambiguatedContractName, new ArrayList<>());
          // Add the contract itself to the symbol table.
          ImmutableList<String> typeParamNamesImmutableList =
              ImmutableList.copyOf(contractDef.getTypeParamNamesList());
          ContractDefinitionStmt contractDefinitionStmt =
              new ContractDefinitionStmt(
                  disambiguatedContractName,
                  typeParamNamesImmutableList,
                  contractDef.getSignaturesList().stream()
                      .map(sig -> {
                        Types.ProcedureType type = getProcedureTypeFromProto(sig);
                        return new ContractProcedureSignatureDefinitionStmt(
                            sig.getName(),
                            Optional.ofNullable(
                                type.hasArgs()
                                ? IntStream.range(0, type.getArgTypes().size()).boxed()
                                    .collect(ImmutableMap.toImmutableMap(
                                        i -> "$" + i, // Don't need (or have) real arg names.
                                        i -> TypeProvider.ImmediateTypeProvider.of(type.getArgTypes().get(i))
                                    ))
                                : null),
                            Optional.ofNullable(
                                type.hasReturnValue()
                                ? TypeProvider.ImmediateTypeProvider.of(type.getReturnType())
                                : null),
                            type.getAnnotatedBlocking(),
                            type.getAnnotatedBlockingGenericOverArgs()
                                .map(
                                    blockingGenArgs ->
                                        blockingGenArgs.asList().stream()
                                            .map(i -> "$" + i) // Use synthetic arg names defined a few lines above.
                                            .collect(ImmutableList.toImmutableList())),
                            type.getGenericProcedureArgNames()
                        );
                      })
                      .collect(ImmutableList.toImmutableList())
              );
          // Just quickly do assertions on this synthetic def so that this is ready. It shouldn't be possible for this
          // to fail, so it's acceptable to do this out of band here.
          try {
            contractDefinitionStmt.assertExpectedExprTypes(scopedHeap);
          } catch (Exception e) {
            throw new RuntimeException("Internal Compiler Error! Somehow failed type assertion on synthetic ContractDefinitionStmt representing dep module contract def.", e);
          }
          scopedHeap.putIdentifierValue(
              disambiguatedContractName,
              Types.$Contract.forContractNameTypeParamNamesAndProcedureNames(
                  // Intentionally going to use the original name in the type so that the original name is accessible.
                  contractDef.getName(),
                  parsedModule.getModuleDescriptor().getUniqueModuleName(),
                  typeParamNamesImmutableList,
                  contractDef.getSignaturesList().stream()
                      .map(SerializedClaroModule.Procedure::getName).collect(ImmutableList.toImmutableList())
              ),
              contractDefinitionStmt
          );
          // Add each of the contract procedure signatures to the symbol table.
          contractDef.getSignaturesList().forEach(
              sig -> {
                String normalizedProcedureName =
                    ContractProcedureSignatureDefinitionStmt.getFormattedInternalContractProcedureName(
                        disambiguatedContractName, sig.getName());
                scopedHeap.putIdentifierValue(normalizedProcedureName, getProcedureTypeFromProto(sig));
                scopedHeap.markIdentifierUsed(normalizedProcedureName);
              }
          );
          // Keep track of all of these generated ContractDefinitionStmts so that I can register any potential dynamic
          // dispatch procedures later.
          importedContractDefinitionStmts.add(contractDefinitionStmt);
        }
    );

    // Register any HttpServiceDefs found in the module.
    parsedModule.getExportedHttpServiceDefinitionsList().forEach(
        httpServiceDef -> {
          String disambiguatedServiceName =
              String.format("%s$%s", httpServiceDef, parsedModule.getModuleDescriptor().getUniqueModuleName());
          scopedHeap.putIdentifierValueAsTypeDef(
              disambiguatedServiceName,
              Types.HttpServiceType.forServiceNameAndDisambiguator(
                  httpServiceDef.getHttpServiceName(), parsedModule.getModuleDescriptor().getUniqueModuleName()),
              null
          );
          // Register any endpoint_handlers registered for HttpServiceDefs in this module.
          if (httpServiceDef.getEndpointsCount() > 0) {
            InternalStaticStateUtil.HttpServiceDef_servicesWithValidEndpointHandlersDefined.add(disambiguatedServiceName);
            InternalStaticStateUtil.HttpServiceDef_endpointProcedureSignatures.putAll(
                httpServiceDef.getEndpointsList().stream().collect(ImmutableTable.toImmutableTable(
                    e -> httpServiceDef.getHttpServiceName(),
                    e -> e.getEndpointName(),
                    e -> getProcedureTypeFromProto(e.getProcedure())
                ))
            );
            InternalStaticStateUtil.HttpServiceDef_endpointPaths.putAll(
                httpServiceDef.getEndpointsList().stream().collect(ImmutableTable.toImmutableTable(
                    e -> httpServiceDef.getHttpServiceName(),
                    e -> e.getEndpointName(),
                    e -> e.getPath()
                ))
            );
          }
        });

    return importedContractDefinitionStmts.build();
  }

  private static void registerDepModuleContractImpls(ScopedHeap scopedHeap, SerializedClaroModule parsedModule) {
    // Register any contract impls found in the module.
    parsedModule.getExportedContractImplementationsList().forEach(
        contractImpl -> registerExportedContractImplementation(
            contractImpl.getImplementedContractName(),
            // TODO(steving) TESTING!!! UPDATE THIS, I NEED TO BE ABLE TO HANDLE THE CASE WHERE THE IMPLEMENTED
            //   CONTRACT WAS ACTUALLY DEFINED IN SOME *OTHER* TRANSITIVE DEP MODULE.
            //   NOTE: This will probably require some re-ordering such that all contracts from all modules are
            //         defined before any implementations are registered.
            parsedModule.getModuleDescriptor().getProjectPackage(),
            parsedModule.getModuleDescriptor().getUniqueModuleName(),
            contractImpl.getConcreteTypeParamsList()
                .stream()
                .map(Types::parseTypeProto)
                .collect(ImmutableList.toImmutableList()),
            contractImpl.getConcreteSignaturesList().stream()
                .collect(ImmutableMap.toImmutableMap(
                    SerializedClaroModule.Procedure::getName,
                    JavaSourceCompilerBackend::getProcedureTypeFromProto
                )),
            scopedHeap
        )
    );
  }

  private static Types.ProcedureType getProcedureTypeFromProto(SerializedClaroModule.Procedure procedureProto) {
    TypeProtos.TypeProto procedureTypeProto;
    switch (procedureProto.getProcedureTypeCase()) {
      case FUNCTION:
        procedureTypeProto = TypeProtos.TypeProto.newBuilder().setFunction(procedureProto.getFunction()).build();
        break;
      case CONSUMER:
        procedureTypeProto = TypeProtos.TypeProto.newBuilder().setConsumer(procedureProto.getConsumer()).build();
        break;
      case PROVIDER:
        procedureTypeProto = TypeProtos.TypeProto.newBuilder().setProvider(procedureProto.getProvider()).build();
        break;
      default:
        throw new RuntimeException(
            "Internal Compiler Error! Encountered unexpected procedure type while parsing newtype definition from .claro_module: " +
            procedureProto.getProcedureTypeCase());
    }
    return (Types.ProcedureType) Types.parseTypeProto(procedureTypeProto);
  }

  private File createOutputFile() {
    File outputFile = null;
    try {
      outputFile = new File(this.OPTIONAL_OUTPUT_FILE_PATH.get());
      outputFile.createNewFile();
    } catch (IOException e) {
      System.err.println("An error occurred while trying to open/create the specified output file: " +
                         this.OPTIONAL_OUTPUT_FILE_PATH.get());
      e.printStackTrace();
      System.exit(1);
    }
    return outputFile;
  }

  private void serializeClaroModule(
      String projectPackage,
      String uniqueModuleName,
      StringBuilder moduleCodegen,
      ImmutableList<SrcFile> moduleImplFiles,
      ScopedHeap scopedHeap) throws IOException {
    SerializedClaroModule.Builder serializedClaroModuleBuilder =
        SerializedClaroModule.newBuilder()
            .setModuleDescriptor(
                SerializedClaroModule.UniqueModuleDescriptor.newBuilder()
                    .setProjectPackage(projectPackage)
                    .setUniqueModuleName(uniqueModuleName))
            .addAllCommandLineArgs(Arrays.asList(this.COMMAND_LINE_ARGS))
            .setExportedTypeDefinitions(
                SerializedClaroModule.ExportedTypeDefinitions.newBuilder()
                    .putAllExportedAliasDefsByName(
                        ProgramNode.moduleApiDef.get().exportedAliasDefs.stream()
                            .collect(ImmutableMap.toImmutableMap(
                                alias -> alias.alias,
                                alias -> alias.resolvedType.toProto()
                            )))
                    .putAllExportedNewtypeDefsByName(
                        ProgramNode.moduleApiDef.get().exportedNewTypeDefs.stream()
                            .collect(
                                ImmutableMap.toImmutableMap(
                                    newtypeDef -> newtypeDef.typeName,
                                    newtypeDef ->
                                        SerializedClaroModule.ExportedTypeDefinitions.NewTypeDef.newBuilder()
                                            .setUserDefinedType(newtypeDef.resolvedType.toProto()
                                                                    .getUserDefinedType())
                                            .setWrappedType(
                                                scopedHeap.getValidatedIdentifierType(newtypeDef.getWrappedTypeIdentifier())
                                                    .toProto())
                                            .addAllTypeParamNames(
                                                Optional.ofNullable(Types.UserDefinedType.$typeParamNames.get(
                                                        String.format("%s$%s", newtypeDef.typeName, uniqueModuleName)))
                                                    .orElse(ImmutableList.of()))
                                            .setConstructor(
                                                getProcedureProtoFromProcedureType(
                                                    newtypeDef.typeName,
                                                    (Types.ProcedureType) scopedHeap.getValidatedIdentifierType(
                                                        String.format("%s$constructor", newtypeDef.typeName))
                                                ))
                                            .build()
                                )))
                    // Add all exported Opaque Types as NewTypeDefs wrapping the synthetic opaque wrapped value type.
                    .putAllExportedNewtypeDefsByName(
                        ProgramNode.moduleApiDef.get().exportedOpaqueTypeDefs.stream()
                            .collect(ImmutableMap.toImmutableMap(
                                opaqueTypeDef -> opaqueTypeDef.getTypeName().identifier,
                                opaqueTypeDef ->
                                    SerializedClaroModule.ExportedTypeDefinitions.NewTypeDef.newBuilder()
                                        .setUserDefinedType(
                                            TypeProtos.UserDefinedType.newBuilder()
                                                .setTypeName(opaqueTypeDef.getTypeName().identifier)
                                                .setDefiningModuleDisambiguator(uniqueModuleName)
                                                .addAllParameterizedTypes(
                                                    opaqueTypeDef.getParameterizedTypeNames().stream()
                                                        .map(n ->
                                                                 TypeProtos.TypeProto.newBuilder()
                                                                     .setGenericTypeParam(
                                                                         TypeProtos.GenericTypeParam.newBuilder()
                                                                             .setName(n))
                                                                     .build())
                                                        .collect(ImmutableList.toImmutableList())))
                                        .setWrappedType(
                                            TypeProtos.TypeProto.newBuilder().setOpaqueWrappedValueType(
                                                TypeProtos.SyntheticOpaqueTypeWrappedValueType.newBuilder()
                                                    .setIsMutable(opaqueTypeDef.getIsMutable())
                                                    .setActualWrappedType(
                                                        scopedHeap.getValidatedIdentifierType(
                                                                String.format(
                                                                    "%s$%s$wrappedType",
                                                                    opaqueTypeDef.getTypeName().identifier,
                                                                    uniqueModuleName
                                                                ))
                                                            .toProto())))
                                        .addAllTypeParamNames(opaqueTypeDef.getParameterizedTypeNames())
                                        // Intentionally do not set the constructor as it's illegal to construct an
                                        // opaque type.
                                        .build()
                            )))
                    .putAllInitializersByTypeName(
                        ProgramNode.moduleApiDef.get().initializersBlocks.entrySet().stream()
                            .collect(ImmutableMap.toImmutableMap(
                                e -> e.getKey().identifier,
                                e -> SerializedClaroModule.ExportedTypeDefinitions.ProcedureList.newBuilder()
                                    .addAllProcedures(
                                        e.getValue().stream()
                                            .map(JavaSourceCompilerBackend::serializeProcedureSignature)
                                            .collect(ImmutableList.toImmutableList()))
                                    .build()
                            )))
                    .putAllUnwrappersByTypeName(
                        ProgramNode.moduleApiDef.get().unwrappersBlocks.entrySet().stream()
                            .collect(ImmutableMap.toImmutableMap(
                                e -> e.getKey().identifier,
                                e -> SerializedClaroModule.ExportedTypeDefinitions.ProcedureList.newBuilder()
                                    .addAllProcedures(
                                        e.getValue().stream()
                                            .map(JavaSourceCompilerBackend::serializeProcedureSignature)
                                            .collect(ImmutableList.toImmutableList()))
                                    .build()
                            ))))
            .addAllExportedAtomDefinitions(
                ProgramNode.moduleApiDef.get().exportedAtomDefs.stream()
                    .map(atomDefinitionStmt -> atomDefinitionStmt.name.identifier)
                    .collect(ImmutableList.toImmutableList()))
            .setExportedFlagDefinitions(
                SerializedClaroModule.ExportedFlagDefinitions.newBuilder()
                    .addAllDirectExportedFlags(
                        ProgramNode.moduleApiDef.get().exportedFlagDefs.stream()
                            .map(f ->
                                     SerializedClaroModule.ExportedFlagDefinitions.ExportedFlag.newBuilder()
                                         .setName(f.identifier.identifier)
                                         .setType(f.resolvedType.toProto())
                                         .build())
                            .collect(Collectors.toList()))
                    .putAllTransitiveExportedFlagsByDefiningModule(
                        // This has already necessarily been validated, so if we're able to get to this point of
                        // serialization then we know there's no duplicates.
                        ProgramNode.transitiveExportedFlags.asMap().entrySet().stream()
                            .collect(ImmutableMap.toImmutableMap(
                                Map.Entry::getKey,
                                e -> SerializedClaroModule.ExportedFlagDefinitions.ExportedFlagsList.newBuilder()
                                    .addAllFlags(e.getValue())
                                    .build()
                            ))
                    ))
            .addAllExportedStaticValues(
                ProgramNode.moduleApiDef.get().exportedStaticValueDefs.stream()
                    .map(s ->
                             SerializedClaroModule.ExportedStaticValue.newBuilder()
                                 .setName(s.identifier.identifier)
                                 .setType(s.resolvedType.get().toProto())
                                 .setIsLazy(s.isLazy)
                                 .build())
                    .collect(Collectors.toList()))
            .addAllExportedProcedureDefinitions(
                ProgramNode.moduleApiDef.get().exportedSignatures.stream()
                    .map(JavaSourceCompilerBackend::serializeProcedureSignature)
                    .collect(ImmutableList.toImmutableList()))
            .addAllExportedContractDefinitions(
                ProgramNode.moduleApiDef.get().exportedContractDefs.stream()
                    .map(c -> SerializedClaroModule.ExportedContractDefinition.newBuilder()
                        .setName(c.contractName)
                        .addAllTypeParamNames(c.typeParamNames)
                        .addAllSignatures(
                            c.declaredContractSignaturesByProcedureName.values().stream()
                                .map(JavaSourceCompilerBackend::serializeProcedureSignature)
                                .collect(Collectors.toList()))
                        .build())
                    .collect(Collectors.toList()))
            .addAllExportedContractImplementations(
                ProgramNode.moduleApiDef.get().exportedContractImpls.entrySet().stream()
                    .map(impl -> {
                      ImmutableList<Type> concreteTypeParams =
                          impl.getValue().stream()
                              .map(tp -> tp.resolveType(scopedHeap))
                              .collect(ImmutableList.toImmutableList());
                      return SerializedClaroModule.ExportedContractImplementation.newBuilder()
                          .setImplementedContractName(impl.getKey().identifier)
                          .addAllConcreteTypeParams(
                              concreteTypeParams.stream().map(Type::toProto).collect(Collectors.toList()))
                          .addAllConcreteSignatures(
                              ((Types.$Contract) scopedHeap.getValidatedIdentifierType(impl.getKey().identifier))
                                  .getProcedureNames().stream()
                                  .map(n -> getProcedureProtoFromProcedureType(
                                      n,
                                      (Types.ProcedureType) scopedHeap.getValidatedIdentifierType(
                                          ContractProcedureImplementationStmt.getCanonicalProcedureName(
                                              impl.getKey().identifier, concreteTypeParams, n))
                                  ))
                                  .collect(Collectors.toList())
                          ).build();
                    })
                    .collect(ImmutableList.toImmutableList()))
            .addAllExportedHttpServiceDefinitions(
                ProgramNode.moduleApiDef.get().exportedHttpServiceDefs.stream()
                    .map(
                        e ->
                            SerializedClaroModule.ExportedHttpServiceDefinition.newBuilder()
                                .setHttpServiceName(e.serviceName.identifier)
                                .addAllEndpoints(
                                    e.syntheticEndpointProcedures.stream()
                                        .map(
                                            p ->
                                                SerializedClaroModule.ExportedHttpServiceDefinition.Endpoint.newBuilder()
                                                    .setEndpointName(p.procedureName)
                                                    .setPath(InternalStaticStateUtil.HttpServiceDef_endpointPaths.get(e.serviceName.identifier, p.procedureName))
                                                    .setProcedure(getProcedureProtoFromProcedureType(p.procedureName, p.resolvedProcedureType))
                                                    .build()
                                        )
                                        .collect(ImmutableList.toImmutableList())
                                )
                                .build()
                    )
                    .collect(ImmutableList.toImmutableList())
            )
            .addAllExportedTransitiveDepModules(
                this.EXPORTS.stream().map(
                        exportedModule ->
                            SerializedClaroModule.ExportedTransitiveDepModule.newBuilder()
                                .setModuleName(exportedModule)
                                .setUniqueModuleName(ScopedHeap.getDefiningModuleDisambiguator(Optional.of(exportedModule)))
                                .build())
                    .collect(ImmutableList.toImmutableList())
            )
            .setStaticJavaCodegen(
                ByteString.copyFrom(moduleCodegen.toString().getBytes(StandardCharsets.UTF_8)))
            .putAllCodegendMonomorphizationsTransitiveClosureByUniqueModuleName(
                // Accumulate all dep module monomorphizations from any direct or transitive module in the dep graph
                // rooted at this current module. They do not need to be codegend again by any module that consumes this
                // module as a dependency.
                Streams.concat(
                        JavaSourceCompilerBackend.depsClosureCodegendMonomorphizationsByModuleAndProc.entrySet().stream()
                            .flatMap(procsByDepModule -> procsByDepModule.getValue().entrySet().stream()
                                .flatMap(typeParamsByProc ->
                                             typeParamsByProc.getValue().stream().map(
                                                 typeParams ->
                                                     ImmutableList.of(
                                                         procsByDepModule.getKey(),
                                                         ImmutableList.of(
                                                             typeParamsByProc.getKey(),
                                                             typeParams.stream()
                                                                 .map(Type::toProto)
                                                                 .collect(ImmutableList.toImmutableList())
                                                         )
                                                     )))),
                        MonomorphizationCoordinator.monomorphizationsByModuleAndRequestCache.cellSet().stream()
                            .map(c -> ImmutableList.of(
                                c.getRowKey(),
                                ImmutableList.of(
                                    c.getColumnKey().getProcedureName(),
                                    c.getColumnKey().getConcreteTypeParamsList()
                                )
                            ))
                    )
                    .collect(ImmutableListMultimap.toImmutableListMultimap(
                        e -> (String) e.get(0),
                        e -> (ImmutableList<? extends Object>) e.get(1)
                    )).asMap().entrySet().stream().collect(ImmutableMap.toImmutableMap(
                        e -> e.getKey(),
                        e -> SerializedClaroModule.CodegendMonomorphizationsList.newBuilder()
                            .addAllMonomorphizations(
                                e.getValue().stream()
                                    .map(mono -> SerializedClaroModule.CodegendMonomorphizationsList.CodegendMonomorphization.newBuilder()
                                        .setProcedureName((String) mono.get(0))
                                        .addAllConcreteTypeParams((Iterable<? extends TypeProtos.TypeProto>) mono.get(1))
                                        .build())
                                    .collect(Collectors.toList())
                            ).build()
                    )));
    for (SrcFile moduleImplFile : moduleImplFiles) {
      serializedClaroModuleBuilder.addModuleImplFiles(
          SerializedClaroModule.ClaroSourceFile.newBuilder()
              .setOriginalFilename(moduleImplFile.getFilename())
              .setSourceUtf8(
                  ByteString.copyFrom(ByteStreams.toByteArray(moduleImplFile.getFileInputStream()))));
    }

    if (this.OPTIONAL_OUTPUT_FILE_PATH.isPresent()) {
      serializedClaroModuleBuilder.build().writeDelimitedTo(Files.newOutputStream(createOutputFile().toPath()));
    } else {
      // Finally write the proto message to stdout where Claro's Bazel rules will pipe this output into the appropriate
      // .claro_module output file.
      serializedClaroModuleBuilder
          .build()
          .writeDelimitedTo(System.out);
    }
  }

  public static void registerExportedContractImplementation(
      String disambiguatedContractName,
      String definingModuleProjectPackage,
      String definingModuleUniqueModuleName,
      ImmutableList<Type> concreteTypeParams,
      ImmutableMap<String, Types.ProcedureType> concreteSignaturesByName,
      ScopedHeap scopedHeap) {
    // For the sake of all contracts always using a consistent naming across all relative deps, the names of
    // contracts are disambiguated at all times.
    String disambiguatedContractImplName =
        ContractImplementationStmt.getContractTypeString(
            disambiguatedContractName,
            concreteTypeParams.stream().map(Type::toString).collect(Collectors.toList())
        );

    // Register the contract impl itself.
    scopedHeap.putIdentifierValue(
        disambiguatedContractImplName,
        Types.$ContractImplementation.forContractNameAndConcreteTypeParams(
            disambiguatedContractName,
            Optional.of(definingModuleUniqueModuleName),
            concreteTypeParams
        ),
        String.format(
            "ContractImpl__%s",
            Hashing.sha256().hashUnencodedChars(disambiguatedContractName + disambiguatedContractImplName)
        )
    );
    ImmutableList<String> contractTypeParamNames =
        ((Types.$Contract) scopedHeap.getValidatedIdentifierType(disambiguatedContractName)).getTypeParamNames();
    ContractDefinitionStmt.contractImplementationsByContractName.get(disambiguatedContractName)
        .add(IntStream.range(0, concreteTypeParams.size()).boxed().collect(ImmutableMap.toImmutableMap(
            contractTypeParamNames::get,
            concreteTypeParams::get
        )));

    // Register the actual contract implementation procedures.
    concreteSignaturesByName.forEach(
        (key, value) ->
            scopedHeap.putIdentifierValue(
                ContractProcedureImplementationStmt.getCanonicalProcedureName(
                    disambiguatedContractName, concreteTypeParams, key),
                value
            ));
  }

  private static SerializedClaroModule.Procedure serializeProcedureSignature(ContractProcedureSignatureDefinitionStmt
                                                                                 sig) {
    Types.ProcedureType sigType =
        sig.getExpectedProcedureTypeForConcreteTypeParams(ImmutableMap.of());
    return getProcedureProtoFromProcedureType(sig.procedureName, sigType);
  }

  private static SerializedClaroModule.Procedure getProcedureProtoFromProcedureType(String
                                                                                        procedureName, Types.ProcedureType sigType) {
    SerializedClaroModule.Procedure.Builder res =
        SerializedClaroModule.Procedure.newBuilder()
            .setName(procedureName);
    switch (sigType.baseType()) {
      case FUNCTION:
        res.setFunction(sigType.toProto().getFunction());
        break;
      case CONSUMER_FUNCTION:
        res.setConsumer(sigType.toProto().getConsumer());
        break;
      case PROVIDER_FUNCTION:
        res.setProvider(sigType.toProto().getProvider());
        break;
      default:
        throw new RuntimeException(
            "Internal Compiler Error! Encountered unexpected procedure type while serializing .claro_module file: " +
            sigType);
    }
    return res.build();
  }

  // TODO(steving) Refactor and drop this duplicated logic once it's possible for IPCMessages and SerializedClaroModule
  //   proto defs to be using the same unified definition of ExportedContractImplementation.
  private static IPCMessages.ExportedContractImplementation.Procedure getIPCMessagesProcedureProtoFromProcedureType
  (String procedureName, Types.ProcedureType sigType) {
    IPCMessages.ExportedContractImplementation.Procedure.Builder res =
        IPCMessages.ExportedContractImplementation.Procedure.newBuilder()
            .setName(procedureName);
    switch (sigType.baseType()) {
      case FUNCTION:
        res.setFunction(sigType.toProto().getFunction());
        break;
      case CONSUMER_FUNCTION:
        res.setConsumer(sigType.toProto().getConsumer());
        break;
      case PROVIDER_FUNCTION:
        res.setProvider(sigType.toProto().getProvider());
        break;
      default:
        throw new RuntimeException(
            "Internal Compiler Error! Encountered unexpected procedure type while serializing .claro_module file: " +
            sigType);
    }
    return res.build();
  }

  private void warnNumErrorsFound(int totalParserErrorsFound) {
    int totalErrorsFound = totalParserErrorsFound + Expr.typeErrorsFound.size() + ProgramNode.miscErrorsFound.size();
    System.err.println(Math.max(totalErrorsFound, 1) + " Error" + (totalErrorsFound > 1 ? "s" : ""));
  }

  @AutoValue
  static abstract class SrcFile {
    abstract String getFilename();

    abstract String getPath();

    abstract Optional<InputStream> getOptionalReadOnceInputStream();

    abstract boolean getUsesClaroInternalFileSuffix();

    abstract boolean getUsesClaroModuleApiFileSuffix();

    abstract boolean getUsesClaroModuleFileSuffix();

    static SrcFile forFilenameAndPath(String filename, String path) {
      return new AutoValue_JavaSourceCompilerBackend_SrcFile(
          filename,
          path,
          Optional.empty(),
          path.endsWith(".claro_internal"),
          path.endsWith(".claro_module_api"),
          path.endsWith(".claro_module")
      );
    }

    static SrcFile create(String filename, InputStream inputStream) {
      return new AutoValue_JavaSourceCompilerBackend_SrcFile(
          filename, "", Optional.of(inputStream), false, false, false);
    }

    public InputStream getFileInputStream() {
      if (getOptionalReadOnceInputStream().isPresent()) {
        return getOptionalReadOnceInputStream().get();
      }
      try {
        return Files.newInputStream(FileSystems.getDefault().getPath(getPath()), StandardOpenOption.READ);
      } catch (IOException e) {
        throw new RuntimeException("File not found:", e);
      }
    }
  }
}
