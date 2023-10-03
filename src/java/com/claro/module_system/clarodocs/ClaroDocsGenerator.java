package com.claro.module_system.clarodocs;

import com.claro.module_system.clarodocs.html_rendering.homepage.HomePageHtml;
import com.claro.module_system.clarodocs.html_rendering.procedures.ProcedureHtml;
import com.claro.module_system.clarodocs.html_rendering.typedefs.TypeHtml;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.devtools.common.options.OptionsParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

public class ClaroDocsGenerator {

  public static void main(String[] args) throws IOException {
    ClaroDocsCLIOptions options = parseCLIOptions(args);

    ImmutableTable.Builder<String, String, String> typeDefHtmlByModuleNameAndTypeNameBuilder = ImmutableTable.builder();
    String renderedHtml =
        HomePageHtml.renderHomePage(
            options.modules.stream()
                .map(ClaroDocsGenerator::readClaroModuleFile)
                .collect(
                    ImmutableMap.toImmutableMap(
                        serializedClaroModule -> serializedClaroModule.getModuleDescriptor().getUniqueModuleName(),
                        serializedClaroModule -> {
                          StringBuilder res = new StringBuilder();
                          // First, type defs.
                          serializedClaroModule.getExportedTypeDefinitions()
                              .getExportedNewtypeDefsByNameMap().entrySet().stream()
                              .map(e -> {
                                String typeDefHtml = TypeHtml.renderTypeDef(res, e.getKey(), e.getValue()).toString();
                                typeDefHtmlByModuleNameAndTypeNameBuilder.put(
                                    serializedClaroModule.getModuleDescriptor().getUniqueModuleName(),
                                    e.getKey(),
                                    typeDefHtml
                                );
                                return typeDefHtml;
                              })
                              .collect(Collectors.joining("\n"));
                          // Top-level procedure defs.
                          res.append(
                              serializedClaroModule.getExportedProcedureDefinitionsList().stream()
                                  .map(ProcedureHtml::generateProcedureHtml)
                                  .collect(Collectors.joining("\n")));
                          return res.toString();
                        }
                    )),
            typeDefHtmlByModuleNameAndTypeNameBuilder.build(),
            getFileInputStream(options.treejs),
            getFileInputStream(options.treejs_css)
        );

    createOutputFile(options.out);
    try (FileOutputStream outputStream = new FileOutputStream(options.out)) {
      outputStream.write(renderedHtml.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static ClaroDocsCLIOptions parseCLIOptions(String... args) {
    OptionsParser parser = OptionsParser.newOptionsParser(ClaroDocsCLIOptions.class);
    parser.parseAndExitUponError(args);
    return parser.getOptions(ClaroDocsCLIOptions.class);
  }

  private static SerializedClaroModule readClaroModuleFile(String claroModuleFilePath) {
    try {
      return SerializedClaroModule.parseDelimitedFrom(getFileInputStream(claroModuleFilePath));
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse given .claro_module file:", e);
    }
  }

  private static InputStream getFileInputStream(String claroModuleFilePath) throws IOException {
    return Files.newInputStream(FileSystems.getDefault().getPath(claroModuleFilePath), StandardOpenOption.READ);
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
