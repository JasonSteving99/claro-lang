package com.claro.tools.clarodocs.generator;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

import java.util.List;

public class ClaroDocsCLIOptions extends OptionsBase {
  @Option(
      name = "module",
      help = "A list of string paths to the .claro_module files for which ClaroDocs will be generated.",
      allowMultiple = true,
      defaultValue = ""
  )
  public List<String> modules;

  @Option(
      name = "stdlib_module",
      help = "A list of string paths to the .claro_module files of the stdlib.",
      allowMultiple = true,
      defaultValue = ""
  )
  public List<String> stdlib_modules;

  @Option(
      name = "optional_stdlib_module",
      help = "A list of string paths to the .claro_module files of the optional stdlib.",
      allowMultiple = true,
      defaultValue = ""
  )
  public List<String> optional_stdlib_modules;

  @Option(
      name = "out",
      help = "The output file to write the generated ClaroDocs JSON config to.",
      defaultValue = ""
  )
  public String out;
}