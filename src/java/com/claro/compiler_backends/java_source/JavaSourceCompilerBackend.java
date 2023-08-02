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
import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.module_system.ModuleApiParserUtil;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;
import com.claro.stdlib.StdLibUtil;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
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
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class JavaSourceCompilerBackend implements CompilerBackend {
  private final ImmutableMap<String, SrcFile> MODULE_DEPS;
  private final ImmutableSet<String> EXPORTS;
  private final boolean SILENT;
  private final Optional<String> GENERATED_CLASSNAME;
  private final Optional<String> PACKAGE_STRING;
  private final ImmutableList<SrcFile> SRCS;
  private final Optional<String> OPTIONAL_UNIQUE_MODULE_NAME;
  private final Optional<String> OPTIONAL_OUTPUT_FILE_PATH;

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
    this.MODULE_DEPS =
        options.deps.stream().collect(ImmutableMap.toImmutableMap(
            s -> s.substring(0, s.indexOf(':')),
            s -> {
              String modulePath = s.substring(s.indexOf(':') + 1);
              return SrcFile.forFilenameAndPath(
                  modulePath.substring(modulePath.lastIndexOf('/') + 1, modulePath.lastIndexOf('.')),
                  modulePath
              );
            }
        ));
    this.EXPORTS = options.exports.stream().collect(ImmutableSet.toImmutableSet());
    this.OPTIONAL_OUTPUT_FILE_PATH =
        Optional.ofNullable(options.output_file_path.isEmpty() ? null : options.output_file_path);
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
      // Before even parsing the given .claro files, handle the given dependencies by accounting for all dep modules.
      // I need to set up the ScopedHeap with all symbols exported by the direct module deps. Additionally, this is
      // where the parsers will get configured with the necessary state to enable parsing references to bindings
      // exported by the dep modules (e.g. `MyDep::foo(...)`) as module references rather than contract references.
      setupModuleDepBindings(scopedHeap, this.MODULE_DEPS);

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
        ScopedHeap.transitiveExportedDepModules = this.EXPORTS;
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
              nonMainSrcFiles,
              scopedHeap
          );
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

  private void setupModuleDepBindings(ScopedHeap scopedHeap, ImmutableMap<String, SrcFile> moduleDeps) throws Exception {
    ImmutableMap.Builder<String, SerializedClaroModule> parsedClaroModuleProtosBuilder = ImmutableMap.builder();
    for (Map.Entry<String, SrcFile> moduleDep : moduleDeps.entrySet()) {
      SerializedClaroModule parsedModule =
          SerializedClaroModule.parseDelimitedFrom(moduleDep.getValue().getFileInputStream());
      parsedClaroModuleProtosBuilder.put(moduleDep.getKey(), parsedModule);

      // First thing, register this dep module somewhere central that can be referenced by both codegen and the parsers.
      ScopedHeap.currProgramDepModules.put(moduleDep.getKey(), /*isUsed=*/false, parsedModule.getModuleDescriptor());

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
        scopedHeap.putIdentifierValueAsTypeDef(
            String.format("$DEP_MODULE$%s$%s", moduleDep.getKey(), exportedNewTypedef.getKey()),
            newType,
            null
        );
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
        // Register its constructor.
        scopedHeap.putIdentifierValue(
            String.format("$DEP_MODULE$%s$%s$constructor", moduleDep.getKey(), exportedNewTypedef.getKey()),
            getProcedureTypeFromProto(exportedNewTypedef.getValue().getConstructor())
        );
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
    }

    // After having successfully parsed all dep module files and registered all of their exported types, it's time to
    // finally register all of their exported procedure signatures.
    for (Map.Entry<String, SerializedClaroModule> moduleDep : parsedClaroModuleProtosBuilder.build().entrySet()) {
      // Setup the regular exported procedures.
      for (SerializedClaroModule.Procedure depExportedProc :
          moduleDep.getValue().getExportedProcedureDefinitionsList()) {
        scopedHeap.putIdentifierValue(
            String.format("$DEP_MODULE$%s$%s", moduleDep.getKey(), depExportedProc.getName()),
            getProcedureTypeFromProto(depExportedProc)
        );
      }
      // Make note of any initializers exported by this dep module.
      for (Map.Entry<String, SerializedClaroModule.ExportedTypeDefinitions.ProcedureList> initializerEntry :
          moduleDep.getValue().getExportedTypeDefinitions().getInitializersByTypeNameMap().entrySet()) {
        initializerEntry.getValue().getProceduresList().forEach(
            p -> scopedHeap.putIdentifierValue(
                String.format("$DEP_MODULE$%s$%s", moduleDep.getKey(), p.getName()), getProcedureTypeFromProto(p)));
        InternalStaticStateUtil.InitializersBlockStmt_initializersByInitializedTypeNameAndModuleDisambiguator
            .put(
                initializerEntry.getKey(),
                moduleDep.getValue().getModuleDescriptor().getUniqueModuleName(),
                initializerEntry.getValue().getProceduresList().stream()
                    .map(
                        // These are actually only used for error-reporting, so I'll use a user-friendly string here.
                        initializer -> String.format("%s::%s", moduleDep.getKey(), initializer.getName()))
                    .collect(ImmutableSet.toImmutableSet())
            );
      }
      // Make note of any unwrappers exported by this dep module.
      for (Map.Entry<String, SerializedClaroModule.ExportedTypeDefinitions.ProcedureList> unwrapperEntry :
          moduleDep.getValue().getExportedTypeDefinitions().getUnwrappersByTypeNameMap().entrySet()) {
        unwrapperEntry.getValue().getProceduresList().forEach(
            p -> scopedHeap.putIdentifierValue(
                String.format("$DEP_MODULE$%s$%s", moduleDep.getKey(), p.getName()), getProcedureTypeFromProto(p)));
        InternalStaticStateUtil.UnwrappersBlockStmt_unwrappersByUnwrappedTypeNameAndModuleDisambiguator
            .put(
                unwrapperEntry.getKey(),
                moduleDep.getValue().getModuleDescriptor().getUniqueModuleName(),
                unwrapperEntry.getValue().getProceduresList().stream()
                    .map(
                        // These are actually only used for error-reporting, so I'll use a user-friendly string here.
                        initializer -> String.format("%s::%s", moduleDep.getKey(), initializer.getName()))
                    .collect(ImmutableSet.toImmutableSet())
            );
      }
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
        throw new RuntimeException("Internal Compiler Error! Encountered unexpected procedure type while parsing newtype definition from .claro_module.");
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
      SrcFile moduleApiSrcFile,
      StringBuilder moduleCodegen,
      ImmutableList<SrcFile> moduleImplFiles,
      ScopedHeap scopedHeap) throws IOException {
    SerializedClaroModule.Builder serializedClaroModuleBuilder =
        SerializedClaroModule.newBuilder()
            .setModuleDescriptor(
                SerializedClaroModule.UniqueModuleDescriptor.newBuilder()
                    .setProjectPackage(projectPackage)
                    .setUniqueModuleName(uniqueModuleName))
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
                                            .setUserDefinedType(newtypeDef.resolvedType.toProto().getUserDefinedType())
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
            .addAllExportedProcedureDefinitions(
                ProgramNode.moduleApiDef.get().exportedSignatures.stream()
                    .map(JavaSourceCompilerBackend::serializeProcedureSignature)
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
                ByteString.copyFrom(moduleCodegen.toString().getBytes(StandardCharsets.UTF_8)));
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

  private static SerializedClaroModule.Procedure serializeProcedureSignature(ContractProcedureSignatureDefinitionStmt sig) {
    Types.ProcedureType sigType =
        sig.getExpectedProcedureTypeForConcreteTypeParams(ImmutableMap.of());
    return getProcedureProtoFromProcedureType(sig.procedureName, sigType);
  }

  private static SerializedClaroModule.Procedure getProcedureProtoFromProcedureType(String procedureName, Types.ProcedureType sigType) {
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
