package com.claro.stdlib.utils;

import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class ClaroModuleDeserializationUtil {
  public static void main(String... args) {
    CLIOptions options = parseCLIOptions(args);

    if (options.claroModuleFilePath.isEmpty() || options.outputFilePath.isEmpty()) {
      System.err.println("Error: both --claro_module_file and --output_file are required.");
      System.exit(1);
      return;
    }

    SerializedClaroModule claroModule;
    try {
      claroModule = getProtoAtPath(options.claroModuleFilePath);
    } catch (IOException e) {
      System.err.println("Error: Unable to read the given file: " + options.claroModuleFilePath);
      e.printStackTrace();
      System.exit(1);
      return;
    }

    try {
      claroModule.getModuleDescriptor().getUniqueModuleNameBytes()
          .writeTo(Files.newOutputStream(createOutputFile(options.outputFilePath).toPath()));
    } catch (IOException e) {
      System.err.println("Error: Unable to open given output file for writing: " + options.outputFilePath);
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static CLIOptions parseCLIOptions(String... args) {
    OptionsParser parser = OptionsParser.newOptionsParser(CLIOptions.class);
    parser.parseAndExitUponError(args);
    return parser.getOptions(CLIOptions.class);
  }

  private static SerializedClaroModule getProtoAtPath(String path) throws IOException {
    return SerializedClaroModule.parseDelimitedFrom(
        Files.newInputStream(FileSystems.getDefault().getPath(path), StandardOpenOption.READ));
  }

  private static File createOutputFile(String path) {
    File outputFile = null;
    try {
      outputFile = new File(path);
      outputFile.createNewFile();
    } catch (IOException e) {
      System.err.println("An error occurred while trying to open/create the specified output file: " + path);
      e.printStackTrace();
      System.exit(1);
    }
    return outputFile;
  }

  public static class CLIOptions extends OptionsBase {
    @Option(
        name = "claro_module_file",
        abbrev = 'f',
        help = "Path to the .claro_module file.",
        defaultValue = ""
    )
    public String claroModuleFilePath;
    @Option(
        name = "unique_module_name",
        abbrev = 'u',
        help = "Configures the .claro_module's unique_module_name to be parsed and included in the output file",
        defaultValue = "false"
    )
    public boolean parseUniqueModuleName;
    @Option(
        name = "output_file",
        abbrev = 'o',
        help = "Path to the output file to write the parsed field(s) read from the given .claro_module file.",
        defaultValue = ""
    )
    public String outputFilePath;
  }
}
