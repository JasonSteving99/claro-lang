package com.claro.tools.clarodocs.generator;

import com.claro.compiler_backends.java_source.JavaSourceCompilerBackendCLIOptions;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public class ClaroDocsGenerator {

  public static void main(String[] args) throws IOException {
    ClaroDocsCLIOptions options = parseCLIOptions(ClaroDocsCLIOptions.class, args);
    ImmutableSet<String> stdlibModules = ImmutableSet.copyOf(options.stdlib_modules);
    ImmutableSet<String> optionalStdlibModules = ImmutableSet.copyOf(options.optional_stdlib_modules);
    Set<String> allStdlibModules = Sets.union(stdlibModules, optionalStdlibModules);


    ImmutableMap<String, ImmutableList<Object>> modules =
        options.modules.stream()
            .map(ClaroDocsGenerator::readClaroModuleFile)
            .collect(
                ImmutableMap.toImmutableMap(
                    serializedClaroModule -> serializedClaroModule.getModuleDescriptor().getUniqueModuleName(),
                    serializedClaroModule -> {
                      JavaSourceCompilerBackendCLIOptions moduleCLIOptions =
                          parseCLIOptions(
                              JavaSourceCompilerBackendCLIOptions.class,
                              serializedClaroModule.getCommandLineArgsList().toArray(new String[]{})
                          );

                      // Collect all the deps.
                      ImmutableMap<String, String> uniqueNameByClaroModule =
                          moduleCLIOptions.dep_graph_claro_module_by_unique_name.stream()
                              .collect(ImmutableMap.toImmutableMap(
                                  s -> s.substring(s.indexOf(':') + 1),
                                  s -> s.substring(0, s.indexOf(':'))
                              ));
                      ImmutableMap<String, String> moduleDeps =
                          moduleCLIOptions.deps.stream()
                              .filter(s -> !allStdlibModules.contains(s.substring(s.indexOf(':') + 1)))
                              .collect(ImmutableMap.toImmutableMap(
                                  s -> s.substring(0, s.indexOf(':')),
                                  s -> uniqueNameByClaroModule.get(s.substring(s.indexOf(':') + 1))
                              ));

                      String moduleAPI = null;
                      try {
                        moduleAPI = readFile(
                            moduleAPI =
                                parseCLIOptions(
                                    JavaSourceCompilerBackendCLIOptions.class,
                                    serializedClaroModule.getCommandLineArgsList().toArray(new String[]{})
                                ).srcs.stream().filter(f -> f.endsWith(".claro_module_api")).findFirst().get()
                        );
                      } catch (IOException e) {
                        String uniqueModuleName = serializedClaroModule.getModuleDescriptor().getUniqueModuleName();
                        throw new RuntimeException(
                            "Failed to read .claro_module_api file for module: " + uniqueModuleName + " at " +
                            moduleAPI, e);
                      }
                      return ImmutableList.of(moduleAPI, moduleDeps);
                    }
                ));

    // Generate the JSON config.
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("\t\"root\": {\n\t\t\"rootName\": \"").append(options.rootName).append("\",\n");
    sb.append("\t\t\"rootDeps\": {\n");
    for (int i = 0; i < options.rootDeps.size(); i++) {
      String s = options.rootDeps.get(i);
      int separatorInd = s.indexOf(':');
      String depName = s.substring(0, separatorInd);
      String uniqueModuleName = s.substring(separatorInd + 1);
      sb.append("\t\t\t\"").append(depName).append("\": \"").append(uniqueModuleName).append("\"");
      // No trailing commas.
      if (i < options.rootDeps.size() - 1) {
        sb.append(",\n");
      }
    }
    sb.append("\t\t}\n");
    sb.append("\t},\n");
    sb.append("\t\"depGraph\": {\n");
    {
      int i = 0;
      for (Map.Entry<String, ImmutableList<Object>> entry : modules.entrySet()) {
        String module = entry.getKey();
        ImmutableList<Object> meta = entry.getValue();
        sb.append('"').append(module).append("\": {\n");
        sb.append("\t\t\"api\": \"")
            .append(((String) meta.get(0)).replace("\n", "\\n").replace("\"", "\\\""))
            .append("\",\n");
        sb.append("\t\t\"deps\": ");
        sb.append(((ImmutableMap<String, String>) meta.get(1)).entrySet().stream()
                      .map(e -> String.format("\"%s\": \"%s\"", e.getKey(), e.getValue()))
                      .collect(Collectors.joining(",\n\t\t\t", "{\n", "}\n")));
        // No trailing commas.
        if (i++ < modules.size() - 1) {
          sb.append("},\n");
        } else {
          sb.append("}\n");
        }
      }
    }
    sb.append("\t}\n}\n");

    // Write it to the output file.
    createOutputFile(options.out);
    try (FileOutputStream outputStream = new FileOutputStream(options.out)) {
      outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
  }

  private static <T extends OptionsBase> T parseCLIOptions(Class<T> optionsClazz, String... args) {
    OptionsParser parser = OptionsParser.newOptionsParser(optionsClazz);
    parser.parseAndExitUponError(args);
    return parser.getOptions(optionsClazz);
  }

  private static SerializedClaroModule readClaroModuleFile(String claroModuleFilePath) {
    try {
      return SerializedClaroModule.parseDelimitedFrom(getFileInputStream(claroModuleFilePath));
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse given .claro_module file:", e);
    }
  }

  private static String readFile(String filePath) throws IOException {
    Scanner scan = new Scanner(getFileInputStream(filePath));

    StringBuilder inputProgram = new StringBuilder();
    while (scan.hasNextLine()) {
      inputProgram.append(scan.nextLine());
      // Scanner is being stupid and dropping all the newlines... so this may give an extra compared to what's in the
      // source file, but who cares, the grammar will handle it.
      inputProgram.append("\n");
    }
    return inputProgram.toString();
  }

  private static InputStream getFileInputStream(String filePath) throws IOException {
    return Files.newInputStream(FileSystems.getDefault().getPath(filePath), StandardOpenOption.READ);
  }

  private static void createOutputFile(String outputFilePath) {
    try {
      new File(outputFilePath).createNewFile();
    } catch (IOException e) {
      throw new RuntimeException(
          "An error occurred while trying to open/create the specified output file: " + outputFilePath, e);
    }
  }
}
